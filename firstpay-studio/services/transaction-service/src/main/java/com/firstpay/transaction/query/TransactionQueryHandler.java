package com.firstpay.transaction.query;

import com.firstpay.transaction.api.dto.TenantStats;
import com.firstpay.transaction.api.dto.TransactionEvent;
import com.firstpay.transaction.domain.Transaction;
import com.firstpay.transaction.domain.TransactionStore;
import com.firstpay.transaction.infra.TransactionEventStream;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Côté lecture (CQRS) : lookups + flux SSE temps réel filtrés par tenant. */
@Service
public class TransactionQueryHandler {

    private final TransactionStore store;
    private final TransactionEventStream events;

    public TransactionQueryHandler(TransactionStore store, TransactionEventStream events) {
        this.store = store;
        this.events = events;
    }

    public Mono<Transaction> findById(UUID id) {
        return store.findById(id);
    }

    public Flux<Transaction> recentForTenant(UUID tenantId, int limit) {
        return store.recentForTenant(tenantId, limit);
    }

    /** Flux des événements appartenant à ce tenant (isolation multi-tenant). */
    public Flux<TransactionEvent> streamForTenant(UUID tenantId) {
        return events.flux().filter(e -> tenantId.equals(e.tenantId()));
    }

    public Mono<TenantStats> realtimeStats(UUID tenantId) {
        return store.realtimeStats(tenantId);
    }
}
