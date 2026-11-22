# Deploying to the Oracle Always Free VM

You do these steps by hand once. After that, each push to `main` deploys on its own (`.github/workflows/deploy.yml`).
Everything here is free. Do not pick a paid shape or add a block volume beyond the free 200 GB.

## 1. Create the VM (Oracle Cloud console)

1. **Compute → Instances → Create.** Image: *Canonical Ubuntu 24.04* (aarch64). Shape: *VM.Standard.A1.Flex* with **2 OCPU and 12 GB**. The console must show the "Always Free-eligible" label.
2. Add your own SSH public key. Keep the boot volume at the default size (≤ 200 GB total is free).
3. **Networking → VCN → Security List → Ingress rules.** Open TCP **22, 80 and 443** from `0.0.0.0/0`. Port 22 has to be public because GitHub-hosted runners have no fixed IPs. The bootstrap script makes it key-only, rate-limited and guarded by fail2ban.
4. Note the public IP.

## 2. DuckDNS

Sign in at <https://www.duckdns.org>, create a subdomain (e.g. `skillsieve`), point it at the VM's IP and copy your token.

## 3. Deploy key for GitHub Actions

Run this on your laptop. The key never goes into Git.

```bash
ssh-keygen -t ed25519 -N "" -C github-actions-skillsieve -f ~/.ssh/skillsieve_deploy
```

## 4. Run the bootstrap on the VM

```bash
scp deploy/k3s/bootstrap.sh ubuntu@<VM_IP>:~
```

```bash
ssh ubuntu@<VM_IP>
```

```bash
ACME_EMAIL=you@example.com DUCKDNS_DOMAIN=skillsieve DUCKDNS_TOKEN=<token> \
DEPLOY_PUBKEY="$(cat skillsieve_deploy.pub)" bash bootstrap.sh
```

(Copy the `.pub` file up first, or paste its contents in.) The script is idempotent, so you can re-run it.

## 5. GitHub settings (repo → Settings)

**Secrets → Actions**

| Name | Value |
|---|---|
| `VM_HOST` | the VM's public IP or `skillsieve.duckdns.org` |
| `VM_USER` | `deploy` |
| `VM_SSH_KEY` | contents of `~/.ssh/skillsieve_deploy` (private key) |
| `VM_KNOWN_HOSTS` | output of `ssh-keyscan -t ed25519 <VM_HOST>` |

**Variables → Actions**

| Name | Value |
|---|---|
| `APP_HOST` | `skillsieve.duckdns.org` |
| `WEB_USE_MOCKS` | `true` until core-api is live, then `false` |

**Environments.** Create `production`. You can add yourself as a required reviewer.

## 6. First deploy, then make the packages public

1. Push to `main`. `ci` runs, then `deploy`.
2. The first deploy fails at the image pull because new GHCR packages start out private. Go to GitHub → your profile → **Packages**. Set both **`skillsieve-web`** and **`charts/skillsieve`** to **Public** (Package settings → Change visibility).
3. Re-run the `deploy` workflow. `https://<APP_HOST>` should serve the board with demo data.

## Checks on the VM

```bash
sudo kubectl -n skillsieve get pods,ingress,certificate
```

```bash
sudo kubectl -n skillsieve describe certificate skillsieve-tls
```
