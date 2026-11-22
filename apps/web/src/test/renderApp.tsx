import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { MemoryRouter, useLocation, useRoutes, type Location } from 'react-router-dom';
import { routes } from '@/App';

/**
 * Renders the real routes against the MSW-mocked API (integration-style).
 * Uses MemoryRouter instead of a data router: jsdom's AbortSignal breaks data-router navigation.
 */
export function renderApp(path = '/') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const location = { current: null as Location | null };
  function Routes() {
    location.current = useLocation();
    return useRoutes(routes);
  }
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { location, client };
}
