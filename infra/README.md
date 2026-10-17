# Deployment na Google Cloud

Instala frontend, UserService, ProfileService, MovieService e RecommendationService, com as quatro bases de dados e o Ollama (servidor do modelo de linguagem usado pelo RecommendationService).

O cluster tem 1 control plane e 2 workers: o worker-1 corre as bases de dados e as aplicações, e o worker-2 corre o Ollama.

## Pré-requisitos

- Docker com Compose e `just` instalados.
- `infra/management/.env` preenchido com o teu `GCP_PROJECT_ID`.
- Login Google válido, chave SSH e APIs/rede `default` já preparados.
- Artifact Registry criado e as cinco imagens `v1` publicadas no teu projeto.
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

Se o registry foi criado com a configuração anterior (tags imutáveis), executar este `plan`/`apply` uma vez para passar a permitir substituir a mesma tag. A mudança atualiza o registry existente; não é necessário recriar as VMs.

### Publicar as cinco imagens — no computador

Na raiz de `g34/`, com o contentor de gestão ainda iniciado:

```bash
just images-build
just images-login
just images-push
just images-pull
```

O justfile usa `v1` por defeito. Para publicar código alterado, repetir estes comandos com a mesma tag; não é preciso mudar `application_image_tag`. Para escolher outra tag, executar `export IMAGE_TAG=OUTRA_TAG` antes e usar o mesmo valor no ficheiro local e no teste da secção 3. Se as imagens atuais já estiverem publicadas, saltar este passo. A publicação requer Docker Engine API 1.46+ (Docker 27+).

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

Rever o plano antes de continuar. O `apply` cria recursos reais que consomem créditos: com 2 workers `e2-standard-4` e o control plane, o cluster custa cerca de 0,36 USD por hora enquanto estiver ligado. O tipo de máquina dos workers está em `worker_machine_type` (`infra/gcp/variables.tf`); o Ollama precisa de 4 núcleos para responder a um pedido em cerca de 46 s (com `e2-medium` demorava cerca de 2 minutos).

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

O `cluster.yml` prepara os dois workers e junta-os ao cluster. **Só se executa uma vez, com as VMs novas**: depois de as bases de dados estarem a correr falha na verificação do disco de dados, porque os Pods usam essa montagem. Para atualizar a aplicação usa `just deploy`. Para acrescentar um worker a um cluster existente, prepara o nó e usa `ansible-playbook create-cluster.yml join-workers.yml` num único comando.

Se usares outra versão, substituir `v1` pela tag definida no ficheiro local. Se o Nginx de teste ainda ocupar a porta 30510, executar `ansible-playbook smoke-test-cleanup.yml` antes de sair do contentor.

## 4. Instalar a aplicação

De volta ao terminal do computador, na raiz de `g34/`:

```bash
just deploy
```

Instala as quatro BDs, o Ollama (que descarrega o modelo, cerca de 5 minutos) e depois as cinco aplicações. Abrir no browser o endereço apresentado: `http://IP_PUBLICO_DO_WORKER:30510/`.

Se as VMs e o cluster já estiverem prontos e o inventário estiver atualizado, basta executar `just start` e `just deploy`. Se algum comando falhar, resolver o erro antes de avançar.

Se surgir `Connection refused` por SSH, confirmar as VMs dentro de `just mgmt` com `gcloud compute instances list`. Os IPs públicos das VMs mudam quando são recriadas, redimensionadas ou paradas e voltam a ligar-se: depois disso corre `terraform refresh` e regenera o inventário (`terraform output -json ansible_inventory > ansible-inventory.json`) antes de `just deploy`. Depois de `terraform destroy`, o ficheiro de inventário pode continuar a existir com IPs antigos: repetir as secções 2–3, incluindo a geração do inventário, antes de `just deploy`. O comando não recria VMs nem Kubernetes.

