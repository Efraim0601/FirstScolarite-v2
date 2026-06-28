#!/usr/bin/env bash
# URL publique de l'application (FRONTEND_ORIGIN prime sur https://DOMAIN).

public_base_url() {
  if [[ -n "${FRONTEND_ORIGIN:-}" ]]; then
    echo "${FRONTEND_ORIGIN%/}"
    return 0
  fi
  if [[ -n "${DOMAIN:-}" ]]; then
    echo "https://${DOMAIN}"
    return 0
  fi
  echo "https://esign.afbdei.com"
}

warn_domain_origin_mismatch() {
  [[ -n "${FRONTEND_ORIGIN:-}" && -n "${DOMAIN:-}" ]] || return 0
  local origin_host
  origin_host="$(echo "${FRONTEND_ORIGIN}" | sed -E 's|https?://||' | cut -d/ -f1)"
  if [[ "${origin_host}" != "${DOMAIN}" ]]; then
    echo "[deploy] ATTENTION : DOMAIN=${DOMAIN} ≠ FRONTEND_ORIGIN (${origin_host}) — URLs affichées depuis FRONTEND_ORIGIN." >&2
  fi
}
