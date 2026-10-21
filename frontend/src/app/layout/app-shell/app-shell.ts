import { Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { HeaderBar } from '../header-bar/header-bar';
import { NavRail } from '../nav-rail/nav-rail';
import { RoleSwitcher } from '../role-switcher/role-switcher';

@Component({
  imports: [RouterOutlet, NavRail, RoleSwitcher, HeaderBar],
  selector: 'app-app-shell',
  styleUrl: './app-shell.css',
  templateUrl: './app-shell.html',
})
export class AppShell {
  private readonly authService = inject(AuthService);

  readonly roleLabel = computed(() => (this.authService.role() === 'OPERATOR' ? 'Operator' : 'Merchant'));
  readonly identityLabel = computed(() => this.authService.merchantId() ?? 'No merchant scope');
}
