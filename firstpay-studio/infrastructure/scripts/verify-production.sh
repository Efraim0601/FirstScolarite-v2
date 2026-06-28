#!/usr/bin/env bash
# Smoke test post-déploiement production (HTTPS).
set -euo pipefail

DOMAIN="${DOMAIN:-firstsign.afbdei.com}"
GATEWAY="${GATEWAY_URL:-https://${DOMAIN}}"
FRONTEND="${FRONTEND_URL:-https://${DOMAIN}}"

echo "==> Certificat SSL"
echo | openssl s_client -connect "${DOMAIN}:443" -servername "${DOMAIN}" 2>/dev/null \
  | openssl x509 -noout -subject -dates 2>/dev/null || echo "WARN: impossible de lire le certificat"

echo "==> Redirection HTTPS"
curl -sfI "http://${DOMAIN}/" | head -5

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
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps --format "table {{.Name}}\t{{.Status}}" 2>/dev/null \
  || docker compose ps

echo "==> Smoke test production terminé"
