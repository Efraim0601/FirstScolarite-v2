import { Component, Input } from '@angular/core';

type Status = 'actif' | 'brouillon' | 'pause' | 'success' | 'pending' | 'failed';

const MAP: Record<Status, { bg: string; color: string; border: string; label: string; dot: string }> = {
  actif:     { bg: 'var(--green-soft)', color: 'var(--green)', border: '#C8E8D5', label: 'Actif', dot: 'var(--green)' },
  brouillon: { bg: '#F2F4F7', color: 'var(--text-2)', border: 'var(--border-strong)', label: 'Brouillon', dot: 'var(--text-3)' },
  pause:     { bg: 'var(--amber-soft)', color: 'var(--amber)', border: '#F2D88B', label: 'En pause', dot: 'var(--amber)' },
  success:   { bg: 'var(--green-soft)', color: 'var(--green)', border: '#C8E8D5', label: 'Réussi', dot: 'var(--green)' },
  pending:   { bg: 'var(--amber-soft)', color: 'var(--amber)', border: '#F2D88B', label: 'En attente', dot: 'var(--amber)' },
  failed:    { bg: 'var(--fp-red-50)', color: 'var(--fp-red)', border: '#F3C9C7', label: 'Échoué', dot: 'var(--fp-red)' },
};

@Component({
  selector: 'fp-status-badge',
  standalone: true,
  template: `
    <span class="badge" [style.background]="s.bg" [style.color]="s.color" [style.borderColor]="s.border">
      <span class="dot" [style.background]="s.dot"></span>{{ s.label }}
    </span>
  `,
  styles: [`
    .badge { display: inline-flex; align-items: center; gap: 6px; padding: 3px 9px;
      border-radius: 999px; font-size: 11.5px; font-weight: 600; border: 1px solid; }
    .dot { width: 6px; height: 6px; border-radius: 99px; }
  `],
})
export class StatusBadgeComponent {
  @Input({ required: true }) status!: Status;
  get s() { return MAP[this.status] ?? MAP.brouillon; }
}
