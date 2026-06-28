package com.firstpay.payment.dto;

/** Config TrustPayWay lue depuis partner-service (/internal/aggregator-config). */
public record AggregatorConfig(
    boolean enabled, String baseUrl, String appId, String secret, String webhookBaseUrl
) {
    public static AggregatorConfig disabled() {
        return new AggregatorConfig(false, "", "", "", "");
    }

    public boolean isReady() {
        return enabled && baseUrl != null && !baseUrl.isBlank()
            && appId != null && !appId.isBlank()
            && secret != null && !secret.isBlank();
    }
}
