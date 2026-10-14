set shell := ["bash", "-euo", "pipefail", "-c"]

export MANAGEMENT_UID := `id -u`
export MANAGEMENT_GID := `id -g`
export IMAGE_TAG := env("IMAGE_TAG", "v1")

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

# Deploy the databases and applications to the existing Kubernetes cluster.
deploy:
    @test -r infra/management/.local/deployment.yml || { echo "Prepare infra/management/.local/deployment.yml with the published image tag and TMDB key." >&2; exit 1; }
    @test -r infra/gcp/ansible-inventory.json || { echo "Generate infra/gcp/ansible-inventory.json from Terraform after creating the VMs." >&2; exit 1; }
    {{compose}} exec -T --workdir /workspace/infra/ansible management ansible-playbook databases.yml
    {{compose}} exec -T --workdir /workspace/infra/ansible management ansible-playbook deploy.yml -e @../management/.local/deployment.yml

# Build with the reusable v1 tag by default; override through IMAGE_TAG if needed.
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
