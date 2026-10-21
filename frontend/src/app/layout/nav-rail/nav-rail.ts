import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { NAV_ITEMS, NavItem } from '../../core/config/nav-items';
import { AuthService } from '../../core/services/auth.service';

@Component({
  imports: [RouterLink, RouterLinkActive],
  selector: 'app-nav-rail',
  styleUrl: './nav-rail.css',
  templateUrl: './nav-rail.html',
})
export class NavRail {
  private readonly authService = inject(AuthService);

  readonly items = computed<NavItem[]>(() => {
    const role = this.authService.role();
    return role ? NAV_ITEMS.filter((item) => item.roles.includes(role)) : [];
  });
}
