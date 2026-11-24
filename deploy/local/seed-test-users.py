#!/usr/bin/env python3
"""Seed test accounts (and optional sample jobs) into the LOCAL cluster for manual testing.

    python3 deploy/local/seed-test-users.py              # accounts + 6 sample jobs each
    python3 deploy/local/seed-test-users.py --no-jobs    # accounts only
    python3 deploy/local/seed-test-users.py --reset      # new passwords for existing accounts

Accounts (written to deploy/local/.test-users, git-ignored, mode 600):
  tester@skillsieve.local  role USER  full access to its own board
  demo@skillsieve.local    role DEMO  read-only (writes return 403)

Local only: it writes to the docker-compose Postgres and calls http://localhost:8089.
Sample jobs come from services/skills-svc/eval/posts (fictional). Extraction uses the Gemini key
in the cluster; the demo account reuses the tester's cached extractions, so it costs no quota.
Standard library only.
"""

from __future__ import annotations

import http.cookiejar
import json
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "deploy/local/docker-compose.yml"
CREDS = ROOT / "deploy/local/.test-users"
POSTS = ROOT / "services/skills-svc/eval/posts"
BASE = "http://localhost:8089"
SECONDS_BETWEEN_POSTS = 16  # stays under skills-svc's 4 LLM calls/minute

ACCOUNTS = [("tester@skillsieve.local", "USER"), ("demo@skillsieve.local", "DEMO")]
# (post file, status to move it to); a mix so every board column and the insights have data.
JOBS = [
    ("01_backend_java", "INTERVIEW"),
    ("03_platform_k8s", "APPLIED"),
    ("04_data_engineer", "SAVED"),
    ("06_ml_engineer", "APPLIED"),
    ("11_lead_microservices", "REJECTED"),
    ("19_llm_product", "OFFER"),
]
# My-skills levels (0 None, 1 Aware, 2 Working, 3 Strong) so some gaps show and some don't.
LEVELS = {"java": 3, "spring-boot": 3, "postgresql": 2, "docker": 2, "kubernetes": 1,
          "python": 2, "aws": 1, "kafka": 0, "terraform": 0}


def psql(sql: str) -> str:
    out = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE), "exec", "-T", "postgres",
         "psql", "-U", "skillsieve_api", "-d", "skillsieve", "-v", "ON_ERROR_STOP=1", "-tAc", sql],
        check=True, capture_output=True, text=True,
    )
    return out.stdout.strip()


def bcrypt(password: str) -> str:
    """BCrypt cost 12 via htpasswd in a throwaway container (same as k3d-up.sh)."""
    out = subprocess.run(
        ["docker", "run", "--rm", "httpd:2.4-alpine", "htpasswd", "-bnBC", "12", "", password],
        check=True, capture_output=True, text=True,
    )
    return out.stdout.strip().lstrip(":")


def sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


class Api:
    def __init__(self) -> None:
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )

    def call(self, method: str, path: str, body: object | None = None) -> tuple[int, object]:
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(BASE + path, data=data, method=method)
        req.add_header("X-Requested-With", "seed-script")
        if data is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with self.opener.open(req, timeout=30) as res:
                raw = res.read()
                return res.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read()
            return e.code, (json.loads(raw) if raw else None)


def ensure_account(email: str, role: str, reset: bool) -> str | None:
    """Create the account if missing. Returns the new password, or None if it already existed."""
    exists = psql(f"SELECT count(*) FROM skillsieve.app_user WHERE email = {sql_str(email)}") == "1"
    if exists and not reset:
        return None
    password = secrets.token_urlsafe(14)
    hashed = bcrypt(password)
    if exists:
        psql(f"UPDATE skillsieve.app_user SET password_hash = {sql_str(hashed)} "
             f"WHERE email = {sql_str(email)}")
    else:
        # Created as USER so sample jobs can be added through the API; DEMO is applied at the end.
        psql("INSERT INTO skillsieve.app_user (email, password_hash, role) "
             f"VALUES ({sql_str(email)}, {sql_str(hashed)}, 'USER')")
    return password


def seed_jobs(email: str, password: str, delay: bool) -> None:
    api = Api()
    status, _ = api.call("POST", "/api/auth/login", {"email": email, "password": password})
    if status != 204:
        sys.exit(f"login as {email} failed ({status}); is the cluster up? run deploy/local/k3d-up.sh")
    for i, (name, target) in enumerate(JOBS):
        text = (POSTS / f"{name}.txt").read_text(encoding="utf-8")
        if delay and i:
            time.sleep(SECONDS_BETWEEN_POSTS)
        status, job = api.call("POST", "/api/jobs", {"rawText": text})
        if status == 409:
            print(f"  {name}: already on the board")
            continue
        if status != 201 or not isinstance(job, dict):
            print(f"  {name}: save failed ({status})")
            continue
        app = job["application"]
        if target != "SAVED":
            api.call("PATCH", f"/api/applications/{app['id']}",
                     {"status": target, "version": app["version"]})
        print(f"  {name}: {job['extractionStatus']:<7} {len(job['skills']):>2} skills -> {target}")
    for slug, level in LEVELS.items():
        api.call("PUT", f"/api/me/skills/{slug}", {"level": level})


def main() -> None:
    reset = "--reset" in sys.argv
    with_jobs = "--no-jobs" not in sys.argv
    try:
        psql("SELECT 1")
    except (subprocess.CalledProcessError, FileNotFoundError):
        sys.exit("local Postgres is not running; run deploy/local/k3d-up.sh first")

    created: dict[str, str] = {}
    for email, role in ACCOUNTS:
        password = ensure_account(email, role, reset)
        if password is None:
            print(f"{email}: already exists (use --reset for a new password)")
            continue
        created[email] = password
        print(f"{email}: {'password reset' if reset else 'created'}")
        if with_jobs and not reset:
            seed_jobs(email, password, delay=(email == ACCOUNTS[0][0]))

    for email, role in ACCOUNTS:  # apply final roles (DEMO is read-only)
        psql(f"UPDATE skillsieve.app_user SET role = {sql_str(role)} WHERE email = {sql_str(email)}")

    if created:
        lines = CREDS.read_text().splitlines() if CREDS.exists() else []
        kept = [ln for ln in lines if ln.split(" ", 1)[0] not in created]
        kept += [f"{email} {pw}" for email, pw in created.items()]
        CREDS.write_text("\n".join(kept) + "\n")
        CREDS.chmod(0o600)
    print(f"\nLogins are in {CREDS.relative_to(ROOT)} (git-ignored). Open {BASE}")


if __name__ == "__main__":
    main()
