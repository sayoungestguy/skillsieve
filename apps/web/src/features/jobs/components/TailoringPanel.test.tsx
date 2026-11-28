import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '@/mocks/db';
import { renderApp } from '@/test/renderApp';

const CV = 'Built REST APIs in Java for bookings\n- Wrote Python scripts';

describe('TailoringPanel', () => {
  it('shouldAskForResumeFirst', async () => {
    const user = userEvent.setup();
    renderApp('/jobs/j-govtech');
    await user.click(await screen.findByRole('button', { name: 'Suggest resume changes' }));

    expect(await screen.findByText(/Save your resume in Settings first/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open Settings' })).toHaveAttribute(
      'href',
      '/settings',
    );
  });

  it('shouldShowSuggestionsAfterBackgroundRun', async () => {
    const user = userEvent.setup();
    db.resume = { text: CV, charCount: CV.length, updatedAt: '2026-11-20T00:00:00Z' };
    renderApp('/jobs/j-govtech');
    await user.click(await screen.findByRole('button', { name: 'Suggest resume changes' }));

    expect(await screen.findByText(/Writing suggestions/)).toBeInTheDocument();
    const list = await screen.findByRole('list', { name: 'Suggested bullets' }, { timeout: 8000 });
    expect(within(list).getByText(/Designed and shipped REST APIs/)).toBeInTheDocument();
    expect(within(list).getByText('Built REST APIs in Java for bookings')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Suggest again' })).toBeInTheDocument();
  }, 10_000);

  it('shouldShowStaleNoticeAndError', async () => {
    db.tailoring['j-govtech'] = {
      status: 'DONE',
      stale: true,
      model: 'm',
      suggestions: [{ original: null, suggested: 'New line', skills: [], reason: '' }],
      error: null,
      requestedAt: '2026-11-20T00:00:00Z',
      finishedAt: '2026-11-20T00:01:00Z',
    };
    renderApp('/jobs/j-govtech');
    expect(await screen.findByText(/changed since these were made/)).toBeInTheDocument();

    db.tailoring['j-govtech'] = {
      ...db.tailoring['j-govtech'],
      status: 'FAILED',
      stale: false,
      suggestions: [],
      error: 'The suggestion model is busy or off.',
    };
    renderApp('/jobs/j-govtech');
    await waitFor(() =>
      expect(screen.getAllByText('The suggestion model is busy or off.').length).toBeGreaterThan(0),
    );
  });

  it('shouldCopySuggestion', async () => {
    const user = userEvent.setup();
    db.tailoring['j-govtech'] = {
      status: 'DONE',
      stale: false,
      model: 'm',
      suggestions: [
        { original: null, suggested: 'Led a team of three', skills: ['Java'], reason: 'r' },
      ],
      error: null,
      requestedAt: '2026-11-20T00:00:00Z',
      finishedAt: '2026-11-20T00:01:00Z',
    };
    renderApp('/jobs/j-govtech');
    await user.click(await screen.findByRole('button', { name: 'Copy: Led a team of three' }));

    expect(await navigator.clipboard.readText()).toBe('Led a team of three');
    expect(screen.getByText('Copied')).toBeInTheDocument();
  });
});
