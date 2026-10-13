"""Exercise the playbooks' safety checks locally, without SSH or disk changes."""

import copy
import json
import subprocess
import tempfile
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]


def guard(filename, name, section=None):
    data = yaml.safe_load((ROOT / filename).read_text())
    tasks = data[0][section] if section else data
    return next(task for task in tasks if task.get("name") == name)


disk_guard = guard(
    "tasks/data-disk.yml",
    "Accept only a blank whole disk or an existing ext4 filesystem",
)
cp_guard = guard(
    "create-cluster.yml", "Refuse a partially initialized control plane", "tasks"
)
version_guard = guard(
    "configure-nodes.yml", "Prevent an implicit upgrade of an existing cluster", "pre_tasks"
)
path = "/var/lib/kubernetes-data"
checks = []


def add(name, task, variables, expected):
    task = copy.deepcopy(task)
    task.pop("when", None)
    checks.append({
        "name": name,
        "vars": variables,
        "block": [
            {"ansible.builtin.set_fact": {"guard_accepted": True}},
            {
                "block": [task],
                "rescue": [{"ansible.builtin.set_fact": {"guard_accepted": False}}],
            },
            {"ansible.builtin.assert": {"that": f"guard_accepted == {expected}"}},
        ],
    })


for name, fstype, mounts, signatures, children, expected in [
    ("blank disk", None, [None], [], [], True),
    ("existing unmounted ext4", "ext4", [None], [{"type": "ext4"}], [], True),
    ("existing mounted ext4", "ext4", [path], [{"type": "ext4"}], [], True),
    ("boot disk", "ext4", ["/"], [{"type": "ext4"}], [], False),
    ("other mount", "ext4", ["/other"], [{"type": "ext4"}], [], False),
    ("other filesystem", "xfs", [None], [{"type": "xfs"}], [], False),
    ("unknown signatures", None, [None], [{"type": "gpt"}], [], False),
    ("partitioned disk", None, [None], [], [{"type": "part"}], False),
]:
    disk = {
        "type": "disk", "fstype": fstype, "mountpoints": mounts, "children": children,
    }
    add(name, disk_guard, {
        "kubernetes_data_path": path,
        "data_disk": {"stdout": json.dumps({"blockdevices": [disk]})},
        "data_signatures": {"stdout": json.dumps({"signatures": signatures})},
    }, expected)

for states in [
    (False, False, False), (True, True, True),
    (True, False, False), (False, True, True),
]:
    add(f"control plane {states}", cp_guard, {
        "control_plane_state": {
            "results": [{"stat": {"exists": state}} for state in states],
        },
    }, len(set(states)) == 1)

for version in ["v1.35.9", "v1.34.8"]:
    add(f"upgrade guard {version}", version_guard, {
        "kubernetes_version": "1.35.9",
        "existing_version": {"stdout": version},
    }, version == "v1.35.9")

play = [{
    "hosts": "localhost",
    "connection": "local",
    "gather_facts": False,
    "vars": {"ansible_python_interpreter": "/opt/ansible/bin/python3"},
    "tasks": checks,
}]

with tempfile.TemporaryDirectory() as temporary_directory:
    playbook = Path(temporary_directory) / "guards.yml"
    playbook.write_text(yaml.safe_dump(play, sort_keys=False))
    result = subprocess.run(
        ["ansible-playbook", "-i", "localhost,", str(playbook)],
        capture_output=True, text=True,
    )
    if result.returncode:
        print(result.stdout)
        print(result.stderr)
        raise SystemExit(result.returncode)
    print(f"PASS: {len(checks)} disk, partial initialization and version guard cases.")
