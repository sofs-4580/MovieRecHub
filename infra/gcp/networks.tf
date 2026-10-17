# Use the existing default network and regional subnet.
resource "google_project_service" "compute" {
  project            = var.project_id
  service            = "compute.googleapis.com"
  disable_on_destroy = false
}

data "google_compute_network" "cluster" {
  project = var.project_id
  name    = "default"
}

data "google_compute_subnetwork" "cluster" {
  project = var.project_id
  name    = "default"
  region  = var.region
}

# As in lab4, SSH and internal traffic use the default network's existing rules.
resource "google_compute_firewall" "cluster_rules" {
  name          = "${local.cluster_name}-cluster"
  network       = data.google_compute_network.cluster.self_link
  source_ranges = ["0.0.0.0/0"]
  target_tags   = [local.cluster_name]

  allow {
    protocol = "tcp"
    ports    = ["80", "443", "30510", "30300", "6443", "179"]
  }

  allow {
    protocol = "ipip"
  }
}
