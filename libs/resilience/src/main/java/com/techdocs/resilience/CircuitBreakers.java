package com.techdocs.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class CircuitBreakers {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakers.class);

    private CircuitBreakers() {
    }

    public static CircuitBreakerRegistry create(
            MeterRegistry meters,
            Map<String, Duration> slowCallByName,
            List<Class<? extends Throwable>> ignored
    ) {
        CircuitBreakerRegistry.Builder builder = CircuitBreakerRegistry.custom()
                .withCircuitBreakerConfig(config(Duration.ofSeconds(3), ignored));
        for (Map.Entry<String, Duration> entry : slowCallByName.entrySet()) {
            builder.addCircuitBreakerConfig(entry.getKey(), config(entry.getValue(), ignored));
        }
        CircuitBreakerRegistry registry = builder.build();
        registry.getEventPublisher().onEntryAdded(event -> watch(event.getAddedEntry()));
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        for (String name : slowCallByName.keySet()) {
            registry.circuitBreaker(name, name);
        }
        return registry;
    }

    private static CircuitBreakerConfig config(Duration slowCall, List<Class<? extends Throwable>> ignored) {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(slowCall)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true);
        if (!ignored.isEmpty()) {
            builder.ignoreExceptions(toArray(ignored));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Throwable>[] toArray(List<Class<? extends Throwable>> ignored) {
        return ignored.toArray(new Class[0]);
    }

    private static void watch(CircuitBreaker breaker) {
        breaker.getEventPublisher().onStateTransition(event -> log.warn(
                "Circuit breaker {} paso de {} a {}",
                event.getCircuitBreakerName(),
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState()
        ));
    }
}
