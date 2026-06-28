import { computed, inject } from '@angular/core';
import { signalStore, withState, withComputed, withMethods, patchState } from '@ngrx/signals';
import { forkJoin, tap } from 'rxjs';
import {
  PaymentInterface, SEED_INTERFACES, NEW_INTERFACE, InterfaceStatus,
} from '../../core/models/interface.model';
import { Transaction } from '../../core/models/transaction.model';
import { PartnerApiService } from '../../core/api/partner-api.service';

interface StudioState {
  interfaces: PaymentInterface[];
  transactions: Transaction[];
  selectedId: string | null;
  editing: PaymentInterface | null;
  loaded: boolean;
  useApi: boolean;
}

const slugify = (s: string) =>
  s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 40) || 'interface';

/* Génère un petit jeu de transactions mock à partir des interfaces actives. */
function seedTransactions(interfaces: PaymentInterface[]): Transaction[] {
  const names: [string, string][] = [
    ['NGONO Marie', '+237 691 23 45 67'], ['FOTSO Jean', '+237 677 88 99 00'],
    ['MBARGA Sophie', '+237 699 11 22 33'], ['ESSOMBA Daniel', '+237 698 76 54 32'],
    ['TCHATAT Léa', '+237 696 54 32 10'], ['KENGNE Pierre', '+237 678 11 22 33'],
  ];
  const statuses: Transaction['status'][] = ['success', 'success', 'success', 'success', 'pending', 'failed'];
  const txs: Transaction[] = [];
  let id = 1;
  interfaces.filter((i) => i.tx > 0).forEach((iface) => {
    const supported = (Object.entries(iface.methods).filter(([, v]) => v).map(([k]) => k)) as Transaction['method'][];
    const count = Math.min(iface.tx, iface.id === 'if-1' ? 40 : 20);
    for (let i = 0; i < count; i++) {
      const [payer, phone] = names[(i + id) % names.length];
      const method = supported[(i) % supported.length];
      const status = statuses[(i * 7 + id) % statuses.length];
      let amount: number;
      if (iface.amountType === 'fixed') amount = +iface.fixedAmount;
      else if (iface.amountType === 'preset') amount = +iface.presets[i % iface.presets.length].amount || 25000;
      else amount = 1000 + ((i * 5000) % 50000);
      const date = new Date(Date.now() - i * 36e5 * (1 + (id % 5))).toISOString();
      txs.push({
        id: 'tx-' + id, reference: 'FP-' + String(100000000 + id).slice(-9),
        payer, phone, interfaceId: iface.id, interfaceName: iface.name,
        method, status, amount, date, fields: {},
      });
      id++;
    }
  });
  return txs.sort((a, b) => +new Date(b.date) - +new Date(a.date));
}

/** Store Studio (NgRx Signal Store). Source de vérité front pour les interfaces. */
export const StudioStore = signalStore(
  { providedIn: 'root' },
  withState<StudioState>({
    interfaces: SEED_INTERFACES,
    transactions: seedTransactions(SEED_INTERFACES),
    selectedId: SEED_INTERFACES[0].id,
    editing: null,
    loaded: false,
    useApi: false,
  }),
  withComputed((store) => ({
    selected: computed(() => store.interfaces().find((i) => i.id === store.selectedId()) ?? null),
    activeCount: computed(() => store.interfaces().filter((i) => i.status === 'actif').length),
    draftCount: computed(() => store.interfaces().filter((i) => i.status === 'brouillon').length),
  })),
  withMethods((store, api = inject(PartnerApiService)) => ({
    /** Charge interfaces + transactions depuis l'API (fallback mock si indisponible). */
    loadFromApi() {
      forkJoin({ ifaces: api.fetchInterfaces(), txs: api.fetchTransactions() }).pipe(
        tap(({ ifaces, txs }) => {
          if (ifaces.length > 0) {
            const enrichedTxs = txs.length > 0 ? enrichTxNames(txs, ifaces) : seedTransactions(ifaces);
            patchState(store, {
              interfaces: ifaces,
              transactions: enrichedTxs,
              selectedId: ifaces[0]?.id ?? null,
              loaded: true,
              useApi: true,
            });
          } else {
            patchState(store, { loaded: true, useApi: false });
          }
        }),
      ).subscribe({ error: () => patchState(store, { loaded: true, useApi: false }) });
    },

    select(id: string) { patchState(store, { selectedId: id }); },

    openEditor(id: string) {
      const it = store.interfaces().find((i) => i.id === id);
      if (it) patchState(store, { selectedId: id, editing: structuredClone(it) });
    },

    openNew() {
      patchState(store, { selectedId: null, editing: NEW_INTERFACE() });
    },

    patchEditing(patch: Partial<PaymentInterface>) {
      const cur = store.editing();
      if (cur) patchState(store, { editing: { ...cur, ...patch } });
    },

    cancel() { patchState(store, { editing: null }); },

    /** Enregistre l'interface en cours ; `status` optionnel pour publier. */
    save(status?: InterfaceStatus) {
      const d = store.editing();
      if (!d) return;
      const next: PaymentInterface = { ...d };
      if (status) next.status = status;
      next.slug = next.customSlug || slugify(next.name);

      const persist = () => {
        const arr = store.interfaces();
        const idx = arr.findIndex((i) => i.id === next.id);
        if (idx >= 0) {
          const copy = [...arr]; copy[idx] = next;
          patchState(store, { interfaces: copy, editing: next, selectedId: next.id });
        } else {
          const finalized = { ...next, id: 'if-' + Date.now() };
          patchState(store, { interfaces: [finalized, ...arr], editing: finalized, selectedId: finalized.id });
        }
      };

      if (store.useApi()) {
        api.saveInterface(next).subscribe((saved) => {
          if (saved) patchState(store, { editing: saved, selectedId: saved.id,
            interfaces: upsertInterface(store.interfaces(), saved) });
          else persist();
        });
      } else {
        persist();
      }
    },

    remove(id: string) {
      const doRemove = () => patchState(store, {
        interfaces: store.interfaces().filter((i) => i.id !== id),
        selectedId: store.selectedId() === id ? null : store.selectedId(),
        editing: store.editing()?.id === id ? null : store.editing(),
      });
      if (store.useApi()) {
        api.deleteInterface(id).subscribe(() => doRemove());
      } else {
        doRemove();
      }
    },
  })),
);

function upsertInterface(list: PaymentInterface[], item: PaymentInterface): PaymentInterface[] {
  const idx = list.findIndex((i) => i.id === item.id);
  if (idx >= 0) { const c = [...list]; c[idx] = item; return c; }
  return [item, ...list];
}

function enrichTxNames(txs: Transaction[], ifaces: PaymentInterface[]): Transaction[] {
  const byId = Object.fromEntries(ifaces.map((i) => [i.id, i.name]));
  return txs.map((t) => ({ ...t, interfaceName: byId[t.interfaceId] ?? (t.interfaceName || '—') }));
}
