-- Tenants démo (alignés sur TenantRegistry gateway) + transactions exemple.

INSERT INTO tenants (id, code, name, status, config, rate_limit_tpm) VALUES
    ('11111111-1111-1111-1111-111111111111', 'FSPAY_202605211633050082', 'SOFT TECHNOLOGIES', 'ACTIVE',
     '{"shortCode":"SOFT","sector":"Fintech"}', 10000),
    ('22222222-2222-2222-2222-222222222222', 'FSPAY_202604130910470215', 'ÉCOLE LES PALMIERS', 'ACTIVE',
     '{"shortCode":"EPAL","sector":"Éducation"}', 5000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO transactions (
    id, tenant_id, interface_id, external_ref, amount, currency, status, type, method,
    idempotency_key, created_at, processed_at, metadata
) VALUES
(
    'c1111111-1111-1111-1111-111111111001',
    '11111111-1111-1111-1111-111111111111',
    'b1111111-1111-1111-1111-111111111101',
    'EXT-001', 150000, 'XAF', 'SUCCESS', 'PAYMENT', 'orange',
    'seed-idem-001', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours',
    '{"payer":"Jean Mbarga","phone":"+237 691 23 45 67","reference":"FP-2025-00142"}'
),
(
    'c1111111-1111-1111-1111-111111111002',
    '11111111-1111-1111-1111-111111111111',
    'b1111111-1111-1111-1111-111111111101',
    'EXT-002', 25000, 'XAF', 'SUCCESS', 'PAYMENT', 'mtn',
    'seed-idem-002', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour',
    '{"payer":"Marie Nguema","phone":"+237 677 88 99 00","reference":"FP-2025-00143"}'
),
(
    'c1111111-1111-1111-1111-111111111003',
    '11111111-1111-1111-1111-111111111111',
    'b1111111-1111-1111-1111-111111111102',
    'EXT-003', 25000, 'XAF', 'PENDING', 'PAYMENT', 'orange',
    'seed-idem-003', NOW() - INTERVAL '30 minutes', NULL,
    '{"payer":"Paul Atangana","phone":"+237 698 76 54 32","reference":"FP-2025-00144"}'
)
ON CONFLICT DO NOTHING;
