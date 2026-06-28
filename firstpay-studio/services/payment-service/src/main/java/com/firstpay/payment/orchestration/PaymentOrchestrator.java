package com.firstpay.payment.orchestration;

import com.firstpay.payment.connector.PaymentConnector;
import com.firstpay.payment.connector.PaymentConnectorRouter;
import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.PendingPayment;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import com.firstpay.payment.infra.PendingPaymentStore;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Orchestration d'un encaissement. Idempotence Redis {@code payment:processed:{txId}}.
 * Les paiements agrégateur en statut PENDING sont enfilés pour réconciliation asynchrone.
 */
@Service
public class PaymentOrchestrator {

    private static final Duration DEDUP_TTL = Duration.ofHours(1);

    private final PaymentConnectorRouter router;
    private final ReactiveStringRedisTemplate redis;
    private final PendingPaymentStore pendingStore;

    public PaymentOrchestrator(PaymentConnectorRouter router, ReactiveStringRedisTemplate redis,
                               PendingPaymentStore pendingStore) {
        this.router = router;
        this.redis = redis;
        this.pendingStore = pendingStore;
    }

    public Mono<PaymentResult> process(TransactionCreatedEvent event) {
        return redis.opsForValue()
            .setIfAbsent("payment:processed:" + event.id(), "1", DEDUP_TTL)
            .flatMap(firstTime -> Boolean.TRUE.equals(firstTime)
                ? charge(event)
                : Mono.empty());
    }

    private Mono<PaymentResult> charge(TransactionCreatedEvent event) {
        PaymentConnector connector = router.forMethod(event.method());
        if (connector == null) {
            return Mono.just(PaymentResult.failed(event, "Moyen de paiement non supporté : " + event.method()));
        }
        return connector.charge(event)
            .flatMap(result -> result.isPending()
                ? enqueuePending(event, result).thenReturn(result)
                : Mono.just(result))
            .onErrorResume(err -> Mono.just(PaymentResult.failed(event, "Connecteur indisponible : " + err.getMessage())));
    }

    private Mono<Void> enqueuePending(TransactionCreatedEvent event, PaymentResult result) {
        PendingPayment p = new PendingPayment(
            event.id(), event.tenantId(), event.amount(), event.method(),
            result.aggregatorTransactionId(),
            event.orderId() != null ? event.orderId() : event.id().toString(),
            Instant.now());
        return pendingStore.save(p);
    }
}
