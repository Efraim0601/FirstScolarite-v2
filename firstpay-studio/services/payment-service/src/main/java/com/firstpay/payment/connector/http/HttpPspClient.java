package com.firstpay.payment.connector.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.firstpay.payment.config.AcquirerProperties;
import com.firstpay.payment.config.RtgsProperties;
import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Connecteur HTTP générique pour acquéreur carte et RTGS/virement.
 * Remplace la simulation lorsque {@code firstpay.payment.acquirer/rtgs.enabled=true}.
 */
@Service
public class HttpPspClient {

    private final WebClient http;
    private final AcquirerProperties acquirer;
    private final RtgsProperties rtgs;

    public HttpPspClient(WebClient.Builder builder, AcquirerProperties acquirer, RtgsProperties rtgs) {
        this.http = builder.build();
        this.acquirer = acquirer;
        this.rtgs = rtgs;
    }

    public Mono<PaymentResult> chargeCard(TransactionCreatedEvent event) {
        if (!acquirer.isReady()) {
            return Mono.error(new IllegalStateException("Connecteur acquéreur carte non configuré"));
        }
        return postCharge(acquirer.baseUrl(), acquirer.chargePath(), acquirer.apiKey(), event, "card");
    }

    public Mono<PaymentResult> transfer(TransactionCreatedEvent event) {
        if (!rtgs.isReady()) {
            return Mono.error(new IllegalStateException("Connecteur RTGS/virement non configuré"));
        }
        return postCharge(rtgs.baseUrl(), rtgs.transferPath(), rtgs.apiKey(), event, "transfer");
    }

    private Mono<PaymentResult> postCharge(String baseUrl, String path, String apiKey,
                                           TransactionCreatedEvent event, String method) {
        Map<String, Object> body = Map.of(
            "orderId", event.id().toString(),
            "amount", event.amount().stripTrailingZeros().toPlainString(),
            "currency", event.currency() != null ? event.currency() : "XAF",
            "method", method
        );
        return http.post()
            .uri(baseUrl + path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .flatMap(res -> {
                String status = res.path("status").asText("FAILED").toUpperCase();
                if ("SUCCESS".equals(status) || "SUCCESSFUL".equals(status)) {
                    return Mono.just(PaymentResult.success(event));
                }
                if ("PENDING".equals(status) || "INITIATED".equals(status)) {
                    String aggId = res.path("transactionId").asText(event.id().toString());
                    return Mono.just(PaymentResult.pending(event, aggId));
                }
                String reason = res.path("message").asText("Refus du connecteur " + method);
                return Mono.just(PaymentResult.failed(event, reason));
            })
            .onErrorResume(e -> Mono.just(PaymentResult.failed(event, method + " : " + e.getMessage())));
    }
}
