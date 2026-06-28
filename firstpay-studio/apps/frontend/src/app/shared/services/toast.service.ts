import { Injectable, signal } from '@angular/core';

/** Toast global léger (Signal) — remplace les toasts inline par feature. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _message = signal<string | null>(null);
  readonly message = this._message.asReadonly();

  show(msg: string, ms = 2800) {
    this._message.set(msg);
    setTimeout(() => this._message.set(null), ms);
  }
}
