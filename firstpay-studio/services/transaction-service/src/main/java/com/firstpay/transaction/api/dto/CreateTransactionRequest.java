package com.firstpay.transaction.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record CreateTransactionRequest(
    @NotBlank String externalRef,
    @NotNull @DecimalMin("0.0001") BigDecimal amount,
    String currency,
    @NotBlank String type,
    String method,
    Map<String, Object> metadata
) {}
