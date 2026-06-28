package com.firstpay.transaction.command;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Commandes CQRS du domaine Transaction (sealed). */
public sealed interface TransactionCommand {

    record CreateTransaction(
        UUID tenantId,
        String externalRef,
        BigDecimal amount,
        String currency,
        String type,
        String method,
        String idempotencyKey,
        Map<String, Object> metadata
    ) implements TransactionCommand {}

    record ProcessTransaction(UUID transactionId, UUID tenantId) implements TransactionCommand {}

    record RefundTransaction(UUID transactionId, UUID tenantId, BigDecimal amount) implements TransactionCommand {}
}
