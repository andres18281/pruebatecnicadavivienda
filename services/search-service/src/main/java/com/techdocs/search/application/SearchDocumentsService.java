package com.techdocs.search.application;

public final class SearchDocumentsService implements SearchDocumentsUseCase {

    private final SearchIndexPort index;
    private final int maxPageSize;
    private final int timeoutMs;

    public SearchDocumentsService(SearchIndexPort index, int maxPageSize, int timeoutMs) {
        this.index = index;
        this.maxPageSize = maxPageSize;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public SearchPage execute(String query, int page, int size, String categoria, String etiqueta) {
        if (query == null || query.isBlank()) {
            throw new InvalidQueryException("El parametro q es obligatorio.");
        }
        String text = query.trim();
        if (text.length() > 500) {
            throw new InvalidQueryException("La consulta supera los 500 caracteres.");
        }
        if (page < 0) {
            throw new InvalidQueryException("La pagina no puede ser negativa.");
        }
        int pageSize = size <= 0 ? 10 : size;
        if (pageSize > maxPageSize) {
            throw new InvalidQueryException("El tamano de pagina maximo es " + maxPageSize + ".");
        }
        if ((long) page * pageSize >= 10_000) {
            throw new InvalidQueryException("La paginacion profunda no esta permitida. Ajusta la consulta.");
        }
        return index.search(new SearchCriteria(text, page, pageSize, blank(categoria), blank(etiqueta), timeoutMs + "ms"));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class InvalidQueryException extends RuntimeException {
        public InvalidQueryException(String message) {
            super(message);
        }
    }
}
