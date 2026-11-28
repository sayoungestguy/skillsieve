import { request, type FullConfig } from '@playwright/test';
import { createAccount, STATE } from './account';

/**
 * Creates the run's account and signs in once, saving the session cookie. Tests reuse it, because
 * core-api allows only 5 sign-ins per minute per IP (TECHNICAL_DESIGN §10).
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const { email, password } = createAccount();
  const baseURL = config.projects[0]?.use.baseURL ?? 'http://localhost:8089';
  const api = await request.newContext({ baseURL });
  const res = await api.post('/api/auth/login', {
    data: { email, password },
    headers: { 'X-Requested-With': 'XMLHttpRequest' },
  });
  if (res.status() !== 204) {
    throw new Error(`e2e sign-in failed: ${res.status()} ${await res.text()}`);
  }
  await api.storageState({ path: STATE });
  await api.dispose();
  console.log(`e2e account: ${email}`);
}
