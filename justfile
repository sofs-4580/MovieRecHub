set shell := ["bash", "-euo", "pipefail", "-c"]

export MANAGEMENT_UID := `id -u`
export MANAGEMENT_GID := `id -g`

compose := "docker compose --env-file infra/management/.env -f infra/management/compose.yaml"
images := "docker --config infra/management/.local/docker compose --env-file infra/management/.env -f infra/images/compose.yaml"

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

# Set IMAGE_TAG in this terminal before building, for example step3-20261013-1.
images-build:
    {{images}} build

# Send a short-lived Google token directly to Docker, without printing it.
images-login:
    install -d -m 0700 infra/management/.local/docker
    {{compose}} exec -T management gcloud auth print-access-token | docker --config infra/management/.local/docker login -u oauth2accesstoken --password-stdin https://europe-west4-docker.pkg.dev

# Publish the four already-built images; requires login and the Terraform registry.
images-push:
    {{images}} config --images | while IFS= read -r image_ref; do docker --config infra/management/.local/docker push --platform linux/amd64 "$image_ref"; done

# Check that the registry can serve the selected version for all four images.
images-pull:
    {{images}} pull
