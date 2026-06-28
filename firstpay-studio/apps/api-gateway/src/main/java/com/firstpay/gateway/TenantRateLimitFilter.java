package com.firstpay.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Rate-limiting PAR TENANT, fenêtre glissante d'1 seconde côté Redis (compteur atomique
 * via Lua). La limite/seconde dérive de tenants.rate_limit_tpm (injecté par
 * {@link TenantExtractorFilter} dans X-Tenant-Rate-Limit). 429 au dépassement.
 * Doit être chaîné APRÈS le TenantExtractorFilter.
 */
@Component
public class TenantRateLimitFilter implements GatewayFilter {

    private static final RedisScript<Long> INCR_WINDOW = RedisScript.of("""
            local c = redis.call('INCR', KEYS[1])
            if tonumber(c) == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return c
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;

    public TenantRateLimitFilter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        String tpmHeader = exchange.getRequest().getHeaders().getFirst("X-Tenant-Rate-Limit");
        if (tenantId == null) return chain.filter(exchange);   // pas de tenant → laisser passer (route publique)

        int tpm = tpmHeader != null ? Integer.parseInt(tpmHeader) : 10000;
        long perSecond = Math.max(1, tpm / 60L);
        String key = "rl:" + tenantId;

        return redis.execute(INCR_WINDOW, List.of(key), List.of("1000"))
            .next()
            .defaultIfEmpty(0L)
            .flatMap(count -> count > perSecond
                ? reject(exchange, perSecond)
                : chain.filter(exchange));
    }

    private Mono<Void> reject(ServerWebExchange exchange, long perSecond) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(perSecond));
        return exchange.getResponse().setComplete();
    }
}
