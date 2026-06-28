package com.firstpay.payment.connector.http;

import com.firstpay.payment.config.AcquirerProperties;
import com.firstpay.payment.config.RtgsProperties;
import com.firstpay.payment.dto.TransactionCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HttpPspClientTest {

    @Test
    void chargeCard_failsWhenAcquirerNotConfigured() {
        HttpPspClient client = new HttpPspClient(WebClient.builder(),
            new AcquirerProperties(false, null, null, null),
            new RtgsProperties(false, null, null, null));

        TransactionCreatedEvent event = new TransactionCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000"), "XAF", "card", null, null);

        StepVerifier.create(client.chargeCard(event))
            .expectErrorSatisfies(e -> assertTrue(e.getMessage().contains("non configuré")))
            .verify();
    }
}
