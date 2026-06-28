package com.firstpay.payment.orchestration;

import com.firstpay.payment.connector.PaymentConnector;
import com.firstpay.payment.connector.PaymentConnectorRouter;
import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import com.firstpay.payment.infra.PendingPaymentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Vérifie l'exactly-once effectif : un événement rejoué n'est pas débité deux fois. */
class PaymentOrchestratorTest {

    private ReactiveStringRedisTemplate redis;
    private ReactiveValueOperations<String, String> valueOps;
    private PaymentConnector orange;
    private PendingPaymentStore pendingStore;
    private PaymentOrchestrator orchestrator;

    private final TransactionCreatedEvent event = new TransactionCreatedEvent(
        UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("25000"), "XAF", "orange",
        "237600000000", "ORD-001");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        orange = mock(PaymentConnector.class);
        pendingStore = mock(PendingPaymentStore.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(orange.method()).thenReturn("orange");
        when(orange.charge(any())).thenReturn(Mono.just(PaymentResult.success(event)));
        when(pendingStore.save(any())).thenReturn(Mono.empty());
        orchestrator = new PaymentOrchestrator(new PaymentConnectorRouter(List.of(orange)), redis, pendingStore);
    }

    @Test
    void firstEvent_chargesConnector() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(orchestrator.process(event))
            .assertNext(r -> { assert r.isSuccess(); })
            .verifyComplete();
        verify(orange).charge(event);
    }

    @Test
    void replayedEvent_isSkipped_noCharge() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(false));

        StepVerifier.create(orchestrator.process(event)).verifyComplete();
        verify(orange, never()).charge(any());
    }

    @Test
    void unsupportedMethod_failsGracefully() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        var crypto = new TransactionCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "XAF", "crypto", "", "");

        StepVerifier.create(orchestrator.process(crypto))
            .assertNext(r -> { assert !r.isSuccess(); })
            .verifyComplete();
    }
}
