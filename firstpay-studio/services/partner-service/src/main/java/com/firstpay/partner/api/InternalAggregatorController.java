package com.firstpay.partner.api;

import com.firstpay.partner.api.dto.Dtos.AggregatorConfigDto;
import com.firstpay.partner.infra.PlatformStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Config agrégateur TrustPayWay pour le payment-service (réseau interne Docker, pas exposé
 * via la gateway publique).
 */
@RestController
@RequestMapping("/internal/aggregator-config")
public class InternalAggregatorController {

    private final PlatformStore platform;

    public InternalAggregatorController(PlatformStore platform) { this.platform = platform; }

    @GetMapping
    public Mono<AggregatorConfigDto> get() {
        return platform.getAggregatorConfig();
    }
}
