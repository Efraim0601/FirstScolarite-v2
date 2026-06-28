package com.firstpay.transaction.api.dto;

/** Stats temps réel par tenant servies au dashboard via SSE. */
public record TenantStats(long tpm, double successRate, long p99LatencyMs) {}
