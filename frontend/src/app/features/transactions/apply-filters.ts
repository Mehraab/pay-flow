import { PaymentViewEnriched } from '../../core/models';
import { statusPillVariant } from '../../shared/utils/payment-status';

export type StatusFilter = 'all' | 'captured' | 'pending' | 'failed' | 'refunded';

export function applyFilters(
  payments: PaymentViewEnriched[],
  statusFilter: StatusFilter,
  searchQuery: string,
): PaymentViewEnriched[] {
  const query = searchQuery.trim().toLowerCase();
  return payments.filter((payment) => {
    const matchesStatus = statusFilter === 'all' || statusPillVariant(payment.status) === statusFilter;
    const matchesQuery =
      !query ||
      payment.paymentId.toLowerCase().includes(query) ||
      (payment.customerName?.toLowerCase().includes(query) ?? false);
    return matchesStatus && matchesQuery;
  });
}
