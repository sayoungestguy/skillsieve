import { execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/** A throwaway account for one run, created straight in the local Postgres (like seed-test-users). */
export interface Account {
  email: string;
  password: string;
}

const HERE = dirname(fileURLToPath(import.meta.url));
export const ROOT = resolve(HERE, '../../../..');
const COMPOSE = resolve(ROOT, 'deploy/local/docker-compose.yml');
const FILE = resolve(HERE, '../.auth/account.json'); // git-ignored
/** Saved session (cookie) for the run's account. */
export const STATE = resolve(HERE, '../.auth/state.json');

function psql(sql: string): string {
  return execFileSync(
    'docker',
    [
      'compose',
      '-f',
      COMPOSE,
      'exec',
      '-T',
      'postgres',
      'psql',
      '-U',
      'skillsieve_api',
      '-d',
      'skillsieve',
      '-v',
      'ON_ERROR_STOP=1',
      '-tAc',
      sql,
    ],
    { encoding: 'utf8' },
  ).trim();
}

const lit = (s: string) => `'${s.replaceAll("'", "''")}'`;

export function createAccount(): Account {
  const run = `${Date.now().toString(36)}${randomBytes(2).toString('hex')}`;
  const email = process.env.E2E_EMAIL ?? `e2e-${run}@skillsieve.local`;
  const password = randomBytes(18).toString('base64url');
  // BCrypt cost 12 via htpasswd in a throwaway container, same as k3d-up.sh.
  const hash = execFileSync(
    'docker',
    ['run', '--rm', 'httpd:2.4-alpine', 'htpasswd', '-bnBC', '12', '', password],
    { encoding: 'utf8' },
  ).replace(/[:\n]/g, '');
  // Never touch an existing account: teardown deletes whatever this file names.
  const inserted = psql(
    `INSERT INTO skillsieve.app_user (email, password_hash, role) VALUES (${lit(email)}, ${lit(hash)}, 'USER') ` +
      `ON CONFLICT (email) DO NOTHING RETURNING id`,
  );
  if (!inserted) {
    throw new Error(
      `${email} already exists; pick another E2E_EMAIL (it is deleted after the run)`,
    );
  }
  const account = { email, password };
  mkdirSync(dirname(FILE), { recursive: true });
  writeFileSync(FILE, JSON.stringify(account), { mode: 0o600 });
  return account;
}

export function readAccount(): Account {
  return JSON.parse(readFileSync(FILE, 'utf8')) as Account;
}

/** Deletes the run's account; jobs, skills, resume and tokens go with it (ON DELETE CASCADE). */
export function deleteAccount(): void {
  if (!existsSync(FILE) || process.env.E2E_KEEP === 'true') return;
  const { email } = readAccount();
  psql(`DELETE FROM skillsieve.app_user WHERE email = ${lit(email)}`); // created by this run only
  rmSync(FILE);
  rmSync(STATE, { force: true });
}
