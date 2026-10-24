/** Shared wire shape for every paginated Gateway response (payments, ledger entries, ...). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
