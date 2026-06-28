#!/usr/bin/env bash
# Vérifications post-déploiement (test / staging).
set -euo pipefail

GATEWAY="${GATEWAY_URL:-http://localhost:18080}"
FRONTEND="${FRONTEND_URL:-http://localhost:14200}"

echo "==> Gateway health"
curl -sf "${GATEWAY}/actuator/health" | head -c 200
echo

echo "==> Login partenaire démo (JWT)"
TOKEN=$(curl -sf -X POST "${GATEWAY}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"jospinleunou@softtech.cm","password":"demo"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
test -n "${TOKEN}" && echo "JWT OK (${#TOKEN} chars)"

echo "==> Transactions (API-key démo SOFT)"
curl -sf "${GATEWAY}/api/v1/transactions" \
  -H 'X-Api-Key: demo-soft-key' \
  -H 'X-Tenant-Id: 11111111-1111-1111-1111-111111111111' | head -c 300
echo

echo "==> Reporting summary"
curl -sf "${GATEWAY}/api/v1/reports/summary" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'X-Tenant-Id: 11111111-1111-1111-1111-111111111111' | head -c 200
echo

echo "==> Frontend"
curl -sf -o /dev/null -w "HTTP %{http_code}\n" "${FRONTEND}/"

echo "==> Smoke test terminé"
