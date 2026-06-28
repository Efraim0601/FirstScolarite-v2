package com.firstpay.transaction.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Crée les topics au démarrage (12 partitions pour les flux chauds — règle 1 part ≈
 * 100k msg/min ; DLQ = transactions.failed). RF=1 en local, 3 en production (via env).
 */
@Configuration
public class TopicInitializer {

    private static final Logger log = LoggerFactory.getLogger(TopicInitializer.class);

    @Value("${KAFKA_BROKERS:localhost:9092}")
    private String brokers;

    @Value("${firstpay.kafka.replication-factor:1}")
    private short rf;

    @Bean
    public ApplicationRunner createTopics() {
        return args -> {
            try (AdminClient admin = AdminClient.create(
                    Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers))) {
                var topics = List.of(
                    new NewTopic("transactions.created", 12, rf),
                    new NewTopic("transactions.processed", 12, rf),
                    new NewTopic("transactions.failed", 6, rf),
                    new NewTopic("payments.outbox", 12, rf));
                admin.createTopics(topics).values().forEach((name, future) ->
                    future.whenComplete((v, err) -> {
                        if (err == null) log.info("Topic prêt : {}", name);
                        else log.info("Topic {} déjà présent ou ignoré : {}", name, err.getMessage());
                    }));
            } catch (Exception e) {
                log.warn("Initialisation des topics ignorée : {}", e.getMessage());
            }
        };
    }
}
