#!/usr/bin/env bash
# Smoke test post-déploiement production (HTTPS).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-firstpay-studio}"
if [[ -f .env ]]; then
  # shellcheck disable=SC1091
  set -a && source .env && set +a
fi
REVERSE_PROXY="${REVERSE_PROXY:-caddy}"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/compose-prod.sh"
refresh_compose_cmd

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/public-url.sh"

DOMAIN="${DOMAIN:-esign.afbdei.com}"
BASE="$(public_base_url)"
GATEWAY="${GATEWAY_URL:-${BASE}}"
FRONTEND="${FRONTEND_URL:-${BASE}}"
# Certificat SSL : nom d'hôte depuis FRONTEND_ORIGIN ou DOMAIN
SSL_HOST="$(echo "${BASE}" | sed -E 's|https?://||' | cut -d/ -f1)"

echo "==> Certificat SSL"
echo | openssl s_client -connect "${SSL_HOST}:443" -servername "${SSL_HOST}" 2>/dev/null \
  | openssl x509 -noout -subject -dates 2>/dev/null || echo "WARN: impossible de lire le certificat"

echo "==> Redirection HTTPS"
curl -sfI "http://${SSL_HOST}/" | head -5

echo "==> Frontend (HTTPS)"
curl -sf -o /dev/null -w "HTTP %{http_code}\n" "${FRONTEND}/"

echo "==> API accessible (HTTPS)"
curl -sf -o /dev/null -w "HTTP %{http_code}\n" "${GATEWAY}/api/v1/auth/login" \
  -X POST -H 'Content-Type: application/json' -d '{}' || echo "WARN: API pas encore joignable"
echo

echo "==> Login partenaire démo (JWT)"
TOKEN=$(curl -sf -X POST "${GATEWAY}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"jospinleunou@softtech.cm","password":"demo"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4 || true)
if [[ -n "${TOKEN}" ]]; then
  echo "JWT OK (${#TOKEN} chars)"
else
  echo "WARN: login démo échoué (normal si données démo purgées en prod opérationnelle)"
fi

if [[ "${REVERSE_PROXY}" == "nginx" ]]; then
  echo "==> Gateway local (nginx hôte)"
  curl -sf -o /dev/null -w "HTTP %{http_code}\n" \
    "http://127.0.0.1:${NGINX_GATEWAY_PORT:-18080}/actuator/health" \
    || echo "WARN: gateway local inaccessible sur 127.0.0.1:${NGINX_GATEWAY_PORT:-18080}"
fi

echo "==> Conteneurs actifs"
${COMPOSE} ps --format "table {{.Name}}\t{{.Status}}"

echo "==> Smoke test production terminé"
