package com.firstpay.payment.connector;

import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import reactor.core.publisher.Mono;

/** Connecteur vers un moyen de paiement (Orange Money, MTN MoMo, carte, virement). */
public interface PaymentConnector {
    /** Identifiant du moyen géré : orange / mtn / card / transfer. */
    String method();

    /** Débite le payeur. Peut échouer (timeout connecteur, fonds insuffisants…). */
    Mono<PaymentResult> charge(TransactionCreatedEvent event);
}
