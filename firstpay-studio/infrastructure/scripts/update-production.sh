#!/usr/bin/env bash
# Mise à jour production : git pull + rebuild + redémarrage des conteneurs.
#
# Usage (depuis la racine du projet sur le serveur) :
#   sudo ./infrastructure/scripts/update-production.sh
#
# Options :
#   GIT_BRANCH=main          branche à déployer
#   SKIP_PULL=1              ne pas faire git pull (rebuild seulement)
#   SKIP_BACKUP=1            ne pas sauvegarder PostgreSQL avant mise à jour

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-firstpay-studio}"
COMPOSE="docker compose -p ${COMPOSE_PROJECT_NAME} -f docker-compose.yml -f docker-compose.prod.yml"
GIT_BRANCH="${GIT_BRANCH:-master}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/firstpay}"

log() { echo "[update] $*"; }
die() { echo "[update] ERREUR: $*" >&2; exit 1; }

[[ -f .env ]] || die "Fichier .env absent. Lancez d'abord deploy-production.sh"
# shellcheck disable=SC1091
set -a && source .env && set +a
DOMAIN="${DOMAIN:-firstsign.afbdei.com}"

render_caddyfile() {
  export DOMAIN SSL_EMAIL="${SSL_EMAIL:-admin@afbdei.com}"
  envsubst '${DOMAIN} ${SSL_EMAIL}' \
    < infrastructure/caddy/Caddyfile.template \
    > infrastructure/caddy/Caddyfile
}

ensure_reverse_proxy_ports() {
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/lib/ensure-ports.sh"
  ensure_reverse_proxy_ports
}

backup_database() {
  if [[ "${SKIP_BACKUP:-0}" == "1" ]]; then
    log "Sauvegarde ignorée (SKIP_BACKUP=1)"
    return 0
  fi
  mkdir -p "${BACKUP_DIR}"
  STAMP=$(date +%Y%m%d_%H%M%S)
  FILE="${BACKUP_DIR}/firstpay_${STAMP}.sql.gz"
  log "Sauvegarde PostgreSQL → ${FILE}…"
  ${COMPOSE} exec -T postgres pg_dump -U "${DB_USER:-firstpay}" firstpay | gzip > "${FILE}" \
    || log "ATTENTION : sauvegarde échouée — poursuite quand même."
  ls -t "${BACKUP_DIR}"/firstpay_*.sql.gz 2>/dev/null | tail -n +8 | xargs -r rm -f
}

git_pull() {
  if [[ "${SKIP_PULL:-0}" == "1" ]]; then
    log "git pull ignoré (SKIP_PULL=1)"
    return 0
  fi
  log "Récupération des mises à jour (branche ${GIT_BRANCH})…"
  git fetch origin
  git checkout "${GIT_BRANCH}"
  git pull --ff-only origin "${GIT_BRANCH}"
}

rebuild_stack() {
  render_caddyfile
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/lib/ensure-ports.sh"
  ensure_reverse_proxy_ports
  log "Rebuild et redémarrage des conteneurs…"
  local attempt=1
  until ${COMPOSE} build --parallel; do
    if [[ $attempt -ge 3 ]]; then
      die "Build Docker échoué après 3 tentatives."
    fi
    log "Build échoué — nouvelle tentative ($((attempt + 1))/3) dans 15 s…"
    sleep 15
    attempt=$((attempt + 1))
  done
  ensure_reverse_proxy_ports
  ${COMPOSE} up -d --remove-orphans
  log "Nettoyage des images Docker inutilisées…"
  docker image prune -f >/dev/null 2>&1 || true
}

main() {
  [[ "${EUID:-0}" -eq 0 ]] || die "Exécutez avec sudo."

  command -v envsubst >/dev/null 2>&1 || apt-get install -y gettext-base 2>/dev/null || true

  git_pull
  backup_database
  rebuild_stack

  log "Attente stabilisation (30 s)…"
  sleep 30

  if [[ -x infrastructure/scripts/verify-production.sh ]]; then
    GATEWAY_URL="https://${DOMAIN}" FRONTEND_URL="https://${DOMAIN}" \
      infrastructure/scripts/verify-production.sh
  fi

  log "Mise à jour terminée — https://${DOMAIN}"
}

main "$@"
