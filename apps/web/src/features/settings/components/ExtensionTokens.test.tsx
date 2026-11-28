import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '@/mocks/db';
import { renderApp } from '@/test/renderApp';

describe('ExtensionTokens', () => {
  it('shouldShowNewTokenOnceAndListIt', async () => {
    const user = userEvent.setup();
    renderApp('/settings');
    expect(await screen.findByText('No tokens yet.')).toBeInTheDocument();

    const name = screen.getByLabelText('Token name');
    await user.clear(name);
    await user.type(name, 'Work laptop');
    await user.click(screen.getByRole('button', { name: 'Create token' }));

    const token = await screen.findByLabelText('New token');
    expect(token.textContent).toMatch(/^ssv_/);
    const list = await screen.findByRole('list', { name: 'Your tokens' });
    expect(within(list).getByText('Work laptop')).toBeInTheDocument();
    expect(within(list).queryByText(token.textContent ?? '')).not.toBeInTheDocument();
  });

  it('shouldRevokeToken', async () => {
    const user = userEvent.setup();
    db.tokens = [
      {
        id: 't1',
        name: 'Old Chrome',
        prefix: 'ssv_abcdefgh',
        createdAt: '2026-11-01T00:00:00Z',
        lastUsedAt: '2026-11-20T00:00:00Z',
      },
    ];
    renderApp('/settings');
    await user.click(await screen.findByRole('button', { name: 'Revoke Old Chrome' }));
    await waitFor(() => expect(db.tokens).toHaveLength(0));
    expect(await screen.findByText('No tokens yet.')).toBeInTheDocument();
  });
});
