-- Renseigne les hash d'API-key (SHA-256 hex) des tenants de démo, pour que l'API Gateway
-- résolve le tenant via partner-service (table tenants) au lieu d'un registre in-memory.
--   demo-soft-key -> SOFT TECHNOLOGIES
--   demo-epal-key -> ÉCOLE LES PALMIERS

UPDATE tenants
SET api_key_hash = 'aa559916e3a7d074d5f9eb5606b1eae220586c98089aa3f5c1a198d4fd73c996'
WHERE id = '11111111-1111-1111-1111-111111111111';

UPDATE tenants
SET api_key_hash = '243138d20cffad04d8b27f418008f12e126d8bdbbd6a68bb8e00fd4c035f9e23'
WHERE id = '22222222-2222-2222-2222-222222222222';

CREATE INDEX IF NOT EXISTS idx_tenants_api_key_hash ON tenants (api_key_hash);
