package com.techdocs.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitGuardTest {

    @Test
    void opensAfterTheFailureThresholdAndSkipsTheCall() throws Exception {
        CircuitBreaker breaker = CircuitBreaker.of("elasticsearch", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        CircuitGuard guard = new CircuitGuard(breaker);
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThrows(IllegalStateException.class, () -> guard.call(() -> {
                throw new IllegalStateException("down");
            }));
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        AtomicInteger calls = new AtomicInteger();
        OpenCircuitException open = assertThrows(OpenCircuitException.class, () -> guard.call(() -> {
            calls.incrementAndGet();
            return "ok";
        }));
        assertEquals(0, calls.get());
        assertEquals("elasticsearch", open.dependency());
    }

    @Test
    void ignoredExceptionDoesNotOpenTheCircuit() {
        CircuitBreaker breaker = CircuitBreaker.of("elasticsearch", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .ignoreExceptions(IllegalArgumentException.class)
                .build());
        CircuitGuard guard = new CircuitGuard(breaker);
        for (int attempt = 0; attempt < 6; attempt++) {
            assertThrows(IllegalArgumentException.class, () -> guard.call(() -> {
                throw new IllegalArgumentException("consulta invalida");
            }));
        }
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test
    void registryPublishesTheOpenState() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        var registry = CircuitBreakers.create(meters, Map.of("minio", Duration.ofSeconds(3)), List.of());
        CircuitGuard guard = CircuitGuard.of(registry, "minio");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(IllegalStateException.class, () -> guard.call(() -> {
                throw new IllegalStateException("down");
            }));
        }
        assertEquals(CircuitBreaker.State.OPEN, registry.circuitBreaker("minio").getState());
        assertEquals(1.0, meters.get("resilience4j.circuitbreaker.state")
                .tag("name", "minio")
                .tag("state", "open")
                .gauge()
                .value());
    }
}
