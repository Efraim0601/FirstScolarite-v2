package com.firstpay.payment.connector;

import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Connecteur carte bancaire. Circuit breaker dédié « card-connector » (isole les pannes par moyen). */
@Component
public class CardConnector implements PaymentConnector {
    @Override public String method() { return "card"; }

    @Override
    @CircuitBreaker(name = "card-connector")
    public Mono<PaymentResult> charge(TransactionCreatedEvent event) {
        return SimulatedConnector.simulate(event, "card");
    }
}
