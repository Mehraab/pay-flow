import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter, startWith } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { SearchQueryService } from '../../shared/services/search-query.service';

function deepestChild(route: ActivatedRoute): ActivatedRoute {
  let current = route;
  while (current.firstChild) {
    current = current.firstChild;
  }
  return current;
}

@Component({
  imports: [],
  selector: 'app-header-bar',
  styleUrl: './header-bar.css',
  templateUrl: './header-bar.html',
})
export class HeaderBar {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly authService = inject(AuthService);
  protected readonly searchQueryService = inject(SearchQueryService);

  readonly initials = computed(() => (this.authService.role() === 'OPERATOR' ? 'OP' : 'ME'));

  private readonly navigationEnd = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      startWith(null),
    ),
  );

  readonly title = computed(() => {
    this.navigationEnd();
    return deepestChild(this.route).snapshot.data['title'] ?? '';
  });

  readonly sub = computed(() => {
    this.navigationEnd();
    return deepestChild(this.route).snapshot.data['sub'] ?? '';
  });

  readonly isTransactionsRoute = computed(() => {
    const event = this.navigationEnd();
    const url = event ? event.urlAfterRedirects : this.router.url;
    return url.startsWith('/transactions');
  });

  onSearchInput(value: string): void {
    this.searchQueryService.query.set(value);
  }
}
