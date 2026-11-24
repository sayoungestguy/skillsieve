import { useNavigate } from 'react-router-dom';
import { Icon } from '@/components/ui/Icon';
import { useLogout, useMe } from '../hooks';

interface SignOutButtonProps {
  showEmail?: boolean;
}

export function SignOutButton({ showEmail = false }: SignOutButtonProps) {
  const { data: me } = useMe();
  const logout = useLogout();
  const navigate = useNavigate();
  if (!me) return null;

  const signOut = () =>
    logout.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) });

  return (
    <div className="flex flex-col gap-2">
      {showEmail && (
        <p className="truncate px-3 text-sm text-muted" title={me.email}>
          {me.email}
          {me.role === 'DEMO' && ' · demo'}
        </p>
      )}
      <button
        type="button"
        className="nav-link w-full border-0 bg-transparent text-left"
        onClick={signOut}
        disabled={logout.isPending}
      >
        <Icon name="logout" />
        <span>Sign out</span>
      </button>
    </div>
  );
}
