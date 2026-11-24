import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { db, MOCK_WRONG_PASSWORD } from '@/mocks/db';
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
