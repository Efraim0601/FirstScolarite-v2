package com.firstpay.payment.api;

import com.firstpay.payment.orchestration.PaymentReconciliationService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Webhooks de confirmation asynchrone des connecteurs. TrustPayWay notifie via
 * {@code notifUrl} ; le statut final peut aussi être obtenu par réconciliation
 * ({@code GET /api/{network}/get-status/{transaction_id}}).
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final PaymentReconciliationService reconciliation;

    public WebhookController(PaymentReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @PostMapping("/{connector}")
    public Mono<Map<String, Object>> callback(@PathVariable String connector,
                                              @RequestBody Map<String, Object> body) {
        if ("trustpayway".equalsIgnoreCase(connector) || body.containsKey("transactionId")) {
            return handleTrustPayWay(body, connector);
        }
        return Mono.just(Map.of("received", true, "connector", connector));
    }

    @PostMapping("/trustpayway/{network}")
    public Mono<Map<String, Object>> trustPayWay(@PathVariable String network,
                                                 @RequestBody Map<String, Object> body) {
        return handleTrustPayWay(body, network);
    }

    private Mono<Map<String, Object>> handleTrustPayWay(Map<String, Object> body, String network) {
        String orderId = str(body.get("orderId"));
        String status = str(body.get("status"));
        String description = str(body.get("description"));
        UUID firstpayId;
        try {
            firstpayId = UUID.fromString(orderId);
        } catch (Exception e) {
            return Mono.just(Map.of("received", false, "error", "orderId invalide"));
        }
        return reconciliation.handleWebhook(network, firstpayId, status, description)
            .thenReturn(Map.of("received", true, "network", network, "orderId", orderId, "status", status));
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
