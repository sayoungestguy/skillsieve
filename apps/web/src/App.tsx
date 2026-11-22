import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider, type RouteObject } from 'react-router-dom';
import { AppLayout } from '@/components/layout/AppLayout';
import { ToastProvider } from '@/components/ui/Toast';
import { BoardPage } from '@/features/board/components/BoardPage';
import { InsightsPage } from '@/features/insights/components/InsightsPage';
import { AddJobDialog } from '@/features/jobs/components/AddJobDialog';
import { JobDetailPage } from '@/features/jobs/components/JobDetailPage';
import { SearchPage } from '@/features/search/components/SearchPage';
import { SettingsPage } from '@/features/settings/components/SettingsPage';
import { MySkillsPage } from '@/features/skills/components/MySkillsPage';

export const routes: RouteObject[] = [
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
];

export function createQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: 1 } } });
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
