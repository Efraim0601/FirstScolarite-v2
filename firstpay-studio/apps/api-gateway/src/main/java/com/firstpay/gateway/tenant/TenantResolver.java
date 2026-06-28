package com.firstpay.gateway.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Résout le tenant à partir de l'API-key. Chaîne de résolution :
 *   1) cache Redis (TTL 5 min) — "tenant:apikey:{hash}" -> "id|code|name|rateLimitTpm" ;
 *   2) partner-service (table {@code tenants}, source de vérité) via endpoint interne ;
 *   3) {@link TenantRegistry} in-memory en dernier recours (résilience si partner-service KO).
 */
@Service
public class TenantResolver {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final ReactiveStringRedisTemplate redis;
    private final TenantRegistry registry;
    private final WebClient partnerClient;

    public TenantResolver(ReactiveStringRedisTemplate redis, TenantRegistry registry,
                          WebClient.Builder webClientBuilder,
                          @Value("${firstpay.partner-service.url:http://partner-service:8080}") String partnerUrl) {
        this.redis = redis;
        this.registry = registry;
        this.partnerClient = webClientBuilder.baseUrl(partnerUrl).build();
    }

    public Mono<TenantInfo> resolve(String apiKey) {
        String hash = ApiKeyHasher.sha256(apiKey);
        String cacheKey = "tenant:apikey:" + hash;
        return redis.opsForValue().get(cacheKey)
            .map(TenantResolver::deserialize)
            .switchIfEmpty(Mono.defer(() -> resolveFromSource(apiKey, hash)
                .flatMap(t -> redis.opsForValue().set(cacheKey, serialize(t), TTL).thenReturn(t))));
    }

    /** partner-service en premier ; repli sur le registre in-memory si indisponible/inconnu. */
    private Mono<TenantInfo> resolveFromSource(String apiKey, String hash) {
        return partnerClient.get()
                .uri("/internal/v1/tenants/by-key-hash/{hash}", hash)
                .retrieve()
                .bodyToMono(TenantInfo.class)
                .onErrorResume(e -> Mono.empty())
                .switchIfEmpty(Mono.defer(() -> registry.findByApiKey(apiKey).map(Mono::just).orElseGet(Mono::empty)));
    }

    private static String serialize(TenantInfo t) {
        return String.join("|", t.id(), t.code(), t.name(), String.valueOf(t.rateLimitTpm()));
    }

    private static TenantInfo deserialize(String s) {
        String[] p = s.split("\\|", 4);
        return new TenantInfo(p[0], p[1], p[2], Integer.parseInt(p[3]));
    }
}
