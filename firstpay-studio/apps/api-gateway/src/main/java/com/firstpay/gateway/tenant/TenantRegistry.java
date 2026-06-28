package com.firstpay.gateway.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Repli in-memory des clés API de démo — actif uniquement si
 * {@code firstpay.tenant.fallback-enabled=true}. Source de vérité : partner-service.
 */
@Component
public class TenantRegistry {

    private final Map<String, TenantInfo> byKeyHash;

    public TenantRegistry(@Value("${firstpay.tenant.fallback-enabled:false}") boolean fallbackEnabled) {
        this.byKeyHash = fallbackEnabled ? Map.of(
            ApiKeyHasher.sha256("demo-soft-key"),
            new TenantInfo("11111111-1111-1111-1111-111111111111", "FSPAY_202605211633050082", "SOFT TECHNOLOGIES", 10000),
            ApiKeyHasher.sha256("demo-epal-key"),
            new TenantInfo("22222222-2222-2222-2222-222222222222", "FSPAY_202604130910470215", "ÉCOLE LES PALMIERS", 5000)
        ) : Collections.emptyMap();
    }

    public Optional<TenantInfo> findByApiKey(String apiKey) {
        return Optional.ofNullable(byKeyHash.get(ApiKeyHasher.sha256(apiKey)));
    }
}
