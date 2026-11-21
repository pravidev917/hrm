#!/usr/bin/env bash
# Deploy one image tag to the Oracle Cloud VM, with automatic rollback.
#
# Runs ON the instance (CI copies it to /opt/hrm/deploy-remote.sh and invokes it
# over SSH). It never builds: the image is pulled from GHCR by tag.
#
#   sudo IMAGE_TAG=production-abc1234 IMAGE_REGISTRY=ghcr.io/pravidev917 \
#        /opt/hrm/deploy-remote.sh
#
# Exit codes: 0 deployed, 1 bad input, 2 pull failed, 3 health failed and the
# previous tag was restored, 4 health failed and rollback failed too.
#
# Layout on the instance:
#   /opt/hrm/docker-compose.yml   from deploy/oracle/docker-compose.yml
#   /opt/hrm/Caddyfile            from deploy/oracle/Caddyfile
#   /opt/hrm/deploy.env           IMAGE_REGISTRY, API_DOMAIN, ACME_EMAIL
#   /opt/hrm/current-tag          last tag that passed the health check
#   /etc/hrm/backend.env          secrets + cookie/CORS settings (root:hrm 0640)

set -Eeuo pipefail

APP_DIR="${HRM_APP_DIR:-/opt/hrm}"
COMPOSE_FILE="${HRM_COMPOSE_FILE:-$APP_DIR/docker-compose.yml}"
DEPLOY_ENV="${HRM_DEPLOY_ENV:-$APP_DIR/deploy.env}"
STATE_FILE="${HRM_STATE_FILE:-$APP_DIR/current-tag}"
ENV_FILE="${HRM_ENV_FILE:-/etc/hrm/backend.env}"
WAIT_ATTEMPTS="${HRM_WAIT_ATTEMPTS:-40}"
WAIT_SECONDS="${HRM_WAIT_SECONDS:-3}"
KEEP_IMAGES="${HRM_KEEP_IMAGES:-3}"
IMAGE_REPOS="${HRM_IMAGE_REPOS:-hrm-backend}"

die() { printf 'ERROR: %s\n' "$*" >&2; exit "${2:-1}"; }
log() { printf '[deploy] %s\n' "$*"; }

[ "$(id -u)" -eq 0 ] || die "run as root (sudo)"
[ -f "$COMPOSE_FILE" ] || die "missing compose file: $COMPOSE_FILE" 1
[ -f "$ENV_FILE" ] || die "missing env file: $ENV_FILE" 1
command -v docker >/dev/null || die "docker is not installed" 1

# deploy.env holds non-secret settings. set -a so docker compose interpolates
# ${IMAGE_REGISTRY}, ${API_DOMAIN} and friends from this process environment.
if [ -f "$DEPLOY_ENV" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$DEPLOY_ENV"
  set +a
fi

: "${IMAGE_TAG:?set IMAGE_TAG}"
: "${IMAGE_REGISTRY:?set IMAGE_REGISTRY (e.g. ghcr.io/pravidev917)}"
export IMAGE_TAG IMAGE_REGISTRY

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

# GHCR packages for a public repository can be pulled anonymously. If they are
# private, deploy.env may carry GHCR_USER plus a read-only PAT in GHCR_TOKEN.
if [ -n "${GHCR_TOKEN:-}" ]; then
  log "logging in to ghcr.io as ${GHCR_USER:?set GHCR_USER when GHCR_TOKEN is set}"
  printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin >/dev/null
fi

previous_tag=""
if [ -s "$STATE_FILE" ]; then
  previous_tag="$(tr -d '\r\n' <"$STATE_FILE")"
fi
log "target tag: ${IMAGE_TAG} (previous: ${previous_tag:-none})"

log "pulling images"
if ! compose pull; then
  die "docker compose pull failed - nothing was changed" 2
fi

log "starting stack"
if ! compose up -d --remove-orphans; then
  if [ -n "$previous_tag" ] && [ "$previous_tag" != "$IMAGE_TAG" ]; then
    log "up failed; restoring ${previous_tag}"
    IMAGE_TAG="$previous_tag" compose up -d --remove-orphans && die "deployment failed, previous tag restored" 3
  fi
  die "deployment failed and no previous tag is known" 4
fi

# The backend is not published on the host (only Caddy reaches it), so the
# container's own healthcheck - curl /api/health, which requires the Supabase
# connection to answer - is the source of truth.
container_health() {
  local cid
  cid="$(compose ps -q backend 2>/dev/null | head -1)" || return 1
  [ -n "$cid" ] || return 1
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null
}

log "waiting for the backend healthcheck"
healthy=0
for attempt in $(seq 1 "$WAIT_ATTEMPTS"); do
  status="$(container_health || echo unknown)"
  if [ "$status" = "healthy" ]; then
    healthy=1
    break
  fi
  [ "$attempt" -eq "$WAIT_ATTEMPTS" ] && log "last status: ${status}"
  sleep "$WAIT_SECONDS"
done

if [ "$healthy" -eq 1 ] && [ -n "${API_DOMAIN:-}" ] && command -v curl >/dev/null; then
  # Also prove the public path (Caddy + certificate) answers, not just the app.
  public_url="https://${API_DOMAIN}/api/health"
  for attempt in $(seq 1 10); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$public_url" || true)"
    [ "$code" = "200" ] && { log "${public_url} -> 200"; break; }
    [ "$attempt" -eq 10 ] && log "WARNING: ${public_url} returned ${code} (certificate may still be issuing)"
    sleep 3
  done
fi

if [ "$healthy" -eq 1 ]; then
  state_tmp="$(mktemp "$STATE_FILE.XXXXXX")"
  printf '%s\n' "$IMAGE_TAG" >"$state_tmp"
  mv -f "$state_tmp" "$STATE_FILE"
  log "healthy - recorded tag ${IMAGE_TAG}"
  compose ps
  # Keep a few old tags around for manual rollback, drop the rest.
  for repo in $IMAGE_REPOS; do
    docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' \
      | awk -v r="${IMAGE_REGISTRY}/${repo}" '$1 ~ "^"r":" {print $1, $2}' \
      | sort -k1 -r | tail -n "+$((KEEP_IMAGES + 1))" \
      | while read -r image _id; do docker rmi "$image" >/dev/null 2>&1 || true; done
  done
  exit 0
fi

compose logs --tail 80 backend >&2 || true
if [ -n "$previous_tag" ] && [ "$previous_tag" != "$IMAGE_TAG" ]; then
  log "health check failed; rolling back to ${previous_tag}"
  if IMAGE_TAG="$previous_tag" compose up -d --remove-orphans; then
    die "health check failed; previous tag ${previous_tag} restored" 3
  fi
  die "health check failed and rollback failed" 4
fi

die "health check failed and no previous tag is available" 4
