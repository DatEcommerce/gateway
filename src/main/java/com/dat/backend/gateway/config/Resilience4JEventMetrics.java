package com.dat.backend.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Resilience4JEventMetrics {
    private final Logger log = LoggerFactory.getLogger(Resilience4JEventMetrics.class);

    // Logs events for all existing and future CircuitBreakers
    @Bean
    public ApplicationRunner resilience4JEventLogger(CircuitBreakerRegistry circuitBreakerRegistry) {
        return args -> {
            circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registerEventLogger);
            circuitBreakerRegistry.getEventPublisher().onEntryAdded(event -> registerEventLogger(event.getAddedEntry()));
        };
    }

    private void registerEventLogger(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onSuccess(event -> log.info("CircuitBreaker {} - Success: {}", event.getCircuitBreakerName(), event.toString()))
                .onError(event -> log.error("CircuitBreaker {} - Error: {}", event.getCircuitBreakerName(), event.toString()))
                .onStateTransition(event -> log.warn("CircuitBreaker {} - State Transition: {}", event.getCircuitBreakerName(), event.toString()));
    }
}
