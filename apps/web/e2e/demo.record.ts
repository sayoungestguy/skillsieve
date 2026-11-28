import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test } from '@playwright/test';
import { readAccount, ROOT } from './support/account';
import { post } from './support/app';
import { caption, installOverlay, point } from './support/overlay';

/**
 * Release 1 demo video (docs/DEMO.md), recorded by Playwright: `npm run demo` with
 * E2E_EMAIL=alex.demo@skillsieve.local for a friendly name. The video lands in e2e/.results.
 */
test.use({ storageState: { cookies: [], origins: [] } });

const pause = (ms = 1200) => new Promise((r) => setTimeout(r, ms));

function grafanaLogin(): { user: string; password: string } | null {
  try {
    const creds = readFileSync(resolve(ROOT, 'deploy/local/.owner-credentials'), 'utf8');
    const get = (k: string) => creds.match(new RegExp(`^${k}=(.*)$`, 'm'))?.[1];
    const user = get('grafana_user');
    const password = get('grafana_password');
    return user && password ? { user, password } : null;
  } catch {
    return null;
  }
}

test('SkillSieve demo', async ({ page }) => {
  await installOverlay(page);
  const { email, password } = readAccount();

  // 1. Intro + sign in
  await page.goto('/login');
  await caption(
    page,
    'SkillSieve: track job applications and see which skills you are missing.',
    3500,
  );
  await caption(
    page,
    'Owner login: core-api issues a JWT in an HttpOnly, SameSite=Strict cookie.',
    1500,
  );
  await point(page, page.getByLabel('Email'));
  await page.getByLabel('Email').pressSequentially(email, { delay: 35 });
  await page.getByLabel('Password').pressSequentially(password.slice(0, 12), { delay: 30 });
  await page.getByLabel('Password').fill(password);
  await point(page, page.getByRole('button', { name: 'Sign in' }));
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Board' })).toBeVisible();
  await caption(page, 'A fresh board: Saved → Applied → Interview → Offer.', 2500);

  // 2. Add a job post
  await caption(page, 'Paste a job post. Here: a fictional Platform Engineer role.', 1500);
  await point(page, page.getByRole('link', { name: 'Add job' }).first());
  await page.getByRole('link', { name: 'Add job' }).first().click();
  const box = page.getByLabel('Paste the job description');
  await point(page, box);
  await box.fill(post('03_platform_k8s'));
  await page.waitForTimeout(1500);
  await caption(
    page,
    'core-api saves it and hashes the text. skills-svc asks Gemini for structured JSON, with contact details stripped first.',
    3500,
  );
  await point(page, page.getByRole('button', { name: 'Save job' }));
  await page.getByRole('button', { name: 'Save job' }).click();
  const saved = page.getByRole('region', { name: /^Saved/ });
  await expect(saved.getByRole('link')).toHaveCount(1);
  await caption(
    page,
    'Extraction runs in the background (RabbitMQ). The card fills in when it is done.',
    3000,
  );
  await point(page, saved.getByRole('link').first());
  await saved.getByRole('link').first().click();
  await expect(page.getByText(/\d+ skills found/)).toBeVisible({ timeout: 75_000 });
  await caption(
    page,
    'Required and preferred skills, mapped to a canonical taxonomy. Gaps are skills I rate Aware or lower.',
    4000,
  );
  await page.mouse.wheel(0, 500);
  await pause(2500);
  await page.mouse.wheel(0, -500);

  // 3. Duplicate detection
  await caption(page, 'Paste the same post again, reformatted…', 1500);
  await page.getByRole('link', { name: 'Add job' }).first().click();
  await page
    .getByLabel('Paste the job description')
    .fill(`\n\n${post('03_platform_k8s').replaceAll('\n', '\n\n')}`);
  await page.getByRole('button', { name: 'Save job' }).click();
  await expect(page.getByText('You already saved this post.')).toBeVisible();
  await caption(
    page,
    'Same normalised text → same hash: caught as a duplicate, and no second LLM call.',
    3500,
  );
  await page.getByRole('link', { name: 'Open it' }).click();

  // 4. Status + history
  await caption(
    page,
    'Move it to Applied. Updates use optimistic locking, and every change is logged.',
    2000,
  );
  const status = page.getByLabel('Status', { exact: true });
  await point(page, status);
  await status.selectOption('APPLIED');
  await expect(
    page.getByRole('region', { name: 'Status history' }).getByText('Applied'),
  ).toBeVisible();
  await point(page, page.getByRole('region', { name: 'Status history' }));
  await pause(2500);

  // More posts so the insights mean something (cached extractions cost no quota).
  await caption(page, 'Two more posts…', 800);
  for (const name of ['01_backend_java', '04_data_engineer']) {
    await page.getByRole('link', { name: 'Add job' }).first().click();
    await page.getByLabel('Paste the job description').fill(post(name));
    await page.getByRole('button', { name: 'Save job' }).click();
    await expect(page.getByRole('heading', { name: 'Board' })).toBeVisible();
  }
  await expect(page.getByText('3 jobs')).toBeVisible();
  await pause(1500);
  // Wait until all three are extracted.
  await expect
    .poll(
      async () =>
        (await page.request.get('/api/jobs?size=20').then((r) => r.json())).content.filter(
          (j: { extractionStatus: string }) => j.extractionStatus === 'DONE',
        ).length,
      { timeout: 90_000 },
    )
    .toBe(3);
  await page.reload();
  await caption(page, 'The board, keyboard and drag-and-drop friendly.', 2500);

  // 5. Insights + rate skills
  await point(page, page.getByRole('link', { name: 'Insights' }).first());
  await page.getByRole('link', { name: 'Insights' }).first().click();
  await expect(page.getByRole('heading', { name: 'Insights' })).toBeVisible();
  await caption(
    page,
    'Insights: Elasticsearch aggregations show which skills appear across my posts, and where I am weak.',
    4500,
  );
  const k8s = page.getByRole('button', { name: /^Kubernetes:/ });
  await point(page, k8s);
  await k8s.click();
  await caption(page, 'Click a skill to see the posts that ask for it.', 2500);
  await point(page, page.getByRole('link', { name: 'My skills' }).first());
  await page.getByRole('link', { name: 'My skills' }).first().click();
  await caption(page, 'Rate my own level: None, Aware, Working, Strong.', 1500);
  await page.getByLabel('Find a skill').pressSequentially('Kubern', { delay: 60 });
  const level = page.getByRole('group', { name: 'Your level in Kubernetes' });
  await point(page, level.getByText('Working', { exact: true }));
  await level.getByText('Working', { exact: true }).click();
  await pause(1200);
  await page.getByRole('link', { name: 'Insights' }).first().click();
  await expect(page.getByRole('button', { name: /^Kubernetes:/ })).toHaveAccessibleName(
    /My level Working/,
  );
  await caption(page, 'Kubernetes is no longer a gap.', 2500);

  // 6. Search
  await caption(
    page,
    'Full-text search with highlighting. If Elasticsearch is down, it falls back to Postgres.',
    1500,
  );
  const search = page.getByPlaceholder('Search jobs, companies, skills');
  await point(page, search);
  await search.pressSequentially('kubernetes', { delay: 70 });
  await search.press('Enter');
  await expect(page.locator('mark').first()).toBeVisible();
  await pause(3500);

  // 7. Observability (Release 2 extra), if Grafana is running
  const grafana = grafanaLogin();
  if (grafana && (await page.request.get('/grafana/api/health')).ok()) {
    await caption(page, 'Release 2 extra: Prometheus metrics and Loki logs in Grafana.', 1500);
    await page.goto('/grafana/login');
    await page.locator('input[name="user"]').fill(grafana.user);
    await page.locator('input[name="password"]').fill(grafana.password);
    await page.locator('button[type="submit"]').click();
    // Let Grafana finish its own post-login redirect before navigating, or the goto is aborted.
    await page.waitForURL((url) => !url.pathname.endsWith('/login'));
    await page.waitForLoadState('domcontentloaded');
    await page.goto('/grafana/d/skillsieve-overview?orgId=1&from=now-30m&to=now&refresh=');
    await page.getByText('Jobs saved (24h)').first().waitFor({ timeout: 30_000 });
    await page.waitForTimeout(2500); // let the panels draw
    await caption(page, 'Latency, errors, LLM quota and cache hit ratio.', 4500);
    await page.goto(
      '/grafana/d/skillsieve-logs?orgId=1&from=now-30m&to=now&refresh=&var-component=core-api&var-component=skills-svc',
    );
    await page.getByText('Log lines by level').first().waitFor({ timeout: 30_000 });
    await page.waitForTimeout(2500);
    await caption(page, 'Logs from every pod, searchable.', 4000);
  }

  // 8. Outro
  await page.goto('/');
  await caption(
    page,
    'k3s · Helm · GitHub Actions · Trivy · Testcontainers · Playwright. Running at $0.',
    4000,
  );
  await caption(page, '', 500);
});
