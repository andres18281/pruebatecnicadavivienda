package com.techdocs.search.application;

public record SearchCriteria(
        String query,
        int page,
        int size,
        String categoria,
        String etiqueta,
        String timeout
) {
}
