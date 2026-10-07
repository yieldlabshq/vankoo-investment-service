package com.liquilabs.vankoo.investment.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;

import java.time.Clock;

@Configuration
public class TimeConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ValidationConfigurationCustomizer validationClockProvider(Clock clock, PricingProperties pricingProperties) {
        return configuration -> configuration.clockProvider(() -> clock.withZone(pricingProperties.pricingZone()));
    }
}
