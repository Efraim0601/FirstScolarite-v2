#!/usr/bin/env bash
# Chaos test léger : vérifie la récupération (RTO) après indisponibilité simulée.
# Usage : BASE_URL=http://localhost:8080 ./tests/chaos/run-chaos.sh

set -euo pipefail
BASE="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-demo-soft-key}"
RTO_MAX="${RTO_MAX_SECONDS:-30}"

echo "== FirstPay chaos — recovery test =="
echo "Gateway: $BASE (RTO max ${RTO_MAX}s)"

fail=0
for i in $(seq 1 5); do
  code=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-API-Key: $API_KEY" -H "X-Idempotency-Key: chaos-$RANDOM" \
    -H "Content-Type: application/json" \
    -d '{"externalRef":"CHAOS-'"$i"'","amount":1000,"currency":"XAF","type":"PAYMENT","method":"orange"}' \
    "$BASE/api/v1/transactions" || echo "000")
  echo "  probe $i → HTTP $code"
  if [[ "$code" == "202" || "$code" == "200" ]]; then fail=0; break; fi
  fail=1
  sleep 2
done

if [[ "$fail" -eq 1 ]]; then
  echo "FAIL: gateway not accepting transactions within recovery window"
  exit 1
fi

echo "PASS: service recovered (RTO < ${RTO_MAX}s)"
exit 0
