-- V1__partner_schema.sql — interfaces de paiement, équipe, paramètres (multi-tenant)

-- Interfaces de paiement (pages de collecte construites par les partenaires)
CREATE TABLE payment_interfaces (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id     UUID NOT NULL,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    sector        VARCHAR(100),
    slug          VARCHAR(80) NOT NULL,
    custom_slug   VARCHAR(80),
    status        VARCHAR(20) NOT NULL DEFAULT 'brouillon',  -- brouillon / actif
    amount_type   VARCHAR(20) NOT NULL DEFAULT 'fixed',       -- fixed / preset / free
    fixed_amount  NUMERIC(19,4),
    min_amount    NUMERIC(19,4),
    max_amount    NUMERIC(19,4),
    currency      CHAR(3) NOT NULL DEFAULT 'XAF',
    presets       JSONB NOT NULL DEFAULT '[]',
    multi_select  BOOLEAN NOT NULL DEFAULT FALSE,
    ref_type      VARCHAR(20) NOT NULL DEFAULT 'auto',        -- auto / custom
    ref_label     VARCHAR(120),
    ref_format    VARCHAR(20) NOT NULL DEFAULT 'any',
    methods       JSONB NOT NULL DEFAULT '{"orange":true,"mtn":true,"card":false,"transfer":false}',
    qr_codes      JSONB NOT NULL DEFAULT '{}',
    tx_count      BIGINT NOT NULL DEFAULT 0,
    collected     NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, slug)
);
CREATE INDEX idx_iface_tenant_status ON payment_interfaces (tenant_id, status);

-- Champs personnalisés d'une interface
CREATE TABLE interface_fields (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    interface_id  UUID NOT NULL REFERENCES payment_interfaces(id) ON DELETE CASCADE,
    type          VARCHAR(20) NOT NULL,        -- text / select
    label         VARCHAR(120) NOT NULL,
    required      BOOLEAN NOT NULL DEFAULT FALSE,
    options       JSONB,                       -- pour type=select
    position      INTEGER NOT NULL DEFAULT 0
);

-- Utilisateurs partenaire & rôles
CREATE TABLE partner_users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id     UUID NOT NULL,
    name          VARCHAR(160) NOT NULL,
    email         VARCHAR(200) NOT NULL,
    role          VARCHAR(40) NOT NULL,        -- partner_admin/manager/accountant/viewer
    status        VARCHAR(20) NOT NULL DEFAULT 'active',  -- active / pending
    last_seen     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, email)
);

-- Paramètres / marque par tenant
CREATE TABLE partner_settings (
    tenant_id     UUID PRIMARY KEY,
    logo_url      VARCHAR(500),
    logo_name     VARCHAR(200),
    brand_color   VARCHAR(9) NOT NULL DEFAULT '#E53935',
    notifications JSONB NOT NULL DEFAULT '{}',
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Journal d'audit (append-only, côté banque)
CREATE TABLE audit_log (
    id            BIGSERIAL PRIMARY KEY,
    actor_email   VARCHAR(200) NOT NULL,
    actor_role    VARCHAR(40) NOT NULL,
    action        VARCHAR(120) NOT NULL,
    target_type   VARCHAR(80),
    target_id     VARCHAR(120),
    tenant_id     UUID,
    detail        JSONB,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_occurred ON audit_log (occurred_at DESC);
