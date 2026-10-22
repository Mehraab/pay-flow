import { Routes } from '@angular/router';
import { authGuard, merchantGuard, operatorGuard } from './core/guards/auth.guard';
import { SignIn } from './features/auth/sign-in/sign-in';
import { CheckoutDemo } from './features/sandbox/checkout-demo/checkout-demo';
import { TransactionDetail } from './features/transactions/transaction-detail/transaction-detail';
import { Transactions } from './features/transactions/transactions';
import { AppShell } from './layout/app-shell/app-shell';
import { PlaceholderView } from './shared/components/placeholder-view/placeholder-view';

export const routes: Routes = [
  { path: 'signin', component: SignIn },
  {
    path: '',
    component: AppShell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      {
        path: 'overview',
        component: PlaceholderView,
        data: { title: 'Overview', sub: 'Last 30 days · all providers' },
      },
      {
        path: 'transactions',
        component: Transactions,
        canActivate: [merchantGuard],
        data: { title: 'Transactions', sub: 'All payment activity' },
      },
      {
        path: 'transactions/:id',
        component: TransactionDetail,
        canActivate: [merchantGuard],
        data: { title: 'Transaction detail' },
      },
      {
        path: 'ledger',
        component: PlaceholderView,
        canActivate: [merchantGuard],
        data: { title: 'Ledger' },
      },
      {
        path: 'sandbox',
        component: CheckoutDemo,
        canActivate: [merchantGuard],
        data: { title: 'Sandbox (Demo)', sub: 'Internal engineering tool — not the real checkout path' },
      },
      {
        path: 'developers',
        component: PlaceholderView,
        canActivate: [merchantGuard],
        data: { title: 'Developers' },
      },
      {
        path: 'operator/integrity',
        component: PlaceholderView,
        canActivate: [operatorGuard],
        data: { title: 'Ledger Integrity' },
      },
      {
        path: 'operator/providers',
        component: PlaceholderView,
        canActivate: [operatorGuard],
        data: { title: 'Provider Health' },
      },
      {
        path: 'operator/saga-events',
        component: PlaceholderView,
        canActivate: [operatorGuard],
        data: { title: 'Saga Events' },
      },
    ],
  },
  { path: '**', redirectTo: 'signin' },
];
