package com.firstpay.payment.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstpay.payment.dto.PendingPayment;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** File d'attente Redis des paiements agrégateur en cours de confirmation. */
@Repository
public class PendingPaymentStore {

    private static final String KEY_PREFIX = "agg:pending:";
    private static final String INDEX = "agg:pending:index";
    private static final Duration TTL = Duration.ofHours(48);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json;

    public PendingPaymentStore(ReactiveStringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public Mono<Void> save(PendingPayment p) {
        String payload = write(p);
        String id = p.firstpayId().toString();
        return redis.opsForValue().set(KEY_PREFIX + id, payload, TTL)
            .then(redis.opsForSet().add(INDEX, id))
            .then();
    }

    public Mono<Void> remove(UUID firstpayId) {
        String id = firstpayId.toString();
        return redis.delete(KEY_PREFIX + id)
            .then(redis.opsForSet().remove(INDEX, id))
            .then();
    }

    public Flux<PendingPayment> all() {
        return redis.opsForSet().members(INDEX)
            .flatMap(id -> redis.opsForValue().get(KEY_PREFIX + id)
                .flatMap(this::read)
                .switchIfEmpty(Mono.defer(() -> redis.opsForSet().remove(INDEX, id).then(Mono.empty()))));
    }

    private Mono<PendingPayment> read(String raw) {
        try {
            var node = json.readTree(raw);
            return Mono.just(new PendingPayment(
                java.util.UUID.fromString(node.get("firstpayId").asText()),
                java.util.UUID.fromString(node.get("tenantId").asText()),
                node.get("amount").decimalValue(),
                node.get("network").asText(),
                node.get("aggregatorTxId").asText(),
                node.get("orderId").asText(),
                Instant.parse(node.get("createdAt").asText())));
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    private String write(PendingPayment p) {
        try {
            return json.writeValueAsString(java.util.Map.of(
                "firstpayId", p.firstpayId().toString(),
                "tenantId", p.tenantId().toString(),
                "amount", p.amount(),
                "network", p.network(),
                "aggregatorTxId", p.aggregatorTxId(),
                "orderId", p.orderId(),
                "createdAt", p.createdAt().toString()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
