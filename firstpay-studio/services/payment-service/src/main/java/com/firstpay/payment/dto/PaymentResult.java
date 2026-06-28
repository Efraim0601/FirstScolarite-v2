package com.firstpay.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Résultat d'un encaissement, publié sur transactions.processed / transactions.failed. */
public record PaymentResult(
    UUID id, UUID tenantId, BigDecimal amount, String status, String reason, String aggregatorTransactionId
) {
    public static PaymentResult success(TransactionCreatedEvent e) {
        return new PaymentResult(e.id(), e.tenantId(), e.amount(), "SUCCESS", null, null);
    }

    public static PaymentResult failed(TransactionCreatedEvent e, String reason) {
        return new PaymentResult(e.id(), e.tenantId(), e.amount(), "FAILED", reason, null);
    }

    /** Paiement initié côté agrégateur (202) — statut final via webhook ou réconciliation. */
    public static PaymentResult pending(TransactionCreatedEvent e, String aggregatorTxId) {
        return new PaymentResult(e.id(), e.tenantId(), e.amount(), "PENDING", null, aggregatorTxId);
    }

    public boolean isSuccess() { return "SUCCESS".equals(status); }
    public boolean isPending() { return "PENDING".equals(status); }
}
