package com.techdocs.document.application.commands;

public record UploadCommand(
        String originalFilename,
        byte[] content,
        String titulo,
        String autor,
        String categoria,
        String etiquetas,
        String version,
        String traceparent
) {
}
