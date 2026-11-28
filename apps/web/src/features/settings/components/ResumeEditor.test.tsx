import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '@/mocks/db';
import { renderApp } from '@/test/renderApp';

describe('ResumeEditor', () => {
  it('shouldSaveResume', async () => {
    const user = userEvent.setup();
    renderApp('/settings');
    const box = await screen.findByLabelText('Resume text');
    await waitFor(() => expect(box).toBeEnabled());
    const saveBtn = screen.getByRole('button', { name: 'Save resume' });
    expect(saveBtn).toBeDisabled();

    await user.type(box, 'Built Kafka pipelines');
    await user.click(saveBtn);

    expect(await screen.findByText('Resume saved.')).toBeInTheDocument();
    expect(db.resume?.text).toBe('Built Kafka pipelines');
    expect(screen.getByRole('button', { name: 'Save resume' })).toBeDisabled();
  });

  it('shouldLoadAndDeleteExistingResume', async () => {
    const user = userEvent.setup();
    db.resume = { text: 'Old CV', charCount: 6, updatedAt: '2026-11-20T00:00:00Z' };
    renderApp('/settings');

    expect(await screen.findByDisplayValue('Old CV')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Delete resume' }));

    await waitFor(() => expect(db.resume).toBeNull());
    expect(await screen.findByText('Resume deleted.')).toBeInTheDocument();
    expect(screen.getByLabelText('Resume text')).toHaveValue('');
  });

  it('shouldBlockTooLongResume', async () => {
    renderApp('/settings');
    const box = await screen.findByLabelText('Resume text');
    await waitFor(() => expect(box).toBeEnabled());
    // Direct change: typing 20k characters is slow.
    fireEvent.change(box, { target: { value: 'x'.repeat(20_001) } });
    expect(screen.getByRole('button', { name: 'Save resume' })).toBeDisabled();
    expect(screen.getByText(/20,001 \/ 20,000/)).toBeInTheDocument();
  });
});
