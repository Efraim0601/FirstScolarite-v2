import { Component, computed, inject, output, signal } from '@angular/core';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { AuthService } from '../../core/auth/auth.service';
import { StudioStore } from './studio.store';

@Component({
  selector: 'fp-interface-list',
  standalone: true,
  imports: [StatusBadgeComponent],
  styleUrl: './interface-list.component.scss',
  template: `
    <div class="panel">
      <div class="head">
        <div>
          <div class="title">Studio de paiement</div>
          <div class="subtitle">Vos interfaces de collecte sans code</div>
        </div>
        @if (canWrite()) {
          <button class="new-btn" (click)="store.openNew()">+ Nouvelle interface</button>
        }
      </div>

      <div class="filters">
        <div class="search">
          <span class="search-ic">⌕</span>
          <input [value]="query()" (input)="query.set($any($event.target).value)" placeholder="Rechercher une interface…" />
        </div>
        <div class="chips">
          @for (f of chips; track f.id) {
            <button class="chip" [class.active]="filter() === f.id" (click)="filter.set(f.id)">
              {{ f.label }}<span class="count">{{ counts()[f.id] }}</span>
            </button>
          }
        </div>
      </div>

      <div class="scroll">
        @for (it of filtered(); track it.id) {
          <div class="card" [class.active]="it.id === store.selectedId()" (click)="store.select(it.id)">
            @if (it.id === store.selectedId()) { <div class="rail"></div> }
            <div class="card-head">
              <div class="card-id">
                <div class="card-name">{{ it.name || 'Sans titre' }}</div>
                <div class="card-slug mono">/{{ it.slug }}</div>
              </div>
              <fp-status-badge [status]="it.status" />
            </div>
            <div class="card-stats">
              <div><div class="cs-lbl">Transactions</div><div class="cs-val">{{ fr(it.tx) }}</div></div>
              <div><div class="cs-lbl">Encaissé</div><div class="cs-val">{{ fr(it.collected) }} <span>XAF</span></div></div>
            </div>
            <div class="card-actions">
              @if (it.status === 'actif') {
                <button class="ghost" (click)="share.emit(it.id); $event.stopPropagation()">↗ Partager</button>
              } @else {
                <button class="ghost" (click)="$event.stopPropagation()">◉ Voir</button>
              }
              @if (canWrite()) {
                <button class="solid" (click)="store.openEditor(it.id); $event.stopPropagation()">✎ Modifier</button>
                <button class="danger" title="Supprimer" (click)="remove.emit(it.id); $event.stopPropagation()">🗑</button>
              }
            </div>
          </div>
        } @empty {
          <div class="empty">Aucune interface trouvée</div>
        }
        <div class="tip">✦ <div><b>Astuce.</b> Dupliquez une interface depuis l'éditeur pour réutiliser sa configuration.</div></div>
      </div>
    </div>
  `,
})
export class InterfaceListComponent {
  readonly store = inject(StudioStore);
  private readonly auth = inject(AuthService);
  readonly share = output<string>();
  readonly remove = output<string>();

  canWrite() { return this.auth.hasPerm('studio.write'); }

  readonly query = signal('');
  readonly filter = signal<'all' | 'actif' | 'brouillon'>('all');
  readonly chips = [
    { id: 'all' as const, label: 'Toutes' },
    { id: 'actif' as const, label: 'Actives' },
    { id: 'brouillon' as const, label: 'Brouillons' },
  ];

  readonly counts = computed(() => {
    const arr = this.store.interfaces();
    return {
      all: arr.length,
      actif: arr.filter((i) => i.status === 'actif').length,
      brouillon: arr.filter((i) => i.status === 'brouillon').length,
    } as Record<string, number>;
  });

  readonly filtered = computed(() => {
    const q = this.query().toLowerCase();
    const f = this.filter();
    return this.store.interfaces().filter((it) => {
      if (f !== 'all' && it.status !== f) return false;
      if (q && !it.name.toLowerCase().includes(q)) return false;
      return true;
    });
  });

  fr(n: number) { return n.toLocaleString('fr-FR'); }
}
