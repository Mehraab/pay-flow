import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) return true;
  return inject(Router).createUrlTree(['/signin']);
};

export const merchantGuard: CanActivateFn = () =>
  inject(AuthService).role() === 'MERCHANT' || inject(Router).createUrlTree(['/signin']);

export const operatorGuard: CanActivateFn = () =>
  inject(AuthService).role() === 'OPERATOR' || inject(Router).createUrlTree(['/signin']);
