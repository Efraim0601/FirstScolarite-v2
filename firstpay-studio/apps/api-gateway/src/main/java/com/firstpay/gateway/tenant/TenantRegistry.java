package com.firstpay.gateway.tenant;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Source de vérité des tenants par hash d'API-key. Implémentation in-memory de démo —
 * À REMPLACER en Phase 8 par un appel à partner-service (table tenants) avec cache Redis.
 * Les clés de démo ci-dessous correspondent au partenaire SOFT TECHNOLOGIES.
 */
@Component
public class TenantRegistry {

    // apiKeyHash (SHA-256 hex) -> TenantInfo. Clé de démo : "demo-soft-key".
    private final Map<String, TenantInfo> byKeyHash = Map.of(
        ApiKeyHasher.sha256("demo-soft-key"),
        new TenantInfo("11111111-1111-1111-1111-111111111111", "FSPAY_202605211633050082", "SOFT TECHNOLOGIES", 10000),
        ApiKeyHasher.sha256("demo-epal-key"),
        new TenantInfo("22222222-2222-2222-2222-222222222222", "FSPAY_202604130910470215", "ÉCOLE LES PALMIERS", 5000)
    );

    public Optional<TenantInfo> findByApiKey(String apiKey) {
        return Optional.ofNullable(byKeyHash.get(ApiKeyHasher.sha256(apiKey)));
    }
}
