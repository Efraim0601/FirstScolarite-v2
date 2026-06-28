package com.firstpay.gateway.tenant;

/** Vue minimale d'un tenant nécessaire au routage et au rate-limiting. */
public record TenantInfo(String id, String code, String name, int rateLimitTpm) {}
