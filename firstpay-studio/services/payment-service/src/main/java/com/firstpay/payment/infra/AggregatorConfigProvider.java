package com.firstpay.payment.infra;

import com.firstpay.payment.dto.AggregatorConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/** Charge la config agrégateur depuis partner-service (cache 60 s). */
@Component
public class AggregatorConfigProvider {

    private final WebClient partner;
    private volatile AggregatorConfig cached = AggregatorConfig.disabled();
    private volatile Instant cachedAt = Instant.EPOCH;

    public AggregatorConfigProvider(@Qualifier("partner") WebClient partnerWebClient) {
        this.partner = partnerWebClient;
    }

    public Mono<AggregatorConfig> get() {
        if (cachedAt.plus(Duration.ofSeconds(60)).isAfter(Instant.now())) {
            return Mono.just(cached);
        }
        return partner.get()
            .uri("/internal/aggregator-config")
            .retrieve()
            .bodyToMono(AggregatorConfig.class)
            .doOnNext(c -> { cached = c; cachedAt = Instant.now(); })
            .onErrorReturn(cached);
    }
}
