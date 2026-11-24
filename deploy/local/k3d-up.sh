#!/usr/bin/env bash
# Build SkillSieve, load it into a local k3s cluster (k3d) and helm-upgrade it.
# Works on macOS and on Linux/WSL2. Needs: docker, k3d, helm, kubectl, git, curl.
#
#   deploy/local/k3d-up.sh                    # build + deploy, mock data on
#   USE_MOCKS=false deploy/local/k3d-up.sh
#   PORT=8089 CLUSTER=skillsieve deploy/local/k3d-up.sh
set -euo pipefail

CLUSTER="${CLUSTER:-skillsieve}"
PORT="${PORT:-8089}"
USE_MOCKS="${USE_MOCKS:-true}"
K3S_IMAGE="rancher/k3s:v1.31.4-k3s1"
NAMESPACE="skillsieve"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHART="$ROOT/deploy/helm/skillsieve"

for bin in docker k3d helm kubectl git curl; do
  command -v "$bin" >/dev/null || { echo "missing: $bin" >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "Docker is not running" >&2; exit 1; }

TAG="sha-$(git -C "$ROOT" rev-parse --short=7 HEAD)"
if [ -n "$(git -C "$ROOT" status --porcelain)" ]; then TAG="$TAG-dirty-$(date +%s)"; fi

log() { printf '\n==> %s\n' "$*"; }

if ! k3d cluster list "$CLUSTER" >/dev/null 2>&1; then
  log "Creating k3d cluster '$CLUSTER' (port $PORT on all interfaces -> Traefik :80)"
  k3d cluster create "$CLUSTER" --image "$K3S_IMAGE" -p "0.0.0.0:${PORT}:80@loadbalancer" --wait
fi
k3d kubeconfig merge "$CLUSTER" --kubeconfig-switch-context >/dev/null

log "Building web image skillsieve-web:$TAG (mocks=$USE_MOCKS)"
docker build --build-arg VITE_USE_MOCKS="$USE_MOCKS" -t "skillsieve-web:$TAG" "$ROOT/apps/web"
k3d image import "skillsieve-web:$TAG" -c "$CLUSTER"

log "helm upgrade --install"
helm upgrade --install skillsieve "$CHART" -n "$NAMESPACE" --create-namespace \
  --atomic --timeout 3m -f "$CHART/values-local.yaml" --set web.image.tag="$TAG"

log "Smoke test"
ok=""
for _ in $(seq 1 20); do
  if curl -fsS "http://localhost:${PORT}/healthz" >/dev/null 2>&1; then ok=1; break; fi
  sleep 3
done
[ -n "$ok" ] || { echo "smoke test failed: http://localhost:${PORT}/healthz" >&2; exit 1; }

case "$(uname -s)" in
  Darwin) LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || true)" ;;
  *)      LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')" ;;
esac
log "Up: http://localhost:${PORT}   LAN: http://${LAN_IP:-<this-machine-ip>}:${PORT}"
