package com.firstpay.payment.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    @Qualifier("partner")
    WebClient partnerWebClient(@Value("${firstpay.partner-service-uri:http://localhost:8080}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    @Qualifier("trustpayway")
    WebClient trustPayWayWebClient() {
        return WebClient.builder().build();
    }
}
