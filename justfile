set shell := ["bash", "-euo", "pipefail", "-c"]

export MANAGEMENT_UID := `id -u`
export MANAGEMENT_GID := `id -g`

compose := "docker compose --env-file infra/management/.env -f infra/management/compose.yaml"

default:
    @just --list

# Build and start the local tools container.
start:
    install -d -m 0700 infra/management/.local infra/management/.local/gcloud
    {{compose}} up --build --detach management

# Run gcloud, Terraform and Ansible directly in this shell.
mgmt:
    {{compose}} exec management bash

# Stop only the local tools container; keep its volumes and cloud resources.
stop:
    {{compose}} stop management
