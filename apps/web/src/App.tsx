import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider, type RouteObject } from 'react-router-dom';
import { ApiError } from '@/api/client';
import { AppLayout } from '@/components/layout/AppLayout';
import { ToastProvider } from '@/components/ui/Toast';
import { LoginPage } from '@/features/auth/components/LoginPage';
import { RequireAuth } from '@/features/auth/components/RequireAuth';
import { meKey } from '@/features/auth/hooks';
import { BoardPage } from '@/features/board/components/BoardPage';
import { InsightsPage } from '@/features/insights/components/InsightsPage';
import { AddJobDialog } from '@/features/jobs/components/AddJobDialog';
import { JobDetailPage } from '@/features/jobs/components/JobDetailPage';
import { SearchPage } from '@/features/search/components/SearchPage';
import { SettingsPage } from '@/features/settings/components/SettingsPage';
import { MySkillsPage } from '@/features/skills/components/MySkillsPage';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: (
          <ToastProvider>
            <AppLayout />
          </ToastProvider>
        ),
        children: [
          {
            path: '/',
            element: <BoardPage />,
            children: [{ path: 'jobs/new', element: <AddJobDialog /> }],
          },
          { path: '/jobs/:id', element: <JobDetailPage /> },
          { path: '/insights', element: <InsightsPage /> },
          { path: '/skills', element: <MySkillsPage /> },
          { path: '/search', element: <SearchPage /> },
          { path: '/settings', element: <SettingsPage /> },
        ],
      },
    ],
  },
];

const isUnauthorized = (e: unknown) => e instanceof ApiError && e.status === 401;

/**
 * A 401 from any call means the session ended (expired cookie, logout in another tab): drop the
 * cached user so RequireAuth sends the visitor to /login.
 */
export function createQueryClient(defaults: { retry: boolean | number } = { retry: 1 }) {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({
      onError: (e, query) => {
        if (isUnauthorized(e) && query.queryKey[0] !== meKey[0]) client.setQueryData(meKey, null);
      },
    }),
    mutationCache: new MutationCache({
      onError: (e) => {
        if (isUnauthorized(e)) client.setQueryData(meKey, null);
      },
    }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: (count, e) => !isUnauthorized(e) && count < Number(defaults.retry),
      },
      mutations: { retry: false },
    },
  });
  return client;
}

const router = createBrowserRouter(routes);
const queryClient = createQueryClient();

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
