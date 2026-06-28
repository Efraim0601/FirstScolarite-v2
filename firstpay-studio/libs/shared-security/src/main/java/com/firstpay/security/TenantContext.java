package com.firstpay.security;

import java.util.UUID;

/**
 * Contexte tenant propagé par requête (multi-tenant). En WebFlux, à porter via le
 * Reactor Context plutôt qu'un ThreadLocal. Squelette partagé entre services.
 */
public record TenantContext(UUID tenantId, String role, java.util.Set<String> scopes) {

    public static final String CTX_KEY = "firstpay.tenant";

    public boolean hasScope(String scope) {
        return scopes.contains("*") || scopes.contains(scope);
    }
}
