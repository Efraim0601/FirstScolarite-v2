package com.firstpay.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AcquirerProperties.class, RtgsProperties.class})
public class PaymentPropertiesConfig {}