Se surgir `REMOTE HOST IDENTIFICATION HAS CHANGED` após recriar VMs, confirmar primeiro que o IP e a nova identidade SSH pertencem à VM correta. Depois, dentro do contentor, remover apenas a entrada antiga com `ssh-keygen -f ~/.ssh/known_hosts -R IP_DA_VM` (substituir pelo IP confirmado) e repetir `ansible-playbook cluster.yml` em `/workspace/infra/ansible`. O playbook pode esperar até cinco minutos em `Wait for SSH and Python` antes de apresentar esse erro. Não é necessário substituir o teu par de chaves SSH.

### O que o `just deploy` faz por dentro

É um atalho definido no `justfile`. Usa Docker Compose para executar Ansible no contentor de gestão, na pasta `/workspace/infra/ansible`. O Ansible liga às VMs por SSH, usando o inventário gerado pelo Terraform, e aplica os manifests através de `kubectl` no control plane. Os serviços ficam a correr no cluster cloud; o contentor local apenas executa as ferramentas.

A sequência é:

1. **Verificar os ficheiros locais:** confirma que o inventário `infra/gcp/ansible-inventory.json` e a configuração `infra/management/.local/deployment.yml` existem e são legíveis.
2. **Executar `databases.yml`:** verifica a montagem do disco de dados, prepara uma pasta por BD e aplica o namespace `movie-rec-hub`, armazenamento persistente, Services internos e StatefulSets das quatro BDs (users-db, profiles-db, movie-db e reco-db). Espera pelas BDs e volumes prontos e testa uma ligação SQL através de cada Service. Reaplicar conserva os dados existentes.
3. **Executar `deploy.yml`:** lê a tag e a chave TMDB do ficheiro local, valida o cluster/BDs e a disponibilidade da porta 30510, aplica ConfigMaps e o Secret TMDB, instala o Ollama e espera que descarregue o modelo, preenche os templates com o registry/tag pessoais e aplica os Deployments e Services das cinco aplicações. Cada execução gera uma nova identificação nos templates para recriar os Pods, mesmo quando a tag é a mesma. Com `imagePullPolicy: Always`, cada novo container consulta a imagem atual no registry, reutilizando as camadas em cache que não mudaram. Os Services permitem a comunicação entre aplicações.
4. **Confirmar o arranque:** espera pelas cinco aplicações prontas, verifica a saúde do frontend e a página de login pelo IP público de cada worker e apresenta o endereço para abrir no browser. Estas verificações não substituem os testes funcionais de contas, perfis, filmes e recomendações.

Os dois comandos Ansible executados dentro do contentor são:

```bash
ansible-playbook databases.yml
ansible-playbook deploy.yml -e @../management/.local/deployment.yml
```

Só avança para a segunda etapa se a primeira terminar com sucesso. Se falhar, interrompe e mantém os recursos já aplicados para diagnóstico. Não cria VMs, instala Kubernetes nem constrói/publica imagens; essas preparações são feitas nas etapas anteriores.

### Atualizar a aplicação com a mesma tag

Com o registry atualizado, as VMs/cluster prontos, o contentor de gestão iniciado e login válido, executar no computador, na raiz de `g34/`:

```bash
just images-build
just images-login
just images-push
just deploy
```

O valor da tag de build (por defeito `v1`) deve coincidir com `application_image_tag` no ficheiro local. A publicação sozinha não atualiza os Pods em execução: `just deploy` provoca a atualização. Cada deployment recria os cinco Pods das aplicações; as BDs não são reiniciadas nem apagadas pelo novo mecanismo. O modelo do Ollama fica num `emptyDir`, por isso é descarregado outra vez sempre que o Pod do Ollama reinicia. Com uma réplica/Recreate haverá uma interrupção durante a atualização, e reiniciar UserService perde as sessões em memória. Não é possível recuperar uma versão antiga apenas pelo nome de uma tag que entretanto foi substituída.

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
