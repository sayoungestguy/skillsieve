import { screen } from '@testing-library/react';
import { renderApp } from '@/test/renderApp';

describe('SearchPage', () => {
  it('shouldHighlightMatchesFromQueryString', async () => {
    renderApp('/search?q=kafka');
    expect(await screen.findByText(/results for “kafka”/)).toBeInTheDocument();
    const marks = await screen.findAllByText(/kafka/i, { selector: 'mark' });
    expect(marks.length).toBeGreaterThan(0);
  });

  it('shouldShowEmptyStateWhenNothingMatches', async () => {
    renderApp('/search?q=cobol');
    expect(await screen.findByText('No posts match “cobol”')).toBeInTheDocument();
  });
});
