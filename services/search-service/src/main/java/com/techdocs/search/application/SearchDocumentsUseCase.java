package com.techdocs.search.application;

public interface SearchDocumentsUseCase {
    SearchPage execute(String query, int page, int size, String categoria, String etiqueta);
}
