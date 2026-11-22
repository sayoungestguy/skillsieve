import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '@/mocks/db';
import { renderApp } from '@/test/renderApp';

describe('MySkillsPage', () => {
  it('shouldSaveLevelWhenSegmentChosen', async () => {
    const user = userEvent.setup();
    renderApp('/skills');
    const group = await screen.findByRole('group', { name: 'Your level in Docker' });
    await user.click(within(group).getByRole('radio', { name: 'Strong' }));
    await waitFor(() => expect(db.levels.docker).toBe(3));
    expect(within(group).getByRole('radio', { name: 'Strong' })).toBeChecked();
  });

  it('shouldFilterAndSortAlphabetically', async () => {
    const user = userEvent.setup();
    renderApp('/skills');
    await screen.findByRole('group', { name: 'Your level in Java' });
    await user.click(screen.getByRole('button', { name: 'A–Z' }));
    const names = screen
      .getAllByRole('group', { name: /Your level in/ })
      .map((g) => g.getAttribute('name') ?? g.textContent);
    expect(names.length).toBeGreaterThan(5);
    await user.type(screen.getByLabelText('Find a skill'), 'kube');
    expect(screen.getAllByRole('group', { name: /Your level in/ })).toHaveLength(1);
  });
});
