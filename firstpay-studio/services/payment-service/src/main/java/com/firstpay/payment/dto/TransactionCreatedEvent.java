package com.firstpay.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Contrat lu sur transactions.created (publié par transaction-service via outbox). */
public record TransactionCreatedEvent(
    UUID id, UUID tenantId, BigDecimal amount, String currency, String method,
    String phone, String orderId
) {}
