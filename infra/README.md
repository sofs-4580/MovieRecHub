# Infraestrutura

Ambiente local com gcloud, Terraform, Ansible e kubectl para preparar o deployment na Google Cloud. Todos utilizam a mesma configuração, mas cada membro usa o seu projeto, credenciais e estado Terraform.

## No computador

Requer Docker com Compose, Bash e `just`. A publicação usa `docker push --platform` (Docker Engine API 1.46+, disponível no Docker 27+). No WSL, o Docker Desktop deve estar aberto e ter a integração com a distribuição ativa.

Na raiz de `g34/`, preparar uma vez:

```bash
cp -n infra/management/.env.example infra/management/.env
chmod 600 infra/management/.env
```

Editar `GCP_PROJECT_ID` no `.env` com o ID do teu projeto. `cp -n` conserva um ficheiro já existente.

| Comando | Para que serve |
|---|---|
| `just` | Lista os comandos disponíveis. |
| `just start` | Constrói a imagem e inicia o contentor de ferramentas em segundo plano. |
| `just mgmt` | Abre uma shell dentro desse contentor, em `/workspace`. |
| `just stop` | Para o contentor local e conserva os dados. **Não desliga as VMs Google Cloud.** |

## Dentro do contentor

Executar os comandos seguintes na shell aberta por `just mgmt`.

### Login e chave SSH

```bash
umask 077
gcloud auth login --no-launch-browser --force --update-adc
gcloud auth application-default set-quota-project "$CLOUDSDK_CORE_PROJECT"
```

O login apresenta um URL para autenticação no browser e guarda também as credenciais usadas pelo Terraform. O segundo comando seleciona o projeto de quota dessas credenciais. Se surgir `invalid_rapt`, repetir o login: a organização pode exigir nova autenticação.

Preparar as APIs necessárias uma vez por projeto:

```bash
gcloud services enable compute.googleapis.com cloudresourcemanager.googleapis.com --project="$CLOUDSDK_CORE_PROJECT"
```

Compute Engine permite gerir as VMs; Cloud Resource Manager permite as consultas ao projeto usadas pelo provider Terraform, incluindo durante `destroy`. Se aparecer `SERVICE_DISABLED`, ativar a API indicada, aguardar a propagação e repetir o comando que falhou.

Preparar a chave para entrar nas futuras VMs, apenas se o par ainda não existir:

```bash
if [ ! -e ~/.ssh/id_ed25519 ] && [ ! -e ~/.ssh/id_ed25519.pub ]; then
    ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519
fi
```

### Terraform

```bash
cd /workspace/infra/gcp
```

| Comando | Para que serve |
|---|---|
| `terraform init` | Prepara o backend local e descarrega o provider Google. |
| `terraform validate` | Verifica a sintaxe e coerência da configuração. |
| `terraform test` | Executa quatro testes com Google Cloud simulada, sem criar recursos reais. |
| `terraform plan -out=deployment.tfplan` | Consulta a cloud, mostra as alterações propostas e guarda o plano; não cria VMs. |
| `terraform apply deployment.tfplan` | Aplica o plano revisto e cria/altera os recursos reais, que consomem créditos cloud. |
| `terraform output public_ips` | Mostra os IPs públicos das máquinas após o deployment. |
| `terraform output -json ansible_inventory > ansible-inventory.json` | Gera o inventário com os dados de ligação para Ansible. |

O plano pressupõe a API Compute Engine ativa, a rede/sub-rede `default` existente e as regras `default-allow-ssh` e `default-allow-internal` a permitir o acesso necessário. **Antes de aplicar a infraestrutura das VMs, criar o registry com `infra/registry/`**, conforme a secção de imagens abaixo. O Terraform de `infra/gcp/` cria também a identidade `psia-g34-nodes` e concede leitura nesse registry; não cria outro registry.

### Ansible — configurar Kubernetes

Podemos validar os playbooks **antes de criar VMs**, sem login Google:

