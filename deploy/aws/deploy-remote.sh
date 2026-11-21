#!/usr/bin/env bash
# Deploy one image tag to the EC2 Docker host, with automatic rollback.
#
# Runs ON the instance (CI copies it to /opt/hrm/deploy-remote.sh and invokes it
# over SSH). It never builds: images are pulled from ECR by tag.
#
#   sudo IMAGE_TAG=<git-sha> ECR_REGISTRY=<acct>.dkr.ecr.<region>.amazonaws.com \
#        /opt/hrm/deploy-remote.sh
#
# Exit codes: 0 deployed, 1 bad input, 2 pull failed, 3 health failed and the
# previous tag was restored, 4 health failed and rollback failed too.
#
# Layout on the instance:
#   /opt/hrm/docker-compose.yml      (from deploy/aws/docker-compose.yml)
#   /opt/hrm/deploy.env             (ECR_REGISTRY, WEB_PORT, AWS_REGION)
#   /opt/hrm/current-tag            (last tag that passed the health check)
#   /etc/hrm/backend.env            (secrets: root:hrm 0640)

set -Eeuo pipefail

APP_DIR="${HRM_APP_DIR:-/opt/hrm}"
COMPOSE_FILE="${HRM_COMPOSE_FILE:-$APP_DIR/docker-compose.yml}"
DEPLOY_ENV="${HRM_DEPLOY_ENV:-$APP_DIR/deploy.env}"
STATE_FILE="${HRM_STATE_FILE:-$APP_DIR/current-tag}"
ENV_FILE="${HRM_ENV_FILE:-/etc/hrm/backend.env}"
HEALTH_URL="${HRM_HEALTH_URL:-http://127.0.0.1:${WEB_PORT:-8080}/api/health}"
WAIT_ATTEMPTS="${HRM_WAIT_ATTEMPTS:-40}"
WAIT_SECONDS="${HRM_WAIT_SECONDS:-3}"
KEEP_IMAGES="${HRM_KEEP_IMAGES:-3}"

die() { printf 'ERROR: %s\n' "$*" >&2; exit "${2:-1}"; }
log() { printf '[deploy] %s\n' "$*"; }

[ "$(id -u)" -eq 0 ] || die "run as root (sudo)"
[ -f "$COMPOSE_FILE" ] || die "missing compose file: $COMPOSE_FILE" 1
[ -f "$ENV_FILE" ] || die "missing env file: $ENV_FILE" 1
command -v docker >/dev/null || die "docker is not installed" 1

# deploy.env holds non-secret settings (registry, port, region).
if [ -f "$DEPLOY_ENV" ]; then
  # shellcheck disable=SC1090
  . "$DEPLOY_ENV"
fi

: "${IMAGE_TAG:?set IMAGE_TAG}"
: "${ECR_REGISTRY:?set ECR_REGISTRY (e.g. 123456789012.dkr.ecr.ap-south-1.amazonaws.com)}"
export IMAGE_TAG ECR_REGISTRY

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

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

log "waiting for ${HEALTH_URL}"
healthy=0
for attempt in $(seq 1 "$WAIT_ATTEMPTS"); do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$HEALTH_URL" || true)"
  if [ "$code" = "200" ]; then
    healthy=1
    break
  fi
  sleep "$WAIT_SECONDS"
done

if [ "$healthy" -eq 1 ]; then
  state_tmp="$(mktemp "$STATE_FILE.XXXXXX")"
  printf '%s\n' "$IMAGE_TAG" >"$state_tmp"
  mv -f "$state_tmp" "$STATE_FILE"
  log "healthy - recorded tag ${IMAGE_TAG}"
  compose ps
  # Keep a few old tags around for manual rollback, drop the rest.
  for repo in hrm-backend hrm-web; do
    docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' \
      | awk -v r="${ECR_REGISTRY}/${repo}" '$1 ~ "^"r":" {print $1, $2}' \
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
