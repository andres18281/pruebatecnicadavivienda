package com.techdocs.search.infrastructure;

import com.techdocs.resilience.CircuitBreakers;
import com.techdocs.search.application.SearchDocumentsService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
public class CircuitBreakerConfiguration {

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(
            MeterRegistry meterRegistry,
            @Value("${app.circuit-breaker.elasticsearch-slow-call:1s}") Duration elasticsearchSlowCall
    ) {
        return CircuitBreakers.create(
                meterRegistry,
                Map.of("elasticsearch", elasticsearchSlowCall),
                List.of(SearchDocumentsService.InvalidQueryException.class)
        );
    }
}
