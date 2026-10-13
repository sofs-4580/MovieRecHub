# Separate state: destroying the test VMs must not delete published images.
terraform {
  required_version = "= 1.14.9"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "= 8.6.0"
    }
  }
  backend "local" {}
}

variable "project_id" {
  description = "Personal project, supplied by TF_VAR_project_id in the management container."
  type        = string
  nullable    = false
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", var.project_id)) && var.project_id != "your-project-id"
    error_message = "Set your real GCP_PROJECT_ID in infra/management/.env."
  }
}

provider "google" {
  project               = var.project_id
  user_project_override = true
  billing_project       = var.project_id
}

resource "google_project_service" "artifact_registry" {
  project            = var.project_id
  service            = "artifactregistry.googleapis.com"
  disable_on_destroy = false
}

resource "google_artifact_registry_repository" "images" {
  project       = var.project_id
  location      = "europe-west4"
  repository_id = "psia-g34"
  description   = "Movie Rec Hub application images"
  format        = "DOCKER"

  # A published version must never silently point to a different build.
  docker_config {
    immutable_tags = true
  }

  # Avoid enabling the separately billed scanning feature for this lab deployment.
  vulnerability_scanning_config {
    enablement_config = "DISABLED"
  }

  depends_on = [google_project_service.artifact_registry]
}

output "image_prefix" {
  description = "Prefix for the four images; append /service:version or /service@sha256:digest."
  value       = "${google_artifact_registry_repository.images.location}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}"
}
