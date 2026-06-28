package com.firstpay.partner.config;

import com.firstpay.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

    @Bean
    public JwtService jwtService(
            @Value("${firstpay.jwt.secret}") String secret,
            @Value("${firstpay.jwt.ttl-seconds:28800}") long ttlSeconds) {
        return new JwtService(secret, ttlSeconds);
    }
}
