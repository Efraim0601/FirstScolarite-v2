#!/usr/bin/env bash
# Libère les ports 80/443 pour Caddy (production). Source depuis deploy-production.sh.
#
# Variables :
#   CADDY_HTTP_PORT   (défaut 80)
#   CADDY_HTTPS_PORT  (défaut 443)
#   STOP_HOST_WEB=1    arrête nginx/apache2 (défaut 1)
#   STOP_FOREIGN_DOCKER=1  arrête les conteneurs Docker étrangers sur 80/443 (défaut 1)

ensure_reverse_proxy_ports() {
  local http_port="${CADDY_HTTP_PORT:-80}"
  local https_port="${CADDY_HTTPS_PORT:-443}"
  local project="${COMPOSE_PROJECT_NAME:-firstpay-studio}"

  _port_in_use() {
    ss -tlnp 2>/dev/null | grep -q ":${1} " || netstat -tlnp 2>/dev/null | grep -q ":${1} "
  }

  _show_port_usage() {
    local p=$1
    echo "  --- port ${p} ---"
    ss -tlnp 2>/dev/null | grep ":${p} " || netstat -tlnp 2>/dev/null | grep ":${p} " || echo "  (détails indisponibles)"
  }

  for port in "$http_port" "$https_port"; do
    if _port_in_use "$port"; then
      echo "[ports] Port ${port} occupé avant libération :"
      _show_port_usage "$port"
    fi
  done

  if [[ "${STOP_HOST_WEB:-1}" == "1" ]]; then
    for svc in nginx nginx-full apache2 httpd; do
      if systemctl is-active --quiet "$svc" 2>/dev/null; then
        echo "[ports] Arrêt de ${svc} (libère 80/443)…"
        systemctl stop "$svc" 2>/dev/null || true
        systemctl disable "$svc" 2>/dev/null || true
      fi
    done
  fi

  if [[ "${STOP_FOREIGN_DOCKER:-1}" == "1" ]] && command -v docker >/dev/null 2>&1; then
    while IFS= read -r c; do
      [[ -z "$c" ]] && continue
      if [[ "$c" != ${project}* ]]; then
        echo "[ports] Arrêt du conteneur Docker « ${c} » (conflit ports 80/443)…"
        docker stop "$c" 2>/dev/null || true
      fi
    done < <(docker ps --format '{{.Names}} {{.Ports}}' 2>/dev/null \
      | grep -E '(:80->|:443->|0\.0\.0\.0:80|0\.0\.0\.0:443|\[::\]:80|\[::\]:443)' \
      | awk '{print $1}' || true)
  fi

  for port in "$http_port" "$https_port"; do
    if _port_in_use "$port"; then
      echo "[ports] ERREUR : le port ${port} est toujours occupé :" >&2
      _show_port_usage "$port" >&2
      echo "[ports] Arrêtez le service manuellement ou définissez CADDY_HTTP_PORT/CADDY_HTTPS_PORT dans .env" >&2
      echo "[ports] Exemple diagnostic : ss -tlnp | grep :${port}" >&2
      return 1
    fi
  done

  echo "[ports] Ports ${http_port} et ${https_port} disponibles pour Caddy."
}
