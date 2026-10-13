mock_provider "google" {}

run "personal_registry" {
  command = plan
  variables {
    project_id = "student-project-123"
  }
  assert {
    condition     = google_artifact_registry_repository.images.project == "student-project-123" && output.image_prefix == "europe-west4-docker.pkg.dev/student-project-123/psia-g34"
    error_message = "Each installation must publish to its personal project using the shared repository and location."
  }
  assert {
    condition     = google_artifact_registry_repository.images.docker_config[0].immutable_tags && !google_project_service.artifact_registry.disable_on_destroy
    error_message = "Published tags must be immutable; removing the registry must not disable a project-wide API."
  }
}

run "reject_example_project" {
  command = plan
  variables {
    project_id = "your-project-id"
  }
  expect_failures = [var.project_id]
}
