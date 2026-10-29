package com.techdocs.document.application.views;

public record DocumentFile(
        String nombreArchivo,
        String mimeType,
        byte[] content
) {
}
