import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useMe } from '../hooks';

/** Gate for the signed-in app: sends visitors without a session to /login?next=… */
export function RequireAuth() {
  const { data: me, isPending, isError } = useMe();
  const location = useLocation();

  if (isPending) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-bg" role="status">
        <span className="spinner" aria-hidden="true" />
        <span className="sr-only">Loading</span>
      </div>
    );
  }
  if (isError) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-bg p-4">
        <p className="panel max-w-sm text-center" role="alert">
          Can&apos;t reach SkillSieve right now. Check your connection and reload the page.
        </p>
      </div>
    );
  }
  if (!me) {
    const next = `${location.pathname}${location.search}`;
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />;
  }
  return <Outlet />;
}
