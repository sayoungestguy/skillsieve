#!/usr/bin/env bash
# First-time setup for the Oracle Always Free ARM VM (Ubuntu 24.04). docs/TECHNICAL_DESIGN.md §9.3
# Idempotent: safe to re-run. Run as a sudo-capable user ON THE VM:
#
#   ACME_EMAIL=you@example.com \
#   DUCKDNS_DOMAIN=skillsieve DUCKDNS_TOKEN=xxxx \
#   DEPLOY_PUBKEY="ssh-ed25519 AAAA... github-actions" \
#   bash bootstrap.sh
#
# Nothing here costs money: k3s, cert-manager, sealed-secrets and DuckDNS are free.
set -euo pipefail

: "${ACME_EMAIL:?email for Lets Encrypt expiry notices}"
: "${DUCKDNS_DOMAIN:?DuckDNS subdomain without .duckdns.org}"
: "${DUCKDNS_TOKEN:?DuckDNS token}"
: "${DEPLOY_PUBKEY:?public key that GitHub Actions uses to SSH in}"

CERT_MANAGER_VERSION="v1.16.2"
SEALED_SECRETS_CHART_VERSION="2.16.2"
NAMESPACE="skillsieve"
DEPLOY_USER="deploy"

log() { printf '\n==> %s\n' "$*"; }

log "1/6 OS updates, unattended-upgrades, firewall"
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y unattended-upgrades ufw curl cron fail2ban
sudo dpkg-reconfigure -f noninteractive unattended-upgrades
# Oracle Ubuntu images ship iptables REJECT rules that block 80/443; UFW replaces them.
sudo iptables -D INPUT -j REJECT --reject-with icmp-host-prohibited 2>/dev/null || true
sudo ufw default deny incoming
sudo ufw default allow outgoing
# GitHub-hosted runners have no fixed IPs, so SSH is open but key-only, rate-limited and fail2ban-guarded.
sudo ufw limit 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
# Pod and service CIDRs must reach each other (k3s defaults).
sudo ufw allow from 10.42.0.0/16
sudo ufw allow from 10.43.0.0/16
sudo ufw --force enable
printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin no\n' \
  | sudo tee /etc/ssh/sshd_config.d/10-skillsieve.conf >/dev/null
sudo systemctl reload ssh
sudo systemctl enable --now fail2ban

log "2/6 k3s (Traefik included; API stays private behind UFW)"
if ! command -v k3s >/dev/null; then
  curl -sfL https://get.k3s.io | INSTALL_K3S_CHANNEL=stable sh -s - --write-kubeconfig-mode 600
fi
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
sudo chmod 600 "$KUBECONFIG"
until sudo -E kubectl get nodes 2>/dev/null | grep -q ' Ready'; do sleep 3; done
kc() { sudo -E kubectl "$@"; }

# Redirect all HTTP to HTTPS at Traefik (cert-manager's HTTP-01 solver still works).
kc apply -f - <<YAML
apiVersion: helm.cattle.io/v1
kind: HelmChartConfig
metadata:
  name: traefik
  namespace: kube-system
spec:
  valuesContent: |-
    ports:
      web:
        redirectTo:
          port: websecure
YAML

log "3/6 Helm, cert-manager, sealed-secrets"
if ! command -v helm >/dev/null; then
  curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
fi
hm() { sudo -E helm "$@"; }
hm repo add jetstack https://charts.jetstack.io --force-update
hm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets --force-update
hm upgrade --install cert-manager jetstack/cert-manager \
  --namespace platform --create-namespace --version "$CERT_MANAGER_VERSION" \
  --set crds.enabled=true \
  --set resources.requests.memory=32Mi --set resources.limits.memory=128Mi \
  --set webhook.resources.requests.memory=16Mi --set webhook.resources.limits.memory=64Mi \
  --set cainjector.resources.requests.memory=32Mi --set cainjector.resources.limits.memory=128Mi \
  --wait
hm upgrade --install sealed-secrets sealed-secrets/sealed-secrets \
  --namespace platform --version "$SEALED_SECRETS_CHART_VERSION" \
  --set fullnameOverride=sealed-secrets-controller \
  --set resources.requests.memory=32Mi --set resources.limits.memory=96Mi \
  --wait

