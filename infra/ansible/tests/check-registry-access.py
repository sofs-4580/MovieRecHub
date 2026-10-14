"""Check real templates/guards offline; optionally run the verified GCP plugin with fake metadata."""

import argparse
import copy
import hashlib
import http.server
import json
import os
import subprocess
import sys
import tempfile
import threading
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--provider-bin", type=Path, help="Downloaded official auth-provider-gcp executable")
options = parser.parse_args()
shared = yaml.safe_load((ROOT / "group_vars/all.yml").read_text())
tasks = yaml.safe_load((ROOT / "tasks/registry-credentials.yml").read_text())
inventory_guard = tasks[0]
identity_guard = next(task for task in tasks if task.get("name") == "Require the Terraform service account on this VM")
variables = {
    **shared,
    "gcp_project_id": "another-student-project",
    "registry_image_prefix": "europe-west4-docker.pkg.dev/another-student-project/psia-g34",
    "registry_service_account": "psia-g34-nodes@another-student-project.iam.gserviceaccount.com",
    "ansible_facts": {"architecture": "x86_64"},
    "kubernetes_node_ip": "10.164.0.99",
    "registry_check_tag": "test-version",
    "registry_check_namespace": "registry-check-local",
    "node_identity": {"content": "psia-g34-nodes@another-student-project.iam.gserviceaccount.com\n"},
    "ansible_python_interpreter": sys.executable,
}

