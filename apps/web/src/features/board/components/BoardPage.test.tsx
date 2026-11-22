import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '@/mocks/db';
import { renderApp } from '@/test/renderApp';

describe('BoardPage', () => {
  it('shouldShowColumnsWithCountsAndEmptyState', async () => {
    renderApp('/');
    const saved = await screen.findByRole('region', { name: /^Saved/ });
    expect(within(saved).getByText('DBS Bank')).toBeInTheDocument();
    expect(within(saved).getByText('Extracting skills…')).toBeInTheDocument();
    const offer = screen.getByRole('region', { name: /^Offer/ });
    expect(within(offer).getByText('Nothing here yet')).toBeInTheDocument();
    expect(screen.getByText(/follow-ups due/)).toBeInTheDocument();
  });

  it('shouldMoveCardToNextColumnWhenShiftArrowRightPressed', async () => {
    const user = userEvent.setup();
    renderApp('/');
    const handle = await screen.findByRole('button', { name: /Move DBS Bank card/ });
    handle.focus();
    await user.keyboard('{Shift>}{ArrowRight}{/Shift}');

    const applied = screen.getByRole('region', { name: /^Applied/ });
    await waitFor(() => expect(within(applied).getByText('DBS Bank')).toBeInTheDocument());
    await waitFor(() =>
      expect(db.jobs.find((j) => j.id === 'j-dbs')?.application.status).toBe('APPLIED'),
    );
  });
});
