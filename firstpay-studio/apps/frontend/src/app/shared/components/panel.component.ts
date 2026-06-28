import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'fp-panel',
  standalone: true,
  template: `
    <div class="panel">
      <div class="panel-head">
        <span>{{ title }}</span>
        @if (linkLabel) {
          <button type="button" class="panel-link" (click)="linkClick.emit()">{{ linkLabel }}</button>
        }
      </div>
      <ng-content />
    </div>
  `,
  styles: [`
    .panel { background: var(--surface); border: 1px solid var(--border); border-radius: 14px; overflow: hidden; }
    .panel-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px;
      border-bottom: 1px solid var(--border); font-weight: 600; font-size: 14px; }
    .panel-link { background: none; border: none; color: var(--blue); font-size: 12.5px; cursor: pointer; font-weight: 500; }
  `],
})
export class PanelComponent {
  @Input({ required: true }) title!: string;
  @Input() linkLabel = '';
  @Output() linkClick = new EventEmitter<void>();
}
