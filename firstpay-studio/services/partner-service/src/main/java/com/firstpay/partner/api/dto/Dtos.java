package com.firstpay.partner.api.dto;

import java.util.List;
import java.util.Map;

/** DTOs REST du partner-service. */
public final class Dtos {
    private Dtos() {}

    public record InterfaceFieldDto(String id, String type, String label, boolean required, List<String> options) {}

    public record PresetDto(long id, String label, String amount) {}

    public record InterfaceDto(
        String id, String tenantId, String name, String description, String sector,
        String slug, String customSlug, String status, long tx, long collected,
        String amountType, String fixedAmount, String minAmount, String maxAmount, String currency,
        List<PresetDto> presets, boolean multiSelect, String refType, String refLabel, String refFormat,
        List<InterfaceFieldDto> customFields, Map<String, Boolean> methods, Map<String, Boolean> qrCodes
    ) {}

    public record SaveInterfaceRequest(
        String id, String name, String description, String sector, String customSlug, String status,
        String amountType, String fixedAmount, String minAmount, String maxAmount, String currency,
        List<PresetDto> presets, boolean multiSelect, String refType, String refLabel, String refFormat,
        List<InterfaceFieldDto> customFields, Map<String, Boolean> methods, Map<String, Boolean> qrCodes
    ) {}

    public record PartnerDto(String id, String code, String shortCode, String name, String sector, String status, int interfaceCount) {}

    /**
     * Création d'un partenaire par l'administrateur banque.
     * settlementAccount = numéro du compte qui recevra les fonds collectés ; accountHolder = titulaire.
     */
    public record CreatePartnerRequest(String name, String sector, String adminName, String adminEmail,
                                       String settlementAccount, String accountHolder, String settlementBank) {}

    /** Réponse de création : le partenaire + l'API-key + les identifiants temporaires (affichés une fois). */
    public record CreatePartnerResponse(PartnerDto partner, String apiKey, String adminEmail, String tempPassword) {}

    /** Configuration SMTP + agrégateur TrustPayWay (secrets masqués en lecture). */
    public record PlatformSettingsDto(
        String smtpHost, int smtpPort, String smtpUsername, String smtpPassword,
        String smtpFromEmail, String smtpFromName, boolean smtpUseTls, boolean smtpEnabled,
        String appBaseUrl, boolean passwordSet,
        boolean aggEnabled, String aggBaseUrl, String aggAppId, String aggSecret, boolean aggSecretSet) {}

    /** Config agrégateur (usage interne payment-service, secret inclus). */
    public record AggregatorConfigDto(
        boolean enabled, String baseUrl, String appId, String secret, String webhookBaseUrl) {}

    public record UserDto(String id, String name, String email, String role, String status) {}

    public record SettingsDto(String tenantId, String logoUrl, String logoName, String brandColor, Map<String, Object> notifications) {}
}
