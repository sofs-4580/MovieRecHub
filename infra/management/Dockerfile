# Local administration tools; this image does not run in the Kubernetes cluster.
ARG GCLOUD_VERSION=588.0.0
FROM gcr.io/google.com/cloudsdktool/google-cloud-cli:${GCLOUD_VERSION}-stable

ARG TERRAFORM_VERSION=1.14.9
ARG ANSIBLE_CORE_VERSION=2.21.5
ARG KUBECTL_VERSION=1.35.9
ARG TARGETARCH

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash ca-certificates curl git openssh-client python3 python3-venv unzip \
    && rm -rf /var/lib/apt/lists/*

# Keep Ansible's Python dependencies separate from the system and gcloud Python.
RUN python3 -m venv /opt/ansible \
    && /opt/ansible/bin/pip install --no-cache-dir "ansible-core==${ANSIBLE_CORE_VERSION}"

# Download the matching architecture and verify the publishers' SHA-256 checksums.
RUN set -eu; \
    case "${TARGETARCH}" in amd64|arm64) ;; *) echo "Unsupported architecture: ${TARGETARCH}" >&2; exit 1 ;; esac; \
    download_dir="$(mktemp -d)"; \
    cd "${download_dir}"; \
    terraform_archive="terraform_${TERRAFORM_VERSION}_linux_${TARGETARCH}.zip"; \
    terraform_url="https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}"; \
    curl -fsSL --retry 3 --connect-timeout 20 "${terraform_url}/${terraform_archive}" -o "${terraform_archive}"; \
    curl -fsSL --retry 3 --connect-timeout 20 "${terraform_url}/terraform_${TERRAFORM_VERSION}_SHA256SUMS" -o terraform.sha256; \
    awk -v archive="${terraform_archive}" '$2 == archive { print }' terraform.sha256 | sha256sum --check --strict; \
    unzip -q "${terraform_archive}" terraform -d /usr/local/bin; \
    kubectl_url="https://dl.k8s.io/release/v${KUBECTL_VERSION}/bin/linux/${TARGETARCH}"; \
    curl -fsSL --retry 3 --connect-timeout 20 "${kubectl_url}/kubectl" -o kubectl; \
    curl -fsSL --retry 3 --connect-timeout 20 "${kubectl_url}/kubectl.sha256" -o kubectl.sha256; \
    awk '{ print $1 "  kubectl" }' kubectl.sha256 | sha256sum --check --strict; \
    install -m 0755 kubectl /usr/local/bin/kubectl; \
    cd /; \
    rm -rf "${download_dir}"

ENV PATH="/opt/ansible/bin:${PATH}" \
    CLOUDSDK_CORE_DISABLE_USAGE_REPORTING=true \
    CLOUDSDK_COMPONENT_MANAGER_DISABLE_UPDATE_CHECK=true \
    CHECKPOINT_DISABLE=1

ARG MANAGEMENT_UID=1000
ARG MANAGEMENT_GID=1000

# Match the host user so private bind-mounted credentials remain writable.
RUN groupmod --non-unique --gid "${MANAGEMENT_GID}" cloudsdk \
    && usermod --uid "${MANAGEMENT_UID}" --gid "${MANAGEMENT_GID}" cloudsdk

# Docker initializes the named volumes from these directories, including ownership.
RUN mkdir -p /workspace /home/cloudsdk/.config/gcloud /home/cloudsdk/.kube /home/cloudsdk/.ssh \
    && chown -R cloudsdk:cloudsdk /workspace /home/cloudsdk/.config /home/cloudsdk/.kube /home/cloudsdk/.ssh \
    && chmod 0700 /home/cloudsdk/.config /home/cloudsdk/.kube /home/cloudsdk/.ssh

USER cloudsdk
WORKDIR /workspace
ENTRYPOINT []
CMD ["/bin/bash"]
