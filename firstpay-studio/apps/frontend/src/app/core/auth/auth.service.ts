import { Injectable, computed, signal } from '@angular/core';
import { Account, ROLES_CATALOG, RoleId } from './roles';

/**
 * État d'authentification basé sur les Signals. Gère aussi l'impersonation
 * (un bank_admin qui délègue sur un partenaire devient partner_admin).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _user = signal<Account | null>(null);
  private readonly _impersonatedPartner = signal<string | null>(null);
  private readonly _token = signal<string | null>(null);
  private readonly _bankToken = signal<string | null>(null);

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);

  /** Rôle effectif (tient compte de l'impersonation). */
  readonly effectiveRole = computed<RoleId | null>(() => {
    const u = this._user();
    if (!u) return null;
    return this._impersonatedPartner() ? 'partner_admin' : u.role;
  });

  readonly roleDef = computed(() => {
    const r = this.effectiveRole();
    return r ? ROLES_CATALOG[r] : null;
  });

  readonly isBank = computed(() => this.roleDef()?.side === 'bank');
  readonly isImpersonating = computed(() => this._impersonatedPartner() !== null);

  token() { return this._token(); }

  login(account: Account, token: string | null = null) {
    this._user.set(account);
    this._token.set(token);
  }

  setToken(token: string | null) { this._token.set(token); }

  logout() {
    this._user.set(null);
    this._impersonatedPartner.set(null);
    this._token.set(null);
    this._bankToken.set(null);
  }

  impersonate(partnerName: string, delegatedToken?: string) {
    if (!this._impersonatedPartner()) {
      this._bankToken.set(this._token());
    }
    if (delegatedToken) this._token.set(delegatedToken);
    this._impersonatedPartner.set(partnerName);
  }

  exitImpersonate() {
    const bank = this._bankToken();
    if (bank) this._token.set(bank);
    this._bankToken.set(null);
    this._impersonatedPartner.set(null);
  }

  hasPerm(perm: string): boolean {
    const perms = this.roleDef()?.perms ?? [];
    return perms.includes('*') || perms.includes(perm)
      || perms.some((p) => p.endsWith('.*') && perm.startsWith(p.slice(0, -1)));
  }
}
