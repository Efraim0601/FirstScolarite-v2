#!/usr/bin/env bash
# Smoke test post-déploiement production (HTTPS).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
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

echo "==> Conteneurs actifs"
  ${COMPOSE} ps --format "table {{.Name}}\t{{.Status}}" 2>/dev/null \
  || docker compose -p firstpay-studio -f docker-compose.yml -f docker-compose.prod.yml ps

echo "==> Smoke test production terminé"
