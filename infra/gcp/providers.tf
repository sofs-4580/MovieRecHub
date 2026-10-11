# Use the local ADC credentials mounted by the management container.
provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone

  user_project_override = true
  billing_project       = var.project_id
}
