#!/usr/bin/env bash
# Déploiement RECETTE avec données seed Flyway (comptes démo, mot de passe « demo »).
# N'utilise PAS les ports 80/443 → pas de conflit avec nginx/Caddy production.
#
# Usage :
#   chmod +x infrastructure/scripts/deploy-demo.sh
#   sudo ./infrastructure/scripts/deploy-demo.sh
#
# Accès :
#   Frontend  http://<IP>:14200
#   API       http://<IP>:18080
#   Payeur    http://<IP>:14300
#
# Comptes : admin.banque@afrilandfirstbank.com / demo (voir docs/DEPLOIEMENT.md)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-firstpay-demo}"
COMPOSE="docker compose -p ${COMPOSE_PROJECT_NAME} -f docker-compose.yml -f docker-compose.demo.yml"

DEMO_SERVICES=(
  postgres pgbouncer kafka redis
  transaction-service partner-service payment-service reporting-service
  api-gateway frontend payer-frontend
)

log() { echo "[demo] $*"; }
die() { echo "[demo] ERREUR: $*" >&2; exit 1; }

ensure_env() {
  if [[ ! -f .env.demo ]]; then
    log "Création de .env.demo depuis .env.demo.example…"
    cp .env.demo.example .env.demo
    PUBLIC_IP=$(curl -sf --max-time 5 https://ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')
    if [[ -n "${PUBLIC_IP}" ]]; then
      sed -i "s|http://62.169.26.178|http://${PUBLIC_IP}|g" .env.demo
      sed -i "s|http://62.169.26.178|http://${PUBLIC_IP}|g" .env.demo
    fi
  fi
  set -a
  # shellcheck disable=SC1091
  source .env.demo
  set +a
  export JWT_SECRET DB_PASS DB_USER INTERNAL_TOKEN DEMO_FRONTEND_ORIGIN DEMO_WEBHOOK_BASE
}

install_docker_if_missing() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    return 0
  fi
  log "Installation de Docker…"
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker
}

build_and_start() {
  log "Build des images (recette)…"
  local attempt=1
  until ${COMPOSE} build --parallel "${DEMO_SERVICES[@]}"; do
    if [[ $attempt -ge 3 ]]; then die "Build échoué après 3 tentatives."; fi
    log "Nouvelle tentative build ($((attempt + 1))/3)…"
    sleep 15
    attempt=$((attempt + 1))
  done

  log "Démarrage stack démo (projet ${COMPOSE_PROJECT_NAME})…"
  ${COMPOSE} up -d "${DEMO_SERVICES[@]}"

  log "Attente transaction-service (max 3 min)…"
  local i=0
  while [[ $i -lt 36 ]]; do
    STATUS=$(${COMPOSE} ps transaction-service --format '{{.Health}}' 2>/dev/null || echo "")
    [[ "${STATUS}" == "healthy" ]] && break
    sleep 5
    i=$((i + 1))
  done
}

post_info() {
  PUBLIC_IP=$(curl -sf --max-time 5 https://ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')
  cat <<EOF

╔══════════════════════════════════════════════════════════════════╗
║  FirstPay Studio — RECETTE (données seed)                        ║
╠══════════════════════════════════════════════════════════════════╣
║  Frontend : http://${PUBLIC_IP:-localhost}:14200
║  API      : http://${PUBLIC_IP:-localhost}:18080
║  Payeur   : http://${PUBLIC_IP:-localhost}:14300
╠══════════════════════════════════════════════════════════════════╣
║  Connexion (mot de passe : demo)
║    Admin banque  : admin.banque@afrilandfirstbank.com
║    Caissière     : caisse.bonanjo@afrilandfirstbank.com
║    Partenaire    : jospinleunou@softtech.cm
╠══════════════════════════════════════════════════════════════════╣
║  Vérification : ./infrastructure/scripts/verify-deployment.sh
║  Arrêt        : docker compose -p ${COMPOSE_PROJECT_NAME} down
╚══════════════════════════════════════════════════════════════════╝

EOF
}

main() {
  [[ "${EUID:-0}" -eq 0 ]] || die "Exécutez avec sudo."
  install_docker_if_missing
  ensure_env
  build_and_start

  GATEWAY_URL="http://127.0.0.1:18080" FRONTEND_URL="http://127.0.0.1:14200" \
    infrastructure/scripts/verify-deployment.sh 2>/dev/null \
    || log "Smoke test partiel — réessayez verify-deployment.sh dans 1 min."

  post_info
}

main "$@"
