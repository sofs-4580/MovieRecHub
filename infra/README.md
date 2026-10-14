# Deployment na Google Cloud

Instala frontend, UserService, ProfileService e MovieService, com as três bases de dados. RecommendationService/Ollama ficam excluídos.

## Pré-requisitos

- Docker com Compose e `just` instalados.
- `infra/management/.env` preenchido com o teu `GCP_PROJECT_ID`.
- Login Google válido, chave SSH e APIs/rede `default` já preparados.
- Artifact Registry criado e as quatro imagens `v1` publicadas no teu projeto.
- `infra/management/.local/deployment.yml` preenchido com `application_image_tag: v1` e a chave API v3 em `tmdb_api_key`. Este ficheiro não vai para o Git.

## 0. Preparar os pré-requisitos

Fazer esta preparação uma vez por computador/projeto pessoal. Saltar o que já estiver preparado. Os comandos no computador assumem uma shell Bash, como o terminal Ubuntu/WSL, na raiz de `g34/`.

### Docker e just — no computador

No Windows/WSL, instalar e abrir [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/) e ativar a distribuição em **Settings → Resources → WSL Integration**. Confirmar no terminal WSL:

```bash
docker version
docker compose version
just --version
```

Se faltar `just`, instalar através do [script oficial](https://just.systems/man/en/pre-built-binaries.html):

```bash
mkdir -p "$HOME/.local/bin"
curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh | bash -s -- --to "$HOME/.local/bin"
export PATH="$HOME/.local/bin:$PATH"
just --version
```

Para conservar esse PATH em novos terminais Bash, adicionar a linha `export PATH="$HOME/.local/bin:$PATH"` a `~/.bashrc`. Não é preciso instalar gcloud, Terraform, Ansible ou kubectl no computador: ficam no contentor de gestão.

### Ficheiros locais — no computador

```bash
cp -n infra/management/.env.example infra/management/.env
chmod 600 infra/management/.env
install -d -m 0700 infra/management/.local
cp -n infra/ansible/deployment.example.yml infra/management/.local/deployment.yml
chmod 600 infra/management/.local/deployment.yml
```

`cp -n` preserva ficheiros já existentes. Abrir os dois ficheiros no editor. No `.env`, substituir o exemplo pelo ID do teu projeto (por exemplo, `GCP_PROJECT_ID=psia-g34`). No `deployment.yml`, preencher:

```yaml
application_image_tag: v1
tmdb_api_key: "A_TUA_CHAVE_API_V3"
```

Obter a chave nas [definições API da TMDB](https://www.themoviedb.org/settings/api). Usar a API Key v3, não o Read Access Token. Estes ficheiros são pessoais e ignorados pelo Git. O projeto Google Cloud deve ter faturação associada e permissões para criar os recursos.

### Login e APIs — dentro do contentor

No computador:

```bash
just start
just mgmt
```

Dentro do contentor:

```bash
umask 077
gcloud auth login --no-launch-browser --force --update-adc
gcloud auth application-default set-quota-project "$CLOUDSDK_CORE_PROJECT"
gcloud services enable compute.googleapis.com cloudresourcemanager.googleapis.com --project="$CLOUDSDK_CORE_PROJECT"
```

Seguir o URL de autenticação apresentado. O login fica guardado; repetir quando expirar ou surgir `invalid_rapt`. Compute Engine permite gerir VMs e Cloud Resource Manager é necessário às consultas do provider Terraform. As APIs de IAM e Artifact Registry são preparadas pelas respetivas configurações Terraform.

### Chave SSH e rede — dentro do contentor

Criar o par SSH apenas se nenhum dos ficheiros existir:

```bash
if [ ! -e ~/.ssh/id_ed25519 ] && [ ! -e ~/.ssh/id_ed25519.pub ]; then
    ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519
fi
test -s ~/.ssh/id_ed25519 && test -s ~/.ssh/id_ed25519.pub
```

Se a verificação falhar, resolver o par incompleto antes do Terraform; não substituir uma chave existente. Confirmar a rede e regras que esta configuração reutiliza:

```bash
gcloud compute networks describe default --format='value(name)'
gcloud compute networks subnets describe default --region=europe-west4 --format='value(name,ipCidrRange)'
gcloud compute firewall-rules describe default-allow-ssh --format='yaml(network,direction,disabled,sourceRanges,allowed,targetTags)'
gcloud compute firewall-rules describe default-allow-internal --format='yaml(network,direction,disabled,sourceRanges,allowed,targetTags)'
```

A rede/sub-rede devem existir; as regras devem permitir SSH TCP 22 a partir do teu computador e tráfego interno entre os nós. Se faltarem ou estiverem bloqueadas, preparar/corrigir esse pré-requisito no projeto antes de continuar. Estes comandos apenas consultam a rede.

### Criar o registry — dentro do contentor

```bash
cd /workspace/infra/registry
terraform init
terraform validate
terraform plan -out=registry.tfplan
```

Rever o plano. Se já indicar que não há alterações, não é necessário aplicar. Caso contrário:

```bash
terraform apply registry.tfplan
```

O registry guarda as imagens no teu projeto e é independente das VMs. Depois sair para o terminal do computador:

```bash
exit
```

### Publicar as quatro imagens — no computador

Na raiz de `g34/`, com o contentor de gestão ainda iniciado:

```bash
export IMAGE_TAG=v1
just images-build
just images-login
just images-push
just images-pull
```

Se as quatro imagens dessa versão já estiverem publicadas com o código pretendido, saltar este passo. Para publicar código alterado, usar uma nova tag (`v2`, `v3`, etc.) e atualizar também `application_image_tag` no ficheiro local e `registry_check_tag` no teste da secção 3. As tags publicadas são imutáveis. A publicação requer Docker Engine API 1.46+ (Docker 27+).

Com estes pré-requisitos preparados, seguir as secções 1–4 para criar as VMs, instalar Kubernetes e fazer deployment.

## 1. Abrir as ferramentas

No terminal do computador, na raiz de `g34/`:

```bash
just start
just mgmt
```

## 2. Criar as VMs

Dentro do contentor:

```bash
cd /workspace/infra/gcp
terraform init
terraform validate
terraform plan -out=deployment.tfplan
```

Rever o plano antes de continuar. O `apply` cria recursos reais que consomem créditos:

```bash
terraform apply deployment.tfplan
terraform output -json ansible_inventory > ansible-inventory.json
```

## 3. Instalar Kubernetes

Ainda dentro do contentor:

```bash
cd /workspace/infra/ansible
ansible-playbook cluster.yml
ansible-playbook registry-access.yml -e registry_check_tag=v1
exit
```

Se usares outra versão, substituir `v1` pela tag definida no ficheiro local. Se o Nginx de teste ainda ocupar a porta 30510, executar `ansible-playbook smoke-test-cleanup.yml` antes de sair do contentor.

## 4. Instalar a aplicação

De volta ao terminal do computador, na raiz de `g34/`:

```bash
just deploy
```

Instala as três BDs e depois as quatro aplicações. Abrir no browser o endereço apresentado: `http://IP_PUBLICO_DO_WORKER:30510/`.

Se as VMs e o cluster já estiverem prontos e o inventário estiver atualizado, basta executar `just start` e `just deploy`. Se algum comando falhar, resolver o erro antes de avançar.

### O que o `just deploy` faz por dentro

É um atalho definido no `justfile`. Usa Docker Compose para executar Ansible no contentor de gestão, na pasta `/workspace/infra/ansible`. O Ansible liga às VMs por SSH, usando o inventário gerado pelo Terraform, e aplica os manifests através de `kubectl` no control plane. Os serviços ficam a correr no cluster cloud; o contentor local apenas executa as ferramentas.

A sequência é:

1. **Verificar os ficheiros locais:** confirma que o inventário `infra/gcp/ansible-inventory.json` e a configuração `infra/management/.local/deployment.yml` existem e são legíveis.
2. **Executar `databases.yml`:** verifica a montagem do disco de dados, prepara uma pasta por BD e aplica o namespace `movie-rec-hub`, armazenamento persistente, Services internos e StatefulSets das três BDs. Espera pelas BDs e volumes prontos e testa uma ligação SQL através de cada Service. Reaplicar conserva os dados existentes.
3. **Executar `deploy.yml`:** lê a tag e a chave TMDB do ficheiro local, valida o cluster/BDs e a disponibilidade da porta 30510, aplica ConfigMaps e o Secret TMDB, preenche os templates com o registry/tag pessoais e aplica os Deployments e Services das quatro aplicações. Kubernetes usa as imagens publicadas para executar os Pods e os Services para permitir a comunicação.
4. **Confirmar o arranque:** espera pelas quatro aplicações prontas, verifica a saúde do frontend e a página de login pelo IP público de cada worker e apresenta o endereço para abrir no browser. Estas verificações não substituem os testes funcionais de contas, perfis e filmes.

Os dois comandos Ansible executados dentro do contentor são:

```bash
ansible-playbook databases.yml
ansible-playbook deploy.yml -e @../management/.local/deployment.yml
```

Só avança para a segunda etapa se a primeira terminar com sucesso. Se falhar, interrompe e mantém os recursos já aplicados para diagnóstico. Não cria VMs, instala Kubernetes, constrói/publica imagens ou inclui RecommendationService; essas preparações são feitas nas etapas anteriores.

## 5. Terminar o teste e remover os recursos cloud

No computador, na raiz de `g34/`, abrir o contentor de gestão (usar `just start` primeiro se estiver parado):

```bash
just mgmt
```

Dentro do contentor:

```bash
cd /workspace/infra/gcp
terraform destroy
```

Rever os recursos apresentados e confirmar com `yes`. Esperar pela mensagem `Destroy complete!` antes de fechar. Isto para a aplicação e elimina as VMs, os discos e os dados das BDs, terminando o consumo desses recursos. O código, o projeto Google Cloud e as imagens no Artifact Registry são conservados. Se falhar, resolver o erro e repetir: podem ter ficado recursos ativos.

### Remover também as imagens (opcional)

O registry é independente das VMs e o armazenamento das imagens pode ter custos. Para o eliminar também, **depois de destruir o cluster**, ainda dentro do contentor:

```bash
cd /workspace/infra/registry
terraform init
terraform destroy
```

Rever e confirmar com `yes`. Isto elimina o registry e todas as imagens nele guardadas; será necessário recriá-lo e publicar as imagens antes de outro deployment. Estes comandos removem os recursos geridos pelos dois estados Terraform, não outros recursos eventualmente criados no projeto.

### Fechar as ferramentas locais

Sair do contentor:

```bash
exit
```

No computador:

```bash
just stop
```

**`just stop` sozinho apenas para o contentor local: não desliga as VMs nem elimina os recursos cloud.**
