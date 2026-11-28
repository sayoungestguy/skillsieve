import { defineConfig, devices } from '@playwright/test';
import { STATE } from './e2e/support/account';

/**
 * End-to-end tests and the demo recording, against a running stack (default: the k3d cluster
 * from deploy/local/k3d-up.sh). Not part of `npm test`: run `npm run e2e` / `npm run demo`.
 */
export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e/.results',
  globalSetup: './e2e/support/global-setup.ts',
  globalTeardown: './e2e/support/global-teardown.ts',
  fullyParallel: false, // one account, one board: keep steps in order
  workers: 1,
  retries: 0,
  timeout: 90_000, // extraction is async (queue + Gemini)
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { outputFolder: 'e2e/.report', open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8089',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'e2e',
      testMatch: /.*\.e2e\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1280, height: 800 },
        storageState: STATE, // signed in once in global setup
      },
    },
    {
      name: 'demo',
      testMatch: /demo\.record\.ts/,
      timeout: 10 * 60_000,
      use: {
        ...devices['Desktop Chrome'],
        // Real Chrome: the video looks like what viewers see in their browser (DEMO_CHANNEL to change).
        channel: process.env.DEMO_CHANNEL ?? 'chrome',
        viewport: { width: 1440, height: 900 },
        video: { mode: 'on', size: { width: 1440, height: 900 } },
        launchOptions: { slowMo: 60 },
      },
    },
  ],
});
