package com.firstpay.transaction.infra;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Event store (CQRS / Event Sourcing). Append-only sur la table partitionnée
 * {@code domain_events}. L'écriture se fait DANS la même transaction SQL
 * ({@code @Transactional} du handler) que l'écriture d'état + l'outbox : l'état,
 * l'événement de domaine et l'événement d'intégration sont atomiques.
 *
 * <p>La table {@code transactions} reste la projection « hot » servie en lecture ;
 * {@code domain_events} est la source de vérité append-only (audit, rejeu, reconstruction).
 */
@Component
public class EventStore {

    private final DatabaseClient db;

    public EventStore(DatabaseClient db) { this.db = db; }

    /** Ajoute un événement de domaine (version 1 par défaut). */
    public Mono<Void> append(UUID aggregateId, UUID tenantId, String eventType, String payloadJson) {
        return append(aggregateId, tenantId, eventType, 1, payloadJson);
    }

    public Mono<Void> append(UUID aggregateId, UUID tenantId, String eventType, int version, String payloadJson) {
        return db.sql("""
                INSERT INTO domain_events
                  (aggregate_id, aggregate_type, event_type, event_version, tenant_id, payload, occurred_at)
                VALUES (:aggId, 'Transaction', :type, :version, :tenant, :payload::jsonb, :occurredAt)
                """)
            .bind("aggId", aggregateId)
            .bind("type", eventType)
            .bind("version", version)
            .bind("tenant", tenantId)
            .bind("payload", payloadJson)
            .bind("occurredAt", Instant.now())
            .then();
    }

    /** Relit le flux d'événements d'un agrégat (reconstruction / audit), ordre chronologique. */
    public Flux<StoredEvent> loadStream(UUID aggregateId) {
        return db.sql("""
                SELECT event_type, event_version, payload::text AS payload, occurred_at
                FROM domain_events
                WHERE aggregate_id = :id
                ORDER BY occurred_at ASC, id ASC
                """)
            .bind("id", aggregateId)
            .map((row) -> new StoredEvent(
                row.get("event_type", String.class),
                row.get("event_version", Integer.class),
                row.get("payload", String.class),
                row.get("occurred_at", Instant.class)))
            .all();
    }

    public record StoredEvent(String type, int version, String payload, Instant occurredAt) {}
}
