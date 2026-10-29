package com.techdocs.document.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.techdocs.document.application.ports.out.ContentReaderPort;
import com.techdocs.resilience.CircuitGuard;
import com.techdocs.resilience.OpenCircuitException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class ElasticsearchContentReader implements ContentReaderPort {

    private final ElasticsearchClient client;
    private final CircuitGuard elasticsearch;
    private final String index;

    public ElasticsearchContentReader(
            ElasticsearchClient client,
            CircuitBreakerRegistry circuitBreakers,
            @Value("${ELASTICSEARCH_INDEX}") String index
    ) {
        this.client = client;
        this.elasticsearch = CircuitGuard.of(circuitBreakers, "elasticsearch");
        this.index = index;
    }

    @Override
    public Optional<String> findContent(UUID documentId) {
        try {
            return elasticsearch.call(() -> read(documentId));
        } catch (OpenCircuitException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el contenido indexado.", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo leer el contenido indexado.", exception);
        }
    }

    private Optional<String> read(UUID documentId) throws IOException {
        GetResponse<IndexedContent> response = client.get(
                request -> request.index(index).id(documentId.toString()),
                IndexedContent.class
        );
        if (!response.found() || response.source() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.source().contenido);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndexedContent {
        @JsonProperty("contenido")
        public String contenido;
    }
}
