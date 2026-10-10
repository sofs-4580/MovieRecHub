#!/usr/bin/env bash
set -euo pipefail

management_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
settings_file="${management_dir}/.env"
credentials_dir="${management_dir}/.local/gcloud"
adc_file="${credentials_dir}/application_default_credentials.json"

usage() {
    cat <<'USAGE'
Usage: ./infra/management/manage.sh <command>

  init             Create local settings and a private credentials directory.
  build            Build the tools image for the current user's UID/GID.
  login            Authorize your Google account and generate local credentials.
  shell [command]  Open the tools shell, or run a command using those credentials.
  check            Verify access to the configured project and ADC authentication.

Edit infra/management/.env after init to set your own GCP_PROJECT_ID.
USAGE
}

fail() {
    printf 'Erro: %s\n' "$*" >&2
    exit 1
}

command_name="${1:-help}"
if [[ $# -gt 0 ]]; then
    shift
fi
case "${command_name}" in
    help|-h|--help) usage; exit 0 ;;
    init|build|login|shell|check) ;;
    *) usage >&2; fail "Comando desconhecido: ${command_name}" ;;
esac
if [[ "${command_name}" != shell && $# -gt 0 ]]; then
    fail "O comando ${command_name} não recebe argumentos adicionais."
fi

export MANAGEMENT_UID="$(id -u)"
export MANAGEMENT_GID="$(id -g)"
[[ "${MANAGEMENT_UID}" != 0 ]] || fail "Executa o script como o teu utilizador normal, sem sudo."

umask 077
if [[ "${command_name}" == init ]]; then
    mkdir -p "${credentials_dir}"
    chmod 0700 "${management_dir}/.local" "${credentials_dir}"
    if [[ ! -e "${settings_file}" ]]; then
        cp "${management_dir}/.env.example" "${settings_file}"
    fi
    chmod 0600 "${settings_file}"
    printf 'Preparação local concluída. Define o teu GCP_PROJECT_ID em %s.\n' "${settings_file}"
    exit 0
fi

command -v docker >/dev/null 2>&1 || fail "Docker não está disponível neste terminal."
[[ -f "${settings_file}" ]] || fail "Executa primeiro ./infra/management/manage.sh init."
[[ -d "${credentials_dir}" && -w "${credentials_dir}" ]] \
    || fail "A pasta local de credenciais não existe ou não é gravável. Executa init como o teu utilizador."

compose=(docker compose --env-file "${settings_file}" -f "${management_dir}/compose.yaml")
# Let Compose parse .env; never execute a personal settings file as shell code.
project_id="$("${compose[@]}" config --environment | sed -n 's/^GCP_PROJECT_ID=//p')"
[[ "${project_id}" != your-project-id ]] || fail "Substitui your-project-id pelo teu projeto no ficheiro .env."
[[ "${project_id}" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] \
    || fail "GCP_PROJECT_ID deve ser um Project ID válido, com 6 a 30 caracteres."

case "${command_name}" in
    build)
        exec "${compose[@]}" build management
        ;;
    login)
        exec "${compose[@]}" run --rm --build management bash -euc '
            umask 077
            gcloud auth login --no-launch-browser --force --update-adc
            gcloud auth application-default set-quota-project "$CLOUDSDK_CORE_PROJECT"
            chmod 0600 "$CLOUDSDK_CONFIG/application_default_credentials.json"
            printf "Credenciais locais preparadas para o projeto %s.\n" "$CLOUDSDK_CORE_PROJECT"
        '
        ;;
    shell|check)
        [[ -s "${adc_file}" && -r "${adc_file}" ]] \
            || fail "Faltam credenciais locais. Executa ./infra/management/manage.sh login."
        if [[ "${command_name}" == check ]]; then
            exec "${compose[@]}" run --rm --build management bash -euc '
                gcloud projects describe "$CLOUDSDK_CORE_PROJECT" --format="value(projectId)"
                gcloud auth application-default print-access-token >/dev/null
                printf "Acesso ao projeto e autenticação ADC confirmados.\n"
            '
        fi
        exec "${compose[@]}" run --rm --build management "$@"
        ;;
esac
