import { Role } from '../models';

export type NavIconName =
  | 'overview'
  | 'transactions'
  | 'ledger'
  | 'sandbox'
  | 'developers'
  | 'integrity'
  | 'provider-health'
  | 'saga-events'
  | 'webhook-dlq';

export interface NavItem {
  label: string;
  icon: NavIconName;
  routerLink: string;
  roles: Role[];
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Overview', icon: 'overview', routerLink: '/overview', roles: ['MERCHANT', 'OPERATOR'] },
  { label: 'Transactions', icon: 'transactions', routerLink: '/transactions', roles: ['MERCHANT'] },
  { label: 'Ledger', icon: 'ledger', routerLink: '/ledger', roles: ['MERCHANT'] },
  { label: 'Sandbox (Demo)', icon: 'sandbox', routerLink: '/sandbox', roles: ['MERCHANT'] },
  { label: 'Developers', icon: 'developers', routerLink: '/developers', roles: ['MERCHANT'] },
  { label: 'Ledger Integrity', icon: 'integrity', routerLink: '/operator/integrity', roles: ['OPERATOR'] },
  { label: 'Provider Health', icon: 'provider-health', routerLink: '/operator/providers', roles: ['OPERATOR'] },
  { label: 'Saga Events', icon: 'saga-events', routerLink: '/operator/saga-events', roles: ['OPERATOR'] },
  { label: 'Webhook DLQ', icon: 'webhook-dlq', routerLink: '/operator/webhooks/dlq', roles: ['OPERATOR'] },
];
