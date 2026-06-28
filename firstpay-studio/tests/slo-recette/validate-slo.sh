#!/usr/bin/env bash
# Valide les SLO à partir d'un rapport Gatling (simulation.log) si présent.
# Usage : ./tests/slo-recette/validate-slo.sh [path/to/simulation.log]

set -euo pipefail
LOG="${1:-tests/load-tests/build/reports/gatling/*/simulation.log}"
shopt -s nullglob
files=($LOG)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "WARN: aucun simulation.log Gatling — exécutez d'abord les tests de charge."
  echo "  cd tests/load-tests && ./gradlew gatlingRun-com.firstpay.load.TransactionLoadSimulation"
  exit 0
fi

file="${files[0]}"
echo "Analyse: $file"

# Gatling log : colonnes REQUEST, STATUS, RESPONSE_TIME
p99=$(awk '/POST \/api\/v1\/transactions/ { rt=$NF; if(rt+0>p99+0) p99=rt } END { print p99+0 }' "$file")
errors=$(awk '/KO/ { ko++ } END { print ko+0 }' "$file")
total=$(awk '/REQUEST/ { t++ } END { print t+0 }' "$file")

echo "  requêtes: $total | erreurs KO: $errors | P99 approx: ${p99}ms"

if [[ "$p99" -gt 500 ]]; then
  echo "FAIL: P99 ${p99}ms > 500ms"
  exit 1
fi

if [[ "$total" -gt 0 && "$errors" -gt 0 ]]; then
  rate=$(awk "BEGIN { printf \"%.2f\", ($errors/$total)*100 }")
  echo "  taux d'erreur: ${rate}%"
  awk "BEGIN { exit ($rate >= 0.1) ? 1 : 0 }" || { echo "FAIL: erreurs >= 0.1%"; exit 1; }
fi

echo "PASS: SLO dev respectés (P99 < 500ms, erreurs < 0.1%)"
exit 0
