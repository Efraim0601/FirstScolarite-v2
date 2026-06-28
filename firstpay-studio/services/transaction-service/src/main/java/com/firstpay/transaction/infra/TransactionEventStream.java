package com.firstpay.transaction.infra;

import com.firstpay.transaction.api.dto.TransactionEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Bus d'événements in-memory (multicast) qui alimente les flux SSE par tenant.
 * En production multi-instance, remplacer par Redis Pub/Sub ou un consumer Kafka
 * dédié (chaque instance s'abonne au topic transactions.created).
 */
@Component
public class TransactionEventStream {

    private final Sinks.Many<TransactionEvent> sink =
        Sinks.many().multicast().onBackpressureBuffer(1024, false);

    public void emit(TransactionEvent event) {
        sink.tryEmitNext(event);   // best-effort : on ne bloque jamais le chemin d'écriture
    }

    public Flux<TransactionEvent> flux() {
        return sink.asFlux();
    }
}
