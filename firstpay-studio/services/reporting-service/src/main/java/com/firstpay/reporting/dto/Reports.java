package com.firstpay.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** DTOs des rapports (read side). */
public final class Reports {
    private Reports() {}

    public record SummaryStats(long totalTx, long successCount, long failedCount,
                               BigDecimal amountTotal, double successRate) {}

    public record DailyStat(LocalDate day, long txCount, long successCount,
                            long failedCount, BigDecimal amountTotal) {}

    public record TxView(UUID id, BigDecimal amount, String currency, String method,
                         String status, Instant createdAt, Instant processedAt) {}

    public record LiveStats(long tpm, double successRate, long p99LatencyMs) {}
}
