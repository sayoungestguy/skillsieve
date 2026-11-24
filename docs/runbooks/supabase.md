# Supabase setup (free plan)

core-api uses Supabase only as a managed Postgres 16. The `anon` key and Supabase Auth are not used. Everything here is free. **Don't** enable paid add-ons (PITR, compute upgrades, custom domains).

## 1. Create the project

1. Go to <https://supabase.com/dashboard> → **New project**. Pick the **Free** plan and region **Southeast Asia (Singapore)**, which is closest to you.
2. Save the generated database password in your password manager. You need it once, in step 2.
3. In **Project Settings → Data API**, turn the Data API **off** (or leave the `skillsieve` schema out of *Exposed schemas*). core-api talks to Postgres directly, so nothing should be reachable over PostgREST.

## 2. Create the app role

In **SQL Editor**, run the following. Use a long random password for the role; `openssl rand -base64 24` makes one.

```sql
CREATE ROLE skillsieve_api LOGIN PASSWORD '<random password>';
GRANT CREATE ON DATABASE postgres TO skillsieve_api;   -- lets Flyway create schema "skillsieve"
```

core-api runs the Flyway migrations as `skillsieve_api`, so that role owns every table. The migration turns on Row Level Security with no policies, so `anon` and `authenticated` can't read anything (TECHNICAL_DESIGN §3).

## 3. Connection string

**Connect → Session pooler** (port **5432**, IPv4). Use the session pooler, not the transaction pooler on 6543: Flyway and Hibernate need session features. The user name looks like `skillsieve_api.<project-ref>`.

```
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=skillsieve_api.<project-ref>
SPRING_DATASOURCE_PASSWORD=<random password>
```

## 4. Point a cluster at it

**Local k3d cluster.** Replace the generated Secret, then restart core-api. Use a single-quoted heredoc or the `$` in a BCrypt hash will be expanded.

```bash
kubectl -n skillsieve delete secret core-api-secrets
```

```bash
kubectl -n skillsieve create secret generic core-api-secrets \
  --from-literal=SPRING_DATASOURCE_URL='jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require' \
  --from-literal=SPRING_DATASOURCE_USERNAME='skillsieve_api.<project-ref>' \
  --from-literal=SPRING_DATASOURCE_PASSWORD='<random password>' \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=OWNER_EMAIL='you@example.com' \
  --from-literal=OWNER_PASSWORD_HASH="$(docker run --rm httpd:2.4-alpine htpasswd -bnBC 12 '' 'your-login-password' | tr -d ':\n')"
```

```bash
kubectl -n skillsieve rollout restart deploy/skillsieve-core-api
```

**Production (Oracle VM, later).** Encrypt the same keys with `kubeseal` and paste the result into `coreApi.sealedSecret.encryptedData` in `values-prod.yaml`. That output is encrypted, so it's safe to commit.

## 5. Keep it awake

Free projects pause after about a week without activity. `keepalive.yml` (planned, TECHNICAL_DESIGN §9.1) will run `SELECT 1` every 3 days. Until it exists, open the app at least weekly.

## Backups

The free plan has no automatic backups. The nightly `pg_dump` CronJob (TECHNICAL_DESIGN §13) covers this once it exists.