```bash
cd /workspace/infra/ansible
ansible-playbook -i tests/inventory.yml cluster.yml --syntax-check
/opt/ansible/bin/python3 tests/check-guards.py
```

O inventário de teste contém IPs fictícios e serve apenas para verificações locais. O teste Python verifica as proteções do disco, inicialização parcial e alterações de versão; não formata discos nem liga a máquinas.

**Quando decidirmos criar a infraestrutura**, depois de aplicar um plano Terraform revisto:

```bash
cd /workspace/infra/gcp
umask 077
terraform output -json ansible_inventory > ansible-inventory.json
cd /workspace/infra/ansible
ansible-inventory --graph
ansible all -m ansible.builtin.ping
ansible-playbook cluster.yml
kubectl get nodes -o wide
kubectl get pods -n kube-system
```

| Ficheiro | O que faz |
|---|---|
| `cluster.yml` | Executa a preparação, criação do cluster e adesão dos workers pela ordem correta. |
| `configure-nodes.yml` | Instala containerd/Kubernetes, configura rede Linux/swap e monta o disco de dados do primeiro worker. |
| `create-cluster.yml` | Inicializa o control plane com kubeadm, instala Calico e guarda o kubeconfig local. |
| `join-workers.yml` | Junta workers novos e verifica nós `Ready`, Calico, CoreDNS e acesso local à API. |

As versões comuns ficam em `group_vars/all.yml`; utilizador SSH, chave e IPs vêm do inventário Terraform. A primeira ligação SSH aceita a chave da VM; uma chave diferente numa ligação posterior é rejeitada. O kubeconfig administrativo fica em `~/.kube/config` no volume pessoal do contentor.

`cluster.yml` instala também o plugin oficial de credenciais de imagens. Regenerar o inventário com o Terraform atualizado: agora inclui projeto, prefixo do registry e identidade esperada das VMs. Sem estes campos, a configuração termina com uma indicação para atualizar o inventário.

Podemos repetir `ansible-playbook cluster.yml`: não faz reset do cluster, não junta novamente workers existentes e só cria ext4 num disco sem partições/assinaturas. Não executa upgrades Kubernetes. `--check` não simula uma instalação completa e é recusado; usar `--syntax-check` para a validação local.

Depois de parar/arrancar VMs, os IPs públicos podem mudar: regenerar o inventário. Se mudar o IP do control plane, o certificado da API também precisa de incluir o novo IP; repetir estes playbooks não renova esse certificado.

### Teste Nginx

O Nginx é um teste temporário **no cluster Google Cloud**, antes do deployment dos nossos serviços. Instala um Pod e expõe a página na porta `30510`. Preparar/analisar os ficheiros localmente não executa esse teste.

Dentro do contentor, em `/workspace/infra/ansible`, validar agora sem VMs:

```bash
ansible-playbook -i tests/inventory.yml smoke-test.yml --syntax-check
ansible-playbook -i tests/inventory.yml smoke-test-cleanup.yml --syntax-check
```

**Quando o cluster real estiver instalado** e o inventário Terraform gerado:

```bash
ansible-playbook smoke-test.yml
kubectl -n nginx-smoke-test get pods,svc
```

O playbook aplica `kubernetes/smoke-test/deployment.yaml`, espera pelo Deployment e verifica HTTP 200 e a página Nginx através do IP público de cada worker, a partir do contentor local. No browser, abrir o URL apresentado: `http://IP_PUBLICO_DO_WORKER:30510/`.

Se faltar `kubectl` ou `/etc/kubernetes/admin.conf` no control plane, o teste termina com uma mensagem para executar `ansible-playbook cluster.yml`. Criar as VMs com Terraform ou obter `pong` por SSH não instala Kubernetes. O playbook do cluster também prepara `~/.kube/config` no contentor; sem essa configuração, o kubectl local pode tentar ligar a `localhost:8080`.

Para remover o teste e libertar a porta antes de instalar o frontend:

```bash
ansible-playbook smoke-test-cleanup.yml
```

