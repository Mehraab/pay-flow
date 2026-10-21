import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { Role } from '../../core/models';

@Component({
  imports: [],
  selector: 'app-role-switcher',
  styleUrl: './role-switcher.css',
  templateUrl: './role-switcher.html',
})
export class RoleSwitcher {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly role = this.authService.role;
  readonly switching = signal(false);

  switchTo(role: Role): void {
    if (role === this.role() || this.switching()) return;
    this.switching.set(true);
    const merchantId = role === 'MERCHANT' ? (this.authService.merchantId() ?? undefined) : undefined;
    this.authService.switchRole(role, merchantId).subscribe({
      next: () => {
        this.switching.set(false);
        this.router.navigateByUrl('/overview');
      },
      error: () => {
        this.switching.set(false);
      },
    });
  }
}
