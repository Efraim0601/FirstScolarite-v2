import { inject } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { PartnerApiService } from '../../core/api/partner-api.service';

export interface NotifPref { email: boolean; sms: boolean; }
export interface SettingsState {
  logo: string | null;
  logoName: string | null;
  brandColor: string;
  notifications: Record<string, NotifPref>;
  useApi: boolean;
}

export const BRAND_PALETTE = ['#E53935', '#C62828', '#2563EB', '#1F9D55', '#7C3AED', '#B7791F', '#0F1115', '#0891B2'];

export const NOTIF_EVENTS: { key: string; label: string; desc: string }[] = [
  { key: 'txSuccess', label: 'Paiement réussi', desc: 'À chaque transaction confirmée.' },
  { key: 'txFailed', label: 'Paiement échoué', desc: 'Quand un paiement échoue.' },
  { key: 'weeklyReport', label: 'Rapport hebdomadaire', desc: 'Synthèse chaque lundi matin.' },
  { key: 'newInterface', label: 'Nouvelle interface', desc: "Quand une interface est publiée." },
  { key: 'teamChange', label: "Changement d'équipe", desc: 'Invitation ou retrait de membre.' },
];

export const SettingsStore = signalStore(
  { providedIn: 'root' },
  withState<SettingsState>({
    logo: null, logoName: null, brandColor: '#E53935', useApi: false,
    notifications: {
      txSuccess: { email: true, sms: false },
      txFailed: { email: true, sms: true },
      weeklyReport: { email: true, sms: false },
      newInterface: { email: false, sms: false },
      teamChange: { email: true, sms: false },
    },
  }),
  withMethods((store, api = inject(PartnerApiService)) => {
    const persist = () => {
      if (!store.useApi()) return;
      api.saveSettings({
        logoUrl: store.logo(),
        logoName: store.logoName(),
        brandColor: store.brandColor(),
        notifications: store.notifications(),
      }).subscribe();
    };
    return {
      loadFromApi() {
        api.fetchSettings().subscribe((s) => {
          if (!s) { patchState(store, { useApi: false }); return; }
          patchState(store, {
            useApi: true,
            logo: s.logoUrl ?? null,
            logoName: s.logoName ?? null,
            brandColor: s.brandColor || store.brandColor(),
            notifications: (s.notifications as Record<string, NotifPref>) ?? store.notifications(),
          });
        });
      },
      setBrandColor(c: string) { patchState(store, { brandColor: c }); persist(); },
      setLogo(logo: string | null, logoName: string | null) { patchState(store, { logo, logoName }); persist(); },
      toggleNotif(key: string, channel: 'email' | 'sms', value: boolean) {
        const n = { ...store.notifications() };
        n[key] = { ...n[key], [channel]: value };
        patchState(store, { notifications: n });
        persist();
      },
    };
  }),
);
