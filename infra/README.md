# Infraestrutura

Ambiente local com gcloud, Terraform, Ansible e kubectl para preparar o deployment na Google Cloud. Todos utilizam a mesma configuração, mas cada membro usa o seu projeto, credenciais e estado Terraform.

## No computador

Requer Docker com Compose, Bash e `just`. No WSL, o Docker Desktop deve estar aberto e ter a integração com a distribuição ativa.

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

O plano pressupõe a API Compute Engine ativa, a rede/sub-rede `default` existente e as regras `default-allow-ssh` e `default-allow-internal` a permitir o acesso necessário.

Para terminar, executar `exit` dentro da shell e depois `just stop` no computador. Credenciais, chaves e kubeconfig persistem nas montagens/volumes; o estado e os planos ficam em `infra/gcp/`. Os ficheiros pessoais são ignorados pelo Git.
