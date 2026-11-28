#!/usr/bin/env bash
# Build SkillSieve, load it into a local k3s cluster (k3d) and helm-upgrade it.
# Works on macOS and on Linux/WSL2. Needs: docker, k3d, helm, kubectl, git, curl, openssl.
#
#   deploy/local/k3d-up.sh                    # build + deploy everything (web uses core-api)
#   USE_MOCKS=true deploy/local/k3d-up.sh     # web shows built-in demo data instead
#   PORT=8089 CLUSTER=skillsieve OWNER_EMAIL=me@example.com deploy/local/k3d-up.sh
#   GEMINI_API_KEY=… deploy/local/k3d-up.sh  # turn on skill extraction (stored in the cluster only)
#   ASYNC=true deploy/local/k3d-up.sh         # R2: RabbitMQ + queued extraction (ADR-006)
#   OBSERVABILITY=true deploy/local/k3d-up.sh # R2: Prometheus + Loki/Alloy + Grafana at /grafana
#   TAILOR=true deploy/local/k3d-up.sh        # R2: in-cluster Ollama for resume tailoring (~2.5 GB RAM)
set -euo pipefail

CLUSTER="${CLUSTER:-skillsieve}"
PORT="${PORT:-8089}"
PG_PORT="${PG_PORT:-55432}"
USE_MOCKS="${USE_MOCKS:-false}"
ASYNC="${ASYNC:-false}"
OBSERVABILITY="${OBSERVABILITY:-false}"
TAILOR="${TAILOR:-false}"
GRAFANA_SECRET="grafana-secrets"
MQ_SECRET="rabbitmq-secrets"
OWNER_EMAIL="${OWNER_EMAIL:-owner@skillsieve.local}"
K3S_IMAGE="rancher/k3s:v1.31.4-k3s1"
NAMESPACE="skillsieve"
SECRET="core-api-secrets"
SKILLS_SECRET="skills-svc-secrets"
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
PG_PORT="$PG_PORT" docker compose -f "$ROOT/deploy/local/docker-compose.yml" up -d --wait postgres

if ! k3d cluster list "$CLUSTER" >/dev/null 2>&1; then
  log "Creating k3d cluster '$CLUSTER' (port $PORT on all interfaces -> Traefik :80)"
  k3d cluster create "$CLUSTER" --image "$K3S_IMAGE" -p "0.0.0.0:${PORT}:80@loadbalancer" --wait
elif k3d cluster list "$CLUSTER" --no-headers | awk '{print $2}' | grep -q '^0/'; then
  # Stopped (e.g. after Docker Desktop restarts): no servers running.
  log "Starting stopped k3d cluster '$CLUSTER'"
  k3d cluster start "$CLUSTER" --wait
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

# Shared core-api <-> skills-svc token. Reuse the one already in the cluster if present.
INTERNAL_TOKEN="$(kubectl -n "$NAMESPACE" get secret "$SKILLS_SECRET" \
  -o jsonpath='{.data.INTERNAL_TOKEN}' 2>/dev/null | base64 -d 2>/dev/null || true)"
if [ -z "$INTERNAL_TOKEN" ]; then INTERNAL_TOKEN="$(openssl rand -hex 32)"; fi
# Older core-api secrets (before skills-svc) lack the token: add it.
kubectl -n "$NAMESPACE" patch secret "$SECRET" --type merge \
  -p "{\"stringData\":{\"INTERNAL_TOKEN\":\"$INTERNAL_TOKEN\"}}" >/dev/null
# Resume encryption key (R2). Added once and never replaced: a new key makes stored resumes unreadable.
if [ -z "$(kubectl -n "$NAMESPACE" get secret "$SECRET" -o jsonpath='{.data.RESUME_KEY}')" ]; then
  kubectl -n "$NAMESPACE" patch secret "$SECRET" --type merge \
    -p "{\"stringData\":{\"RESUME_KEY\":\"$(openssl rand -base64 32)\"}}" >/dev/null
fi

EXISTING_KEY="$(kubectl -n "$NAMESPACE" get secret "$SKILLS_SECRET" \
  -o jsonpath='{.data.GEMINI_API_KEY}' 2>/dev/null | base64 -d 2>/dev/null || true)"
GEMINI_KEY="${GEMINI_API_KEY:-$EXISTING_KEY}"
case "$GEMINI_KEY" in
  *[[:space:]]*)
    echo "GEMINI_API_KEY contains spaces or line breaks. Paste only the key (AI Studio's Copy button)." >&2
    exit 1
    ;;
