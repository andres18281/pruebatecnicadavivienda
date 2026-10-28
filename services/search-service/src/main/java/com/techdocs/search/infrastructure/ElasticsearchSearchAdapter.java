package com.techdocs.search.infrastructure;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.techdocs.search.application.SearchCriteria;
import com.techdocs.search.application.SearchDocumentsService;
import com.techdocs.search.application.SearchHit;
import com.techdocs.search.application.SearchIndexPort;
import com.techdocs.search.application.SearchPage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ElasticsearchSearchAdapter implements SearchIndexPort {

    private final ElasticsearchClient client;
    private final String index;

    public ElasticsearchSearchAdapter(ElasticsearchClient client, @Value("${ELASTICSEARCH_INDEX}") String index) {
        this.client = client;
        this.index = index;
    }

    @Override
    public SearchPage search(SearchCriteria criteria) {
        try {
            SearchResponse<IndexedDocument> response = client.search(request -> request
                            .index(index)
                            .timeout(criteria.timeout())
                            .trackTotalHits(hits -> hits.enabled(true))
                            .from(criteria.page() * criteria.size())
                            .size(criteria.size())
                            .source(source -> source.filter(filter -> filter.excludes("contenido")))
                            .query(query -> query.bool(bool -> {
                                bool.must(must -> must.simpleQueryString(simple -> simple
                                        .query(criteria.query())
                                        .fields(List.of("titulo^3", "etiquetas^2", "autor", "categoria", "contenido"))
                                        .defaultOperator(Operator.And)
                                        .lenient(true)));
                                bool.filter(filter -> filter.term(term -> term
                                        .field("estado")
                                        .value(value -> value.stringValue("INDEXADO"))));
                                if (criteria.categoria() != null) {
                                    bool.filter(filter -> filter.term(term -> term
                                            .field("categoria")
                                            .value(value -> value.stringValue(criteria.categoria()))));
                                }
                                if (criteria.etiqueta() != null) {
                                    bool.filter(filter -> filter.term(term -> term
                                            .field("etiquetas")
                                            .value(value -> value.stringValue(criteria.etiqueta()))));
                                }
                                return bool;
                            }))
                            .highlight(highlight -> highlight
                                    .preTags("<mark>")
                                    .postTags("</mark>")
                                    .fields("titulo", field -> field.numberOfFragments(0))
                                    .fields("contenido", field -> field.fragmentSize(180).numberOfFragments(3))),
                    IndexedDocument.class);
            List<SearchHit> results = new ArrayList<>();
            for (Hit<IndexedDocument> hit : response.hits().hits()) {
                results.add(toHit(hit));
            }
            long total = response.hits().total() == null ? results.size() : response.hits().total().value();
            return new SearchPage(
                    criteria.query(),
                    criteria.page(),
                    criteria.size(),
                    total,
                    Boolean.TRUE.equals(response.timedOut()),
                    response.took(),
                    results
            );
        } catch (ElasticsearchException exception) {
            if (exception.status() == 400) {
                throw new com.techdocs.search.application.SearchDocumentsService.InvalidQueryException(
                        "La consulta no es valida para el motor de busqueda."
                );
            }
            throw new SearchUnavailableException("El motor de busqueda no respondio.", exception);
        } catch (SearchDocumentsService.InvalidQueryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SearchUnavailableException("El motor de busqueda no respondio.", exception);
        }
    }

    private static SearchHit toHit(Hit<IndexedDocument> hit) {
        IndexedDocument source = hit.source();
        String titulo = source == null || source.titulo == null ? "" : source.titulo;
        String highlightedTitle = first(hit, "titulo", titulo);
        return new SearchHit(
                source != null && source.id != null ? source.id : hit.id(),
                titulo,
                highlightedTitle,
                all(hit, "contenido"),
                source == null ? null : source.autor,
                source == null ? null : source.categoria,
                source == null || source.etiquetas == null ? List.of() : source.etiquetas,
                source == null ? null : source.version,
                source == null ? null : source.nombreArchivo,
                source == null ? null : source.mimeType
        );
    }

    private static String first(Hit<IndexedDocument> hit, String field, String fallback) {
        if (hit.highlight() == null || hit.highlight().get(field) == null || hit.highlight().get(field).isEmpty()) {
            return fallback;
        }
        return hit.highlight().get(field).get(0);
    }

    private static List<String> all(Hit<IndexedDocument> hit, String field) {
        if (hit.highlight() == null || hit.highlight().get(field) == null) {
            return List.of();
        }
        return hit.highlight().get(field);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndexedDocument {
        public String id;
        public String titulo;
        public String autor;
        public String categoria;
        public List<String> etiquetas;
        public String version;
        @JsonProperty("nombre_archivo")
        public String nombreArchivo;
        @JsonProperty("mime_type")
        public String mimeType;
    }

    public static final class SearchUnavailableException extends RuntimeException {
        public SearchUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
