package com.ledger.gateway.client;

import com.ledger.gateway.entity.EventRecord;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Circuit Breaker resiliency pattern.
 * Verifies that circuit breaker is configured and available.
 */
@SpringBootTest
public class AccountServiceClientResiliencyTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void testCircuitBreakerExists() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("accountService");
        assertNotNull(cb);
    }

    @Test
    void testCircuitBreaker_InitiallyClosedState() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("accountService");
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void testCircuitBreakerConfiguration() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("accountService");

        // Verify circuit breaker is configured with expected settings
        assertNotNull(cb.getCircuitBreakerConfig());
        assertEquals(5, cb.getCircuitBreakerConfig().getSlidingWindowSize());
        assertEquals(50.0f, cb.getCircuitBreakerConfig().getFailureRateThreshold());
    }
}



