import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { Role } from '../../../core/models';

@Component({
  imports: [],
  selector: 'app-sign-in',
  styleUrl: './sign-in.css',
  templateUrl: './sign-in.html',
})
export class SignIn {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly role = signal<Role>('MERCHANT');
  readonly merchantId = signal('');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  selectRole(role: Role): void {
    this.role.set(role);
  }

  onMerchantIdInput(value: string): void {
    this.merchantId.set(value);
  }

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    const merchantId = this.role() === 'MERCHANT' ? this.merchantId().trim() || undefined : undefined;
    this.authService.login(this.role(), merchantId).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigateByUrl('/overview');
      },
      error: () => {
        this.loading.set(false);
        this.error.set("Couldn't sign in — check the Gateway is running and try again.");
      },
    });
  }
}
