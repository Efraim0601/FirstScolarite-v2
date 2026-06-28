package com.firstpay.transaction.integration;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Valide la connectivité Kafka réelle (Testcontainers) utilisée par l'outbox poller
 * et les consommateurs transaction-service.
 */
@Testcontainers(disabledWithoutDocker = true)
class TransactionKafkaIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static KafkaSender<String, String> sender;

    @BeforeAll
    static void setup() {
        SenderOptions<String, String> opts = SenderOptions.<String, String>create(
            java.util.Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(),
                "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer", "org.apache.kafka.common.serialization.StringSerializer"
            ));
        sender = KafkaSender.create(opts);
    }

    @AfterAll
    static void teardown() {
        if (sender != null) sender.close();
    }

    @Test
    void publishesAndConsumes_onRealKafka() {
        String topic = "firstpay-it-" + UUID.randomUUID();
        String key = "tx-" + UUID.randomUUID();
        String payload = "{\"event\":\"TransactionCreated\"}";

        sender.send(reactor.core.publisher.Mono.just(
            new ProducerRecord<>(topic, key, payload)))
            .blockLast(Duration.ofSeconds(10));

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            var records = consumer.poll(Duration.ofSeconds(15));
            assertFalse(records.isEmpty(), "Le message publié doit être consommable sur Kafka");
            assertEquals(payload, records.iterator().next().value());
            assertEquals(key, records.iterator().next().key());
        }
    }
}
