import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '@/mocks/db';
import { renderApp } from '@/test/renderApp';

describe('JobDetailPage', () => {
  it('shouldShowSkillsWithLevelsAndGaps', async () => {
    renderApp('/jobs/j-govtech');
    expect(
      await screen.findByRole('heading', { name: 'Full-stack Developer' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Required · 7')).toBeInTheDocument();
    expect(
      screen.getAllByText(', required, my level Strong', { exact: false }).length,
    ).toBeGreaterThan(0);
    expect(screen.getByText(/gaps for this job/)).toBeInTheDocument();
  });

  it('shouldChangeStatusAndRecordHistory', async () => {
    const user = userEvent.setup();
    renderApp('/jobs/j-govtech');
    await user.selectOptions(await screen.findByLabelText('Status'), 'INTERVIEW');
    await waitFor(() =>
      expect(db.jobs.find((j) => j.id === 'j-govtech')?.application.status).toBe('INTERVIEW'),
    );
    const history = screen.getByRole('region', { name: 'Status history' });
    expect(await within(history).findByText('Interview')).toBeInTheDocument();
  });

  it('shouldShowExtractingStateForPendingJob', async () => {
    renderApp('/jobs/j-shopee');
    expect(await screen.findByText('Extracting skills…')).toBeInTheDocument();
    expect(screen.queryByText('Required · 7')).not.toBeInTheDocument();
  });

  it('shouldOfferRetryWhenExtractionIsPendingWithError', async () => {
    const job = db.jobs.find((j) => j.id === 'j-shopee');
    if (!job) throw new Error('fixture job missing');
    job.extractionError = 'Skill extraction is busy or offline. Try again later.';
    renderApp('/jobs/j-shopee');

    expect(await screen.findByRole('alert')).toHaveTextContent('Skills pending');
    expect(screen.queryByText('Extracting skills…')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled();
  });
});
