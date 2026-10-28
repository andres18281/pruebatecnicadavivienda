package com.techdocs.search.infrastructure;

import com.techdocs.search.application.SearchDocumentsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SearchDocumentsService.InvalidQueryException.class)
    public ResponseEntity<ApiError> invalid(SearchDocumentsService.InvalidQueryException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_QUERY", exception.getMessage()));
    }

    @ExceptionHandler(ElasticsearchSearchAdapter.SearchUnavailableException.class)
    public ResponseEntity<ApiError> unavailable(ElasticsearchSearchAdapter.SearchUnavailableException exception) {
        log.warn("Busqueda no disponible: {}", exception.getMessage());
        return ResponseEntity.status(503).body(new ApiError("SEARCH_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Error no controlado", exception);
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "No se pudo completar la busqueda."));
    }

    public record ApiError(String error, String message) {
    }
}
