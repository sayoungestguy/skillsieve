import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '@/mocks/db';
import { renderApp } from '@/test/renderApp';

const POST = 'Backend engineer at a Singapore fintech. '.repeat(6);

describe('AddJobDialog', () => {
  it('shouldKeepSaveDisabledUntil200Chars', async () => {
    const user = userEvent.setup();
    renderApp('/jobs/new');
    const save = await screen.findByRole('button', { name: 'Save job' });
    expect(save).toBeDisabled();
    await user.type(screen.getByLabelText('Paste the job description'), 'Too short');
    expect(screen.getByText(/paste 191 more to save/)).toBeInTheDocument();
    expect(save).toBeDisabled();
  });

  it('shouldSaveJobAndShowToastWhenTextIsLongEnough', async () => {
    const user = userEvent.setup();
    const { location } = renderApp('/jobs/new');
    const box = await screen.findByLabelText('Paste the job description');
    await user.click(box);
    await user.paste(POST);
    await user.click(screen.getByRole('button', { name: 'Save job' }));

    expect(await screen.findByText('Saved. Extracting skills…')).toBeInTheDocument();
    expect(location.current?.pathname).toBe('/');
    expect(db.jobs[0]?.rawText).toBe(POST.trim());
  });

  it('shouldShowDuplicateMessageWhenPostAlreadySaved', async () => {
    const user = userEvent.setup();
    const existing = db.jobs.find((j) => j.id === 'j-govtech');
    renderApp('/jobs/new');
    const box = await screen.findByLabelText('Paste the job description');
    await user.click(box);
    await user.paste(existing?.rawText ?? '');
    await user.click(screen.getByRole('button', { name: 'Save job' }));

    expect(await screen.findByText('You already saved this post.')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('link', { name: 'Open it' })).toHaveAttribute(
        'href',
        '/jobs/j-govtech',
      ),
    );
  });
});
