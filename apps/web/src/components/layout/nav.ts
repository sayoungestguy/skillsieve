import type { IconName } from '@/components/ui/Icon';

export interface NavItem {
  to: string;
  label: string;
  icon: IconName;
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Board', icon: 'board' },
  { to: '/insights', label: 'Insights', icon: 'insights' },
  { to: '/skills', label: 'My skills', icon: 'skills' },
  { to: '/search', label: 'Search', icon: 'search' },
  { to: '/settings', label: 'Settings', icon: 'settings' },
];
