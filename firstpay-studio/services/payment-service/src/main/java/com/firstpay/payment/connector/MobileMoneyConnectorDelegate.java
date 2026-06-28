package com.firstpay.payment.connector;

import com.firstpay.payment.connector.trustpayway.TrustPayWayClient;
import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import com.firstpay.payment.infra.AggregatorConfigProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Délègue MTN / Orange vers TrustPayWay (si activé) ou simulation locale. */
@Component
public class MobileMoneyConnectorDelegate {

    private final AggregatorConfigProvider config;
    private final TrustPayWayClient tpw;

    public MobileMoneyConnectorDelegate(AggregatorConfigProvider config, TrustPayWayClient tpw) {
        this.config = config;
        this.tpw = tpw;
    }

    public Mono<PaymentResult> charge(TransactionCreatedEvent event, String network) {
        return config.get().flatMap(cfg ->
            cfg.isReady() ? tpw.initiatePayment(event, network, cfg) : SimulatedConnector.simulate(event, network));
    }
}
