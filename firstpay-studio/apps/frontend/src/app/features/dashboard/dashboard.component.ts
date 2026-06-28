import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { StudioStore } from '../studio/studio.store';
import { TenantContextService } from '../../core/tenant/tenant-context.service';
import { ReportingApiService } from '../../core/api/reporting-api.service';
import { UsersStore } from '../users/users.store';
import { StatCardComponent } from '../../shared/components/stat-card.component';
import { PanelComponent } from '../../shared/components/panel.component';

@Component({
  selector: 'fp-dashboard',
  standalone: true,
  imports: [StatCardComponent, PanelComponent],
  styleUrl: './dashboard.component.scss',
  template: `
    <div class="page">
      <div class="bg-lines"></div>
      <div class="inner">
        <!-- Hero -->
        <div class="hero">
          <div>
            <div class="eyebrow">Portail partenaire · {{ today }}</div>
            <div class="hello">Bonjour, {{ partner()?.name }} 👋</div>
            <div class="sub">
              {{ store.activeCount() }} interface(s) active(s)
              @if (store.draftCount() > 0) { · {{ store.draftCount() }} brouillon(s) }
            </div>
          </div>
          <div class="hero-actions">
            <button class="btn-primary" (click)="go('studio')">+ Nouvelle interface</button>
            <button class="btn-ghost" (click)="go('transactions')">Voir les transactions</button>
          </div>
        </div>

        <!-- Stats -->
        <div class="stats">
          <fp-stat-card label="Transactions" [value]="fr(stats().totalTx)" [sub]="todaySub()" icon="∑" accent="#2563EB" />
          <fp-stat-card label="Encaissé" [value]="fr(Math.round(stats().collected/1000)) + ' k'" sub="XAF · cumulé" icon="₣" accent="#1F9D55" />
          <fp-stat-card label="Taux de succès" [value]="stats().rate + ' %'" sub="sur 30 jours" icon="↗" accent="#7C3AED" />
          <fp-stat-card label="Interfaces" [value]="'' + store.interfaces().length" [sub]="store.activeCount() + ' active(s)'" icon="▤" accent="#E53935" />
        </div>

        <!-- Modules -->
        <div class="section-title">Modules</div>
        <div class="modules">
          @for (m of modules; track m.route) {
            <div class="module" (click)="go(m.route)">
              <div class="blob" [style.background]="m.accent + '10'"></div>
              <div class="module-head">
                <div class="module-ic" [style.background]="m.accent">{{ m.glyph }}</div>
                <div>
                  <div class="module-title">{{ m.title }}</div>
                  <div class="module-metric">{{ m.metricLabel }} : <b>{{ m.metric() }}</b></div>
                </div>
              </div>
              <div class="module-desc">{{ m.desc }}</div>
              <div class="module-cta" [style.color]="m.accent">{{ m.cta }} →</div>
            </div>
          }
        </div>

        <div class="two-col">
          <fp-panel title="Activité récente" linkLabel="Tout voir ›" (linkClick)="go('transactions')">
            @for (t of recent(); track t.id) {
              <div class="tx-row">
                <div class="tx-ic" [class.ok]="t.status==='success'" [class.ko]="t.status==='failed'" [class.wait]="t.status==='pending'">
                  {{ t.status==='success' ? '✓' : t.status==='failed' ? '✕' : '•' }}
                </div>
                <div class="tx-main">
                  <div class="tx-payer">{{ t.payer }}</div>
                  <div class="tx-meta">{{ t.interfaceName }} · {{ t.method }}</div>
                </div>
                <div class="tx-right">
                  <div class="tx-amount">{{ fr(t.amount) }} <span>XAF</span></div>
                  <div class="tx-date">{{ shortDate(t.date) }}</div>
                </div>
              </div>
            }
          </fp-panel>

          <fp-panel title="Top interfaces" linkLabel="Studio ›" (linkClick)="go('studio')">
            <div class="top-body">
              @for (it of top(); track it.id; let i = $index) {
                <div class="top-row">
                  <div class="top-line"><span class="top-name">{{ it.name }}</span><b>{{ fr(it.tx) }}</b></div>
                  <div class="bar"><div class="bar-fill" [style.width.%]="pct(it.tx)" [style.background]="barColor(i)"></div></div>
                  <div class="top-sub">{{ fr(Math.round(it.collected/1000)) }} k XAF encaissés</div>
                </div>
              }
            </div>
          </fp-panel>
        </div>
      </div>
    </div>
  `,
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly store = inject(StudioStore);
  readonly usersStore = inject(UsersStore);
  private readonly tenant = inject(TenantContextService);
  private readonly router = inject(Router);
  private readonly reporting = inject(ReportingApiService);
  readonly Math = Math;

  private liveSub?: Subscription;
  private readonly apiRate = signal<number | null>(null);

  readonly partner = this.tenant.partner;
  readonly today = new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

  ngOnInit() {
    this.store.loadFromApi();
    this.usersStore.loadFromApi();
    this.reporting.summary().subscribe((s) => {
      if (s) this.apiSummary.set(s);
    });
    this.liveSub = this.reporting.liveStats().subscribe({
      next: (s) => this.apiRate.set(s.successRate),
      error: () => {},
    });
  }

  ngOnDestroy() { this.liveSub?.unsubscribe(); }

  private readonly apiSummary = signal<{ totalTx: number; amountTotal: number; successRate: number } | null>(null);

  readonly stats = computed(() => {
    const api = this.apiSummary();
    const txs = this.store.transactions();
    const success = txs.filter((t) => t.status === 'success');
    const todayStr = new Date().toDateString();
    const local = {
      totalTx: txs.length,
      collected: success.reduce((s, t) => s + t.amount, 0),
      todayTx: txs.filter((t) => new Date(t.date).toDateString() === todayStr).length,
      rate: txs.length ? Math.round((success.length / txs.length) * 1000) / 10 : 0,
    };
    if (!api) return local;
    return {
      totalTx: api.totalTx || local.totalTx,
      collected: api.amountTotal ? +api.amountTotal : local.collected,
      todayTx: local.todayTx,
      rate: this.apiRate() ?? api.successRate ?? local.rate,
    };
  });

  readonly recent = computed(() => this.store.transactions().slice(0, 5));
  readonly top = computed(() => [...this.store.interfaces()].sort((a, b) => b.tx - a.tx).slice(0, 3));

  readonly modules = [
    { route: 'studio', accent: '#E53935', glyph: '◫', title: 'Studio de paiement', metricLabel: 'Interfaces',
      metric: () => this.store.interfaces().length,
      desc: 'Créez, modifiez et publiez des interfaces de collecte sans code. Stepper guidé en 4 étapes, aperçu avant publication.', cta: 'Ouvrir le Studio' },
    { route: 'transactions', accent: '#2563EB', glyph: '∿', title: 'Mes transactions', metricLabel: 'Total',
      metric: () => this.fr(this.store.transactions().length),
      desc: 'Suivez tous les paiements globalement ou par interface. Filtres avancés et export CSV/Excel/JSON.', cta: "Voir l'historique" },
    { route: 'users', accent: '#7C3AED', glyph: '◑', title: 'Utilisateurs & rôles', metricLabel: 'Membres',
      metric: () => '' + this.usersStore.users().length,
      desc: 'Invitez votre équipe avec des rôles fins : administrateur, gestionnaire, comptable, lecture seule.', cta: "Gérer l'équipe" },
    { route: 'settings', accent: '#1F9D55', glyph: '⚙', title: 'Paramètres', metricLabel: 'Marque',
      metric: () => 'Configurée',
      desc: 'Logo et identité visuelle, import des données clients, sécurité et notifications.', cta: 'Configurer' },
  ];

  fr(n: number) { return n.toLocaleString('fr-FR'); }
  todaySub() { return `+${this.stats().todayTx} aujourd'hui`; }
  pct(tx: number) { const max = Math.max(...this.store.interfaces().map((x) => x.tx)) || 1; return Math.max(Math.round((tx / max) * 100), 4); }
  barColor(i: number) { return i === 0 ? 'var(--fp-red)' : i === 1 ? 'var(--blue)' : 'var(--green)'; }
  shortDate(d: string) { return new Date(d).toLocaleString('fr-FR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }); }
  go(route: string) { this.router.navigate(['/', route]); }
}
