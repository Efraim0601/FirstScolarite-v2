import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { PARTNERS_DB, PartnerRecord } from '../../core/models/partner.model';
import { PaymentInterface, SEED_INTERFACES, Method, METHOD_LABELS } from '../../core/models/interface.model';
import { PartnerApiService } from '../../core/api/partner-api.service';
import { TransactionApiService } from '../../core/api/transaction-api.service';
import { TenantContextService } from '../../core/tenant/tenant-context.service';
import { apiKeyForPartner } from '../../core/auth/api-keys';

interface Receipt {
  ref: string; payer: string; phone: string; amount: number; method: Method;
  iface: PaymentInterface; partner: PartnerRecord; at: string; fields: Record<string, string>;
}

const STEP_LABELS = ['Identification', 'Montant', 'Mode', 'Validation'];

/* Génère un catalogue plausible pour les partenaires hors SOFT (démo). */
function partnerInterfaces(p: PartnerRecord): PaymentInterface[] {
  if (p.shortCode === 'SOFT') return SEED_INTERFACES.filter((i) => i.status === 'actif');
  return Array.from({ length: Math.min(p.interfaces, 3) }, (_, i) => ({
    ...SEED_INTERFACES[i % SEED_INTERFACES.length],
    id: `${p.shortCode}-if-${i + 1}`,
    name: `${p.sector === 'Éducation' ? 'Frais' : 'Collecte'} ${p.shortCode} #${i + 1}`,
    status: 'actif' as const,
  }));
}

