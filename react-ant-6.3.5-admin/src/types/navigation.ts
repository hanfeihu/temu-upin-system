import type { ReactNode } from 'react';

export interface AppNavItem {
  key: string;
  label: string;
  path?: string;
  icon?: ReactNode;
  description?: string;
  children?: AppNavItem[];
}

export interface AppRouteMeta {
  path: string;
  title: string;
  description: string;
  menuKey: string;
}
