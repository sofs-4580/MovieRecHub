"""Validate real deployment guards/templates and Compose contracts without cloud or secrets."""

import argparse
import base64
import copy
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[3]
ANSIBLE = REPO / "infra/ansible"
APPS = ["user-service", "profile-service", "movie-service", "frontend"]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", type=Path, help="Keep rendered manifests for an isolated integration test")
parser.add_argument("--worker-name", default="test-app-worker", help="Worker name for the test PVs")
options = parser.parse_args()
deployment = yaml.safe_load((ANSIBLE / "deploy.yml").read_text())[0]
tag_guard = deployment["pre_tasks"][0]
key_guard = deployment["pre_tasks"][1]
port_guard = next(task for task in deployment["pre_tasks"] if task.get("name") == "Refuse a frontend NodePort already used by another application")
secret_task = next(task for task in deployment["tasks"] if task.get("name") == "Apply the TMDB Secret through stdin without writing a remote credential file")
assert secret_task["no_log"] and key_guard["no_log"]
configs_path = REPO / "kubernetes/config/application.yaml"
configmaps = {obj["metadata"]["name"]: obj for obj in yaml.safe_load_all(configs_path.read_text())}
variables = {
    "gcp_project_id": "g34-deploy-test",
    "registry_image_prefix": "europe-west4-docker.pkg.dev/g34-deploy-test/psia-g34",
    "application_image_tag": "step3-20261013-1",
    "application_config_checksum": hashlib.sha256(configs_path.read_text().rstrip('\n').encode()).hexdigest(),
    "tmdb_api_key": "0" * 32,
    "database_data_worker": options.worker_name,
    "ansible_python_interpreter": sys.executable,
}

