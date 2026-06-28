-- V6 : login unifié email + mot de passe (hash BCrypt) + paramètres SMTP de la plateforme.

-- Mot de passe (hash) pour le login. NULL = compte de démo (le backend accepte alors "demo").
ALTER TABLE partner_users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Paramètres plateforme (singleton id=1) : SMTP + URL de l'application.
CREATE TABLE IF NOT EXISTS platform_settings (
    id              INT PRIMARY KEY DEFAULT 1,
    smtp_host       VARCHAR(200),
    smtp_port       INT NOT NULL DEFAULT 587,
    smtp_username   VARCHAR(200),
    smtp_password   VARCHAR(500),
    smtp_from_email VARCHAR(200),
    smtp_from_name  VARCHAR(200) NOT NULL DEFAULT 'FirstPay — Afriland First Bank',
    smtp_use_tls    BOOLEAN NOT NULL DEFAULT TRUE,
    smtp_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    app_base_url    VARCHAR(300) NOT NULL DEFAULT 'http://localhost:14200',
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT platform_settings_singleton CHECK (id = 1)
);

INSERT INTO platform_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
