package com.techdocs.document.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.techdocs.document.application.ports.out.ContentReaderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class ElasticsearchContentReader implements ContentReaderPort {

    private final ElasticsearchClient client;
    private final String index;

    public ElasticsearchContentReader(
            ElasticsearchClient client,
            @Value("${ELASTICSEARCH_INDEX}") String index
    ) {
        this.client = client;
        this.index = index;
    }

    @Override
    public Optional<String> findContent(UUID documentId) {
        try {
            GetResponse<IndexedContent> response = client.get(
                    request -> request.index(index).id(documentId.toString()),
                    IndexedContent.class
            );
            if (!response.found() || response.source() == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.source().contenido);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el contenido indexado.", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndexedContent {
        @JsonProperty("contenido")
        public String contenido;
    }
}
