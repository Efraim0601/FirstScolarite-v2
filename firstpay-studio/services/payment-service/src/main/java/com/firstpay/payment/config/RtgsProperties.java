package com.firstpay.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firstpay.payment.rtgs")
public record RtgsProperties(
    boolean enabled,
    String baseUrl,
    String apiKey,
    String transferPath
) {
    public RtgsProperties {
        if (transferPath == null || transferPath.isBlank()) transferPath = "/api/v1/transfers";
    }

    public boolean isReady() {
        return enabled && baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
