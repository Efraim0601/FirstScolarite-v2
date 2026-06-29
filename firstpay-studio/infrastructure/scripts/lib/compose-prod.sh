#!/usr/bin/env bash
# Utilitaires Docker Compose production (source depuis deploy/update/verify).
#
# Prérequis côté appelant : PROJECT_ROOT, COMPOSE_PROJECT_NAME, REVERSE_PROXY (.env).

_compose_log() {
  if declare -f log >/dev/null 2>&1; then
    log "$@"
  else
    echo "[compose] $*"
  fi
}

compose_prod_file_args() {
  echo "-f docker-compose.yml -f docker-compose.prod.yml"
  if [[ "${REVERSE_PROXY:-caddy}" == "nginx" ]]; then
    echo "-f docker-compose.nginx-prod.yml"
  fi
}

refresh_compose_cmd() {
  COMPOSE="docker compose -p ${COMPOSE_PROJECT_NAME:-firstpay-studio} $(compose_prod_file_args | tr '\n' ' ')"
}

stop_conflicting_stacks() {
  if [[ "${STOP_DEMO_STACK:-1}" != "1" ]]; then
    return 0
  fi
  if docker compose -p firstpay-demo ps -q 2>/dev/null | grep -q .; then
    _compose_log "Arrêt de la stack recette firstpay-demo (ports 14200/18080)…"
    docker compose -p firstpay-demo -f docker-compose.yml -f docker-compose.demo.yml down 2>/dev/null || true
  fi
}

# Détecte les ports publiés en double (fusion Compose sans !reset).
verify_compose_port_bindings() {
  local config err=0
  if ! config=$(${COMPOSE} config 2>&1); then
    _compose_log "ERREUR: docker compose config a échoué — ${config}"
    return 1
  fi

  _published_count() {
    local svc=$1 port=$2
    awk -v svc="$svc" -v port="$port" '
      $0 ~ "^  " svc ":$" { in_svc=1; next }
      in_svc && /^  [a-zA-Z0-9_-]+:$/ { exit }
      in_svc && $1 == "published:" {
        gsub(/"/, "", $2)
        if ($2 == port) count++
      }
      END { print count + 0 }
    ' <<<"$config"
  }

  if [[ "${REVERSE_PROXY:-caddy}" == "nginx" ]]; then
    local svc port count
    for svc in api-gateway frontend payer-frontend; do
      case "$svc" in
        api-gateway) port="${NGINX_GATEWAY_PORT:-18080}" ;;
        frontend) port="${NGINX_FRONTEND_PORT:-14200}" ;;
        payer-frontend) port="${NGINX_PAYER_PORT:-14300}" ;;
      esac
      count=$(_published_count "$svc" "$port")
      if [[ "$count" -ne 1 ]]; then
        _compose_log "ERREUR: ${svc} — ${count} mapping(s) sur le port ${port} (attendu: 1)."
        _compose_log "  Cause probable : ports: [] sans !reset dans docker-compose.prod.yml."
        err=1
      fi
    done
  else
    local svc count
    for svc in api-gateway frontend payer-frontend; do
      count=$(awk -v svc="$svc" '
        $0 ~ "^  " svc ":$" { in_svc=1; next }
        in_svc && /^  [a-zA-Z0-9_-]+:$/ { exit }
        in_svc && $1 == "published:" { count++ }
        END { print count + 0 }
      ' <<<"$config")
      if [[ "$count" -ne 0 ]]; then
        _compose_log "ERREUR: ${svc} expose ${count} port(s) hôte en mode Caddy (attendu: 0)."
        err=1
      fi
    done
  fi

  return "$err"
}

ensure_postgres_for_backup() {
  if ${COMPOSE} ps --status running postgres 2>/dev/null | grep -q .; then
    return 0
  fi
  _compose_log "PostgreSQL arrêté — démarrage pour la sauvegarde…"
  ${COMPOSE} up -d postgres
  local i=0
  while [[ $i -lt 24 ]]; do
    local health
    health=$(${COMPOSE} ps postgres --format '{{.Health}}' 2>/dev/null || echo "")
    if [[ "${health}" == "healthy" ]]; then
      _compose_log "PostgreSQL prêt pour la sauvegarde."
      return 0
    fi
    sleep 5
    i=$((i + 1))
  done
  _compose_log "ATTENTION : PostgreSQL non healthy — la sauvegarde peut échouer."
}
