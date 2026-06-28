-- Données démo pour le journal d'audit (Phase 6).
INSERT INTO audit_log (actor_email, actor_role, action, target_type, target_id, tenant_id, detail, occurred_at) VALUES
('jospinleunou@softtech.cm', 'partner_admin', 'publish', 'interface', 'Frais de scolarité 2025-2026', '11111111-1111-1111-1111-111111111111', '{"partner":"SOFT TECHNOLOGIES"}', now() - interval '5 minutes'),
('admin.banque@afrilandfirstbank.com', 'bank_admin', 'impersonate_start', 'partner', 'ÉCOLE LES PALMIERS', '22222222-2222-2222-2222-222222222222', '{"partner":"ÉCOLE LES PALMIERS"}', now() - interval '2 hours'),
('caisse.bonanjo@afrilandfirstbank.com', 'bank_cashier', 'login', 'session', 'Agence Bonanjo', NULL, '{"partner":"—"}', now() - interval '22 minutes'),
('marie.ngono@softtech.cm', 'partner_manager', 'delete', 'interface', 'Test campagne santé Q1', '11111111-1111-1111-1111-111111111111', '{"partner":"SOFT TECHNOLOGIES"}', now() - interval '1 hour');
