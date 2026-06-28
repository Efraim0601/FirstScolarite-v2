package com.firstpay.transaction.api;

import com.firstpay.transaction.command.TransactionCommand;
import com.firstpay.transaction.command.TransactionCommandHandler;
import com.firstpay.transaction.domain.Transaction;
import com.firstpay.transaction.infra.EventStore;
import com.firstpay.transaction.query.TransactionQueryHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = TransactionController.class)
class TransactionControllerTest {

    @Autowired WebTestClient client;
    @MockBean TransactionCommandHandler commandHandler;
    @MockBean TransactionQueryHandler queryHandler;
    @MockBean EventStore eventStore;

    private final UUID tenant = UUID.randomUUID();

    @Test
    void post_returns202_withLocation() {
        Transaction tx = Transaction.create(tenant, "REF-1", new BigDecimal("25000"), "XAF", "PAYMENT", "orange", "idem-1");
        when(commandHandler.handle(any(TransactionCommand.CreateTransaction.class))).thenReturn(Mono.just(tx));

        client.post().uri("/api/v1/transactions")
            .header("X-Tenant-Id", tenant.toString())
            .header("X-Idempotency-Key", "idem-1")
            .bodyValue(Map.of("externalRef", "REF-1", "amount", 25000, "type", "PAYMENT", "method", "orange"))
            .exchange()
            .expectStatus().isAccepted()
            .expectHeader().exists("Location")
            .expectBody().jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void getById_notFound_returns404() {
        UUID id = UUID.randomUUID();
        when(queryHandler.findById(id)).thenReturn(Mono.empty());

        client.get().uri("/api/v1/transactions/{id}", id)
            .exchange()
            .expectStatus().isNotFound();
    }
}
