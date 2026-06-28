-- V7 : configuration de l'agrégateur de paiement (TrustPayWay) dans les paramètres plateforme.
-- app_id + secret saisis dans l'UI admin ; le secret n'est jamais renvoyé en clair en lecture.

ALTER TABLE platform_settings ADD COLUMN IF NOT EXISTS agg_enabled  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE platform_settings ADD COLUMN IF NOT EXISTS agg_base_url VARCHAR(300) NOT NULL DEFAULT 'https://mobilewallet.trustpayway.com';
ALTER TABLE platform_settings ADD COLUMN IF NOT EXISTS agg_app_id   VARCHAR(200);
ALTER TABLE platform_settings ADD COLUMN IF NOT EXISTS agg_secret   VARCHAR(500);
