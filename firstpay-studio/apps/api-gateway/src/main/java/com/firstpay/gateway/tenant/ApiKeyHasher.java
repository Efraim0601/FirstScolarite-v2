package com.firstpay.gateway.tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hash SHA-256 (hex) de l'API-key, comparé à tenants.api_key_hash. */
public final class ApiKeyHasher {
    private ApiKeyHasher() {}

    public static String sha256(String apiKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(apiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
