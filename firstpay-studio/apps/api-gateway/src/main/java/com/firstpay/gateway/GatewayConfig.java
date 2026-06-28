package com.firstpay.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routage vers les microservices. Chaque route applique, dans l'ordre :
 *   1) TenantExtractorFilter (auth API-key + injection X-Tenant-*) ;
 *   2) TenantRateLimitFilter (quota Redis par tenant) ;
 *   3) retry + circuit breaker (résilience).
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                               TenantExtractorFilter tenantFilter,
                               TenantRateLimitFilter rateLimitFilter) {
        return builder.routes()
            // Route publique : login du portail (émet le JWT) — pas de filtre tenant/API-key.
            .route("partner-auth", r -> r.path("/api/v1/auth/**")
                .uri("lb://partner-service"))
            .route("payment-webhooks", r -> r.path("/webhooks/**")
                .uri("lb://payment-service"))
            // Route publique : page payeur (lien/QR partagé) — résolution shortCode/slug,
            // sans filtre tenant/API-key (le client final n'est pas authentifié).
            .route("public-checkout", r -> r.path("/public/**")
                .uri("lb://partner-service"))
            .route("transaction-service", r -> r.path("/api/v1/transactions/**")
                .filters(f -> f
                    .filter(tenantFilter)
                    .filter(rateLimitFilter)
                    .retry(c -> c.setRetries(3))
                    .circuitBreaker(c -> c.setName("transaction-cb").setFallbackUri("forward:/fallback")))
                .uri("lb://transaction-service"))
            .route("partner-service", r -> r.path(
                    "/api/v1/interfaces/**", "/api/v1/partners/**",
                    "/api/v1/users/**", "/api/v1/settings/**", "/api/v1/audit/**")
                .filters(f -> f.filter(tenantFilter).filter(rateLimitFilter))
                .uri("lb://partner-service"))
            .route("reporting-service", r -> r.path("/api/v1/reports/**")
                .filters(f -> f.filter(tenantFilter).filter(rateLimitFilter))
                .uri("lb://reporting-service"))
            .build();
    }
}
