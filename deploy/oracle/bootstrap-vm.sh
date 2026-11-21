#!/usr/bin/env bash
# Prepare a fresh Oracle Cloud (Ubuntu 22.04 / 24.04) instance for HRM.
#
# Run once on the instance, as a sudoer:
#
#   sudo ./bootstrap-vm.sh                 # docker + directories only
#   sudo ./bootstrap-vm.sh --open-http     # also open 80/443 in the host firewall
#
# It never touches application data: no database here (Supabase), no frontend
# (Vercel). The host runs the backend container plus Caddy, nothing else.
#
# Two firewalls exist on OCI. Opening the ports in the console security list is
# not enough for the Ubuntu images, whose iptables rules reject everything except
# SSH - that is what --open-http fixes. Both are needed.

set -Eeuo pipefail

APP_DIR="${HRM_APP_DIR:-/opt/hrm}"
ENV_DIR="${HRM_ENV_DIR:-/etc/hrm}"
SERVICE_USER="${HRM_SERVICE_USER:-hrm}"
OPEN_HTTP=0

for arg in "$@"; do
  case "$arg" in
    --open-http) OPEN_HTTP=1 ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) printf 'unknown argument: %s\n' "$arg" >&2; exit 1 ;;
  esac
done

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
log() { printf '[bootstrap] %s\n' "$*"; }

[ "$(id -u)" -eq 0 ] || die "run as root (sudo)"
command -v apt-get >/dev/null || die "this script targets Ubuntu/Debian (apt-get not found)"

log "installing docker engine and the compose plugin"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
# docker.io + docker-compose-v2 exist in the Ubuntu archive; no third-party repo,
# no downloaded install script.
apt-get install -y -qq docker.io docker-compose-v2 ca-certificates curl >/dev/null
systemctl enable --now docker

log "docker: $(docker --version)"
docker compose version >/dev/null || die "the docker compose plugin is missing (apt install docker-compose-v2)"

log "creating ${SERVICE_USER} group and ${APP_DIR} / ${ENV_DIR}"
getent group "$SERVICE_USER" >/dev/null || groupadd --system "$SERVICE_USER"
install -d -m 0755 -o root -g root "$APP_DIR"
# Secrets live here, owned root:hrm 0640 - the deploy script runs as root, so the
# group is only for humans who need to read them.
install -d -m 0750 -o root -g "$SERVICE_USER" "$ENV_DIR"

if [ ! -f "$ENV_DIR/backend.env" ]; then
  log "NOTE: $ENV_DIR/backend.env does not exist yet - copy it from"
  log "      deploy/oracle/env/<environment>.backend.env.example before the first deploy"
fi

if [ "$OPEN_HTTP" -eq 1 ]; then
  log "allowing 80/443 in the host firewall (OCI Ubuntu images reject them by default)"
  if command -v iptables >/dev/null; then
    for port in 80 443; do
      # Insert ACCEPT ahead of the catch-all REJECT rule of the OCI image.
      iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null || {
        reject_line="$(iptables -L INPUT --line-numbers -n | awk '/REJECT/ {print $1; exit}')"
        if [ -n "$reject_line" ]; then
          iptables -I INPUT "$reject_line" -p tcp --dport "$port" -j ACCEPT
        else
          iptables -A INPUT -p tcp --dport "$port" -j ACCEPT
        fi
      }
    done
    if command -v netfilter-persistent >/dev/null; then
      netfilter-persistent save >/dev/null
      log "rules persisted with netfilter-persistent"
    else
      log "WARNING: rules are live but not persisted; install iptables-persistent"
      log "         (apt install iptables-persistent) or they vanish on reboot"
    fi
  else
    log "WARNING: iptables not found - open 80/443 in the OCI console only, and check with"
    log "         curl -I http://<public-ip> from outside"
  fi
fi

cat <<EOF

Next steps
  1. OCI console -> Networking -> VCN -> Security list: allow ingress TCP 80 and 443
     from 0.0.0.0/0 (nothing else; SSH from your address only).
  2. Copy the two config files:
       sudo install -m 0640 -o root -g ${SERVICE_USER} <env>.backend.env ${ENV_DIR}/backend.env
       sudo install -m 0640 -o root -g root ${APP_DIR}/deploy.env
  3. Point DNS: ${APP_DIR}/deploy.env API_DOMAIN -> this instance's public IP.
  4. Deploy from CI, or by hand:
       sudo IMAGE_TAG=production-<sha> IMAGE_REGISTRY=ghcr.io/<owner> ${APP_DIR}/deploy-remote.sh

Caddy obtains the certificate on the first request after DNS resolves; nothing
else needs to listen on 80/443.
EOF
