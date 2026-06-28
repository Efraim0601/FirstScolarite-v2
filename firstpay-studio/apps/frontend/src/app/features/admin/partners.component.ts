import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PARTNERS_DB, PartnerRecord } from '../../core/models/partner.model';
import { AuthService } from '../../core/auth/auth.service';
import { TenantContextService } from '../../core/tenant/tenant-context.service';
import { PartnerApiService } from '../../core/api/partner-api.service';
import { AuditApiService } from '../../core/api/audit-api.service';
import { apiKeyForPartner } from '../../core/auth/api-keys';

interface Draft {
  name: string; sector: string; adminName: string; adminEmail: string;
  settlementAccount: string; accountHolder: string; settlementBank: string;
}

const SECTORS = ['Fintech', 'Éducation', 'ONG / Associatif', 'Commerce', 'Santé', 'Transport', 'Autre'];

@Component({
  selector: 'fp-partners',
  standalone: true,
  imports: [FormsModule],
  styleUrl: './partners.component.scss',
  template: `
    <div class="page">
      <div class="head">
        <div>
          <div class="eyebrow">Plateforme FirstPay · Supervision</div>
          <div class="title">Partenaires</div>
          <div class="subtitle">Tous les partenaires enrôlés. Cliquez sur « Ouvrir » pour vous connecter en leur nom et déboguer.</div>
        </div>
        <div class="head-right">
          <span class="count">{{ filtered().length }} partenaire(s)</span>
          <button class="new-btn" (click)="openCreate()">+ Nouveau partenaire</button>
        </div>
      </div>

      <div class="body">
        <div class="search"><span>⌕</span>
          <input [ngModel]="search()" (ngModelChange)="search.set($event)" placeholder="Rechercher par nom, code partenaire ou secteur…"></div>

        <div class="table">
          <div class="thead"><div>Partenaire</div><div>Secteur</div><div>Interfaces</div><div>Statut</div><div></div></div>
          @for (p of filtered(); track p.code; let i = $index) {
            <div class="trow" [class.alt]="i % 2 === 0">
              <div><div class="p-name">{{ p.name }}</div><div class="p-code mono">{{ p.code }}</div></div>
              <div class="muted">{{ p.sector }}</div>
              <div>{{ p.interfaces }}</div>
              <div>
                <span class="status" [class.active]="p.active">
                  <span class="dot"></span>{{ p.active ? 'Actif' : 'Suspendu' }}
                </span>
              </div>
              <div class="actions">
                <button class="open" [disabled]="!p.active" (click)="impersonate(p)" title="Se connecter en son nom">◉ Ouvrir</button>
              </div>
            </div>
          }
        </div>
      </div>

      <!-- Modale : création d'un partenaire -->
      @if (draft(); as d) {
        <div class="overlay" (click)="closeCreate()">
          <div class="modal" (click)="$event.stopPropagation()">
            <div class="m-head">
              <div class="m-title">Nouveau partenaire</div>
              <button class="x" (click)="closeCreate()">✕</button>
            </div>

            @if (createdKey()) {
              <!-- Écran de succès : on montre l'API-key une seule fois -->
              <div class="m-body">
                <div class="ok-banner">✓ Partenaire « {{ d.name }} » créé.</div>

                @if (d.adminEmail) {
                  <div class="key-lbl">Identifiants de connexion de l'administrateur</div>
                  <div class="creds">
                    <div class="cred-row"><span>Email</span><b class="mono">{{ d.adminEmail }}</b></div>
                    <div class="cred-row"><span>Mot de passe temporaire</span><b class="mono">{{ createdPassword() }}</b></div>
                  </div>
                  <div class="hint">Un email contenant le lien de l'application et ces identifiants a été envoyé à l'administrateur (si le SMTP est activé). La connexion se fait par email + mot de passe.</div>
                }

                <div class="key-lbl">Clé API (accès machine-à-machine — affichée une seule fois)</div>
                <div class="key-box">
                  <span class="key mono">{{ createdKey() }}</span>
                  <button class="copy" (click)="copyKey()">{{ copied() ? '✓ Copié' : 'Copier' }}</button>
                </div>
              </div>
              <div class="m-foot"><button class="primary" (click)="closeCreate()">Terminer</button></div>
            } @else {
              <div class="m-body">
                @if (error()) { <div class="err">{{ error() }}</div> }
                <label class="fld"><span>Nom du partenaire <i>*</i></span>
                  <input [ngModel]="d.name" (ngModelChange)="patch({ name: $event })" placeholder="Ex : Boulangerie Du Coin"></label>
                <label class="fld"><span>Secteur</span>
                  <select [ngModel]="d.sector" (ngModelChange)="patch({ sector: $event })">
                    @for (s of sectors; track s) { <option [value]="s">{{ s }}</option> }
                  </select></label>
                <div class="two">
                  <label class="fld"><span>Nom de l'administrateur</span>
                    <input [ngModel]="d.adminName" (ngModelChange)="patch({ adminName: $event })" placeholder="Ex : Awa Touré"></label>
                  <label class="fld"><span>Email de l'administrateur</span>
                    <input type="email" [ngModel]="d.adminEmail" (ngModelChange)="patch({ adminEmail: $event })" placeholder="admin@partenaire.cm"></label>
                </div>

                <div class="section-sep">Compte de règlement (réception des fonds)</div>
                <label class="fld"><span>Numéro de compte <i>*</i></span>
                  <input [ngModel]="d.settlementAccount" (ngModelChange)="patch({ settlementAccount: $event })" placeholder="Ex : 10005 00012 12345678901 23"></label>
                <div class="two">
                  <label class="fld"><span>Titulaire du compte</span>
                    <input [ngModel]="d.accountHolder" (ngModelChange)="patch({ accountHolder: $event })" placeholder="Ex : Clinique Saint-Luc SARL"></label>
                  <label class="fld"><span>Banque</span>
                    <input [ngModel]="d.settlementBank" (ngModelChange)="patch({ settlementBank: $event })" placeholder="Ex : Afriland First Bank"></label>
                </div>
              </div>
              <div class="m-foot">
                <button class="ghost" (click)="closeCreate()">Annuler</button>
                <button class="primary" [disabled]="!d.name || !d.settlementAccount || creating()" (click)="submitCreate()">
                  {{ creating() ? 'Création…' : 'Créer le partenaire' }}
                </button>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class PartnersComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly tenant = inject(TenantContextService);
  private readonly partnerApi = inject(PartnerApiService);
  private readonly audit = inject(AuditApiService);
  private readonly router = inject(Router);

  readonly search = signal('');
  readonly sectors = SECTORS;
  private readonly rows = signal<PartnerRecord[]>(PARTNERS_DB);

  readonly draft = signal<Draft | null>(null);
  readonly creating = signal(false);
  readonly createdKey = signal<string | null>(null);
  readonly createdPassword = signal<string | null>(null);
  readonly copied = signal(false);
  readonly error = signal('');

  ngOnInit() { this.reload(); }

  private reload() {
    this.partnerApi.listPartners().subscribe((list) => {
      if (list.length > 0) {
        this.rows.set(list.map((d) => ({
          name: d.name, code: d.code, shortCode: d.shortCode, sector: d.sector,
          interfaces: d.interfaceCount, active: d.status === 'ACTIVE', tenantId: d.id,
        })));
      }
    });
  }

  readonly filtered = computed(() => {
    const q = this.search().toLowerCase();
    return this.rows().filter((p) => !q || p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q) || p.sector.toLowerCase().includes(q));
  });

  // ---- Création ----
  openCreate() {
    this.error.set(''); this.createdKey.set(null); this.createdPassword.set(null); this.copied.set(false);
    this.draft.set({ name: '', sector: 'Fintech', adminName: '', adminEmail: '', settlementAccount: '', accountHolder: '', settlementBank: '' });
  }
  patch(p: Partial<Draft>) { const d = this.draft(); if (d) this.draft.set({ ...d, ...p }); }
  closeCreate() { this.draft.set(null); }

  submitCreate() {
    const d = this.draft();
    if (!d || !d.name.trim()) return;
    this.creating.set(true); this.error.set('');
    this.partnerApi.createPartner(d).subscribe((res) => {
      this.creating.set(false);
      if (!res) { this.error.set("Échec de la création (droits insuffisants ou backend indisponible)."); return; }
      // Ajoute le partenaire en tête de liste et affiche la clé API
      const p = res.partner;
      this.rows.set([{
        name: p.name, code: p.code, shortCode: p.shortCode, sector: p.sector,
        interfaces: p.interfaceCount, active: true, tenantId: p.id,
      }, ...this.rows()]);
      this.createdKey.set(res.apiKey);
      this.createdPassword.set(res.tempPassword ?? '—');
      this.audit.log('partner_create', 'partner', p.name, p.name, `Création du partenaire ${p.name}`).subscribe();
    });
  }

  copyKey() {
    const k = this.createdKey();
    if (k) { navigator.clipboard?.writeText(k); this.copied.set(true); setTimeout(() => this.copied.set(false), 1800); }
  }

  impersonate(p: PartnerRecord) {
    this.tenant.setPartner({ name: p.name, code: p.code, shortCode: p.shortCode, sector: p.sector });
    if (p.tenantId) this.tenant.setTenantId(p.tenantId);
    this.tenant.setApiKey(apiKeyForPartner(p.name));
    this.auth.impersonate(p.name);
    this.audit.log('impersonate_start', 'partner', p.name, p.name, `Délégation banque → ${p.name}`).subscribe();
    this.router.navigate(['/home']);
  }
}
