package com.firstpay.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway FirstPay (Spring Cloud Gateway, réactif). Point d'entrée des partenaires :
 * auth API-key + multi-tenant, rate-limiting par tenant, retry/circuit breaker, CORS.
 * Routage défini dans {@link GatewayConfig}.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
