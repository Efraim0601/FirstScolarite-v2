import { inject } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { PartnerApiService, ApiUserDto } from '../../core/api/partner-api.service';

export type PartnerRole = 'admin' | 'manager' | 'accountant' | 'viewer';

/** Mappe le rôle plateforme (partner_admin…) vers le rôle court de l'UI. */
const API_TO_UI_ROLE: Record<string, PartnerRole> = {
  partner_admin: 'admin', partner_manager: 'manager',
  partner_accountant: 'accountant', partner_viewer: 'viewer',
  admin: 'admin', manager: 'manager', accountant: 'accountant', viewer: 'viewer',
};
const UI_TO_API_ROLE: Record<PartnerRole, string> = {
  admin: 'partner_admin', manager: 'partner_manager',
  accountant: 'partner_accountant', viewer: 'partner_viewer',
};

export interface PartnerUser {
  id: string; name: string; email: string;
  role: PartnerRole; status: 'active' | 'pending'; lastSeen: string;
}

export const PARTNER_ROLES: Record<PartnerRole, { label: string; color: string; bg: string; border: string; desc: string }> = {
  admin:      { label: 'Administrateur', color: '#E53935', bg: 'var(--fp-red-50)', border: '#F5C8C6', desc: "Accès complet : crée, modifie, supprime, gère l'équipe et les paramètres." },
  manager:    { label: 'Gestionnaire', color: '#2563EB', bg: 'var(--blue-soft)', border: '#BFD4F8', desc: "Crée et modifie les interfaces, voit les transactions, n'invite pas d'utilisateurs." },
  accountant: { label: 'Comptable', color: '#1F9D55', bg: 'var(--green-soft)', border: '#C8E8D5', desc: 'Consulte et exporte les transactions, accède aux rapports comptables.' },
  viewer:     { label: 'Lecture seule', color: '#7C8493', bg: '#F2F4F7', border: 'var(--border-strong)', desc: 'Consulte les interfaces et transactions sans pouvoir modifier.' },
};

const SEED: PartnerUser[] = [
  { id: 'u-1', name: 'Jospin Leunou', email: 'jospinleunou@softtech.cm', role: 'admin', status: 'active', lastSeen: 'Il y a 2 heures' },
  { id: 'u-2', name: 'Marie Ngono', email: 'marie.ngono@softtech.cm', role: 'manager', status: 'active', lastSeen: 'Hier · 16:42' },
  { id: 'u-3', name: 'Daniel Essomba', email: 'd.essomba@softtech.cm', role: 'accountant', status: 'active', lastSeen: 'Il y a 3 jours' },
  { id: 'u-4', name: 'Sophie Mbarga', email: 's.mbarga@softtech.cm', role: 'viewer', status: 'active', lastSeen: 'Il y a 1 semaine' },
  { id: 'u-5', name: 'Ahmed Talla', email: 'a.talla@softtech.cm', role: 'manager', status: 'pending', lastSeen: '—' },
];

export const UsersStore = signalStore(
  { providedIn: 'root' },
  withState<{ users: PartnerUser[]; useApi: boolean }>({ users: SEED, useApi: false }),
  withMethods((store, api = inject(PartnerApiService)) => ({
    loadFromApi() {
      api.fetchUsers().subscribe((rows) => {
        if (rows && rows.length > 0) {
          patchState(store, { users: rows.map(fromApi), useApi: true });
        } else {
          patchState(store, { useApi: false });
        }
      });
    },
    upsert(u: PartnerUser) {
      const persist = () => {
        const arr = store.users();
        const idx = arr.findIndex((x) => x.id === u.id);
        if (idx >= 0) { const copy = [...arr]; copy[idx] = u; patchState(store, { users: copy }); }
        else patchState(store, { users: [{ ...u, id: 'u-' + Date.now(), lastSeen: '—' }, ...arr] });
      };
      if (store.useApi()) {
        api.saveUser(toApi(u)).subscribe((saved) => {
          if (saved) {
            const mapped = fromApi(saved);
            const arr = store.users();
            const idx = arr.findIndex((x) => x.email === mapped.email);
            if (idx >= 0) { const copy = [...arr]; copy[idx] = mapped; patchState(store, { users: copy }); }
            else patchState(store, { users: [mapped, ...arr] });
          } else { persist(); }
        });
      } else { persist(); }
    },
    remove(id: string) {
      const doRemove = () => patchState(store, { users: store.users().filter((u) => u.id !== id) });
      if (store.useApi()) { api.deleteUser(id).subscribe(() => doRemove()); } else { doRemove(); }
    },
  })),
);

function fromApi(d: ApiUserDto): PartnerUser {
  return {
    id: d.id, name: d.name, email: d.email,
    role: API_TO_UI_ROLE[d.role] ?? 'viewer',
    status: d.status === 'pending' ? 'pending' : 'active',
    lastSeen: '—',
  };
}

function toApi(u: PartnerUser): ApiUserDto {
  return {
    id: u.id && !u.id.startsWith('u-') ? u.id : '',
    name: u.name, email: u.email,
    role: UI_TO_API_ROLE[u.role], status: u.status,
  };
}
