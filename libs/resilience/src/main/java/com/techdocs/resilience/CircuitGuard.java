package com.techdocs.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.util.concurrent.Callable;

public final class CircuitGuard {

    private final CircuitBreaker breaker;

    public CircuitGuard(CircuitBreaker breaker) {
        this.breaker = breaker;
    }

    public static CircuitGuard of(CircuitBreakerRegistry registry, String name) {
        return new CircuitGuard(registry.circuitBreaker(name, name));
    }

    public <T> T call(Callable<T> callable) throws Exception {
        try {
            return breaker.executeCallable(callable);
        } catch (CallNotPermittedException exception) {
            throw new OpenCircuitException(breaker.getName(), exception);
        }
    }
}
