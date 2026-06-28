package com.firstpay.payment.connector;

import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Connecteur simulé (démo) : latence réaliste + ~5 % d'échecs déterministes.
 * Utilisé lorsque l'agrégateur TrustPayWay n'est pas activé.
 */
public final class SimulatedConnector {

    private SimulatedConnector() {}

    public static Mono<PaymentResult> simulate(TransactionCreatedEvent e, String method) {
        long latency = 20 + Math.floorMod(e.id().getLeastSignificantBits(), 60);
        boolean fail = Math.floorMod(e.id().hashCode(), 20) == 0;
        return Mono.delay(Duration.ofMillis(latency))
            .map(tick -> fail
                ? PaymentResult.failed(e, "Refus du connecteur " + method)
                : PaymentResult.success(e));
    }
}
