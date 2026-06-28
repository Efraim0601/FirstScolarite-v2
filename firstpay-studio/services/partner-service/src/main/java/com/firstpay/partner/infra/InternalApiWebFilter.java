package com.firstpay.partner.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Protège les endpoints {@code /internal/**} par un token partagé (réseau Docker / mesh). */
@Component
@Order(-100)
public class InternalApiWebFilter implements WebFilter {

    private final String expectedToken;

    public InternalApiWebFilter(@Value("${firstpay.internal-token:}") String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/internal/")) {
            return chain.filter(exchange);
        }
        if (expectedToken.isBlank()) {
            return chain.filter(exchange);
        }
        String token = exchange.getRequest().getHeaders().getFirst("X-Internal-Token");
        if (!expectedToken.equals(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
