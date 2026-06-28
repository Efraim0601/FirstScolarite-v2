-- V5 : tenant "banque" + comptes du personnel Afriland (bank_admin, bank_cashier) afin
-- qu'ils obtiennent un vrai JWT à la connexion (le login JOINt partner_users + tenants).
-- Le bank_admin pourra créer des partenaires via POST /api/v1/partners.

INSERT INTO tenants (id, code, name, status, config, rate_limit_tpm) VALUES
    ('00000000-0000-0000-0000-000000000001', 'AFB_BANK', 'Afriland First Bank', 'ACTIVE',
     '{"shortCode":"AFB","sector":"Banque"}', 6000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO partner_users (tenant_id, name, email, role, status) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Cécile Mvondo', 'admin.banque@afrilandfirstbank.com', 'bank_admin', 'active'),
    ('00000000-0000-0000-0000-000000000001', 'Sylvie Atangana', 'caisse.bonanjo@afrilandfirstbank.com', 'bank_cashier', 'active')
ON CONFLICT (tenant_id, email) DO NOTHING;
