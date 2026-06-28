package com.firstpay.payment.connector;

import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Connecteur MTN MoMo via TrustPayWay (ou simulation si agrégateur désactivé). */
@Component
public class MtnMomoConnector implements PaymentConnector {

    private final MobileMoneyConnectorDelegate delegate;

    public MtnMomoConnector(MobileMoneyConnectorDelegate delegate) { this.delegate = delegate; }

    @Override public String method() { return "mtn"; }

    @Override
    @CircuitBreaker(name = "mtn-connector")
    public Mono<PaymentResult> charge(TransactionCreatedEvent event) {
        return delegate.charge(event, "mtn");
    }
}
