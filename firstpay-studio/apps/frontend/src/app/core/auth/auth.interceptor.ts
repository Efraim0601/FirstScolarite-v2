import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { TenantContextService } from '../tenant/tenant-context.service';

/** Injecte JWT, X-API-Key (M2M) et X-Tenant-Id sur chaque requête API. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const tenant = inject(TenantContextService);
  const token = auth.token();
  const tenantId = tenant.tenantId();
  const apiKey = tenant.apiKey();

  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (apiKey) headers['X-API-Key'] = apiKey;
  if (tenantId) headers['X-Tenant-Id'] = tenantId;

  if (!req.url.includes('/api/v1/')) {
    return next(req);
  }

  return next(req.clone({ setHeaders: headers }));
};
