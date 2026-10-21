import { Injectable, signal } from '@angular/core';

/** Shared between HeaderBarComponent's global search input and TransactionsComponent's filter. */
@Injectable({ providedIn: 'root' })
export class SearchQueryService {
  readonly query = signal('');
}
