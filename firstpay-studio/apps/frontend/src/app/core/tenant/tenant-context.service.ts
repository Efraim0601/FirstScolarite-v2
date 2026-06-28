import { Injectable, signal } from '@angular/core';

export interface Partner {
  name: string; code: string; shortCode: string; sector: string;
}

/** Contexte tenant courant (partenaire actif). Source du header X-Tenant-Id. */
@Injectable({ providedIn: 'root' })
export class TenantContextService {
  private readonly _partner = signal<Partner | null>({
    name: 'SOFT TECHNOLOGIES',
    code: 'FSPAY_202605211633050082',
    shortCode: 'SOFT',
    sector: 'Fintech',
  });
  private readonly _tenantId = signal<string | null>('11111111-1111-1111-1111-111111111111');
  private readonly _apiKey = signal<string | null>('demo-soft-key');

  readonly partner = this._partner.asReadonly();
  readonly tenantId = this._tenantId.asReadonly();
  readonly apiKey = this._apiKey.asReadonly();

  setPartner(p: Partner | null) { this._partner.set(p); }
  setTenantId(id: string | null) { this._tenantId.set(id); }
  setApiKey(key: string | null) { this._apiKey.set(key); }
}
