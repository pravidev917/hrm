#!/usr/bin/env bash
# EC2 bootstrap for the Docker deployment (Amazon Linux 2023).
#
# Run as root once per instance, or let Terraform pass this file as user_data
# (deploy/aws/terraform/main.tf does exactly that).
#
# It installs Docker + the compose plugin, prepares the directories the
# deployment expects, and enables log rotation. It does NOT start the stack:
# images and /etc/hrm/backend.env are deployed by CI (see deploy-aws.yml).

set -euo pipefail

log() { printf '[user-data] %s\n' "$*"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "run as root" >&2
  exit 1
fi

log "installing docker"
dnf install -y docker
systemctl enable --now docker

# The compose plugin ships as a docker CLI plugin on AL2023.
if ! docker compose version >/dev/null 2>&1; then
  log "installing docker compose plugin"
  dnf install -y docker-compose-plugin || {
    mkdir -p /usr/libexec/docker/cli-plugins /usr/local/lib/docker/cli-plugins
    curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
      -o /usr/libexec/docker/cli-plugins/docker-compose
    chmod +x /usr/libexec/docker/cli-plugins/docker-compose
    ln -sf /usr/libexec/docker/cli-plugins/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
  }
fi

log "creating directories"
install -d -m 0755 /opt/hrm
install -d -m 0750 -g adm /etc/hrm
install -d -m 0750 /var/backups/hrm

# Docker logs are capped per container in the compose file; keep the daemon's
# own logs from growing without bound too.
if [ -f /etc/docker/daemon.json ] && [ -s /etc/docker/daemon.json ]; then
  log "docker daemon.json already present - leaving it alone"
else
  cat >/etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
  systemctl restart docker
fi

log "installing operational helpers"
dnf install -y postgresql16 curl jq || true

log "done - waiting for /etc/hrm/backend.env and an image tag from CI"
