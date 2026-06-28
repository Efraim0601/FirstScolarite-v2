package com.firstpay.transaction.api.dto;

import com.firstpay.transaction.domain.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Événement poussé en temps réel (SSE /stream) à la création/changement de statut. */
public record TransactionEvent(
    UUID id, UUID tenantId, String type, String status, BigDecimal amount, Instant at
) {
    public static TransactionEvent created(Transaction t) {
        return new TransactionEvent(t.getId(), t.getTenantId(), "TransactionCreated",
            t.getStatus(), t.getAmount(), t.getCreatedAt());
    }
}
