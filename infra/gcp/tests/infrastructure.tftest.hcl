# Plan-only tests: the mocked provider never authenticates or creates GCP resources.
mock_provider "google" {
  mock_data "google_compute_network" {
    defaults = {
      self_link = "https://www.googleapis.com/compute/v1/projects/g34-test-project/global/networks/default"
    }
  }
  mock_data "google_compute_subnetwork" {
    defaults = {
      self_link     = "https://www.googleapis.com/compute/v1/projects/g34-test-project/regions/europe-west4/subnetworks/default"
      ip_cidr_range = "10.164.0.0/20"
    }
  }
}

# The account email is computed by Google; make it known in these plan-only tests.
override_resource {
  target          = google_service_account.nodes
  override_during = plan
  values = {
    email = "psia-g34-nodes@g34-test-project.iam.gserviceaccount.com"
  }
}

variables {
  project_id          = "g34-test-project"
  ssh_public_key_path = "tests/ssh-test.pub"
}

run "initial_infrastructure" {
  command = plan

  assert {
    condition = (
      google_service_account.nodes.project == var.project_id &&
      google_artifact_registry_repository_iam_member.nodes.role == "roles/artifactregistry.reader" &&
      google_artifact_registry_repository_iam_member.nodes.repository == "psia-g34" &&
      google_artifact_registry_repository_iam_member.nodes.location == "europe-west4" &&
      google_artifact_registry_repository_iam_member.nodes.member == "serviceAccount:${google_service_account.nodes.email}" &&
      !google_project_service.iam.disable_on_destroy &&
      google_compute_instance.control_plane.service_account[0].email == google_service_account.nodes.email &&
      google_compute_instance.worker[0].service_account[0].email == google_service_account.nodes.email &&
      google_compute_instance.worker[0].service_account[0].scopes == toset(["https://www.googleapis.com/auth/cloud-platform"]) &&
      output.ansible_inventory.all.vars.registry_image_prefix == "europe-west4-docker.pkg.dev/g34-test-project/psia-g34" &&
      output.ansible_inventory.all.vars.registry_service_account == google_service_account.nodes.email
    )
    error_message = "Nodes must share the dedicated VM identity, with a repository-scoped reader role, OAuth scope and matching inventory, without personal keys."
  }

  assert {
    condition     = length(google_compute_instance.worker) == 1 && length(output.public_ips.workers) == 1 && length(output.ansible_inventory.all.children.control_plane.hosts) == 1
    error_message = "The initial installation must have one control plane and one worker."
  }

  assert {
    condition     = data.google_compute_network.cluster.name == "default" && google_compute_instance.control_plane.network_interface[0].subnetwork == data.google_compute_subnetwork.cluster.self_link && google_compute_instance.worker[0].network_interface[0].subnetwork == data.google_compute_subnetwork.cluster.self_link
    error_message = "Both VMs must reuse the default regional subnet."
  }

  assert {
    condition = (
      google_compute_firewall.cluster_rules.network == data.google_compute_network.cluster.self_link &&
      google_compute_firewall.cluster_rules.target_tags == toset([local.cluster_name]) &&
      google_compute_firewall.cluster_rules.source_ranges == toset(["0.0.0.0/0"]) &&
      toset(one([for rule in google_compute_firewall.cluster_rules.allow : rule if rule.protocol == "tcp"]).ports) == toset(["80", "443", "30510", "6443", "179"]) &&
      contains([for rule in google_compute_firewall.cluster_rules.allow : rule.protocol], "ipip")
    )
    error_message = "The lab-style rule must allow web, Kubernetes API and Calico traffic on tagged cluster VMs in the default network."
  }

  assert {
    condition     = google_compute_instance.control_plane.boot_disk[0].initialize_params[0].size + google_compute_instance.worker[0].boot_disk[0].initialize_params[0].size + google_compute_disk.worker_data.size == 60
    error_message = "The initial installation must provision only the agreed 60 GB of disks."
  }

  assert {
    condition     = output.ansible_inventory.all.children.workers.hosts[google_compute_instance.worker[0].name].kubernetes_data_device == "/dev/disk/by-id/google-${local.data_device_name}" && output.ansible_inventory.all.children.workers.hosts[google_compute_instance.worker[0].name].kubernetes_data_path == local.data_mount_path && output.ansible_inventory.all.vars.calico_ipip_mode == "Always"
    error_message = "The inventory must identify the data worker and Calico encapsulation mode."
  }
}

run "add_worker_without_moving_data" {
  command = plan

  variables {
    worker_count = 2
  }

  assert {
    condition     = length(google_compute_instance.worker[0].attached_disk) == 1 && length(google_compute_instance.worker[1].attached_disk) == 0 && length(output.ansible_inventory.all.children.workers.hosts) == 2 && google_compute_instance.worker[1].service_account[0].email == google_service_account.nodes.email
    error_message = "Adding a worker must keep the data disk on worker 1 and include both workers in the inventory."
  }
}

run "reject_missing_ssh_key" {
  command = plan
  variables {
    ssh_public_key_path = "tests/nonexistent.pub"
  }
  expect_failures = [var.ssh_public_key_path]
}

run "reject_undersized_data_disk" {
  command = plan
  variables {
    worker_data_disk_size_gb = 10
  }
  expect_failures = [var.worker_data_disk_size_gb]
}
