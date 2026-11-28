import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { db, MOCK_USER, MOCK_WRONG_PASSWORD } from '@/mocks/db';
import { server } from '@/mocks/server';
import { renderApp } from '@/test/renderApp';
import { safeNext } from './LoginPage';

describe('Login', () => {
  it('sends a visitor without a session to /login, then back after signing in', async () => {
    db.session = null;
    const user = userEvent.setup();
    const { location } = renderApp('/insights');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(location.current?.pathname).toBe('/login');
    expect(location.current?.search).toBe('?next=%2Finsights');

    await user.type(screen.getByLabelText('Email'), 'demo@skillsieve.dev');
    await user.type(screen.getByLabelText('Password'), 'right-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Insights' })).toBeInTheDocument();
    expect(location.current?.pathname).toBe('/insights');
  });

  it('shows validation and wrong-password errors', async () => {
    db.session = null;
    const user = userEvent.setup();
    renderApp('/login');

    await user.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Enter the email you sign in with.')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Email'), 'demo@skillsieve.dev');
    await user.type(screen.getByLabelText('Password'), MOCK_WRONG_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is wrong.');
  });

  it('returns to /login when the session expires mid-use', async () => {
    const { location } = renderApp('/');
    expect(await screen.findByRole('heading', { name: 'Board' })).toBeInTheDocument();

    db.session = null;
    server.use(http.get('/api/insights/summary', () => new HttpResponse(null, { status: 401 })));
    const user = userEvent.setup();
    const [insightsLink] = screen.getAllByRole('link', { name: 'Insights' });
    if (!insightsLink) throw new Error('Insights link missing');
    await user.click(insightsLink);

    await waitFor(() => expect(location.current?.pathname).toBe('/login'));
  });

  it('signs out from the sidebar', async () => {
    const user = userEvent.setup();
    const { location } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: 'Sign out' }));

    await waitFor(() => expect(location.current?.pathname).toBe('/login'));
    expect(db.session).toBeNull();
  });

  it('only redirects to paths inside the app', () => {
    expect(safeNext('/jobs/1')).toBe('/jobs/1');
    expect(safeNext('//evil.example')).toBe('/');
    expect(safeNext('https://evil.example')).toBe('/');
    expect(safeNext(null)).toBe('/');
  });
});

describe('Keycloak sign-in (ADR-009)', () => {
  const LOGOUT = 'http://localhost:8089/auth/realms/skillsieve/protocol/openid-connect/logout?x=1';

  afterEach(() => vi.restoreAllMocks());

  it('hides the Keycloak button when Keycloak is off', async () => {
    db.session = null;
    renderApp('/login');
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'Sign in with Keycloak' })).not.toBeInTheDocument(),
    );
  });

  it('offers Keycloak as a full-page link when it is on', async () => {
    db.session = null;
    db.authConfig = { oidcEnabled: true, oidcLogoutUrl: LOGOUT };
    renderApp('/login');
    expect(await screen.findByRole('link', { name: 'Sign in with Keycloak' })).toHaveAttribute(
      'href',
      '/api/auth/oidc/login',
    );
  });

  it.each([
    ['cancelled', 'Keycloak sign-in was cancelled.'],
    ['expired', 'That sign-in took too long or was opened twice. Try again.'],
    ['unverified', /already exists\. Verify your email in Keycloak/],
    ['something-new', 'Keycloak sign-in failed. Try again.'],
  ])('explains ?oidc=%s', async (reason, message) => {
    db.session = null;
    renderApp(`/login?oidc=${reason}`);
    expect(await screen.findByRole('alert')).toHaveTextContent(message);
  });

  it('ends the Keycloak session too when the user signed in through Keycloak', async () => {
    const assign = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, assign });
    db.session = { ...MOCK_USER, viaKeycloak: true };
    db.authConfig = { oidcEnabled: true, oidcLogoutUrl: LOGOUT };
    const user = userEvent.setup();
    renderApp('/settings');

    const [signOut] = await screen.findAllByRole('button', { name: 'Sign out' });
    if (!signOut) throw new Error('no Sign out button');
    await user.click(signOut);

    await waitFor(() => expect(assign).toHaveBeenCalledWith(LOGOUT));
    expect(db.session).toBeNull();
  });

  it('stays in the app on sign-out for password sessions', async () => {
    const assign = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, assign });
    db.authConfig = { oidcEnabled: true, oidcLogoutUrl: LOGOUT };
    const user = userEvent.setup();
    const { location } = renderApp('/settings');

    const [signOut] = await screen.findAllByRole('button', { name: 'Sign out' });
    if (!signOut) throw new Error('no Sign out button');
    await user.click(signOut);

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(location.current?.pathname).toBe('/login');
    expect(assign).not.toHaveBeenCalled();
  });
});
