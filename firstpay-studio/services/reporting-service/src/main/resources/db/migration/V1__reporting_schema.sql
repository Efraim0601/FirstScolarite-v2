-- V1__reporting_schema.sql — read models (projections CQRS) du reporting-service.
-- Alimentés par Kafka, servis depuis des read replicas → isolés du chemin d'écriture.

-- Projection dénormalisée de chaque transaction (vue lecture).
CREATE TABLE IF NOT EXISTS transaction_projection (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    amount       NUMERIC(19,4) NOT NULL,
    currency     CHAR(3) NOT NULL DEFAULT 'XAF',
    method       VARCHAR(20),
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_proj_tenant_created ON transaction_projection (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_proj_tenant_status  ON transaction_projection (tenant_id, status);

-- Agrégat quotidien par tenant/devise (alimente les graphes & exports comptables).
CREATE TABLE IF NOT EXISTS tx_stats_daily (
    tenant_id     UUID NOT NULL,
    day           DATE NOT NULL,
    currency      CHAR(3) NOT NULL DEFAULT 'XAF',
    tx_count      BIGINT NOT NULL DEFAULT 0,
    success_count BIGINT NOT NULL DEFAULT 0,
    failed_count  BIGINT NOT NULL DEFAULT 0,
    amount_total  NUMERIC(19,4) NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, day, currency)
);