with tempfile.TemporaryDirectory() as temporary_directory:
    temporary = Path(temporary_directory)
    checks = []
    for name, patch, expected, guard in [
        ("valid personal project", {}, True, inventory_guard),
        ("wrong repository", {"registry_image_prefix": variables["registry_image_prefix"] + "-other"}, False, inventory_guard),
        ("wrong account", {"registry_service_account": "other@example.invalid"}, False, inventory_guard),
        ("unsupported node architecture", {"ansible_facts": {"architecture": "aarch64"}}, False, inventory_guard),
        ("correct VM identity", {}, True, identity_guard),
        ("different VM identity", {"node_identity": {"content": "other@example.invalid"}}, False, identity_guard),
    ]:
        checks.append({"name": name, "vars": patch, "block": [
            {"ansible.builtin.set_fact": {"guard_accepted": True}},
            {"block": [copy.deepcopy(guard)], "rescue": [{"ansible.builtin.set_fact": {"guard_accepted": False}}]},
            {"ansible.builtin.assert": {"that": f"guard_accepted == {expected}"}},
        ]})
    checks += [{"name": "Render real template " + name, "ansible.builtin.template": {
        "src": str(ROOT / "templates" / name), "dest": str(temporary / name.removesuffix(".j2")),
    }} for name in ["image-credentials.yaml.j2", "kubelet-default.j2", "registry-access-check.yaml.j2"]]
    playbook = temporary / "check.yml"
    inventory = temporary / "inventory.json"
    inventory.write_text(json.dumps({"all": {"children": {
        "control_plane": {"hosts": {"test-control": {}}},
        "workers": {"hosts": {"test-worker-1": {}, "test-worker-2": {}}},
        "test_controller": {"hosts": {"localhost": {}}},
    }}}))
    variables["ansible_remote_tmp"] = str(temporary / "remote")
    playbook.write_text(yaml.safe_dump([{
        "hosts": "localhost", "connection": "local", "gather_facts": False,
        "vars": variables, "tasks": checks,
    }], sort_keys=False))
    result = subprocess.run(["ansible-playbook", "-i", str(inventory), str(playbook)], capture_output=True, text=True)
    if result.returncode:
        print(result.stdout, result.stderr)
        raise SystemExit(result.returncode)
    config = yaml.safe_load((temporary / "image-credentials.yaml").read_text())
    provider = config["providers"][0]
    assert config["apiVersion"] == "kubelet.config.k8s.io/v1"
    assert provider["apiVersion"] == "credentialprovider.kubelet.k8s.io/v1"
    assert provider["matchImages"] == [variables["registry_image_prefix"] + "/"]
    assert provider["name"] == "auth-provider-gcp" and provider["args"] == ["get-credentials"]
    flags = (temporary / "kubelet-default").read_text()
    assert "--node-ip=10.164.0.99" in flags
    assert "--image-credential-provider-config=" + shared["credential_provider_config"] in flags
    assert "--image-credential-provider-bin-dir=" + shared["credential_provider_bin_dir"] in flags
    resources = list(yaml.safe_load_all((temporary / "registry-access-check.yaml").read_text()))
    assert resources[0]["metadata"]["name"] == "registry-check-local"
    pods = resources[1:]
    assert {pod["spec"]["nodeName"] for pod in pods} == {"test-control", "test-worker-1", "test-worker-2"}
    assert all(pod["spec"]["containers"][0]["imagePullPolicy"] == "Always" for pod in pods)
    assert all(pod["spec"]["containers"][0]["image"] == variables["registry_image_prefix"] + "/frontend:test-version" for pod in pods)
    assert all("imagePullSecrets" not in pod["spec"] for pod in pods)
    print("PASS: 6 real guards; personal repository/identity, kubelet flags and three per-node pull Pods rendered.")

    if options.provider_bin:
        binary = options.provider_bin.resolve()
        assert hashlib.sha512(binary.read_bytes()).hexdigest() == shared["gcp_credential_provider_sha512"]
        requests = []
        state = {"token": "FAKE_LOCAL_TOKEN_ONE"}

        class MetadataProxy(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append((self.path, self.headers.get("Metadata-Flavor")))
                if self.path.endswith("/token"):
                    body = json.dumps({"access_token": state["token"], "expires_in": 3600, "token_type": "Bearer"})
                elif self.path.endswith("/email"):
                    body = variables["registry_service_account"]
                elif self.path.endswith("/scopes"):
                    body = "https://www.googleapis.com/auth/cloud-platform"
                else:
                    body = "default/"
                self.send_response(200)
                self.send_header("Metadata-Flavor", "Google")
                self.end_headers()
                self.wfile.write(body.encode())

            def log_message(self, *args):
                pass

        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), MetadataProxy)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            plugin_env = dict(os.environ)
            plugin_env.update({entry["name"]: entry["value"] for entry in provider["env"]})
            # Only in this fixture: redirect the official binary's metadata HTTP requests to localhost.
            plugin_env.update({"HTTP_PROXY": f"http://127.0.0.1:{server.server_port}", "http_proxy": f"http://127.0.0.1:{server.server_port}", "NO_PROXY": "", "no_proxy": ""})
            for token in ["FAKE_LOCAL_TOKEN_ONE", "FAKE_LOCAL_TOKEN_TWO"]:
                state["token"] = token
                request = {"apiVersion": provider["apiVersion"], "kind": "CredentialProviderRequest", "image": variables["registry_image_prefix"] + "/frontend:test-version"}
                result = subprocess.run([str(binary), *provider["args"]], input=json.dumps(request), env=plugin_env, capture_output=True, text=True, timeout=20)
                assert result.returncode == 0, result.stderr
                response = json.loads(result.stdout)
                assert response["apiVersion"] == provider["apiVersion"] and response["kind"] == "CredentialProviderResponse"
                assert response["cacheKeyType"] == "Registry" and response["cacheDuration"] in ["1m0s", "60s"]
                assert response["auth"] and all(item["password"] == token for item in response["auth"].values())
                assert all(item["username"] == "_token" for item in response["auth"].values())
            assert requests and all(header == "Google" for _, header in requests)
            print("PASS: verified official binary emits v1 credentials from fake metadata, cache=1m; a new invocation reads the changed token.")
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
    else:
        print("INFO: binary execution skipped; supply --provider-bin to test it with fake metadata.")
