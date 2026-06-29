#!/usr/bin/env bash
# Restaure nginx hôte sur 80/443 après un conflit avec Caddy/Docker.
#
# Symptômes :
#   - certbot : bind() to 0.0.0.0:80 failed (Address already in use)
#   - nginx.service is not active, cannot reload
#
# Usage (sur le VPS) :
#   sudo ./infrastructure/scripts/recover-host-nginx.sh
#
# Puis :
#   sudo certbot --nginx -d esign.afbvcard.com
#   sudo REVERSE_PROXY=nginx DOMAIN=esign.afbvcard.com ./infrastructure/scripts/deploy-production.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

COMPOSE_PROJECT="${COMPOSE_PROJECT_NAME:-firstpay-studio}"
REVERSE_PROXY="${REVERSE_PROXY:-nginx}"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/compose-prod.sh"

log() { echo "[recover-nginx] $*"; }
die() { echo "[recover-nginx] ERREUR: $*" >&2; exit 1; }

[[ "${EUID:-0}" -eq 0 ]] || die "Exécutez avec sudo."

show_ports() {
  log "Occupation actuelle des ports 80 et 443 :"
  ss -tlnp 2>/dev/null | grep -E ':80 |:443 ' || echo "  (aucun listener détecté via ss)"
  docker ps --format 'table {{.Names}}\t{{.Ports}}' 2>/dev/null \
    | grep -E '80|443' || true
}

stop_compose_caddy() {
  command -v docker >/dev/null 2>&1 || return 0
  local files
  files="$(compose_prod_file_args | tr '\n' ' ')"
  if docker compose -p "${COMPOSE_PROJECT}" ${files} ps -q caddy 2>/dev/null | grep -q .; then
    log "Arrêt du conteneur Caddy (projet ${COMPOSE_PROJECT})…"
    docker compose -p "${COMPOSE_PROJECT}" ${files} stop caddy 2>/dev/null || true
    docker compose -p "${COMPOSE_PROJECT}" ${files} rm -f caddy 2>/dev/null || true
  fi
}

stop_docker_on_web_ports() {
  command -v docker >/dev/null 2>&1 || return 0
  while IFS= read -r name; do
    [[ -z "${name}" ]] && continue
    log "Arrêt du conteneur Docker « ${name} » (ports 80/443)…"
    docker stop "${name}" 2>/dev/null || true
  done < <(docker ps --format '{{.Names}} {{.Ports}}' 2>/dev/null \
    | grep -E '(:80->|:443->|0\.0\.0\.0:80|0\.0\.0\.0:443|\[::\]:80|\[::\]:443)' \
    | awk '{print $1}' || true)
}

start_nginx() {
  log "Activation et démarrage de nginx…"
  systemctl enable nginx 2>/dev/null || true
  if ! systemctl start nginx; then
    log "Échec systemctl start nginx — diagnostic :"
    journalctl -u nginx -n 20 --no-pager 2>/dev/null || true
    nginx -t 2>&1 || true
    die "Impossible de démarrer nginx. Vérifiez ss -tlnp | grep -E ':80|:443'"
  fi
  systemctl status nginx --no-pager -l | head -15 || true
}

main() {
  log "=== Récupération nginx hôte ==="
  show_ports

  stop_compose_caddy
  stop_docker_on_web_ports

  if ss -tlnp 2>/dev/null | grep -qE ':80 |:443 '; then
    log "ATTENTION : 80/443 encore occupés après arrêt Docker :"
    show_ports
    die "Libérez manuellement le processus ci-dessus, puis relancez ce script."
  fi

  start_nginx

  log "=== Ports après récupération ==="
  show_ports

  cat <<EOF

Prochaines étapes :
  1. Copier/activer le site nginx :
       infrastructure/nginx/esign.afbvcard.com.conf.example
     → /etc/nginx/sites-available/esign.afbvcard.com
     sudo ln -sf /etc/nginx/sites-available/esign.afbvcard.com /etc/nginx/sites-enabled/
     sudo nginx -t && sudo systemctl reload nginx

  2. Certificat SSL :
       sudo certbot --nginx -d esign.afbvcard.com

  3. Stack Docker SANS Caddy (ports locaux 14200/18080) :
       cd ${PROJECT_ROOT}
       # .env : REVERSE_PROXY=nginx, DOMAIN=esign.afbvcard.com
       sudo REVERSE_PROXY=nginx DOMAIN=esign.afbvcard.com \\
         ./infrastructure/scripts/deploy-production.sh

  4. Vérification :
       curl -I http://127.0.0.1:14200/
       curl -I https://esign.afbvcard.com/

EOF
}

main "$@"
