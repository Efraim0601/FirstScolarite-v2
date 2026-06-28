-- Bootstrap minimal pour exploitation prod (sans données démo).
-- Exécuter APRÈS prod-cleanup-demo-data.sql (ou sur une base où V2/V4 n'ont pas tourné).
--
-- Générer le hash API-key :
--   echo -n 'VOTRE_CLE_SECRETE' | sha256sum | awk '{print $1}'
--
-- Remplacer les placeholders ci-dessous avant exécution.

BEGIN;

INSERT INTO tenants (id, code, name, status, config, api_key_hash, rate_limit_tpm)
VALUES (
  '00000000-0000-0000-0000-000000000001',
  'FSPAY_PLATFORM',
  'AFRILAND FIRST BANK — Plateforme',
  'ACTIVE',
  '{"shortCode":"BANK","sector":"Banque"}',
  'REMPLACER_PAR_SHA256_HEX_DE_LA_CLE_API_BANQUE',
  50000
)
ON CONFLICT (id) DO NOTHING;

-- Premier partenaire réel (exemple — adapter code/nom)
INSERT INTO tenants (id, code, name, status, config, api_key_hash, rate_limit_tpm)
VALUES (
  gen_random_uuid(),
  'FSPAY_202606270000000001',
  'PREMIER PARTENAIRE PILOTE',
  'ACTIVE',
  '{"shortCode":"PILOT","sector":"Éducation"}',
  'REMPLACER_PAR_SHA256_HEX_DE_LA_CLE_API_PARTENAIRE',
  10000
)
ON CONFLICT (code) DO NOTHING;

-- Administrateur partenaire (mot de passe géré par le futur IdP ; démo actuelle : "demo")
INSERT INTO partner_users (tenant_id, name, email, role, status)
SELECT id, 'Administrateur Pilote', 'admin@partenaire-pilote.cm', 'partner_admin', 'active'
FROM tenants WHERE code = 'FSPAY_202606270000000001'
ON CONFLICT (tenant_id, email) DO NOTHING;

INSERT INTO partner_settings (tenant_id, brand_color, notifications)
SELECT id, '#E53935', '{"email":true,"sms":false}'
FROM tenants WHERE code = 'FSPAY_202606270000000001'
ON CONFLICT (tenant_id) DO NOTHING;

COMMIT;
