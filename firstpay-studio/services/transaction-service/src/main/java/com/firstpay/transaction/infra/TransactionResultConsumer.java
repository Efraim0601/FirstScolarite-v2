package com.firstpay.transaction.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstpay.transaction.api.dto.TransactionEvent;
import com.firstpay.transaction.domain.TransactionStore;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Consomme transactions.processed / transactions.failed (résultats émis par
 * payment-service), met à jour le statut final en base et pousse l'événement SSE.
 * Traitement parallèle PAR partition, ack manuel après succès.
 */
@Service
public class TransactionResultConsumer {

    private final ReceiverOptions<String, String> options;
    private final TransactionStore store;
    private final EventStore eventStore;
    private final TransactionEventStream events;
    private final ObjectMapper json;

    public TransactionResultConsumer(ReceiverOptions<String, String> resultReceiverOptions,
                                     TransactionStore store, EventStore eventStore,
                                     TransactionEventStream events, ObjectMapper json) {
        this.options = resultReceiverOptions;
        this.store = store;
        this.eventStore = eventStore;
        this.events = events;
        this.json = json;
    }

    @PostConstruct
    public void start() {
        KafkaReceiver.create(options)
            .receive()
            .groupBy(ReceiverRecord::partition)
            .flatMap(partition -> partition.concatMap(this::handle))
            .subscribe();
    }

    private Mono<Void> handle(ReceiverRecord<String, String> rec) {
        String status = rec.topic().endsWith("failed") ? "FAILED" : "SUCCESS";
        return Mono.fromCallable(() -> json.readTree(rec.value()))
            .flatMap(node -> {
                UUID id = UUID.fromString(node.get("id").asText());
                UUID tenantId = node.hasNonNull("tenantId") ? UUID.fromString(node.get("tenantId").asText()) : null;
                BigDecimal amount = node.hasNonNull("amount") ? node.get("amount").decimalValue() : null;
                String eventType = "Transaction" + (status.equals("SUCCESS") ? "Processed" : "Failed");
                String payload = "{\"id\":\"%s\",\"tenantId\":\"%s\",\"status\":\"%s\"}".formatted(id, tenantId, status);
                return store.updateStatus(id, status)
                    .then(tenantId != null ? eventStore.append(id, tenantId, eventType, payload) : Mono.empty())
                    .doOnSuccess(n -> events.emit(new TransactionEvent(
                        id, tenantId, eventType, status, amount, Instant.now())))
                    .then();
            })
            .onErrorResume(e -> Mono.empty())          // message illisible → on n'interrompt pas le flux
            .doFinally(s -> rec.receiverOffset().acknowledge());
    }
}
