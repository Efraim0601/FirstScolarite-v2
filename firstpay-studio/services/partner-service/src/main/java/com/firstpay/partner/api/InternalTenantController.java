package com.firstpay.partner.api;

import com.firstpay.partner.infra.PartnerStore;
import com.firstpay.partner.infra.PartnerStore.TenantResolution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Endpoint INTERNE (non exposé au public via la gateway) : permet à l'API Gateway de
 * résoudre un tenant à partir du hash de son API-key, contre la table {@code tenants}
 * (source de vérité), remplaçant le registre in-memory historique.
 */
@RestController
@RequestMapping("/internal/v1/tenants")
public class InternalTenantController {

    private final PartnerStore partners;

    public InternalTenantController(PartnerStore partners) {
        this.partners = partners;
    }

    @GetMapping("/by-key-hash/{hash}")
    public Mono<ResponseEntity<TenantResolution>> byKeyHash(@PathVariable String hash) {
        return partners.resolveByApiKeyHash(hash)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
