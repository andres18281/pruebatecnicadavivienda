package com.techdocs.worker.infrastructure;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import com.techdocs.worker.application.SearchIndexPort;
import com.techdocs.worker.application.StoredDocument;
import com.techdocs.resilience.CircuitGuard;
import com.techdocs.resilience.OpenCircuitException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ElasticsearchIndexAdapter implements SearchIndexPort {

    private final ElasticsearchClient client;
    private final CircuitGuard elasticsearch;
    private final String index;

    public ElasticsearchIndexAdapter(
            ElasticsearchClient client,
            CircuitBreakerRegistry circuitBreakers,
            @Value("${ELASTICSEARCH_INDEX}") String index
    ) {
        this.client = client;
        this.elasticsearch = CircuitGuard.of(circuitBreakers, "elasticsearch");
        this.index = index;
    }

    @Override
    public void index(StoredDocument document, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", document.id().toString());
        body.put("titulo", document.titulo());
        body.put("autor", document.autor());
        body.put("categoria", document.categoria());
        body.put("etiquetas", document.etiquetas());
        body.put("version", document.version());
        body.put("nombre_archivo", document.nombreArchivo());
        body.put("mime_type", document.mimeType());
        body.put("tamano_bytes", document.tamanoBytes());
        body.put("estado", "INDEXADO");
        body.put("creado_en", document.creadoEn().toInstant().toString());
        body.put("contenido", content == null ? "" : content);
        try {
            elasticsearch.call(() -> {
                client.index(request -> request
                        .index(index)
                        .id(document.id().toString())
                        .refresh(Refresh.WaitFor)
                        .document(body));
                return null;
            });
        } catch (OpenCircuitException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo indexar el documento.", exception);
        }
    }
}
