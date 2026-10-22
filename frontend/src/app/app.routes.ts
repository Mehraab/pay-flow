import { Routes } from '@angular/router';
import { authGuard, merchantGuard, operatorGuard } from './core/guards/auth.guard';
import { SignIn } from './features/auth/sign-in/sign-in';
import { Developers } from './features/developers/developers';
import { Ledger } from './features/ledger/ledger';
import { Overview } from './features/overview/overview';
import { LedgerIntegrity } from './features/operator/ledger-integrity/ledger-integrity';
import { ProviderRouting } from './features/operator/provider-routing/provider-routing';
import { SagaEvents } from './features/operator/saga-events/saga-events';
import { WebhookDlq } from './features/operator/webhook-dlq/webhook-dlq';
import { CheckoutDemo } from './features/sandbox/checkout-demo/checkout-demo';
import { TransactionDetail } from './features/transactions/transaction-detail/transaction-detail';
import { Transactions } from './features/transactions/transactions';
import { AppShell } from './layout/app-shell/app-shell';

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
        component: Overview,
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
        component: Ledger,
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
        component: Developers,
        canActivate: [merchantGuard],
        data: { title: 'Developers' },
      },
      {
        path: 'operator/integrity',
        component: LedgerIntegrity,
        canActivate: [operatorGuard],
        data: { title: 'Ledger Integrity' },
      },
      {
        path: 'operator/providers',
        component: ProviderRouting,
        canActivate: [operatorGuard],
        data: { title: 'Provider Health' },
      },
      {
        path: 'operator/saga-events',
        component: SagaEvents,
        canActivate: [operatorGuard],
        data: { title: 'Saga Events' },
      },
      {
        path: 'operator/webhooks/dlq',
        component: WebhookDlq,
        canActivate: [operatorGuard],
        data: { title: 'Webhook DLQ', sub: 'Deliveries that exhausted automatic retries' },
      },
    ],
  },
  { path: '**', redirectTo: 'signin' },
];
