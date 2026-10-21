import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/**
 * Temporary stand-in for a routed view whose feature component hasn't been built yet.
 * Each later task replaces the `component` on its route in app.routes.ts with the real one.
 */
@Component({
  imports: [],
  selector: 'app-placeholder-view',
  styleUrl: './placeholder-view.css',
  templateUrl: './placeholder-view.html',
})
export class PlaceholderView {
  private readonly route = inject(ActivatedRoute);
  protected readonly title = this.route.snapshot.data['title'] ?? 'This view';
}
