/** Clés API de démo (dev local avec TENANT_FALLBACK_ENABLED=true). */
export const DEMO_API_KEYS: Record<string, string> = {
  SOFT: 'demo-soft-key',
  EPAL: 'demo-epal-key',
};

export const DEMO_TENANT_IDS: Record<string, string> = {
  'SOFT TECHNOLOGIES': '11111111-1111-1111-1111-111111111111',
  'ÉCOLE LES PALMIERS': '22222222-2222-2222-2222-222222222222',
};

/** Raccourci démo uniquement — en prod, utiliser JWT ou clé API renvoyée à la création. */
export function demoApiKeyForPartner(partnerName?: string): string | null {
  if (partnerName === 'ÉCOLE LES PALMIERS') return DEMO_API_KEYS['EPAL'];
  if (partnerName === 'SOFT TECHNOLOGIES') return DEMO_API_KEYS['SOFT'];
  return null;
}

export function demoTenantIdForPartner(partnerName?: string): string | null {
  return DEMO_TENANT_IDS[partnerName ?? ''] ?? null;
}
