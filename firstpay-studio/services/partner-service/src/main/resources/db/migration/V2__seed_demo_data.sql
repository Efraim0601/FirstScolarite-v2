-- Données démo alignées sur TenantRegistry (SOFT + EPAL) et le prototype frontend.

INSERT INTO tenants (id, code, name, status, config, rate_limit_tpm) VALUES
    ('11111111-1111-1111-1111-111111111111', 'FSPAY_202605211633050082', 'SOFT TECHNOLOGIES', 'ACTIVE',
     '{"shortCode":"SOFT","sector":"Fintech"}', 10000),
    ('22222222-2222-2222-2222-222222222222', 'FSPAY_202604130910470215', 'ÉCOLE LES PALMIERS', 'ACTIVE',
     '{"shortCode":"EPAL","sector":"Éducation"}', 5000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO payment_interfaces (
    id, tenant_id, name, description, sector, slug, custom_slug, status,
    amount_type, fixed_amount, currency, presets, multi_select,
    ref_type, ref_label, ref_format, methods, qr_codes, tx_count, collected
) VALUES
(
    'b1111111-1111-1111-1111-111111111101',
    '11111111-1111-1111-1111-111111111111',
    'Frais de scolarité 2025-2026',
    'Paiement des frais de scolarité pour l''année académique 2025-2026.',
    'Éducation', 'frais-scolarite-2025-2026', 'frais-scolarite-2025-2026', 'actif',
    'preset', NULL, 'XAF',
    '[{"id":1,"label":"Inscription","amount":"25000"},{"id":2,"label":"Tranche 1","amount":"150000"},{"id":3,"label":"Tranche 2","amount":"150000"},{"id":4,"label":"Solde","amount":"75000"}]',
    TRUE, 'custom', 'Matricule élève', 'alphanum',
    '{"orange":true,"mtn":true,"card":true,"transfer":true}',
    '{"orange":true,"mtn":true,"card":true,"transfer":false}',
    1284, 47620000
),
(
    'b1111111-1111-1111-1111-111111111102',
    '11111111-1111-1111-1111-111111111111',
    'Cotisation tontine mensuelle',
    'Collecte mensuelle pour la tontine du groupe Mboa.',
    'Fintech', 'tontine-mboa', 'tontine-mboa', 'actif',
    'fixed', 25000, 'XAF', '[]', FALSE,
    'auto', NULL, 'any',
    '{"orange":true,"mtn":true,"card":false,"transfer":false}',
    '{"orange":true,"mtn":true,"card":false,"transfer":false}',
    342, 8550000
),
(
    'b1111111-1111-1111-1111-111111111103',
    '11111111-1111-1111-1111-111111111111',
    'Don campagne santé', '', 'ONG / Associatif', 'don-sante', 'don-sante', 'brouillon',
    'free', NULL, 'XAF', '[]', FALSE,
    'auto', NULL, 'any',
    '{"orange":true,"mtn":false,"card":true,"transfer":false}',
    '{"orange":true,"mtn":false,"card":true,"transfer":false}',
    0, 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO interface_fields (interface_id, type, label, required, options, position) VALUES
('b1111111-1111-1111-1111-111111111101', 'text', 'Matricule élève', TRUE, NULL, 0),
('b1111111-1111-1111-1111-111111111101', 'select', 'Classe', TRUE, '["6e","5e","4e","3e","2nde","1ère","Terminale"]', 1),
('b1111111-1111-1111-1111-111111111101', 'text', 'Nom du parent', FALSE, NULL, 2),
('b1111111-1111-1111-1111-111111111102', 'text', 'Numéro adhérent', TRUE, NULL, 0);

INSERT INTO partner_users (tenant_id, name, email, role, status) VALUES
('11111111-1111-1111-1111-111111111111', 'Jospin Leunou', 'jospinleunou@softtech.cm', 'partner_admin', 'active'),
('11111111-1111-1111-1111-111111111111', 'Marie Ngono', 'marie.ngono@softtech.cm', 'partner_manager', 'active'),
('11111111-1111-1111-1111-111111111111', 'Daniel Essomba', 'd.essomba@softtech.cm', 'partner_accountant', 'active')
ON CONFLICT (tenant_id, email) DO NOTHING;

INSERT INTO partner_settings (tenant_id, brand_color, notifications) VALUES
('11111111-1111-1111-1111-111111111111', '#E53935', '{"email":true,"sms":false}')
ON CONFLICT (tenant_id) DO NOTHING;
