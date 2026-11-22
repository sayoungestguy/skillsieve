import { NavLink } from 'react-router-dom';
import { Icon, type IconName } from '@/components/ui/Icon';

const TABS: { to: string; label: string; icon: IconName }[] = [
  { to: '/', label: 'Board', icon: 'board' },
  { to: '/insights', label: 'Insights', icon: 'insights' },
  { to: '/jobs/new', label: 'Add', icon: 'plus' },
  { to: '/skills', label: 'Skills', icon: 'skills' },
  { to: '/settings', label: 'More', icon: 'settings' },
];

/** Bottom tab bar below 1024 px (sidebar takes over at lg); "Add" is the centre button (DESIGN.md §2). */
export function MobileTabBar() {
  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-40 flex items-stretch border-t border-line bg-surface px-2 pb-5 pt-1 lg:hidden"
    >
      {TABS.map((t) =>
        t.icon === 'plus' ? (
          <NavLink
            key={t.to}
            to={t.to}
            className="flex flex-1 items-center justify-center no-underline"
            aria-label="Add job"
          >
            <span className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-accent text-white shadow-modal">
              <Icon name="plus" size={24} />
            </span>
          </NavLink>
        ) : (
          <NavLink
            key={t.to}
            to={t.to}
            end={t.to === '/'}
            className={({ isActive }) =>
              `flex min-h-14 flex-1 flex-col items-center justify-center gap-0.5 text-[0.6875rem] font-semibold no-underline ${
                isActive ? 'text-indigo-700 hover:text-indigo-700' : 'text-muted hover:text-ink'
              }`
            }
          >
            <Icon name={t.icon} size={22} />
            <span>{t.label}</span>
          </NavLink>
        ),
      )}
    </nav>
  );
}