Remove o namespace dedicado `nginx-smoke-test` e os seus recursos; reservar esse namespace exclusivamente para o teste. Repetir instalação/limpeza é permitido. A limpeza não elimina as VMs nem os discos; os custos dessas máquinas continuam até serem paradas/removidas. A aplicação completa ainda falta instalar.

### Bases de dados da aplicação

Esta etapa instala apenas PostgreSQL para UserService, ProfileService e MovieService, em `movie-rec-hub`. Mantém `trust`, sem passwords, e Services internos na porta `5432`. RecommendationService e Ollama ficam para depois.

Validar localmente no contentor, sem VMs:

```bash
cd /workspace/infra/ansible
ansible-playbook -i tests/inventory.yml databases.yml --syntax-check
/opt/ansible/bin/python3 tests/check-databases.py
```

Quando as VMs e o cluster estiverem instalados, com inventário Terraform atualizado:

```bash
ansible-playbook databases.yml
kubectl -n movie-rec-hub get pods,svc,pvc
kubectl get pv
```

O playbook verifica que o disco está montado, cria três diretórios, aplica os manifests de `kubernetes/` e espera pelos Pods/PVCs. No final verifica ligações SQL pelos nomes internos `users-db`, `profiles-db` e `movie-db`, sem password. As tabelas da aplicação serão geridas pelos serviços quando estes arrancarem: Flyway em User/Profile e Hibernate na configuração atual de Movie.

Cada BD tem uma réplica, um PVC de `4Gi` e um PV local com `Retain`, associado ao worker do disco de dados. O Service `*-headless` fornece identidade ao StatefulSet; o Service com o nome da BD é o endereço interno usado pela aplicação. Os `4Gi` são capacidade declarada, sem quota individual no filesystem partilhado.

Reaplicar o playbook preserva os dados. Substituir um Pod reutiliza o seu PVC. Não eliminar o namespace/PVCs para reiniciar as BDs: um PV retido pode ficar `Released` e exigir recuperação manual. `terraform destroy` elimina também o disco de dados; PV/PVC não são backups nem tornam as BDs independentes desse worker. O disco deve permanecer montado durante a execução.

### Imagens e Artifact Registry

Construímos frontend, UserService, ProfileService e MovieService com os seus Dockerfiles. `infra/images/compose.yaml` serve para build/push; não executar `up` nesse ficheiro. As imagens são `linux/amd64`, como as VMs `e2`. RecommendationService/Ollama ficam adiados.

No contentor de gestão, preparar o registry privado do projeto pessoal, sem criar VMs:

```bash
cd /workspace/infra/registry
terraform init
terraform validate
terraform test
terraform plan -out=registry.tfplan
terraform apply registry.tfplan
terraform output image_prefix
```

Rever o plano antes de aplicar. Declara apenas a API Artifact Registry e um repositório Docker `psia-g34`, em `europe-west4`, com tags imutáveis e scanning desativado. A configuração é comum; o Project ID e o estado são pessoais. O estado separado conserva as imagens quando fazemos `destroy` em `infra/gcp/`.

No terminal **do computador**, na raiz de `g34/`:

```bash
export IMAGE_TAG=step3-20261013-1  # Exemplo: escolher uma versão NOVA para outro build.
just images-build
just images-login
just images-push
just images-pull
```

| Comando | Para que serve |
|---|---|
| `images-build` | Compila/empacota as quatro aplicações e atribui o endereço/tag final; não precisa de login nem de registry existente. |
| `images-login` | Usa o gcloud do contentor já iniciado para autenticar o Docker do computador com um token temporário enviado por stdin. |
| `images-push` | Obtém os quatro endereços do Compose e publica os manifests amd64; não faz build nem inicia aplicações. |
| `images-pull` | Confirma que essa versão pode ser descarregada do registry. |

O prefixo é `europe-west4-docker.pkg.dev/PROJECT_ID/psia-g34`; os nomes são `frontend`, `user-service`, `profile-service` e `movie-service`. Não usar `latest`: outra construção deve ter outra tag, pois a versão publicada não pode ser substituída por outro conteúdo. O build inclui o `pom.xml`/`src` atuais; fazer commit e usar uma tag identificável ao preparar uma entrega. Os Dockerfiles atuais saltam os testes Maven; compilar não substitui testes funcionais.

