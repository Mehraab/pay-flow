import { Component, computed, input, output, signal } from '@angular/core';
import { buildPageWindow } from '../../utils/pagination';

/**
 * First purely presentational ("dumb") component in this codebase — signal-based
 * input()/output() instead of injecting its own service. The consumer owns page state
 * and the fetch; this only renders page-number buttons and reports clicks, so the same
 * component can sit under any paginated table (Ledger today, Ledger Integrity's
 * platform-wide log as a planned follow-up).
 */
@Component({
  imports: [],
  selector: 'app-pagination',
  styleUrl: './pagination.css',
  templateUrl: './pagination.html',
})
export class Pagination {
  readonly currentPage = input.required<number>(); // 0-indexed, matches PageResponse.page
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly windowItems = computed(() => buildPageWindow(this.currentPage(), this.totalPages()));
  protected readonly jumpValue = signal('');

  protected goTo(page: number): void {
    if (page === this.currentPage() || page < 0 || page >= this.totalPages()) return;
    this.pageChange.emit(page);
  }

  protected isAdjacent(page: number): boolean {
    return page === this.currentPage() - 1 || page === this.currentPage() + 1;
  }

  protected onJumpInput(value: string): void {
    this.jumpValue.set(value);
  }

  protected submitJump(): void {
    const typed = Number(this.jumpValue());
    if (!Number.isInteger(typed)) return;
    this.goTo(typed - 1); // displayed page numbers are 1-indexed
    this.jumpValue.set('');
  }
}
