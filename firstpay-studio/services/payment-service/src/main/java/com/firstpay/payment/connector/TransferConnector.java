package com.firstpay.payment.connector;

import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Connecteur virement. Circuit breaker dédié « transfer-connector » (isole les pannes par moyen). */
@Component
public class TransferConnector implements PaymentConnector {
    @Override public String method() { return "transfer"; }

    @Override
    @CircuitBreaker(name = "transfer-connector")
    public Mono<PaymentResult> charge(TransactionCreatedEvent event) {
        return SimulatedConnector.simulate(event, "transfer");
    }
}
