package com.firstpay.gateway.tenant;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TenantRegistryTest {

    private final TenantRegistry registry = new TenantRegistry();

    @Test
    void knownApiKey_resolvesTenant() {
        Optional<TenantInfo> t = registry.findByApiKey("demo-soft-key");
        assertTrue(t.isPresent());
        assertEquals("SOFT TECHNOLOGIES", t.get().name());
        assertEquals(10000, t.get().rateLimitTpm());
    }

    @Test
    void unknownApiKey_isEmpty() {
        assertTrue(registry.findByApiKey("nope").isEmpty());
    }

    @Test
    void hash_isStableAndHex() {
        String h = ApiKeyHasher.sha256("demo-soft-key");
        assertEquals(64, h.length());
        assertEquals(h, ApiKeyHasher.sha256("demo-soft-key"));
        assertTrue(h.matches("[0-9a-f]{64}"));
    }
}
