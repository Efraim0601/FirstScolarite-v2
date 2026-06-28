package com.firstpay.payment.connector;

import com.firstpay.payment.connector.http.HttpPspClient;
import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Connecteur virement RTGS (HTTP configurable ou simulation dev). */
@Component
public class TransferConnector implements PaymentConnector {
    private final HttpPspClient psp;
    private final boolean simulationEnabled;

    public TransferConnector(HttpPspClient psp,
                             @Value("${firstpay.payment.simulation-enabled:false}") boolean simulationEnabled) {
        this.psp = psp;
        this.simulationEnabled = simulationEnabled;
    }

    @Override public String method() { return "transfer"; }

    @Override
    @CircuitBreaker(name = "transfer-connector")
    public Mono<PaymentResult> charge(TransactionCreatedEvent event) {
        return psp.transfer(event)
            .onErrorResume(IllegalStateException.class, e ->
                simulationEnabled
                    ? SimulatedConnector.simulate(event, "transfer")
                    : Mono.just(PaymentResult.failed(event, e.getMessage())));
    }
}
