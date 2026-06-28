import { Component, Input } from '@angular/core';

@Component({
  selector: 'fp-stat-card',
  standalone: true,
  template: `
    <div class="stat-card">
      <div class="ic" [style.background]="accent + '18'" [style.color]="accent">{{ icon }}</div>
      <div>
        <div class="lbl">{{ label }}</div>
        <div class="val">{{ value }}</div>
        @if (sub) { <div class="sub">{{ sub }}</div> }
      </div>
    </div>
  `,
  styles: [`
    .stat-card { display: flex; gap: 14px; align-items: flex-start; background: var(--surface);
      border: 1px solid var(--border); border-radius: 14px; padding: 18px 20px; }
    .ic { width: 40px; height: 40px; border-radius: 10px; display: grid; place-items: center;
      font-size: 18px; font-weight: 700; flex-shrink: 0; }
    .lbl { font-size: 12px; color: var(--text-3); font-weight: 500; margin-bottom: 4px; }
    .val { font-size: 22px; font-weight: 700; letter-spacing: -0.02em; line-height: 1.1; }
    .sub { font-size: 11.5px; color: var(--text-3); margin-top: 4px; }
  `],
})
export class StatCardComponent {
  @Input({ required: true }) label!: string;
  @Input({ required: true }) value!: string;
  @Input() sub = '';
  @Input() icon = '∑';
  @Input() accent = '#2563EB';
}
