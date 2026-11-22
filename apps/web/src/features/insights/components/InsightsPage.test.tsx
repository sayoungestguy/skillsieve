import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderApp } from '@/test/renderApp';

describe('InsightsPage', () => {
  it('shouldShowBarsWithGapBadgesAndPostsForSelectedSkill', async () => {
    const user = userEvent.setup();
    renderApp('/insights');
    const docker = await screen.findByRole('button', { name: /^Docker: .*Gap\. Show posts\./ });
    expect(within(docker).getByText('GAP')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /^React: .*My level Strong\. Show posts\./ }),
    ).not.toHaveTextContent('GAP');

    await user.click(screen.getByRole('button', { name: /^Kubernetes:/ }));
    expect(
      await screen.findByRole('heading', { name: 'Posts with Kubernetes' }),
    ).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: /NCS · Cloud Engineer/ })).toBeInTheDocument();
  });
});