O push usa explicitamente `--platform linux/amd64`: publica a imagem para as VMs, sem índice de outras plataformas/attestations. No Docker Desktop deste ensaio, o push normal tentou associar manifests diferentes à mesma tag imutável e falhou; esta opção resolveu o problema mantendo a imutabilidade. [Opção oficial Docker](https://docs.docker.com/reference/cli/docker/image/push/).

`images-login` exige `just start` e login Google válido. Se surgir `invalid_rapt`, repetir o login em `just mgmt`. O token dura cerca de uma hora; renovar o login Docker quando necessário. A configuração Docker fica em `infra/management/.local/docker/`, privada e ignorada pelo Git; o socket Docker não é montado no contentor. [Autenticação oficial](https://docs.cloud.google.com/artifact-registry/docs/docker/authentication).

O registry privado exige autenticação também no cluster kubeadm/containerd: login Docker no computador **não autentica os nós**. O acesso automático está preparado na secção seguinte; os Deployments da aplicação ainda faltam. Não precisamos de chaves JSON de service accounts ou de imagens públicas.

Para listar imagens/digests, dentro de `just mgmt`:

```bash
gcloud artifacts docker images list \
  "europe-west4-docker.pkg.dev/$CLOUDSDK_CORE_PROJECT/psia-g34" --include-tags
```

Guardar imagens pode ter custo de armazenamento; descarregá-las para o computador pode ter custo de tráfego. O registry permanece depois de parar ferramentas ou destruir VMs. Não há limpeza automática de versões. `terraform destroy` **em `infra/registry/`** elimina o registry e as suas imagens; é uma limpeza independente e destrutiva. [Preços oficiais](https://cloud.google.com/artifact-registry/pricing).

### Acesso dos nós às imagens privadas

`infra/gcp/registry-access.tf` cria uma **service account Google das VMs**, com `Artifact Registry Reader` apenas no repositório `psia-g34`. Ambas as VMs usam essa identidade. O Ansible instala `auth-provider-gcp` e configura o kubelet para pedir tokens ao metadata server Google quando precisa de uma imagem desse registry. As credenciais são renováveis; não copiamos o login pessoal ou um token permanente para os nós.

Validar localmente em `just mgmt`, sem VMs:

```bash
cd /workspace/infra/gcp
terraform validate
terraform test
cd /workspace/infra/ansible
ansible-playbook -i tests/inventory.yml registry-access.yml --syntax-check
ansible-playbook -i tests/inventory.yml cluster.yml --syntax-check
/opt/ansible/bin/python3 tests/check-registry-access.py
```

O Python testa guards/templates com projeto/nós fictícios. Opcionalmente, `--provider-bin CAMINHO` testa também o executável oficial já descarregado e verificado, com metadata/tokens simulados em localhost; sem esse argumento não executa o plugin. Não consulta Google Cloud nem configura o computador como nó.

**Mais tarde**, depois de rever/aplicar Terraform, regenerar o inventário e instalar o cluster:

```bash
cd /workspace/infra/ansible
ansible-playbook registry-access.yml -e registry_check_tag=step3-20261013-1
```

Usar uma versão de `frontend` publicada **no teu próprio projeto**. O playbook atualiza a configuração se necessário e testa o pull num Pod por nó, incluindo o control plane. Usa `imagePullPolicy: Always`, para verificar acesso mesmo com imagem em cache. Os Pods apenas confirmam a existência do JAR; não arrancam a aplicação. Espera por `Succeeded` e tenta limpar o namespace temporário no fim, também em caso de falha. Não altera as BDs ou instala os serviços.

O binário/ficheiros de configuração são mantidos pelo Ansible; mudanças reiniciam kubelet, preservando também `--node-ip`. No cluster novo já ficam instalados por `cluster.yml`; repetir `registry-access.yml` acrescenta a verificação real. Este teste **ainda não foi executado nas VMs**. Não é necessário `images-login` nos nós nem `imagePullSecrets` com tokens de uma hora.

O registry é gerido por `infra/registry/`; identidade e permissão pertencem ao estado `infra/gcp/` e são removidas com a instalação das VMs. Não destruir o registry enquanto o cluster depender das suas imagens. [Credential providers Kubernetes](https://kubernetes.io/docs/tasks/administer-cluster/kubelet-credential-provider/).

### Deployment das quatro aplicações

`deploy.yml` instala frontend, UserService, ProfileService e MovieService em `movie-rec-hub`. Usa as imagens publicadas, ligações internas às três BDs e Secret para a chave TMDB. RecommendationService/Ollama continuam adiados. Não cria VMs nem instala o cluster/BDs por si.

No computador, na raiz de `g34/`, preparar uma vez:

```bash
install -d -m 0700 infra/management/.local
cp -n infra/ansible/deployment.example.yml infra/management/.local/deployment.yml
chmod 600 infra/management/.local/deployment.yml
```

Editar **apenas a cópia local**: `application_image_tag` deve existir nas quatro imagens do teu registry; preencher `tmdb_api_key` com a chave API v3 TMDB. O ficheiro fica ignorado pelo Git e acessível no contentor pelo mount do projeto. Não passar a chave na linha de comandos. O Secret é aplicado por stdin, com output oculto, sem escrever a chave num ficheiro remoto; Secret não equivale a encriptação de dados no cluster.

Validar agora em `just mgmt`, sem VMs/chave real:

```bash
cd /workspace/infra/ansible
ansible-playbook -i tests/inventory.yml deploy.yml --syntax-check
/opt/ansible/bin/python3 tests/check-deployment.py
```

Os testes usam variáveis e chave fictícias, verificam guards, templates e contratos com o Compose. Não fazem deployment nem contactam Google/TMDB.

**Mais tarde**, com imagens publicadas, Terraform aplicado, inventário atualizado e `cluster.yml` concluído:

```bash
cd /workspace/infra/ansible
ansible-playbook registry-access.yml -e registry_check_tag=step3-20261013-1
# Se o teste Nginx ainda ocupar a porta 30510:
ansible-playbook smoke-test-cleanup.yml
ansible-playbook databases.yml
ansible-playbook deploy.yml -e @../management/.local/deployment.yml
kubectl -n movie-rec-hub get deployments,pods,services
```

Usar em `registry_check_tag` a versão escolhida no ficheiro local. O deploy recusa a porta 30510 se estiver ocupada por outro Service, espera pelas BDs, aplica ConfigMaps/Secret/manifests, espera pelos quatro Deployments e verifica readiness/página de login através do IP público dos workers. Mostra `http://IP_PUBLICO_DO_WORKER:30510/` para abrir no browser. Os backends e as BDs usam Services internos.

Os manifests ficam em `kubernetes/config/` e `kubernetes/services/`. Têm uma réplica e estratégia `Recreate`: atualizar um serviço implica uma interrupção curta; reiniciar UserService perde as sessões em memória. As probes distinguem liveness de readiness, que inclui a própria BD nos backends. Recursos/JVM têm valores iniciais para as VMs pequenas, sem teste de carga GCP.

Repetir com os mesmos parâmetros mantém os Pods. Mudanças na configuração fazem rollout; mudar a chave reinicia MovieService para carregar o novo ambiente. Os dados das BDs são conservados. A automação não apaga PVCs/namespace nem ressemeia uma BD Movie já preenchida.

Os manifests passaram num KIND temporário local; execução Ansible por SSH e instalação completa nas VMs GCP continuam por validar.

Para terminar, executar `exit` dentro da shell e depois `just stop` no computador. Credenciais, chaves e kubeconfig persistem nas montagens/volumes; estado/planos ficam em `infra/gcp/` e `infra/registry/`. Os ficheiros pessoais são ignorados pelo Git.
