package com.techdocs.document.application.views;

import com.techdocs.document.domain.model.Document;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DocumentDetail(
        UUID id,
        String titulo,
        String autor,
        String categoria,
        List<String> etiquetas,
        String version,
        String nombreArchivo,
        String mimeType,
        long tamanoBytes,
        String estado,
        String error,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn,
        String contenido
) {
    public static DocumentDetail of(Document document, String contenido) {
        return new DocumentDetail(
                document.id(),
                document.titulo(),
                document.autor(),
                document.categoria(),
                document.etiquetas(),
                document.version(),
                document.nombreArchivo(),
                document.mimeType(),
                document.tamanoBytes(),
                document.estado().name(),
                document.errorMsg(),
                document.creadoEn(),
                document.actualizadoEn(),
                contenido
        );
    }
}
