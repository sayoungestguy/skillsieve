import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, type Page } from '@playwright/test';
import { readAccount, ROOT } from './account';

/** Fictional job posts from the extraction eval set. */
export function post(name: string): string {
  return readFileSync(resolve(ROOT, 'services/skills-svc/eval/posts', `${name}.txt`), 'utf8');
}

/** Opens the board with the saved session. */
export async function openBoard(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Board' })).toBeVisible();
}

/** Signs in through the form (counts against the 5-per-minute limit: use sparingly). */
export async function signIn(page: Page): Promise<void> {
  const { email, password } = readAccount();
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Board' })).toBeVisible();
}

/** Opens Add job, pastes the post and saves. */
export async function addJob(page: Page, text: string): Promise<void> {
  await page.getByRole('link', { name: 'Add job' }).first().click();
  await page.getByLabel('Paste the job description').fill(text);
  await page.getByRole('button', { name: 'Save job' }).click();
}
