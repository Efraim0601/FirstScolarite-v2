package com.firstpay.transaction.infra;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Relais Outbox → Kafka (publication fiable, at-least-once). Lit périodiquement les
 * événements PENDING, les publie sur le topic correspondant (clé = aggregate_id pour
 * conserver l'ordre par transaction), puis les marque PROCESSED.
 * Un seul tick s'exécute à la fois (garde {@code running}).
 * En production multi-instance : verrou consultatif Postgres ou SELECT … FOR UPDATE
 * SKIP LOCKED, ou remplacer par Debezium CDC.
 */
@Component
public class OutboxPoller {

    private static final Map<String, String> TOPIC_BY_EVENT = Map.of(
        "TransactionCreated", "transactions.created");

    private final DatabaseClient db;
    private final KafkaSender<String, String> sender;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxPoller(DatabaseClient db, KafkaSender<String, String> sender) {
        this.db = db;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${firstpay.outbox.poll-ms:500}")
    public void tick() {
        if (!running.compareAndSet(false, true)) return;
        poll().doFinally(s -> running.set(false)).subscribe();
    }

    private Flux<Long> poll() {
        return db.sql("""
                SELECT id, aggregate_id, event_type, payload::text AS payload
                FROM outbox_events
                WHERE status = 'PENDING'
                ORDER BY created_at
                LIMIT 200
                """)
            .map((row) -> new Row(
                row.get("id", UUID.class),
                row.get("aggregate_id", UUID.class),
                row.get("event_type", String.class),
                row.get("payload", String.class)))
            .all()
            .concatMap(this::publishAndMark);
    }

    private Mono<Long> publishAndMark(Row r) {
        String topic = TOPIC_BY_EVENT.get(r.eventType());
        if (topic == null) return markProcessed(r.id());   // type inconnu → on draine
        SenderRecord<String, String, UUID> rec = SenderRecord.create(
            new ProducerRecord<>(topic, r.aggregateId().toString(), r.payload()), r.id());
        return sender.send(Mono.just(rec)).next().then(markProcessed(r.id()));
    }

    private Mono<Long> markProcessed(UUID id) {
        return db.sql("UPDATE outbox_events SET status = 'PROCESSED', processed_at = now() WHERE id = :id")
            .bind("id", id).fetch().rowsUpdated();
    }

    private record Row(UUID id, UUID aggregateId, String eventType, String payload) {}
}
