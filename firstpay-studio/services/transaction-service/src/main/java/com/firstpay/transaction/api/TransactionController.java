package com.firstpay.transaction.api;

import com.firstpay.transaction.api.dto.CreateTransactionRequest;
import com.firstpay.transaction.api.dto.TenantStats;
import com.firstpay.transaction.api.dto.TransactionEvent;
import com.firstpay.transaction.api.dto.TransactionResponse;
import com.firstpay.transaction.command.TransactionCommand;
import com.firstpay.transaction.command.TransactionCommandHandler;
import com.firstpay.transaction.infra.EventStore;
import com.firstpay.transaction.query.TransactionQueryHandler;
import java.math.BigDecimal;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * API REST réactive du domaine Transaction.
 *  - POST  : écriture asynchrone, 202 Accepted (le traitement suit via Kafka) ;
 *  - GET /{id} : lecture d'une transaction ;
 *  - GET /stream : flux SSE des événements du tenant (temps réel) ;
 *  - GET /live-stats : flux SSE des stats agrégées (1 s).
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionCommandHandler commandHandler;
    private final TransactionQueryHandler queryHandler;
    private final EventStore eventStore;

    public TransactionController(TransactionCommandHandler commandHandler, TransactionQueryHandler queryHandler,
                                EventStore eventStore) {
        this.commandHandler = commandHandler;
        this.queryHandler = queryHandler;
        this.eventStore = eventStore;
    }

    @PostMapping
    @RateLimiter(name = "transaction-api")
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "createFallback")
    public Mono<ResponseEntity<TransactionResponse>> create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest req) {

        return commandHandler.handle(new TransactionCommand.CreateTransaction(
                tenantId, req.externalRef(), req.amount(), req.currency(),
                req.type(), req.method(), idempotencyKey, req.metadata()))
            .map(tx -> ResponseEntity
                .accepted()
                .header("Location", "/api/v1/transactions/" + tx.getId())
                .body(TransactionResponse.from(tx)));
    }

    Mono<ResponseEntity<TransactionResponse>> createFallback(UUID tenantId, String key,
                                                             CreateTransactionRequest req, Throwable t) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }

    @GetMapping
    public Flux<TransactionResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "100") int limit) {
        return queryHandler.recentForTenant(tenantId, limit).map(TransactionResponse::from);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<TransactionResponse>> getById(@PathVariable UUID id) {
        return queryHandler.findById(id)
            .map(tx -> ResponseEntity.ok(TransactionResponse.from(tx)))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /** Traitement explicite d'une transaction PENDING (commande ProcessTransaction). */
    @PostMapping("/{id}/process")
    public Mono<ResponseEntity<TransactionResponse>> process(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return commandHandler.handle(new TransactionCommand.ProcessTransaction(id, tenantId))
            .map(tx -> ResponseEntity.ok(TransactionResponse.from(tx)))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /** Remboursement total ou partiel d'une transaction SUCCESS (commande RefundTransaction). */
    @PostMapping("/{id}/refund")
    public Mono<ResponseEntity<TransactionResponse>> refund(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id,
            @RequestParam(required = false) BigDecimal amount) {
        return commandHandler.handle(new TransactionCommand.RefundTransaction(id, tenantId, amount))
            .map(tx -> ResponseEntity.ok(TransactionResponse.from(tx)))
            .onErrorResume(IllegalArgumentException.class, e -> Mono.just(ResponseEntity.notFound().build()))
            .onErrorResume(IllegalStateException.class, e -> Mono.just(ResponseEntity.unprocessableEntity().build()));
    }

    /** Historique des événements de domaine (event store) — audit / traçabilité. */
    @GetMapping("/{id}/events")
    public Flux<EventStore.StoredEvent> eventHistory(@PathVariable UUID id) {
        return eventStore.loadStream(id);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TransactionEvent>> stream(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return queryHandler.streamForTenant(tenantId)
            .map(e -> ServerSentEvent.<TransactionEvent>builder()
                .id(e.id().toString())
                .event(e.type())
                .data(e)
                .build());
    }

    @GetMapping(value = "/live-stats", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TenantStats>> liveStats(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return Flux.interval(Duration.ofSeconds(1))
            .concatMap(tick -> queryHandler.realtimeStats(tenantId))
            .map(stats -> ServerSentEvent.<TenantStats>builder().event("stats").data(stats).build());
    }
}