esac
# RabbitMQ credentials (R2). Generated once, shared by the broker, core-api and skills-svc.
MQ_PASS="$(kubectl -n "$NAMESPACE" get secret "$MQ_SECRET" \
  -o jsonpath='{.data.RABBITMQ_DEFAULT_PASS}' 2>/dev/null | base64 -d 2>/dev/null || true)"
if [ -z "$MQ_PASS" ]; then MQ_PASS="$(openssl rand -hex 24)"; fi
AMQP_URL=""
if [ "$ASYNC" = "true" ]; then
  kubectl -n "$NAMESPACE" create secret generic "$MQ_SECRET" \
    --from-literal=RABBITMQ_DEFAULT_USER=skillsieve \
    --from-literal=RABBITMQ_DEFAULT_PASS="$MQ_PASS" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  kubectl -n "$NAMESPACE" patch secret "$SECRET" --type merge \
    -p "{\"stringData\":{\"SPRING_RABBITMQ_USERNAME\":\"skillsieve\",\"SPRING_RABBITMQ_PASSWORD\":\"$MQ_PASS\"}}" >/dev/null
  AMQP_URL="amqp://skillsieve:${MQ_PASS}@skillsieve-rabbitmq:5672/"
fi
kubectl -n "$NAMESPACE" create secret generic "$SKILLS_SECRET" \
  --from-literal=DATABASE_URL="postgresql://skillsieve_api:skillsieve_api@host.k3d.internal:${PG_PORT}/skillsieve" \
  --from-literal=INTERNAL_TOKEN="$INTERNAL_TOKEN" \
  --from-literal=GEMINI_API_KEY="$GEMINI_KEY" \
  --from-literal=AMQP_URL="$AMQP_URL" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
if [ -z "$GEMINI_KEY" ]; then
  echo "Note: no GEMINI_API_KEY set, so skill extraction is off (jobs stay 'pending')."
fi

if [ "$OBSERVABILITY" = "true" ] && ! kubectl -n "$NAMESPACE" get secret "$GRAFANA_SECRET" >/dev/null 2>&1; then
  GRAFANA_PASSWORD="$(openssl rand -base64 18 | tr -d '/+=')"
  kubectl -n "$NAMESPACE" create secret generic "$GRAFANA_SECRET" \
    --from-literal=GF_SECURITY_ADMIN_USER=admin \
    --from-literal=GF_SECURITY_ADMIN_PASSWORD="$GRAFANA_PASSWORD" >/dev/null
  (umask 077; printf 'grafana_user=admin\ngrafana_password=%s\n' "$GRAFANA_PASSWORD" >> "$CREDS")
  echo "Grafana login added to deploy/local/.owner-credentials (git-ignored)."
fi

log "Building images (tag $TAG, web mocks=$USE_MOCKS)"
docker build -q --build-arg VITE_USE_MOCKS="$USE_MOCKS" -t "skillsieve-web:$TAG" "$ROOT/apps/web"
docker build -q -t "skillsieve-core-api:$TAG" "$ROOT/services/core-api"
docker build -q -t "skillsieve-skills-svc:$TAG" "$ROOT/services/skills-svc"
k3d image import "skillsieve-web:$TAG" "skillsieve-core-api:$TAG" "skillsieve-skills-svc:$TAG" -c "$CLUSTER"

log "helm upgrade --install"
helm upgrade --install skillsieve "$CHART" -n "$NAMESPACE" \
  --atomic --timeout 15m -f "$CHART/values-local.yaml" \
  --set web.image.tag="$TAG" --set coreApi.image.tag="$TAG" --set skillsSvc.image.tag="$TAG" \
  --set rabbitmq.enabled="$ASYNC" \
  --set observability.enabled="$OBSERVABILITY" \
  --set ollama.enabled="$TAILOR" \
  --set observability.grafana.rootUrl="http://localhost:${PORT}/grafana/"

log "Smoke test"
ok=""
for _ in $(seq 1 30); do
  if curl -fsS "http://localhost:${PORT}/healthz" >/dev/null 2>&1 \
    && curl -fsS "http://localhost:${PORT}/api/health" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 3
done
[ -n "$ok" ] || { echo "smoke test failed: /healthz or /api/health" >&2; exit 1; }

case "$(uname -s)" in
  Darwin) LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || true)" ;;
  *)      LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')" ;;
esac
[ "$OBSERVABILITY" = "true" ] && echo "Grafana: http://localhost:${PORT}/grafana (login in deploy/local/.owner-credentials)"
log "Up: http://localhost:${PORT}   LAN: http://${LAN_IP:-<this-machine-ip>}:${PORT}   API: /api"
