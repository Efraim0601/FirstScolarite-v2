package com.firstpay.transaction.api.dto;

import com.firstpay.transaction.domain.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
    UUID id, UUID tenantId, UUID interfaceId, String externalRef, BigDecimal amount, String currency,
    String status, String type, String method, String payer, String reference, String phone,
    Instant createdAt, Instant processedAt
) {
    public static TransactionResponse from(Transaction t) {
        String payer = null, reference = null, phone = null;
        if (t.getMetadata() != null) {
            payer = (String) t.getMetadata().get("payer");
            reference = (String) t.getMetadata().get("reference");
            phone = (String) t.getMetadata().get("phone");
        }
        return new TransactionResponse(t.getId(), t.getTenantId(), t.getInterfaceId(), t.getExternalRef(),
            t.getAmount(), t.getCurrency(), t.getStatus(), t.getType(), t.getMethod(),
            payer, reference != null ? reference : t.getExternalRef(), phone,
            t.getCreatedAt(), t.getProcessedAt());
    }
}
