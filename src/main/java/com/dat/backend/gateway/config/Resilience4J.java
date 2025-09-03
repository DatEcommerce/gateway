package com.dat.backend.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnSuccessEvent;
import io.github.resilience4j.core.EventConsumer;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class Resilience4J {
    private final EventConsumer<CircuitBreakerOnErrorEvent> normalErrorConsumer =
            event -> log.error("Normal cb error event: {}", event);
    private final EventConsumer<CircuitBreakerOnSuccessEvent> normalSuccessConsumer =
            event -> log.info("Normal cb success event: {}", event);

    private final EventConsumer<CircuitBreakerOnErrorEvent> slowErrorConsumer =
            event -> log.error("Slow cb error event: {}", event);
    private final EventConsumer<CircuitBreakerOnSuccessEvent> slowSuccessConsumer =
            event -> log.info("Slow cb success event: {}", event);

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(4))
                        .build())
                .build());
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> slowCustomizer() {
        return factory -> {
            factory.configure(builder -> builder
                    .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build())
                    .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults()), "slow");
            factory.addCircuitBreakerCustomizer(circuitBreaker -> circuitBreaker.getEventPublisher()
                    .onError(slowErrorConsumer).onSuccess(slowSuccessConsumer), "slow");
            factory.addCircuitBreakerCustomizer(circuitBreaker -> circuitBreaker.getEventPublisher()
                    .onError(normalErrorConsumer).onSuccess(normalSuccessConsumer), "normal");
        };
    }
}
