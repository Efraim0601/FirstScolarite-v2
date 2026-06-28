package com.firstpay.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** reporting-service — projections CQRS alimentées par Kafka, servies depuis read replicas. */
@SpringBootApplication
public class ReportingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