log "4/6 ClusterIssuer letsencrypt-prod"
kc apply -f - <<YAML
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: ${ACME_EMAIL}
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
      - http01:
          ingress:
            ingressClassName: traefik
YAML

log "5/6 Namespace and restricted deploy user for GitHub Actions"
kc create namespace "$NAMESPACE" --dry-run=client -o yaml | kc apply -f -
kc -n "$NAMESPACE" create serviceaccount deployer --dry-run=client -o yaml | kc apply -f -
# Namespace-scoped admin only: Actions can manage the release, not the cluster.
kc -n "$NAMESPACE" create rolebinding deployer-admin --clusterrole=admin \
  --serviceaccount="$NAMESPACE:deployer" --dry-run=client -o yaml | kc apply -f -
kc apply -f - <<YAML
apiVersion: v1
kind: Secret
metadata:
  name: deployer-token
  namespace: ${NAMESPACE}
  annotations:
    kubernetes.io/service-account.name: deployer
type: kubernetes.io/service-account-token
YAML
until kc -n "$NAMESPACE" get secret deployer-token -o jsonpath='{.data.token}' | grep -q .; do sleep 1; done

if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  sudo useradd --create-home --shell /bin/bash "$DEPLOY_USER"
fi
DEPLOY_HOME="/home/$DEPLOY_USER"
sudo install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$DEPLOY_HOME/.ssh" "$DEPLOY_HOME/.kube"
# The key may only run helm/kubectl via a forced wrapper; no port forwarding, no PTY.
sudo tee /usr/local/bin/skillsieve-deploy-shell >/dev/null <<'SH'
#!/usr/bin/env bash
set -euo pipefail
case "${SSH_ORIGINAL_COMMAND:-}" in
  helm\ *|kubectl\ *) exec bash -c "$SSH_ORIGINAL_COMMAND" ;;
  *) echo "only helm/kubectl commands are allowed" >&2; exit 1 ;;
esac
SH
sudo chmod 755 /usr/local/bin/skillsieve-deploy-shell
echo "command=\"/usr/local/bin/skillsieve-deploy-shell\",no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty ${DEPLOY_PUBKEY}" \
  | sudo tee "$DEPLOY_HOME/.ssh/authorized_keys" >/dev/null
sudo chown "$DEPLOY_USER:$DEPLOY_USER" "$DEPLOY_HOME/.ssh/authorized_keys"
sudo chmod 600 "$DEPLOY_HOME/.ssh/authorized_keys"

TOKEN=$(kc -n "$NAMESPACE" get secret deployer-token -o jsonpath='{.data.token}' | base64 -d)
CA=$(kc -n "$NAMESPACE" get secret deployer-token -o jsonpath='{.data.ca\.crt}')
sudo tee "$DEPLOY_HOME/.kube/config" >/dev/null <<YAML
apiVersion: v1
kind: Config
clusters:
  - name: k3s
    cluster: { server: https://127.0.0.1:6443, certificate-authority-data: ${CA} }
users:
  - name: deployer
    user: { token: ${TOKEN} }
contexts:
  - name: deployer
    context: { cluster: k3s, user: deployer, namespace: ${NAMESPACE} }
current-context: deployer
YAML
sudo chown "$DEPLOY_USER:$DEPLOY_USER" "$DEPLOY_HOME/.kube/config"
sudo chmod 600 "$DEPLOY_HOME/.kube/config"

log "6/6 DuckDNS IP updater (every 5 minutes)"
sudo install -d -m 700 /opt/duckdns
printf 'url="https://www.duckdns.org/update?domains=%s&token=%s&ip="\n' "$DUCKDNS_DOMAIN" "$DUCKDNS_TOKEN" \
  | sudo tee /opt/duckdns/duck.conf >/dev/null
sudo chmod 600 /opt/duckdns/duck.conf
echo "*/5 * * * * root curl -s -K /opt/duckdns/duck.conf -o /opt/duckdns/last.log" \
  | sudo tee /etc/cron.d/duckdns >/dev/null
sudo curl -s -K /opt/duckdns/duck.conf -o /opt/duckdns/last.log
echo "DuckDNS: $(sudo cat /opt/duckdns/last.log)"

log "Done. Next: add the GitHub secrets listed in deploy/k3s/README.md, then push to main."
