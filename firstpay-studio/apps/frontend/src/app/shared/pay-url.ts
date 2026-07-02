/**
 * Domaine public utilisé pour construire les liens de paiement.
 *
 * Dérivé du domaine courant de l'application (esign.afbdei.com en production,
 * localhost:14200 en développement, etc.) afin que les liens partagés pointent
 * toujours vers l'instance réellement déployée — et non un domaine codé en dur.
 */
export function payHost(): string {
  if (typeof window !== 'undefined' && window.location?.host) {
    return window.location.host;
  }
  return 'esign.afbdei.com';
}

/** Construit l'URL publique d'une interface de paiement : host/SHORTCODE/slug */
export function payUrl(shortCode: string, slug: string): string {
  return `${payHost()}/${shortCode}/${slug}`;
}
