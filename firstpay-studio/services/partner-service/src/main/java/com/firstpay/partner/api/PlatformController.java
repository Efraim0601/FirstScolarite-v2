package com.firstpay.partner.api;

import com.firstpay.partner.api.dto.Dtos.PlatformSettingsDto;
import com.firstpay.partner.infra.EmailService;
import com.firstpay.partner.infra.PlatformStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Paramètres plateforme (config SMTP) — réservés à l'administrateur banque. Monté sous
 * /api/v1/settings/platform (routé vers partner-service via /api/v1/settings/**, sans
 * modifier la gateway). Le rôle est lu dans X-User-Role injecté par la gateway.
 */
@RestController
@RequestMapping("/api/v1/settings/platform")
public class PlatformController {

    private final PlatformStore platform;
    private final EmailService email;

    public PlatformController(PlatformStore platform, EmailService email) {
        this.platform = platform;
        this.email = email;
    }

    @GetMapping
    public Mono<PlatformSettingsDto> get(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireBankAdmin(role);
        return platform.getMasked();
    }

    @PutMapping
    public Mono<PlatformSettingsDto> save(@RequestHeader(value = "X-User-Role", required = false) String role,
                                          @RequestBody PlatformSettingsDto body) {
        requireBankAdmin(role);
        return platform.save(body);
    }

    @PostMapping("/test")
    public Mono<Map<String, Object>> test(@RequestHeader(value = "X-User-Role", required = false) String role,
                                          @RequestBody Map<String, String> body) {
        requireBankAdmin(role);
        String to = body.getOrDefault("to", "");
        return email.sendTest(to).map(ok -> Map.of("sent", ok));
    }

    private void requireBankAdmin(String role) {
        if (!"bank_admin".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé à l'administrateur banque");
        }
    }
}