with tempfile.TemporaryDirectory() as temporary_directory:
    temporary = Path(temporary_directory)
    output = options.output.resolve() if options.output else temporary / "rendered"
    output.mkdir(parents=True, exist_ok=True)
    inventory = temporary / "inventory.json"
    inventory.write_text(json.dumps({"all": {"children": {
        "control_plane": {"hosts": {"test-control": {}}},
        "workers": {"hosts": {options.worker_name: {"kubernetes_data_path": "/var/lib/kubernetes-data"}}},
        "test_controller": {"hosts": {"localhost": {}}},
    }}}))
    checks = []
    for name, patch, guard, accepted in [
        ("valid deployment version", {}, tag_guard, True),
        ("latest version", {"application_image_tag": "latest"}, tag_guard, False),
        ("invalid version", {"application_image_tag": "bad/tag"}, tag_guard, False),
        ("wrong registry project", {"registry_image_prefix": "europe-west4-docker.pkg.dev/other/psia-g34"}, tag_guard, False),
        ("nonempty TMDB key", {}, key_guard, True),
        ("empty TMDB key", {"tmdb_api_key": ""}, key_guard, False),
        ("whitespace TMDB key", {"tmdb_api_key": "  "}, key_guard, False),
        ("nonstring TMDB key", {"tmdb_api_key": 123}, key_guard, False),
        ("free frontend port", {"existing_services": {"stdout": json.dumps({"items": []})}}, port_guard, True),
        ("existing own frontend port", {"existing_services": {"stdout": json.dumps({"items": [{"metadata": {"name": "frontend", "namespace": "movie-rec-hub"}, "spec": {"ports": [{"nodePort": 30510}]}}]})}}, port_guard, True),
        ("Nginx occupies frontend port", {"existing_services": {"stdout": json.dumps({"items": [{"metadata": {"name": "nginx", "namespace": "nginx-smoke-test"}, "spec": {"ports": [{"nodePort": 30510}]}}]})}}, port_guard, False),
    ]:
        checks.append({"name": name, "vars": patch, "block": [
            {"ansible.builtin.set_fact": {"guard_accepted": True}},
            {"block": [copy.deepcopy(guard)], "rescue": [{"ansible.builtin.set_fact": {"guard_accepted": False}}]},
            {"ansible.builtin.assert": {"that": f"guard_accepted == {accepted}"}},
        ]})
    checks.append({"name": "Render real application templates", "ansible.builtin.template": {
        "src": str(REPO / "kubernetes/services") + "/{{ item }}.yaml.j2",
        "dest": str(output) + "/{{ item }}.yaml",
    }, "loop": APPS})
    checks.append({"name": "Render existing data worker volume template", "ansible.builtin.template": {
        "src": str(REPO / "kubernetes/storage/local-volumes.yaml.j2"), "dest": str(output / "local-volumes.yaml"),
    }})
    # Exercise the actual Secret task's stdin/no_log; replace only the destination command.
    collector = temporary / "capture.py"
    collector.write_text("import pathlib, sys\npathlib.Path(sys.argv[1]).write_text(sys.stdin.read())\nprint('secret/movie-service-tmdb created')\n")
    capture = copy.deepcopy(secret_task)
    capture["ansible.builtin.command"]["argv"] = [sys.executable, str(collector), str(output / "secret.json")]
    checks.append(capture)
    playbook = temporary / "check.yml"
    variables["ansible_remote_tmp"] = str(temporary / "remote")
    playbook.write_text(yaml.safe_dump([{"hosts": "localhost", "connection": "local", "gather_facts": False, "vars": variables, "tasks": checks}], sort_keys=False))
    result = subprocess.run(["ansible-playbook", "-i", str(inventory), str(playbook)], capture_output=True, text=True)
    if result.returncode:
        print(result.stdout, result.stderr)
        raise SystemExit(result.returncode)
    secret = json.loads((output / "secret.json").read_text())
    assert secret["kind"] == "Secret" and secret["metadata"]["namespace"] == "movie-rec-hub"
    assert base64.b64decode(secret["data"]["TMDB_API_KEY"]).decode() == variables["tmdb_api_key"]
    assert variables["tmdb_api_key"] not in configs_path.read_text()
    compose = yaml.safe_load((REPO / "docker-compose.yml").read_text())["services"]
    for name in APPS:
        rendered = (output / f"{name}.yaml").read_text()
        assert variables["tmdb_api_key"] not in rendered
        app, service = list(yaml.safe_load_all(rendered))
        pod = app["spec"]["template"]
        container = pod["spec"]["containers"][0]
        assert app["spec"]["replicas"] == 1 and app["spec"]["strategy"]["type"] == "Recreate"
        assert app["spec"]["selector"]["matchLabels"] == service["spec"]["selector"] == pod["metadata"]["labels"]
        assert container["image"] == variables["registry_image_prefix"] + f"/{name}:" + variables["application_image_tag"]
        assert container["imagePullPolicy"] == "IfNotPresent"
        assert container["ports"][0]["containerPort"] == service["spec"]["ports"][0]["port"] == int(configmaps[name + "-config"]["data"]["SERVER_PORT"])
        assert service["spec"]["ports"][0]["targetPort"] == container["ports"][0]["name"] == "http"
        assert [entry["configMapRef"]["name"] for entry in container["envFrom"]] == ["application-common", name + "-config"]
        for key, value in compose[name]["environment"].items():
            if key != "TMDB_API_KEY":
                assert configmaps[name + "-config"]["data"][key] == value, (name, key)
        assert container["livenessProbe"]["httpGet"]["path"] == container["startupProbe"]["httpGet"]["path"] == "/actuator/health/liveness"
        assert container["readinessProbe"]["httpGet"]["path"] == "/actuator/health/readiness"
        assert service["spec"]["type"] == ("NodePort" if name == "frontend" else "ClusterIP")
        assert "nodePort" not in service["spec"]["ports"][0] or name == "frontend"
        if name == "frontend":
            assert service["spec"]["ports"][0]["nodePort"] == 30510
        else:
            assert configmaps[name + "-config"]["data"]["DB_PASSWORD"] == ""
            assert configmaps[name + "-config"]["data"]["MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE"] == "readinessState,db"
        if name == "movie-service":
            reference = container["env"][0]["valueFrom"]["secretKeyRef"]
            assert reference["name"] == secret["metadata"]["name"] and reference["key"] in secret["data"]
            assert pod["metadata"]["annotations"]["checksum/tmdb"] == hashlib.sha256(variables["tmdb_api_key"].encode()).hexdigest()
    assert configmaps["application-common"]["data"]["MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED"] == "true"
    print("PASS: 11 real guards, private Secret stdin, four templates/Compose contracts, probes, session strategy and configuration hashes.")
    if options.output:
        print("Rendered manifests kept for local integration; Secret contains a fake test key only.")
