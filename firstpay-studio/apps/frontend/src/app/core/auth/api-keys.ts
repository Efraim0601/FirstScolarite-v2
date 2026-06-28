/** Clés API démo — alignées sur TenantRegistry (gateway). */
export const API_KEYS: Record<string, string> = {
  SOFT: 'demo-soft-key',
  EPAL: 'demo-epal-key',
  BANK: 'demo-soft-key',
};

export const TENANT_IDS: Record<string, string> = {
  'SOFT TECHNOLOGIES': '11111111-1111-1111-1111-111111111111',
  'ÉCOLE LES PALMIERS': '22222222-2222-2222-2222-222222222222',
};

export function apiKeyForPartner(partnerName?: string): string {
  if (partnerName === 'ÉCOLE LES PALMIERS') return API_KEYS['EPAL'];
  return API_KEYS['SOFT'];
}

export function tenantIdForPartner(partnerName?: string): string {
  return TENANT_IDS[partnerName ?? 'SOFT TECHNOLOGIES'] ?? TENANT_IDS['SOFT TECHNOLOGIES'];
}
