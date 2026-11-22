import { Link, NavLink } from 'react-router-dom';
import { Icon } from '@/components/ui/Icon';
import { NAV_ITEMS } from './nav';

export function Sidebar() {
  return (
    <nav
      aria-label="Main"
      className="sticky top-0 hidden h-screen w-60 flex-none flex-col gap-6 border-r border-line bg-surface px-4 py-5 lg:flex"
    >
      <Link
        to="/"
        className="flex min-h-11 items-center gap-2.5 px-2 text-ink no-underline hover:text-ink"
      >
        <span className="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-accent text-white">
          <Icon name="logo" size={18} />
        </span>
        <span className="text-lg font-bold tracking-tight">SkillSieve</span>
      </Link>
      <div className="flex flex-col gap-1">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) => `nav-link ${isActive ? 'nav-link-active' : ''}`}
          >
            <Icon name={item.icon} />
            <span>{item.label}</span>
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
