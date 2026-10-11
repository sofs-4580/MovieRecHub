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
