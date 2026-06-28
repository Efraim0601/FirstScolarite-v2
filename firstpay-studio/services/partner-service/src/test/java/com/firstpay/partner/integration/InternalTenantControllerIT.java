package com.firstpay.partner.integration;

import com.firstpay.partner.api.InternalTenantController;
import com.firstpay.partner.infra.PartnerStore;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class InternalTenantControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("firstpay").withUsername("firstpay").withPassword("dev_password");

    static InternalTenantController controller;
    static UUID tenantId;

    @BeforeAll
    static void setup() {
        ConnectionFactory cf = ConnectionFactories.get(
            "r2dbc:postgresql://%s:%s@%s:%d/firstpay".formatted(
                POSTGRES.getUsername(), POSTGRES.getPassword(),
                POSTGRES.getHost(), POSTGRES.getFirstMappedPort()));
        DatabaseClient db = DatabaseClient.create(cf);

        db.sql("""
            CREATE TABLE tenants (
              id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL, name VARCHAR(200) NOT NULL,
              status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', config JSONB DEFAULT '{}',
              api_key_hash VARCHAR(64), rate_limit_tpm INTEGER NOT NULL DEFAULT 5000)
            """).fetch().rowsUpdated().block();

        tenantId = UUID.randomUUID();
        String apiKey = "fpk_live_integration_test";
        String hash = sha256(apiKey);

        db.sql("""
            INSERT INTO tenants (id, code, name, status, api_key_hash, rate_limit_tpm)
            VALUES (:id, 'FSPAY_IT', 'Integration Partner', 'ACTIVE', :hash, 8000)
            """)
            .bind("id", tenantId).bind("hash", hash)
            .fetch().rowsUpdated().block();

        PartnerStore store = new PartnerStore(db, new com.fasterxml.jackson.databind.ObjectMapper(),
            new com.firstpay.partner.infra.PasswordHasher());
        controller = new InternalTenantController(store);
    }

    @Test
    void byKeyHash_resolvesActiveTenant() {
        StepVerifier.create(controller.byKeyHash(sha256("fpk_live_integration_test")))
            .assertNext(res -> {
                assertTrue(res.getStatusCode().is2xxSuccessful());
                assertNotNull(res.getBody());
                assertEquals(tenantId.toString(), res.getBody().id());
                assertEquals("Integration Partner", res.getBody().name());
                assertEquals(8000, res.getBody().rateLimitTpm());
            })
            .verifyComplete();
    }

    @Test
    void byKeyHash_unknown_returns404() {
        StepVerifier.create(controller.byKeyHash(sha256("unknown-key")))
            .assertNext(res -> assertEquals(404, res.getStatusCode().value()))
            .verifyComplete();
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
