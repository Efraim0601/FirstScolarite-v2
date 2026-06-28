import { Component, computed, input, output, signal } from '@angular/core';

interface Channel { id: string; label: string; color: string; href: string; }

/** Modale de partage : onglet Lien (copier + canaux) et onglet QR. */
@Component({
  selector: 'fp-share-modal',
  standalone: true,
  styleUrl: './share-modal.component.scss',
  template: `
    <div class="overlay" (click)="close.emit()">
      <div class="modal" (click)="$event.stopPropagation()">
        <div class="head">
          <div>
            <div class="eyebrow">Partager · {{ partnerName() }}</div>
            <div class="name">{{ name() }}</div>
          </div>
          <button class="x" (click)="close.emit()">✕</button>
        </div>

        <div class="tabs">
          <button class="tab" [class.on]="tab() === 'link'" (click)="tab.set('link')">Lien de paiement</button>
          <button class="tab" [class.on]="tab() === 'qr'" (click)="tab.set('qr')">QR code</button>
        </div>

        <div class="body">
          @if (tab() === 'link') {
            <div class="lbl">URL publique</div>
            <div class="url-box">
              <span class="url mono">{{ url() }}</span>
              <button class="copy" (click)="copy()">{{ copied() ? '✓ Copié' : 'Copier' }}</button>
            </div>
            <div class="lbl">Partager via</div>
            <div class="channels">
              @for (c of channels(); track c.id) {
                <a class="channel" [href]="c.href" target="_blank" rel="noopener" [style.borderColor]="c.color + '55'">
                  <span class="ch-dot" [style.background]="c.color"></span>{{ c.label }}
                </a>
              }
            </div>
          } @else {
            <div class="qr-wrap">
              <img class="qr-img" [src]="qrImageUrl()" width="200" height="200" alt="QR code de paiement">
              <div class="qr-cap">Scannez pour payer · {{ name() }}</div>
              <button class="ghost" (click)="downloadQr()">⭳ Télécharger le QR</button>
            </div>
          }
        </div>
      </div>
    </div>
  `,
})
export class ShareModalComponent {
  readonly url = input.required<string>();
  readonly name = input.required<string>();
  readonly partnerName = input.required<string>();
  readonly close = output<void>();

  readonly tab = signal<'link' | 'qr'>('link');
  readonly copied = signal(false);

  readonly fullUrl = computed(() => 'https://' + this.url());
  readonly qrImageUrl = computed(() =>
    'https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=' + encodeURIComponent(this.fullUrl()),
  );
  readonly channels = computed<Channel[]>(() => {
    const u = encodeURIComponent(this.fullUrl());
    const txt = encodeURIComponent(`Payez en ligne sur ${this.name()} : ${this.fullUrl()}`);
    return [
      { id: 'whatsapp', label: 'WhatsApp', color: '#25D366', href: `https://wa.me/?text=${txt}` },
      { id: 'email', label: 'Email', color: '#2563EB', href: `mailto:?subject=${encodeURIComponent('Lien de paiement — ' + this.name())}&body=${txt}` },
      { id: 'sms', label: 'SMS', color: '#1F9D55', href: `sms:?body=${txt}` },
      { id: 'telegram', label: 'Telegram', color: '#0088CC', href: `https://t.me/share/url?url=${u}` },
    ];
  });

  copy() {
    navigator.clipboard?.writeText(this.fullUrl());
    this.copied.set(true);
    setTimeout(() => this.copied.set(false), 1800);
  }

  downloadQr() {
    const a = document.createElement('a');
    a.href = this.qrImageUrl();
    a.download = 'qr-firstpay.png';
    a.target = '_blank';
    a.rel = 'noopener';
    a.click();
  }
}
