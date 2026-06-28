package com.firstpay.partner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Clients HTTP sortants du partner-service. Pour l'instant : appel server-to-server vers
 * transaction-service (initiation de paiement depuis la page payeur publique).
 */
@Configuration
public class WebClientConfig {

    @Bean
    WebClient transactionWebClient(@Value("${firstpay.transaction-service-uri:http://localhost:8080}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
