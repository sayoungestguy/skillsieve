#!/usr/bin/env bash
# Build SkillSieve, load it into a local k3s cluster (k3d) and helm-upgrade it.
# Works on macOS and on Linux/WSL2. Needs: docker, k3d, helm, kubectl, git, curl, openssl.
#
#   deploy/local/k3d-up.sh                    # build + deploy everything
#   USE_MOCKS=false deploy/local/k3d-up.sh    # web talks to core-api instead of demo data
#   PORT=8089 CLUSTER=skillsieve OWNER_EMAIL=me@example.com deploy/local/k3d-up.sh
set -euo pipefail

CLUSTER="${CLUSTER:-skillsieve}"
PORT="${PORT:-8089}"
PG_PORT="${PG_PORT:-55432}"
USE_MOCKS="${USE_MOCKS:-true}"
OWNER_EMAIL="${OWNER_EMAIL:-owner@skillsieve.local}"
K3S_IMAGE="rancher/k3s:v1.31.4-k3s1"
NAMESPACE="skillsieve"
SECRET="core-api-secrets"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHART="$ROOT/deploy/helm/skillsieve"
CREDS="$ROOT/deploy/local/.owner-credentials"   # git-ignored

for bin in docker k3d helm kubectl git curl openssl; do
  command -v "$bin" >/dev/null || { echo "missing: $bin" >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "Docker is not running" >&2; exit 1; }

TAG="sha-$(git -C "$ROOT" rev-parse --short=7 HEAD)"
if [ -n "$(git -C "$ROOT" status --porcelain)" ]; then TAG="$TAG-dirty-$(date +%s)"; fi

log() { printf '\n==> %s\n' "$*"; }

log "Postgres (docker compose, port $PG_PORT) — stands in for Supabase"
PG_PORT="$PG_PORT" docker compose -f "$ROOT/deploy/local/docker-compose.yml" up -d --wait

if ! k3d cluster list "$CLUSTER" >/dev/null 2>&1; then
  log "Creating k3d cluster '$CLUSTER' (port $PORT on all interfaces -> Traefik :80)"
  k3d cluster create "$CLUSTER" --image "$K3S_IMAGE" -p "0.0.0.0:${PORT}:80@loadbalancer" --wait
fi
k3d kubeconfig merge "$CLUSTER" --kubeconfig-switch-context >/dev/null
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null

if ! kubectl -n "$NAMESPACE" get secret "$SECRET" >/dev/null 2>&1; then
  log "Creating $SECRET (first run): random JWT secret and owner password"
  OWNER_PASSWORD="$(openssl rand -base64 18 | tr -d '/+=')"
  # BCrypt cost 12 via htpasswd in a throwaway container, so no local tools are needed.
  OWNER_HASH="$(docker run --rm httpd:2.4-alpine htpasswd -bnBC 12 '' "$OWNER_PASSWORD" | tr -d ':\n')"
  kubectl -n "$NAMESPACE" create secret generic "$SECRET" \
    --from-literal=SPRING_DATASOURCE_URL="jdbc:postgresql://host.k3d.internal:${PG_PORT}/skillsieve" \
    --from-literal=SPRING_DATASOURCE_USERNAME=skillsieve_api \
    --from-literal=SPRING_DATASOURCE_PASSWORD=skillsieve_api \
    --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
    --from-literal=OWNER_EMAIL="$OWNER_EMAIL" \
    --from-literal=OWNER_PASSWORD_HASH="$OWNER_HASH" >/dev/null
  (umask 077; printf 'email=%s\npassword=%s\n' "$OWNER_EMAIL" "$OWNER_PASSWORD" > "$CREDS")
  echo "Owner login saved to deploy/local/.owner-credentials (git-ignored)."
fi

log "Building images (tag $TAG, web mocks=$USE_MOCKS)"
docker build -q --build-arg VITE_USE_MOCKS="$USE_MOCKS" -t "skillsieve-web:$TAG" "$ROOT/apps/web"
docker build -q -t "skillsieve-core-api:$TAG" "$ROOT/services/core-api"
k3d image import "skillsieve-web:$TAG" "skillsieve-core-api:$TAG" -c "$CLUSTER"

log "helm upgrade --install"
helm upgrade --install skillsieve "$CHART" -n "$NAMESPACE" \
  --atomic --timeout 5m -f "$CHART/values-local.yaml" \
  --set web.image.tag="$TAG" --set coreApi.image.tag="$TAG"

log "Smoke test"
ok=""
for _ in $(seq 1 30); do
  if curl -fsS "http://localhost:${PORT}/healthz" >/dev/null 2>&1 \
    && curl -fsS "http://localhost:${PORT}/api/actuator/health" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 3
done
[ -n "$ok" ] || { echo "smoke test failed: /healthz or /api/actuator/health" >&2; exit 1; }

case "$(uname -s)" in
  Darwin) LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || true)" ;;
  *)      LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')" ;;
esac
log "Up: http://localhost:${PORT}   LAN: http://${LAN_IP:-<this-machine-ip>}:${PORT}   API: /api"
