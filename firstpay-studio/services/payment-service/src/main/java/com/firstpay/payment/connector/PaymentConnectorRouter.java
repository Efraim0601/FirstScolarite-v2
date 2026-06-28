package com.firstpay.payment.connector;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Aiguille un événement vers le bon connecteur selon le moyen de paiement. */
@Component
public class PaymentConnectorRouter {

    private final Map<String, PaymentConnector> byMethod;

    public PaymentConnectorRouter(List<PaymentConnector> connectors) {
        this.byMethod = connectors.stream()
            .collect(Collectors.toMap(PaymentConnector::method, Function.identity()));
    }

    /** Connecteur pour le moyen donné, ou {@code null} si non supporté. */
    public PaymentConnector forMethod(String method) {
        return method == null ? null : byMethod.get(method);
    }
}
