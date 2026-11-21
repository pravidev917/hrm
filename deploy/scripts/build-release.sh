#!/usr/bin/env bash
# Build a deployable release on a development machine or CI runner.
# Nothing is uploaded: the script produces build/release/<timestamp>/ with the
# jar, the SPA bundle and a manifest of checksums.
#
# Usage: deploy/scripts/build-release.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="$ROOT_DIR/build/release/$STAMP"

echo "== HRM release build: $STAMP =="

command -v java >/dev/null || { echo "java not found"; exit 1; }
command -v node >/dev/null || { echo "node not found"; exit 1; }

# The Spring context-load test needs a database and a strong JWT secret. On a
# build machine these come from <repo>/.env (never printed, never in the release).
if [ -f "$ROOT_DIR/.env" ]; then
  set -a; . "$ROOT_DIR/.env"; set +a
  echo "-- loaded build variables from .env"
else
  echo "-- no .env: DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET must already be set"
fi

echo "-- backend: mvnw -B verify"
(cd "$ROOT_DIR/backend" && ./mvnw -B verify)

echo "-- frontend: install, lint, build (same-origin API base)"
(cd "$ROOT_DIR/frontend" && npm ci)
(cd "$ROOT_DIR/frontend" && npm run lint)
(cd "$ROOT_DIR/frontend" && VITE_API_BASE_URL= npm run build)

if grep -RIl "localhost:8080" "$ROOT_DIR/frontend/dist" >/dev/null 2>&1; then
  echo "REFUSING: the bundle still contains a localhost:8080 reference"
  grep -RIl "localhost:8080" "$ROOT_DIR/frontend/dist"
  exit 1
fi

mkdir -p "$OUT_DIR"
cp "$ROOT_DIR/backend/target/backend-"*.jar "$OUT_DIR/backend.jar"
cp -r "$ROOT_DIR/frontend/dist" "$OUT_DIR/dist"
(cd "$OUT_DIR" && sha256sum backend.jar > SHA256SUMS)

echo
echo "Release ready: $OUT_DIR"
echo "Next: copy the folder to the server, then run (on the server)"
echo "  sudo deploy/scripts/deploy-backend.sh  <stamp>/backend.jar"
echo "  sudo deploy/scripts/deploy-frontend.sh <stamp>/dist"
