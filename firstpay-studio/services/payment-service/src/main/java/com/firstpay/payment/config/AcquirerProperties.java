package com.firstpay.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firstpay.payment.acquirer")
public record AcquirerProperties(
    boolean enabled,
    String baseUrl,
    String apiKey,
    String chargePath
) {
    public AcquirerProperties {
        if (chargePath == null || chargePath.isBlank()) chargePath = "/api/v1/charges";
    }

    public boolean isReady() {
        return enabled && baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
