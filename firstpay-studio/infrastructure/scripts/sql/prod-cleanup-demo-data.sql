-- Supprime les données de démonstration insérées par V2/V4 (Flyway).
-- À exécuter UNE FOIS après le premier déploiement prod si les migrations seed ont tourné.
-- Ne supprime PAS le schéma ni les tables Flyway.

BEGIN;

-- Audit démo
DELETE FROM audit_log
WHERE actor_email IN (
  'jospinleunou@softtech.cm',
  'admin.banque@afrilandfirstbank.com',
  'caisse.bonanjo@afrilandfirstbank.com',
  'marie.ngono@softtech.cm'
);

-- Transactions démo (ids fixes V2)
DELETE FROM domain_events WHERE aggregate_id IN (
  'c1111111-1111-1111-1111-111111111001'::uuid,
  'c1111111-1111-1111-1111-111111111002'::uuid,
  'c1111111-1111-1111-1111-111111111003'::uuid
);
DELETE FROM outbox_events WHERE aggregate_id IN (
  'c1111111-1111-1111-1111-111111111001'::uuid,
  'c1111111-1111-1111-1111-111111111002'::uuid,
  'c1111111-1111-1111-1111-111111111003'::uuid
);
DELETE FROM transactions WHERE id IN (
  'c1111111-1111-1111-1111-111111111001'::uuid,
  'c1111111-1111-1111-1111-111111111002'::uuid,
  'c1111111-1111-1111-1111-111111111003'::uuid
);

-- Interfaces & champs démo
DELETE FROM interface_fields WHERE interface_id IN (
  'b1111111-1111-1111-1111-111111111101'::uuid,
  'b1111111-1111-1111-1111-111111111102'::uuid,
  'b1111111-1111-1111-1111-111111111103'::uuid
);
DELETE FROM payment_interfaces WHERE id IN (
  'b1111111-1111-1111-1111-111111111101'::uuid,
  'b1111111-1111-1111-1111-111111111102'::uuid,
  'b1111111-1111-1111-1111-111111111103'::uuid
);

-- Utilisateurs & paramètres démo
DELETE FROM partner_users WHERE tenant_id IN (
  '11111111-1111-1111-1111-111111111111'::uuid,
  '22222222-2222-2222-2222-222222222222'::uuid
);
DELETE FROM partner_settings WHERE tenant_id IN (
  '11111111-1111-1111-1111-111111111111'::uuid,
  '22222222-2222-2222-2222-222222222222'::uuid
);

-- Tenants démo (SOFT TECHNOLOGIES, ÉCOLE LES PALMIERS)
DELETE FROM tenants WHERE id IN (
  '11111111-1111-1111-1111-111111111111'::uuid,
  '22222222-2222-2222-2222-222222222222'::uuid
);

COMMIT;
