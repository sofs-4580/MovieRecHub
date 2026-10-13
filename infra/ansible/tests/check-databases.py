"""Check Compose/Kubernetes contracts and render volumes without SSH or cloud."""

import copy
import subprocess
import tempfile
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[3]
ANSIBLE = REPO / "infra/ansible"
NAMESPACE = "movie-rec-hub"
DATABASES = {
    "users-db": "UserService",
    "profiles-db": "ProfileService",
    "movie-db": "MovieService",
}

namespace = yaml.safe_load((REPO / "kubernetes/namespace.yaml").read_text())
assert namespace["metadata"]["name"] == NAMESPACE
storage_class = yaml.safe_load(
    (REPO / "kubernetes/storage/storage-class.yaml").read_text()
)
assert storage_class["provisioner"] == "kubernetes.io/no-provisioner"
assert storage_class["volumeBindingMode"] == "WaitForFirstConsumer"
assert storage_class["reclaimPolicy"] == "Retain"
claims = {}

for name, owner in DATABASES.items():
    compose = yaml.safe_load((REPO / owner / "compose.yaml").read_text())
    expected = compose["services"][name]["environment"]
    resources = list(yaml.safe_load_all(
        (REPO / "kubernetes/databases" / f"{name}.yaml").read_text()
    ))
    assert len(resources) == 4
    assert all(resource["metadata"]["namespace"] == NAMESPACE for resource in resources)
    claim = next(r for r in resources if r["kind"] == "PersistentVolumeClaim")
    claims[name] = claim
    stateful_set = next(r for r in resources if r["kind"] == "StatefulSet")
    services = {r["metadata"]["name"]: r for r in resources if r["kind"] == "Service"}
    pod = stateful_set["spec"]["template"]
    assert stateful_set["spec"]["replicas"] == 1
    assert stateful_set["spec"]["selector"]["matchLabels"] == pod["metadata"]["labels"]
    assert services[name]["spec"]["type"] == "ClusterIP"
    assert services[f"{name}-headless"]["spec"]["clusterIP"] == "None"
    assert stateful_set["spec"]["serviceName"] == f"{name}-headless"
    for service in services.values():
        assert service["spec"]["selector"] == pod["metadata"]["labels"]
        assert "nodePort" not in service["spec"]["ports"][0]
        assert service["spec"]["ports"][0]["port"] == 5432
        assert service["spec"]["ports"][0]["targetPort"] == "postgres"
    postgres = pod["spec"]["containers"][0]
    environment = {entry["name"]: entry["value"] for entry in postgres["env"]}
    for key in ["POSTGRES_DB", "POSTGRES_USER", "POSTGRES_HOST_AUTH_METHOD"]:
        assert environment[key] == expected[key], (owner, key)
    assert environment["POSTGRES_HOST_AUTH_METHOD"] == "trust"
    assert "POSTGRES_PASSWORD" not in environment
    assert postgres["image"].startswith("postgres:16.")
    assert environment["PGDATA"] == "/var/lib/postgresql/data/pgdata"
    assert postgres["volumeMounts"][0]["mountPath"] == "/var/lib/postgresql/data"
    assert postgres["ports"][0]["name"] == "postgres"
    assert postgres["ports"][0]["containerPort"] == 5432
    assert pod["spec"]["volumes"][0]["persistentVolumeClaim"]["claimName"] == claim["metadata"]["name"]
    assert claim["spec"]["storageClassName"] == storage_class["metadata"]["name"]
    for probe in ["startupProbe", "readinessProbe", "livenessProbe"]:
        assert "-h 127.0.0.1" in postgres[probe]["exec"]["command"][-1]

with tempfile.TemporaryDirectory() as temporary_directory:
    temporary = Path(temporary_directory)
    inventory = yaml.safe_load((ANSIBLE / "tests/inventory.yml").read_text())
    # A different hostname/path detects hardcoded dependence on personal infrastructure.
    worker = inventory["all"]["children"]["workers"]["hosts"].pop("psia-g34-worker-1")
    worker["kubernetes_data_path"] = "/test-data"
    inventory["all"]["children"]["workers"]["hosts"]["test-storage-worker"] = worker
    inventory_file = temporary / "inventory.yml"
    inventory_file.write_text(yaml.safe_dump(inventory))
    output = temporary / "local-volumes.yaml"
    deployment = yaml.safe_load((ANSIBLE / "databases.yml").read_text())
    discovery_names = {
        "Find workers with a dedicated data disk in the Terraform inventory",
        "Require one control plane and one data worker",
        "Select the data worker",
    }
    discovery = [task for task in deployment[1]["tasks"] if task["name"] in discovery_names]
    mount_guard = next(
        task for task in deployment[0]["tasks"][0]["block"]
        if task["name"] == "Refuse database directories on an unmounted or different disk"
    )
    mount_checks = []
    for mount_rc, mount_uuid, disk_rc, disk_uuid, accepted in [
        (0, "data-uuid", 0, "data-uuid", True),
        (1, "", 0, "data-uuid", False),
        (0, "boot-uuid", 0, "data-uuid", False),
        (0, "", 0, "", False),
        (0, "data-uuid", 2, "", False),
    ]:
        mount_checks.append({
            "vars": {
                "database_mount": {"rc": mount_rc, "stdout": mount_uuid},
                "database_disk": {"rc": disk_rc, "stdout": disk_uuid},
            },
            "block": [
                {"ansible.builtin.set_fact": {"mount_accepted": True}},
                {"block": [copy.deepcopy(mount_guard)],
                 "rescue": [{"ansible.builtin.set_fact": {"mount_accepted": False}}]},
                {"ansible.builtin.assert": {"that": f"mount_accepted == {accepted}"}},
            ],
        })
    play = [{
        "hosts": "control_plane", "connection": "local", "gather_facts": False,
        "vars": {
            "ansible_python_interpreter": "/opt/ansible/bin/python3",
        },
        "tasks": discovery + mount_checks + [{"ansible.builtin.template": {
            "src": str(REPO / "kubernetes/storage/local-volumes.yaml.j2"),
            "dest": str(output), "mode": "0644",
        }}],
    }]
    playbook = temporary / "render.yml"
    playbook.write_text(yaml.safe_dump(play))
    result = subprocess.run(
        ["ansible-playbook", "-i", str(inventory_file), str(playbook)],
        capture_output=True, text=True,
    )
    if result.returncode:
        print(result.stdout)
        print(result.stderr)
        raise SystemExit(result.returncode)
    volumes = list(yaml.safe_load_all(output.read_text()))
    assert len(volumes) == len(DATABASES)
    for volume in volumes:
        name = volume["metadata"]["name"].removesuffix("-data")
        claim = claims[name]
        spec = volume["spec"]
        assert claim["spec"]["volumeName"] == volume["metadata"]["name"]
        assert spec["claimRef"] == {"namespace": NAMESPACE, "name": claim["metadata"]["name"]}
        assert spec["capacity"]["storage"] == claim["spec"]["resources"]["requests"]["storage"]
        assert spec["accessModes"] == claim["spec"]["accessModes"]
        assert spec["persistentVolumeReclaimPolicy"] == "Retain"
        assert spec["local"]["path"] == f"/test-data/{name}"
        affinity = spec["nodeAffinity"]["required"]["nodeSelectorTerms"][0]["matchExpressions"][0]
        assert affinity == {"key": "kubernetes.io/hostname", "operator": "In", "values": ["test-storage-worker"]}

print("PASS: three Compose/database contracts, internal Services, inventory-derived volumes and five disk mount guards.")
