package com.techdocs.search.application;

import java.util.List;

public record SearchHit(
        String id,
        String titulo,
        String tituloResaltado,
        List<String> fragmentos,
        String autor,
        String categoria,
        List<String> etiquetas,
        String version,
        String nombreArchivo,
        String mimeType
) {
}
