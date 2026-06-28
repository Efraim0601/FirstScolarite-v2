import { Injectable, signal } from '@angular/core';

export interface Partner {
  name: string; code: string; shortCode: string; sector: string;
}

/** Contexte tenant courant (partenaire actif). Source du header X-Tenant-Id. */
@Injectable({ providedIn: 'root' })
export class TenantContextService {
  private readonly _partner = signal<Partner | null>(null);
  private readonly _tenantId = signal<string | null>(null);
  private readonly _apiKey = signal<string | null>(null);

  readonly partner = this._partner.asReadonly();
  readonly tenantId = this._tenantId.asReadonly();
  readonly apiKey = this._apiKey.asReadonly();

  setPartner(p: Partner | null) { this._partner.set(p); }
  setTenantId(id: string | null) { this._tenantId.set(id); }
  setApiKey(key: string | null) { this._apiKey.set(key); }
}
