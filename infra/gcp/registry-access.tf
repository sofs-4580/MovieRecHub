# The registry is created first by infra/registry/, with its own persistent state.
locals {
  registry_location     = "europe-west4"
  registry_repository   = "psia-g34"
  registry_image_prefix = "${local.registry_location}-docker.pkg.dev/${var.project_id}/${local.registry_repository}"
}

resource "google_project_service" "iam" {
  project            = var.project_id
  service            = "iam.googleapis.com"
  disable_on_destroy = false
}

# VM identity only; no JSON keys or personal OAuth credentials on cluster nodes.
resource "google_service_account" "nodes" {
  project      = var.project_id
  account_id   = "psia-g34-nodes"
  display_name = "PSIA Kubernetes image pulls"
  depends_on   = [google_project_service.iam]
}

# Add one reader without replacing other members of the repository's IAM policy.
resource "google_artifact_registry_repository_iam_member" "nodes" {
  project    = var.project_id
  location   = local.registry_location
  repository = local.registry_repository
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${google_service_account.nodes.email}"
}