@Component({
  selector: 'fp-cashier',
  standalone: true,
  imports: [FormsModule],
  styleUrl: './cashier.component.scss',
  template: `
    <div class="page">
      <div class="bg-lines"></div>
      <div class="inner">
        @if (!processing()) {
          <!-- Hero -->
          <div class="hero">
            <div class="hero-blob"></div>
            <div class="eyebrow">{{ agency() }} · {{ today }}</div>
            <div class="hello">Bonjour {{ firstName() }} 👋</div>
            <div class="sub">Vous avez encaissé <b>{{ count() }}</b> paiement(s) aujourd'hui.</div>
          </div>

          <div class="stats">
            <div class="cstat" style="border-top-color:#7C3AED"><div class="cs-lbl">Encaissé aujourd'hui</div><div class="cs-val">{{ fr(collected()) }} XAF</div><div class="cs-sub">{{ count() }} transaction(s)</div></div>
            <div class="cstat" style="border-top-color:#1F8A5B"><div class="cs-lbl">Solde caisse</div><div class="cs-val">280 500 XAF</div><div class="cs-sub">à reverser ce soir</div></div>
            <div class="cstat" style="border-top-color:#B7791F"><div class="cs-lbl">En attente</div><div class="cs-val">0</div><div class="cs-sub">aucune réconciliation</div></div>
          </div>

          @if (!partner()) {
            <!-- Step 1: partner -->
            <div class="flow-step"><div class="flow-dot">1</div><div><div class="fs-title">Choisir le partenaire</div><div class="fs-sub">Sélectionnez le partenaire pour lequel vous encaissez le client.</div></div></div>
            <div class="search big"><span>⌕</span>
              <input [ngModel]="pSearch()" (ngModelChange)="pSearch.set($event)" placeholder="Rechercher un partenaire par nom, code FSPAY, ou secteur…"></div>
            <div class="grid2">
              @for (p of partners(); track p.code) {
                <div class="pick" (click)="choosePartner(p)">
                  <div class="pick-logo">{{ p.shortCode }}</div>
                  <div><div class="pick-name">{{ p.name }}</div><div class="pick-sub">{{ p.sector }} · {{ p.interfaces }} interface(s)</div></div>
                </div>
              }
            </div>
          } @else {
            <!-- Step 2: interface -->
            <div class="flow-step"><div class="flow-dot purple">2</div>
              <div><div class="fs-title">Choisir l'interface — {{ partner()!.name }}</div><div class="fs-sub">Quel paiement le client souhaite-t-il régler ?</div></div>
              <button class="back" (click)="partner.set(null)">‹ Changer de partenaire</button></div>
            <div class="grid2">
              @for (it of ifaces(); track it.id) {
                <div class="pick" (click)="startProcess(it)">
                  <div class="pick-logo amount">{{ amountGlyph(it) }}</div>
                  <div><div class="pick-name">{{ it.name }}</div><div class="pick-sub mono">/{{ it.slug }}</div></div>
                  <span class="go">Encaisser ›</span>
                </div>
              }
            </div>
          }
        } @else {
          <!-- Process flow -->
          <div class="process">
            <div class="proc-head">
              <div><div class="eyebrow">Encaissement · {{ partner()!.name }}</div><div class="proc-title">{{ processing()!.name }}</div></div>
              <button class="back" (click)="cancel()">✕ Annuler</button>
            </div>

            <div class="stepper">
              @for (l of stepLabels; track l; let i = $index) {
                <div class="st" [class.on]="step() === i" [class.done]="i < step()">
                  <span class="st-num">{{ i < step() ? '✓' : i + 1 }}</span><span class="st-lbl">{{ l }}</span>
                </div>
              }
            </div>

            <div class="proc-body">
              @switch (step()) {
                @case (0) {
                  <label class="fld"><span>Nom du payeur <i>*</i></span><input [ngModel]="payer()" (ngModelChange)="payer.set($event)" placeholder="Nom et prénom"></label>
                  <label class="fld"><span>Téléphone <i>*</i></span><input [ngModel]="phone()" (ngModelChange)="phone.set($event)" placeholder="+237 6XX XX XX XX"></label>
                  @for (f of processing()!.customFields; track f.id) {
                    <label class="fld"><span>{{ f.label }} @if (f.required) { <i>*</i> }</span>
                      @if (f.type === 'select') {
                        <select [ngModel]="fieldVal(f.label)" (ngModelChange)="setField(f.label, $event)">
                          <option value="">Sélectionner…</option>
                          @for (o of f.options || []; track o) { <option [value]="o">{{ o }}</option> }
                        </select>
                      } @else {
                        <input [ngModel]="fieldVal(f.label)" (ngModelChange)="setField(f.label, $event)" [placeholder]="f.label">
                      }
                    </label>
                  }
                }
                @case (1) {
                  @switch (processing()!.amountType) {
                    @case ('fixed') { <div class="amount-fixed">{{ fr(+processing()!.fixedAmount) }} <span>XAF</span><div class="af-sub">Montant fixe</div></div> }
                    @case ('preset') {
                      <div class="presets">
                        @for (p of validPresets(); track p.id) {
                          <button class="preset" [class.on]="amount() === +p.amount" (click)="amount.set(+p.amount)">
                            <span>{{ p.label || 'Montant' }}</span><b>{{ fr(+p.amount) }} XAF</b>
                          </button>
                        }
                      </div>
                    }
                    @case ('free') {
                      <label class="fld"><span>Montant ({{ processing()!.currency }})</span>
                        <input type="number" [ngModel]="amount()" (ngModelChange)="amount.set(+$event)" [placeholder]="'Entre ' + processing()!.minAmount + ' et ' + processing()!.maxAmount"></label>
                    }
                  }
                }
                @case (2) {
                  <div class="methods">
                    @for (m of methodsOf(); track m) {
                      <button class="method" [class.on]="method() === m" (click)="method.set(m)">
                        <span class="m-dot" [style.background]="mcolor(m)"></span>{{ mlabel(m) }}
                      </button>
                    }
                  </div>
                }
                @case (3) {
                  <div class="confirm">
                    <div class="cf-row"><span>Payeur</span><b>{{ payer() }}</b></div>
                    <div class="cf-row"><span>Téléphone</span><b>{{ phone() }}</b></div>
                    <div class="cf-row"><span>Interface</span><b>{{ processing()!.name }}</b></div>
                    <div class="cf-row"><span>Moyen</span><b>{{ mlabel(method()!) }}</b></div>
                    <div class="cf-row total"><span>Montant</span><b>{{ fr(amount()) }} XAF</b></div>
                  </div>
                }
              }
            </div>

            <div class="proc-foot">
              @if (step() > 0) { <button class="ghost" (click)="step.set(step() - 1)">‹ Précédent</button> }
              <div class="spacer"></div>
              @if (step() < 3) {
                <button class="primary" [disabled]="!canNext()" (click)="step.set(step() + 1)">Suivant ›</button>
              } @else {
                <button class="primary" (click)="complete()">✓ Valider l'encaissement</button>
              }
            </div>
          </div>
        }
      </div>

      <!-- Receipt -->
      @if (receipt(); as r) {
        <div class="overlay" (click)="receipt.set(null)">
          <div class="ticket" (click)="$event.stopPropagation()">
            <div class="t-brand"><div class="t-logo">FP</div><div><b>FirstPay</b><div class="t-bank">Afriland First Bank</div></div></div>
            <div class="t-ok">✓ Paiement encaissé</div>
            <div class="t-amount">{{ fr(r.amount) }} XAF</div>
            <div class="t-rows">
              <div class="t-row"><span>Référence</span><b class="mono">{{ r.ref }}</b></div>
              <div class="t-row"><span>Payeur</span><b>{{ r.payer }}</b></div>
              <div class="t-row"><span>Interface</span><b>{{ r.iface.name }}</b></div>
              <div class="t-row"><span>Partenaire</span><b>{{ r.partner.name }}</b></div>
              <div class="t-row"><span>Moyen</span><b>{{ mlabel(r.method) }}</b></div>
              <div class="t-row"><span>Caissier</span><b>{{ auth.user()?.name }}</b></div>
              <div class="t-row"><span>Date</span><b>{{ r.at }}</b></div>
            </div>
            <div class="t-actions">
              <button class="ghost" (click)="print()">⎙ Imprimer</button>
              <button class="primary" (click)="receipt.set(null)">Terminer</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
})
export class CashierComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly partnerApi = inject(PartnerApiService);
  private readonly txApi = inject(TransactionApiService);
  private readonly tenant = inject(TenantContextService);

  readonly partner = signal<PartnerRecord | null>(null);
  readonly pSearch = signal('');
  readonly processing = signal<PaymentInterface | null>(null);
  readonly step = signal(0);
  readonly stepLabels = STEP_LABELS;
  private readonly partnerRows = signal<PartnerRecord[]>(PARTNERS_DB);
  private readonly apiIfaces = signal<PaymentInterface[] | null>(null);

  // process state
  readonly payer = signal('');
  readonly phone = signal('');
  readonly amount = signal(0);
  readonly method = signal<Method | null>(null);
  readonly fields = signal<Record<string, string>>({});
  readonly receipt = signal<Receipt | null>(null);
  private dayCount = signal(0);

  readonly today = new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });

  readonly partners = computed(() => {
    const q = this.pSearch().toLowerCase();
    return this.partnerRows().filter((p) => p.active).filter((p) => !q || p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q) || p.sector.toLowerCase().includes(q));
  });
  readonly ifaces = computed(() => {
    const api = this.apiIfaces();
    if (api?.length) return api;
    return this.partner() ? partnerInterfaces(this.partner()!) : [];
  });
  readonly validPresets = computed(() => this.processing()?.presets.filter((p) => p.amount) ?? []);
  readonly count = computed(() => this.dayCount());
  readonly collected = signal(0);

  firstName() { return (this.auth.user()?.name ?? '').split(' ')[0]; }
  agency() { return this.auth.user()?.agency ?? 'Agence Bonanjo · Douala'; }
  fr(n: number) { return (n || 0).toLocaleString('fr-FR'); }
  mlabel(m: Method) { return METHOD_LABELS[m]; }
  mcolor(m: Method) { return ({ orange: '#FF7900', mtn: '#B89F00', card: '#2563EB', transfer: '#1F9D55' } as Record<Method, string>)[m]; }
  methodsOf() { return (Object.entries(this.processing()!.methods).filter(([, v]) => v).map(([k]) => k)) as Method[]; }
  amountGlyph(it: PaymentInterface) { return it.amountType === 'fixed' ? '₣' : it.amountType === 'preset' ? '☰' : '∞'; }
  fieldVal(label: string) { return this.fields()[label] ?? ''; }
  setField(label: string, v: string) { this.fields.set({ ...this.fields(), [label]: v }); }

  ngOnInit() {
    this.partnerApi.listPartners().subscribe((list) => {
      if (list.length) {
        this.partnerRows.set(list.map((d) => ({
          name: d.name, code: d.code, shortCode: d.shortCode, sector: d.sector,
          interfaces: d.interfaceCount, active: d.status === 'ACTIVE', tenantId: d.id,
        })));
      }
    });
  }

  choosePartner(p: PartnerRecord) {
    this.partner.set(p);
    this.apiIfaces.set(null);
    if (p.tenantId) {
      this.tenant.setTenantId(p.tenantId);
      this.tenant.setApiKey(apiKeyForPartner(p.name));
      this.partnerApi.fetchInterfaces().subscribe((rows) => {
        this.apiIfaces.set(rows.filter((i) => i.status === 'actif'));
      });
    }
  }
  startProcess(it: PaymentInterface) {
    this.processing.set(it);
    this.step.set(0); this.payer.set(''); this.phone.set(''); this.fields.set({}); this.method.set(null);
    this.amount.set(it.amountType === 'fixed' ? +it.fixedAmount : 0);
  }
  cancel() { this.processing.set(null); }

  canNext(): boolean {
    if (this.step() === 0) return this.payer().trim().length > 0 && this.phone().trim().length > 0;
    if (this.step() === 1) return this.amount() > 0;
    if (this.step() === 2) return this.method() !== null;
    return true;
  }

  complete() {
    const it = this.processing()!;
    const body = {
      externalRef: 'CASH-' + Date.now(),
      amount: this.amount(),
      currency: it.currency || 'XAF',
      type: 'PAYMENT',
      method: this.method(),
      metadata: {
        payer: this.payer(), phone: this.phone(), interfaceId: it.id, interfaceName: it.name,
        cashierId: this.auth.user()?.id, ...this.fields(),
      },
    };
    const fallback = () => this.showReceipt(it, body.externalRef);
    this.txApi.create(body).subscribe({
      next: (res) => this.showReceipt(it, res?.externalRef ?? body.externalRef),
      error: () => fallback(),
    });
  }

  private showReceipt(it: PaymentInterface, ref: string) {
    const r: Receipt = {
      ref,
      payer: this.payer(), phone: this.phone(), amount: this.amount(), method: this.method()!,
      iface: it, partner: this.partner()!, fields: this.fields(),
      at: new Date().toLocaleString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }),
    };
    this.receipt.set(r);
    this.dayCount.set(this.dayCount() + 1);
    this.collected.set(this.collected() + r.amount);
    this.processing.set(null);
  }

  print() {
    document.body.classList.add('print-receipt');
    window.print();
    setTimeout(() => document.body.classList.remove('print-receipt'), 500);
  }
}
