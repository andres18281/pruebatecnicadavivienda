package com.techdocs.worker.application;

import com.techdocs.contracts.DocumentStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StoredDocument(
        UUID id,
        String titulo,
        String autor,
        String categoria,
        List<String> etiquetas,
        String version,
        String nombreArchivo,
        String mimeType,
        long tamanoBytes,
        DocumentStatus estado,
        String error,
        OffsetDateTime creadoEn
) {
}
