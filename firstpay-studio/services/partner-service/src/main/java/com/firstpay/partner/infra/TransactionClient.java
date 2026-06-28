package com.firstpay.partner.infra;

import com.firstpay.partner.api.dto.Dtos.PublicTxStatusDto;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Appel server-to-server vers transaction-service (hors gateway : on parle directement au
 * service interne et on injecte nous-mêmes X-Tenant-Id / X-Idempotency-Key, exactement comme
 * le ferait le gateway pour une session authentifiée).
 */
@Component
public class TransactionClient {

    private final WebClient http;

    public TransactionClient(WebClient transactionWebClient) {
        this.http = transactionWebClient;
    }

    /** Crée une transaction PENDING pour le compte du tenant résolu. Renvoie son id. */
    public Mono<String> createTransaction(UUID tenantId, String idempotencyKey, String externalRef,
                                          BigDecimal amount, String currency, String method,
                                          Map<String, Object> metadata) {
        Map<String, Object> body = Map.of(
            "externalRef", externalRef,
            "amount", amount,
            "currency", currency,
            "type", "payment",
            "method", method,
            "metadata", metadata
        );
        return http.post().uri("/api/v1/transactions")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(TxResponse.class)
            .map(TxResponse::id);
    }

    /** Lit le statut public d'une transaction (polling). Mono.empty() si introuvable. */
    public Mono<PublicTxStatusDto> status(String transactionId) {
        return http.get().uri("/api/v1/transactions/{id}", transactionId)
            .retrieve()
            .bodyToMono(TxResponse.class)
            .map(t -> new PublicTxStatusDto(
                t.id(), t.reference() != null ? t.reference() : t.externalRef(),
                t.status(), t.amount() != null ? t.amount().stripTrailingZeros().toPlainString() : "",
                t.currency(), t.method()))
            .onErrorResume(e -> Mono.empty());
    }

    /** Sous-ensemble de la réponse transaction-service qui nous intéresse. */
    private record TxResponse(String id, String externalRef, BigDecimal amount, String currency,
                              String status, String method, String reference) {}
}
