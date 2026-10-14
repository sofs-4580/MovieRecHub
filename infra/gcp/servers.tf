locals {
  cluster_name = "psia-g34"
  ssh_user     = "ubuntu"
  # Keep Pods outside 10.128.0.0/9, reserved for Google auto-mode subnets.
  pod_cidr         = "10.32.0.0/16"
  service_cidr     = "10.96.0.0/12"
  ubuntu_image     = "ubuntu-os-cloud/ubuntu-2204-lts"
  data_device_name = "kubernetes-data"
  data_mount_path  = "/var/lib/kubernetes-data"
  ssh_public_key   = try(trimspace(file(pathexpand(var.ssh_public_key_path))), "")
  instance_metadata = {
    ssh-keys       = "${local.ssh_user}:${local.ssh_public_key}"
    enable-oslogin = "FALSE"
  }
}

# This is independent of the worker's boot disk and survives VM replacement.
# An explicit terraform destroy also deletes this disk; this is not a backup.
resource "google_compute_disk" "worker_data" {
  name            = "${local.cluster_name}-worker-1-data"
  zone            = var.zone
  type            = "pd-standard"
  size            = var.worker_data_disk_size_gb
  deletion_policy = "DELETE"

  depends_on = [google_project_service.compute]
}

resource "google_compute_instance" "control_plane" {
  name                      = "${local.cluster_name}-control-plane"
  machine_type              = var.control_plane_machine_type
  zone                      = var.zone
  can_ip_forward            = true
  allow_stopping_for_update = true
  tags                      = [local.cluster_name]
  metadata                  = local.instance_metadata

  service_account {
    email  = google_service_account.nodes.email
    scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }
  depends_on = [google_artifact_registry_repository_iam_member.nodes]

  boot_disk {
    auto_delete = true
    initialize_params {
      image = local.ubuntu_image
      size  = 20
      type  = "pd-standard"
    }
  }

  network_interface {
    network    = data.google_compute_network.cluster.self_link
    subnetwork = data.google_compute_subnetwork.cluster.self_link
    # An ephemeral IPv4 avoids retaining a reserved public IP when stopped.
    access_config {}
  }
}

resource "google_compute_instance" "worker" {
  count = var.worker_count

  name                      = "${local.cluster_name}-worker-${count.index + 1}"
  machine_type              = var.worker_machine_type
  zone                      = var.zone
  can_ip_forward            = true
  allow_stopping_for_update = true
  tags                      = [local.cluster_name]
  metadata                  = local.instance_metadata

  service_account {
    email  = google_service_account.nodes.email
    scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }
  depends_on = [google_artifact_registry_repository_iam_member.nodes]

  boot_disk {
    auto_delete = true
    initialize_params {
      image = local.ubuntu_image
      size  = 20
      type  = "pd-standard"
    }
  }

  # Only the first worker hosts the initial local-path database volumes.
  dynamic "attached_disk" {
    for_each = count.index == 0 ? [google_compute_disk.worker_data] : []
    content {
      source      = attached_disk.value.id
      device_name = local.data_device_name
      mode        = "READ_WRITE"
    }
  }

  network_interface {
    network    = data.google_compute_network.cluster.self_link
    subnetwork = data.google_compute_subnetwork.cluster.self_link
    access_config {}
  }
}
