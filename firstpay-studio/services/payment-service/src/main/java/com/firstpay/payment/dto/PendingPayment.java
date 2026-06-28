package com.firstpay.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Paiement mobile en attente de confirmation (INITIATED / PENDING côté TrustPayWay). */
public record PendingPayment(
    UUID firstpayId, UUID tenantId, BigDecimal amount, String network,
    String aggregatorTxId, String orderId, Instant createdAt
) {}
