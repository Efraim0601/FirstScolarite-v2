/** Annuaire des partenaires côté banque — fidèle au prototype (PARTNERS_DB). */
export interface PartnerRecord {
  name: string; code: string; shortCode: string; sector: string;
  interfaces: number; active: boolean; tenantId?: string;
}

export const PARTNERS_DB: PartnerRecord[] = [
  { name: 'SOFT TECHNOLOGIES', code: 'FSPAY_202605211633050082', shortCode: 'SOFT', sector: 'Fintech', interfaces: 3, active: true },
  { name: 'ÉCOLE LES PALMIERS', code: 'FSPAY_202604130910470215', shortCode: 'EPAL', sector: 'Éducation', interfaces: 5, active: true },
  { name: 'INSTITUT NOTRE-DAME', code: 'FSPAY_202603100920310044', shortCode: 'IND', sector: 'Éducation', interfaces: 4, active: true },
  { name: 'TONTINE MBOA', code: 'FSPAY_202602010923310011', shortCode: 'MBOA', sector: 'Associatif', interfaces: 2, active: true },
  { name: 'ONG SANTÉ POUR TOUS', code: 'FSPAY_202601150811290002', shortCode: 'ONG', sector: 'Associatif', interfaces: 6, active: true },
  { name: 'PME RESTAURANT BANDJOUN', code: 'FSPAY_202605120815460091', shortCode: 'BNDJ', sector: 'Commerce', interfaces: 1, active: false },
];
