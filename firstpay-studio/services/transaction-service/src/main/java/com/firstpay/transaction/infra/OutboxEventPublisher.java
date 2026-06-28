package com.firstpay.transaction.infra;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbox pattern : on insère l'événement dans `outbox_events` DANS la même transaction
 * SQL que l'écriture métier. Un poller (ou Debezium CDC) relit la table et publie sur
 * Kafka → at-least-once fiable sans 2-phase commit.
 */
@Component
public class OutboxEventPublisher {

    private final DatabaseClient db;

    public OutboxEventPublisher(DatabaseClient db) { this.db = db; }

    public Mono<Void> publish(UUID aggregateId, UUID tenantId, String eventType, String payloadJson) {
        return db.sql("""
                INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, tenant_id, payload)
                VALUES ('Transaction', :aggId, :type, :tenant, :payload::jsonb)
                """)
            .bind("aggId", aggregateId)
            .bind("type", eventType)
            .bind("tenant", tenantId)
            .bind("payload", payloadJson)
            .then();
    }
}
