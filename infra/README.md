# Deployment on Google Cloud

Installs the frontend, UserService, ProfileService, MovieService and RecommendationService, with the four databases and Ollama (the language model server used by RecommendationService).

The cluster has 1 control plane and 2 workers: worker-1 runs the databases and applications, and worker-2 runs Ollama.

### Demonstration Deployment Video

> To watch the complete deployment process, see the [deployment video](https://drive.google.com/file/d/1qKnY1MhyN-pFYNAMLfd3pG8QvVGAjFlN/view?usp=sharing).

## Prerequisites

- Docker with Compose and `just` installed.
- `infra/management/.env` filled in with your `GCP_PROJECT_ID`.
- A valid Google login, SSH key and the APIs/`default` network already prepared.
- Artifact Registry created and the five `v1` images published in your project.
- `infra/management/.local/deployment.yml` filled in with `application_image_tag: v1` and the v3 API key in `tmdb_api_key`. This file is not committed to Git.

## 0. Prepare the prerequisites

Do this preparation once per computer/personal project. Skip anything already prepared. Commands on your computer assume a Bash shell, such as the Ubuntu/WSL terminal, at the root of `g34/`.

### Docker and just — on your computer

On Windows/WSL, install and open [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/) and enable your distribution under **Settings → Resources → WSL Integration**. Check in the WSL terminal:

```bash
docker version
docker compose version
just --version
```

If `just` is missing, install it using the [official script](https://just.systems/man/en/pre-built-binaries.html):

```bash
mkdir -p "$HOME/.local/bin"
curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh | bash -s -- --to "$HOME/.local/bin"
export PATH="$HOME/.local/bin:$PATH"
just --version
```

To keep this PATH in new Bash terminals, add the line `export PATH="$HOME/.local/bin:$PATH"` to `~/.bashrc`. You do not need to install gcloud, Terraform, Ansible or kubectl on your computer: they are provided by the management container.

### Local files — on your computer

```bash
cp -n infra/management/.env.example infra/management/.env
chmod 600 infra/management/.env
install -d -m 0700 infra/management/.local
cp -n infra/ansible/deployment.example.yml infra/management/.local/deployment.yml
chmod 600 infra/management/.local/deployment.yml
```

`cp -n` preserves existing files. Open both files in your editor. In `.env`, replace the example with your project ID (for example, `GCP_PROJECT_ID=psia-g34`). In `deployment.yml`, fill in:

```yaml
application_image_tag: v1
tmdb_api_key: "A_TUA_CHAVE_API_V3"
```

Get the key from the [TMDB API settings](https://www.themoviedb.org/settings/api). Use the API Key v3, not the Read Access Token. These files are personal and ignored by Git. The Google Cloud project must have billing linked and permissions to create resources.

### Login and APIs — inside the container

On your computer:

```bash
just start
just mgmt
```

Inside the container:

```bash
umask 077
gcloud auth login --no-launch-browser --force --update-adc
gcloud auth application-default set-quota-project "$CLOUDSDK_CORE_PROJECT"
gcloud services enable compute.googleapis.com cloudresourcemanager.googleapis.com --project="$CLOUDSDK_CORE_PROJECT"
```

Follow the authentication URL shown. The login is saved; repeat it when it expires or `invalid_rapt` appears. Compute Engine allows you to manage VMs, and Cloud Resource Manager is required for the Terraform provider's queries. The IAM and Artifact Registry APIs are prepared by their respective Terraform configurations.

### SSH key and network — inside the container

Create the SSH key pair only if neither file exists:

```bash
if [ ! -e ~/.ssh/id_ed25519 ] && [ ! -e ~/.ssh/id_ed25519.pub ]; then
    ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519
fi
test -s ~/.ssh/id_ed25519 && test -s ~/.ssh/id_ed25519.pub
```

If the check fails, resolve the incomplete pair before running Terraform; do not replace an existing key. Check the network and rules reused by this configuration:

```bash
gcloud compute networks describe default --format='value(name)'
gcloud compute networks subnets describe default --region=europe-west4 --format='value(name,ipCidrRange)'
gcloud compute firewall-rules describe default-allow-ssh --format='yaml(network,direction,disabled,sourceRanges,allowed,targetTags)'
gcloud compute firewall-rules describe default-allow-internal --format='yaml(network,direction,disabled,sourceRanges,allowed,targetTags)'
```

The network/subnet must exist; the rules must allow SSH TCP 22 from your computer and internal traffic between nodes. If they are missing or blocked, prepare/fix this prerequisite in the project before continuing. These commands only query the network.

### Create the registry — inside the container

```bash
cd /workspace/infra/registry
terraform init
terraform validate
terraform plan -out=registry.tfplan
```

Review the plan. If it already shows no changes, there is no need to apply it. Otherwise:

```bash
terraform apply registry.tfplan
```

The registry stores images in your project and is independent of the VMs. Then return to your computer's terminal:

```bash
exit
```

If the registry was created with the previous configuration (immutable tags), run this `plan`/`apply` once to allow the same tag to be replaced. The change updates the existing registry; there is no need to recreate the VMs.

### Publish the five images — on your computer

At the root of `g34/`, with the management container still running:

```bash
just images-build
just images-login
just images-push
just images-pull
```

The justfile uses `v1` by default. To publish changed code, repeat these commands with the same tag; there is no need to change `application_image_tag`. To choose another tag, run `export IMAGE_TAG=OUTRA_TAG` first and use the same value in the local file and in the check in section 3. If the current images are already published, skip this step. Publishing requires Docker Engine API 1.46+ (Docker 27+).

With these prerequisites prepared, follow sections 1–4 to create the VMs, install Kubernetes and deploy the application.

## 1. Open the tools

In your computer's terminal, at the root of `g34/`:

```bash
just start
just mgmt
```

## 2. Create the VMs

Inside the container:

```bash
cd /workspace/infra/gcp
terraform init
terraform validate
terraform plan -out=deployment.tfplan
```

Review the plan before continuing. The `apply` creates real resources that consume credits: with 2 `e2-standard-4` workers and the control plane, the cluster costs about USD 0.36 per hour while running. The workers' machine type is set in `worker_machine_type` (`infra/gcp/variables.tf`); Ollama needs 4 cores to respond to a request in about 46 s (with `e2-medium`, it took about 2 minutes).

```bash
terraform apply deployment.tfplan
terraform output -json ansible_inventory > ansible-inventory.json
```

## 3. Install Kubernetes

Still inside the container:

```bash
cd /workspace/infra/ansible
ansible-playbook cluster.yml
ansible-playbook registry-access.yml -e registry_check_tag=v1
exit
```

`cluster.yml` prepares both workers and joins them to the cluster. **Run it only once, on new VMs**: after the databases are running, it fails the data disk check because the Pods use that mount. To update the application, use `just deploy`. To add a worker to an existing cluster, prepare the node and use `ansible-playbook create-cluster.yml join-workers.yml` in a single command.

If you use another version, replace `v1` with the tag defined in the local file. If the Nginx test still occupies port 30510, run `ansible-playbook smoke-test-cleanup.yml` before leaving the container.

## 4. Install the application

Back in your computer's terminal, at the root of `g34/`:

```bash
just deploy
```

Installs the four databases, Ollama (which downloads the model, about 5 minutes) and then the five applications. Open the displayed address in your browser: `http://IP_PUBLICO_DO_WORKER:30510/`.

If the VMs and cluster are already ready and the inventory is up to date, just run `just start` and `just deploy`. If a command fails, resolve the error before continuing.

If `Connection refused` occurs over SSH, check the VMs inside `just mgmt` with `gcloud compute instances list`. The VMs' public IPs change when they are recreated, resized or stopped and started again: afterwards, run `terraform refresh` and regenerate the inventory (`terraform output -json ansible_inventory > ansible-inventory.json`) before `just deploy`. After `terraform destroy`, the inventory file may still exist with old IPs: repeat sections 2–3, including inventory generation, before `just deploy`. The command does not recreate VMs or Kubernetes.

If `REMOTE HOST IDENTIFICATION HAS CHANGED` appears after recreating VMs, first confirm that the IP and new SSH identity belong to the correct VM. Then, inside the container, remove only the old entry with `ssh-keygen -f ~/.ssh/known_hosts -R IP_DA_VM` (replacing it with the verified IP) and repeat `ansible-playbook cluster.yml` in `/workspace/infra/ansible`. The playbook may wait up to five minutes at `Wait for SSH and Python` before reporting this error. You do not need to replace your SSH key pair.

### What `just deploy` does internally

It is a shortcut defined in the `justfile`. It uses Docker Compose to run Ansible in the management container, in `/workspace/infra/ansible`. Ansible connects to the VMs over SSH, using the inventory generated by Terraform, and applies the manifests through `kubectl` on the control plane. The services run in the cloud cluster; the local container only runs the tools.

The sequence is:

1. **Check local files:** confirms that the inventory `infra/gcp/ansible-inventory.json` and configuration `infra/management/.local/deployment.yml` exist and are readable.
2. **Run `databases.yml`:** checks the data disk mount, prepares a folder per database and applies the `movie-rec-hub` namespace, persistent storage, internal Services and StatefulSets for the four databases (users-db, profiles-db, movie-db and reco-db). Waits for the databases and volumes to be ready and tests an SQL connection through each Service. Reapplying preserves existing data.
3. **Run `deploy.yml`:** reads the tag and TMDB key from the local file, validates the cluster/databases and port 30510 availability, applies ConfigMaps and the TMDB Secret, installs Ollama and waits for it to download the model, fills in the templates with the personal registry/tag and applies the five applications' Deployments and Services. Each run generates a new identifier in the templates to recreate the Pods, even when the tag is unchanged. With `imagePullPolicy: Always`, each new container checks the current image in the registry, reusing cached layers that have not changed. Services allow the applications to communicate.
4. **Confirm startup:** waits for the five applications to be ready, checks frontend health and the login page through each worker's public IP, and shows the address to open in the browser. These checks do not replace functional tests of accounts, profiles, movies and recommendations.

The two Ansible commands run inside the container are:

```bash
ansible-playbook databases.yml
ansible-playbook deploy.yml -e @../management/.local/deployment.yml
```

It only proceeds to the second stage if the first succeeds. If it fails, it stops and keeps the resources already applied for diagnosis. It does not create VMs, install Kubernetes or build/publish images; those preparations are done in the earlier stages.

### Update the application using the same tag

With the registry updated, the VMs/cluster ready, the management container running and a valid login, run on your computer, at the root of `g34/`:

```bash
just images-build
just images-login
just images-push
just deploy
```

The build tag value (`v1` by default) must match `application_image_tag` in the local file. Publishing alone does not update running Pods: `just deploy` triggers the update. Each deployment recreates the five application Pods; the databases are not restarted or deleted by the new mechanism. The Ollama model is stored in an `emptyDir`: if the Pod is replaced, the model must be downloaded again. With one replica/Recreate, there will be an interruption during the update. UserService now stores sessions in PostgreSQL; earlier sessions that existed only in memory require a new login. You cannot recover an old version using only the name of a tag that has since been replaced.

## 5. Pause and resume while keeping the VMs and data

On your computer, at the root of `g34/`:

```bash
just pause
just stop
```

`just pause` stops the workers and then the control plane, without deleting VMs or disks. `just stop` only closes the local tools container. Disks and registry images can still consume credits. To start again:

```bash
just resume
```

Resume automatically starts the local tools, starts the control plane before the workers, updates IPs/inventory/kubeconfig and checks services, the disk by UUID, nodes, networking, four databases, five applications and Ollama. If the monitoring namespace exists, it also waits for Prometheus, Grafana, kube-state-metrics and node-exporter. It does not install or reapply manifests, build images or format disks. A valid Google login and the existing personal Terraform state are required.

The public address can change and the browser can request a new login, even with the session in the database; the timeout remains 30 minutes by default. Sessions held only in memory before the update are not recovered. Ollama, Prometheus and Grafana still have data in `emptyDir`: these changes do not guarantee preservation of that data if the Pods are replaced. The complete task for the entire application still requires those volumes and cloud validation.

On the first update of an existing installation, run inside `just mgmt`, without running `apply` on the infrastructure:

```bash
cd /workspace/infra/gcp
terraform apply -refresh-only
terraform output -json ansible_inventory > ansible-inventory.json
```

This updates the outputs with the SSH identity added in `outputs.tf`. Review the refresh and confirm; it does not recreate VMs. Publish the updated UserService image and run `just deploy` to apply migration V4 and activate Spring Session. If the installation is paused, `just resume` restores access before deployment. For an older installation without recommendation resources, there is an explicit upgrade option, `ansible-playbook -i localhost, resume.yml -e resume_verify_recommendations=false`, in `/workspace/infra/ansible`; it is not the normal mode and does not demonstrate recommendation persistence.

## 6. Finish the test and remove cloud resources

On your computer, at the root of `g34/`, open the management container (use `just start` first if it is stopped):

```bash
just mgmt
```

Inside the container:

```bash
cd /workspace/infra/gcp
terraform destroy
```

Review the resources shown and confirm with `yes`. Wait for the `Destroy complete!` message before closing. This stops the application and deletes the VMs, disks and database data, ending consumption by those resources. The code, Google Cloud project and images in Artifact Registry are preserved. If it fails, resolve the error and repeat: active resources may remain.

### Also remove the images (optional)

The registry is independent of the VMs, and image storage can incur costs. To remove it as well, **after destroying the cluster**, still inside the container:

```bash
cd /workspace/infra/registry
terraform init
terraform destroy
```

Review and confirm with `yes`. This deletes the registry and all images stored in it; you will need to recreate it and publish the images before another deployment. These commands remove the resources managed by the two Terraform states, not other resources that may have been created in the project.

### Close the local tools

Exit the container:

```bash
exit
```

On your computer:

```bash
just stop
```

**`just stop` alone only stops the local container: it does not stop the VMs or delete cloud resources.**
