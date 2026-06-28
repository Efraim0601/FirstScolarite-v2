-- V1__init_schema.sql — schéma haute performance FirstPay Studio

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_partman";

-- Tenants (partenaires)
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(50) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    config          JSONB NOT NULL DEFAULT '{}',
    api_key_hash    VARCHAR(255),
    rate_limit_tpm  INTEGER DEFAULT 10000,        -- transactions / minute
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Transactions (partitionnée par RANGE(created_at), mensuel)
CREATE TABLE transactions (
    id              UUID NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    interface_id    UUID,
    external_ref    VARCHAR(100) NOT NULL,
    amount          NUMERIC(19,4) NOT NULL,
    currency        CHAR(3) NOT NULL DEFAULT 'XAF',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/SUCCESS/FAILED/REFUNDED
    type            VARCHAR(30) NOT NULL,
    method          VARCHAR(20),                              -- orange/mtn/card/transfer
    metadata        JSONB,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Partitions mensuelles automatiques (pg_partman, 3 mois d'avance)
SELECT partman.create_parent(
    p_parent_table => 'public.transactions',
    p_control      => 'created_at',
    p_type         => 'range',
    p_interval     => '1 month',
    p_premake      => 3
);

-- Index critiques
CREATE INDEX idx_tx_tenant_status
    ON transactions (tenant_id, status, created_at DESC);
CREATE UNIQUE INDEX idx_tx_idempotency
    ON transactions (tenant_id, idempotency_key, created_at);

-- Event Store (CQRS / Event Sourcing), partitionné
CREATE TABLE domain_events (
    id              BIGSERIAL,
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_version   INTEGER NOT NULL DEFAULT 1,
    tenant_id       UUID NOT NULL,
    payload         JSONB NOT NULL,
    metadata        JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

SELECT partman.create_parent(
    p_parent_table => 'public.domain_events',
    p_control      => 'occurred_at',
    p_type         => 'range',
    p_interval     => '1 month',
    p_premake      => 3
);

-- Outbox pattern (fiabilité de publication Kafka)
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    tenant_id       UUID NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending
    ON outbox_events (status, created_at) WHERE status = 'PENDING';
