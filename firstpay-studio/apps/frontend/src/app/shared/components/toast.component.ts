import { Component, inject } from '@angular/core';
import { ToastService } from '../services/toast.service';

@Component({
  selector: 'fp-toast',
  standalone: true,
  template: `
    @if (toast.message(); as m) {
      <div class="toast">✓ {{ m }}</div>
    }
  `,
  styles: [`
    .toast {
      position: fixed; bottom: 24px; right: 24px; z-index: 9999;
      background: #0F1115; color: #fff; padding: 12px 18px; border-radius: 10px;
      box-shadow: 0 8px 24px rgba(0,0,0,.18); font-size: 14px;
    }
  `],
})
export class ToastComponent {
  readonly toast = inject(ToastService);
}
