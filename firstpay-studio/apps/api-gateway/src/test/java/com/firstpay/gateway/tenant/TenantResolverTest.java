package com.firstpay.gateway.tenant;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@WireMockTest
class TenantResolverTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    static ReactiveStringRedisTemplate redis;
    static LettuceConnectionFactory redisFactory;

    @BeforeAll
    static void startRedis() {
        redisFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
    }

    @AfterAll
    static void stopRedis() {
        if (redisFactory != null) redisFactory.destroy();
    }

    @BeforeEach
    void clearCache() {
        redis.keys("tenant:apikey:*").flatMap(k -> redis.delete(k)).blockLast();
    }

    @Test
    void resolvesTenant_fromPartnerService(WireMockRuntimeInfo wm) {
        String hash = ApiKeyHasher.sha256("fpk_live_test");
        stubFor(get("/internal/v1/tenants/by-key-hash/" + hash)
            .willReturn(okJson("""
                {"id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","code":"FSPAY_TEST","name":"Test Partner","rateLimitTpm":5000}
                """)));

        TenantResolver resolver = new TenantResolver(
            redis, new TenantRegistry(false), WebClient.builder(),
            "http://localhost:" + wm.getHttpPort(), "secret-token", false);

        StepVerifier.create(resolver.resolve("fpk_live_test"))
            .assertNext(t -> {
                assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", t.id());
                assertEquals("Test Partner", t.name());
                assertEquals(5000, t.rateLimitTpm());
            })
            .verifyComplete();
    }

    @Test
    void usesInMemoryFallback_whenEnabled_andPartnerMissing(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathMatching("/internal/v1/tenants/by-key-hash/.*"))
            .willReturn(notFound()));

        TenantResolver resolver = new TenantResolver(
            redis, new TenantRegistry(true), WebClient.builder(),
            "http://localhost:" + wm.getHttpPort(), "", true);

        StepVerifier.create(resolver.resolve("demo-soft-key"))
            .assertNext(t -> assertEquals("SOFT TECHNOLOGIES", t.name()))
            .verifyComplete();
    }

    @Test
    void empty_whenPartnerMissing_andFallbackDisabled(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathMatching("/internal/v1/tenants/by-key-hash/.*"))
            .willReturn(notFound()));

        TenantResolver resolver = new TenantResolver(
            redis, new TenantRegistry(false), WebClient.builder(),
            "http://localhost:" + wm.getHttpPort(), "", false);

        StepVerifier.create(resolver.resolve("unknown-key"))
            .verifyComplete();
    }
}
