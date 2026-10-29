package com.techdocs.document.infrastructure.config;

import com.techdocs.resilience.CircuitBreakers;
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
            @Value("${app.circuit-breaker.elasticsearch-slow-call:2s}") Duration elasticsearchSlowCall,
            @Value("${app.circuit-breaker.minio-slow-call:10s}") Duration minioSlowCall
    ) {
        return CircuitBreakers.create(
                meterRegistry,
                Map.of("elasticsearch", elasticsearchSlowCall, "minio", minioSlowCall),
                List.of()
        );
    }
}
