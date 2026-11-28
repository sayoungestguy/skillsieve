import { expect, test } from '@playwright/test';
import { addJob, openBoard, post, signIn } from './support/app';

/**
 * Release 1 user journey against the real stack (web → core-api → skills-svc/Gemini → Postgres +
 * Elasticsearch). One fresh account per run (global setup), steps in order.
 */
test.describe.configure({ mode: 'serial' });

const K8S = post('03_platform_k8s');
const JAVA = post('01_backend_java');

test.describe('signed out', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('redirects to sign in, then shows an empty board', async ({ page }) => {
    await page.goto('/insights');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();

    await signIn(page);
    await expect(page.getByText('0 jobs')).toBeVisible();
  });
});

test('saves a post and extracts its skills', async ({ page }) => {
  await openBoard(page);
  await addJob(page, K8S);

  const saved = page.getByRole('region', { name: /^Saved/ });
  await expect(saved.getByRole('link')).toHaveCount(1);
  await saved.getByRole('link').first().click();

  // Async extraction: queue → skills-svc → Gemini (or the cache) → result event.
  await expect(page.getByText(/\d+ skills found/)).toBeVisible({ timeout: 75_000 });
  await expect(page.getByText(/^Required · \d+/)).toBeVisible();
  await expect(page.getByRole('heading', { level: 1 })).not.toHaveText('');
});

test('catches the same post pasted again', async ({ page }) => {
  await openBoard(page);
  // Reformatted copy: same normalised text, so same hash.
  await addJob(page, `\n\n${K8S.replaceAll('\n', '\n\n')}  `);

  await expect(page.getByText('You already saved this post.')).toBeVisible();
  await page.getByRole('link', { name: 'Open it' }).click();
  await expect(page).toHaveURL(/\/jobs\//);
});

test('moves the job to Applied and records history', async ({ page }) => {
  await openBoard(page);
  await page
    .getByRole('region', { name: /^Saved/ })
    .getByRole('link')
    .first()
    .click();

  await page.getByLabel('Status', { exact: true }).selectOption('APPLIED');
  const history = page.getByRole('region', { name: 'Status history' });
  await expect(history.getByText('Applied')).toBeVisible();

  await page.goto('/');
  await expect(page.getByRole('region', { name: /^Applied/ }).getByRole('link')).toHaveCount(1);
  await expect(page.getByRole('region', { name: /^Saved/ }).getByRole('link')).toHaveCount(0);
});

test('shows skill gaps and updates them when I rate a skill', async ({ page }) => {
  await openBoard(page);
  await addJob(page, JAVA);
  await page
    .getByRole('region', { name: /^Saved/ })
    .getByRole('link')
    .first()
    .click();
  await expect(page.getByText(/\d+ skills found/)).toBeVisible({ timeout: 75_000 });

  await page.goto('/insights');
  await expect(page.getByRole('heading', { name: 'Insights' })).toBeVisible();
  const k8s = page.getByRole('button', { name: /^Kubernetes:/ });
  await expect(k8s).toBeVisible();
  await expect(k8s).toHaveAccessibleName(/Gap/);

  await page.goto('/skills');
  await page.getByLabel('Find a skill').fill('Kubernetes');
  const level = page.getByRole('group', { name: 'Your level in Kubernetes' });
  await level.getByText('Strong', { exact: true }).click(); // the radio itself is visually hidden
  await expect(level.getByRole('radio', { name: 'Strong' })).toBeChecked();

  await page.goto('/insights');
  await expect(page.getByRole('button', { name: /^Kubernetes:/ })).toHaveAccessibleName(
    /My level Strong/,
  );
});

test('finds posts with full-text search', async ({ page }) => {
  await openBoard(page);
  await page.goto('/search?q=kubernetes');
  await expect(page.getByText(/results? for “kubernetes”/)).toBeVisible();
  await expect(page.locator('mark').first()).toBeVisible();

  await page.goto('/search?q=cobol');
  await expect(page.getByText('No posts match “cobol”')).toBeVisible();
});

test('saves a resume privately in Settings', async ({ page }) => {
  await openBoard(page);
  await page.goto('/settings');
  const box = page.getByLabel('Resume text');
  await expect(box).toBeEnabled();
  await box.fill(
    'E2E Tester\n- Built REST APIs in Java and Spring Boot\n- Ran services on Kubernetes',
  );
  await page.getByRole('button', { name: 'Save resume' }).click();
  await expect(page.getByText('Resume saved.')).toBeVisible();

  await page.reload();
  await expect(page.getByLabel('Resume text')).toHaveValue(/Built REST APIs/);
});

test('signs out and protects pages again', async ({ browser }) => {
  // Own sign-in: signing out must not end the shared session the other tests use.
  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const page = await context.newPage();
  await signIn(page);
  await page.getByRole('button', { name: 'Sign out' }).first().click();
  await expect(page).toHaveURL(/\/login/);

  await page.goto('/');
  await expect(page).toHaveURL(/\/login/);
  await context.close();
});
