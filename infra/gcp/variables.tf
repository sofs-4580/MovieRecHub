variable "project_id" {
  description = "Personal Google Cloud Project ID, supplied through TF_VAR_project_id by Compose."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", var.project_id)) && var.project_id != "your-project-id"
    error_message = "Set a valid Google Cloud Project ID (6 to 30 lowercase letters, digits or hyphens), replacing the example value."
  }
}

# Shared infrastructure defaults: change them in Git for the whole group.
variable "region" {
  description = "Common Google Cloud region for each independent installation."
  type        = string
  default     = "europe-west4"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z]+-[a-z]+[0-9]+$", var.region))
    error_message = "Use a Google Cloud region name, such as europe-west4."
  }
}

variable "zone" {
  description = "Common Google Cloud zone, following the lab4 reference."
  type        = string
  default     = "europe-west4-b"
  nullable    = false

  validation {
    condition     = can(regex("^${var.region}-[a-z]$", var.zone))
    error_message = "The zone must belong to the configured region, such as europe-west4-b for europe-west4."
  }
}

variable "control_plane_machine_type" {
  description = "Common machine type for the single Kubernetes control-plane VM."
  type        = string
  default     = "e2-medium"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", var.control_plane_machine_type))
    error_message = "Use a Google Cloud machine type name, such as e2-medium."
  }
}

variable "worker_machine_type" {
  description = "Common machine type for Kubernetes worker VMs."
  type        = string
  default     = "e2-medium"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", var.worker_machine_type))
    error_message = "Use a Google Cloud machine type name, such as e2-medium."
  }
}

variable "worker_count" {
  description = "Common number of worker VMs; initially one worker plus one control plane."
  type        = number
  default     = 1
  nullable    = false

  validation {
    condition     = var.worker_count >= 1 && var.worker_count == floor(var.worker_count)
    error_message = "The worker count must be a positive integer."
  }
}
