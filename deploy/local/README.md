# Self-hosted cluster (k3d)

While the Oracle VM is on hold, SkillSieve runs on a local k3s cluster created by [k3d](https://k3d.io). It uses the same Helm chart and the same hardened image as production, with `values-local.yaml` on top: no TLS, and no fixed hostname so any IP on your network works.

## One command

```bash
deploy/local/k3d-up.sh
```

The script starts Postgres with docker compose on port 55432 (it stands in for Supabase). It creates the cluster the first time it runs. Each run then builds the web image (tagged `sha-<commit>`), imports it into k3d, runs `helm upgrade --install --atomic` and smoke-tests `/healthz`. At the end it prints the local and LAN URLs.

| Env var | Default | Meaning |
|---|---|---|
| `PORT` | `8089` | Host port mapped to Traefik, bound on all interfaces |
| `USE_MOCKS` | `true` | Build the web app with MSW demo data. Switch to `false` once the web login page exists. |
| `OWNER_EMAIL` | `owner@skillsieve.local` | Owner account seeded on the first run |
| `PG_PORT` | `55432` | Host port for the compose Postgres (55432 avoids clashing with a local Postgres on 5432) |
| `CLUSTER` | `skillsieve` | k3d cluster name |

Stop and start without losing anything with `k3d cluster stop skillsieve` and `k3d cluster start skillsieve`. Delete everything with `k3d cluster delete skillsieve`.

## core-api and the owner login

On the first run the script creates the Secret `core-api-secrets` in the cluster: a random JWT secret, and the owner email with a random password stored as a BCrypt hash. It writes the plain login to `deploy/local/.owner-credentials`, which is git-ignored with mode 600. The API is at `/api` on the same host and port. Sign in from a terminal like this:

```bash
source deploy/local/.owner-credentials && curl -c /tmp/ss -H 'X-Requested-With: x' -H 'Content-Type: application/json' -d "{\"email\":\"$email\",\"password\":\"$password\"}" http://localhost:8089/api/auth/login
```

The owner is only created when that email doesn't exist yet. To start the database from scratch, run `docker compose -f deploy/local/docker-compose.yml down -v` and `kubectl -n skillsieve delete secret core-api-secrets`, then rerun the script. **This deletes all local data.** To use Supabase instead of the local Postgres, see `docs/runbooks/supabase.md`.

## macOS (current host)

You need Docker Desktop and `brew install k3d helm kubectl`. Other devices on your Wi-Fi open `http://<mac-ip>:8089`. If macOS asks whether Docker may accept incoming connections, allow it. The site is only reachable while the Mac is awake.

## Windows PC with WSL2 (planned host)

1. Install WSL2 Ubuntu and Docker Desktop with *WSL integration* turned on for that distro (or Docker Engine inside WSL).
2. In WSL, install k3d, helm and kubectl, clone the repo **inside the Linux filesystem** (`~/code`, not `/mnt/c`), then run `deploy/local/k3d-up.sh`.
3. Make it reachable on the LAN. WSL2 runs behind NAT by default, so do one of these:
   - **Mirrored networking (Windows 11 22H2+, simplest):** add the following to `%UserProfile%\.wslconfig`, then run `wsl --shutdown`.
     ```ini
     [wsl2]
     networkingMode=mirrored
     ```
   - **Docker Desktop:** ports published by Docker Desktop already listen on Windows, so this step may not be needed.
4. Allow the port through Windows Firewall (admin PowerShell), limited to your private network:
   ```powershell
   New-NetFirewallRule -DisplayName "SkillSieve 8089" -Direction Inbound -Protocol TCP -LocalPort 8089 -Action Allow -Profile Private
   ```
5. Open `http://<windows-pc-ip>:8089` from another device.

## Security notes

- This is plain HTTP for your home LAN only. Don't port-forward 8089 on your router.
- `values-local.yaml` sets `COOKIE_SECURE=false` so login works over plain HTTP on the LAN. Production keeps `Secure` cookies behind HTTPS. Adding LAN HTTPS (mkcert + a Traefik TLS secret) would let you turn this back on.
- On WSL2 with Docker Engine running inside WSL (instead of Docker Desktop), `host.k3d.internal` may not reach a Postgres bound to `127.0.0.1`. If core-api can't connect, bind compose to `0.0.0.0` behind the Windows firewall, or point it at Supabase.
