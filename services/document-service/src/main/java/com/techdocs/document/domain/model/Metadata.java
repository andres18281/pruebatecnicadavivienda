package com.techdocs.document.domain.model;

import com.techdocs.document.domain.errors.DomainException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record Metadata(
        String titulo,
        String autor,
        String categoria,
        List<String> etiquetas,
        String version
) {
    private static final Pattern TAG = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]{0,49}");
    private static final Pattern VERSION = Pattern.compile("[\\p{L}\\p{N}._-]{1,50}");

    public static Metadata create(String titulo, String autor, String categoria, String etiquetasRaw, String version) {
        String title = required(titulo, "titulo", 300);
        String author = required(autor, "autor", 200);
        String category = required(categoria, "categoria", 100);
        String documentVersion = required(version, "version", 50);
        if (!VERSION.matcher(documentVersion).matches()) {
            throw new DomainException.InvalidMetadata("version solo admite letras, numeros, punto, guion y guion bajo.");
        }
        return new Metadata(title, author, category, parseTags(etiquetasRaw), documentVersion);
    }

    private static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split("[,;]");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            String tag = part.trim();
            if (tag.isEmpty()) {
                continue;
            }
            if (tag.length() > 50 || !TAG.matcher(tag).matches()) {
                throw new DomainException.InvalidMetadata("La etiqueta '" + tag + "' no es valida.");
            }
            tags.add(tag);
        }
        if (tags.size() > 20) {
            throw new DomainException.InvalidMetadata("Se permiten como maximo 20 etiquetas.");
        }
        return List.copyOf(tags);
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new DomainException.InvalidMetadata(field + " es obligatorio.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new DomainException.InvalidMetadata(field + " supera los " + max + " caracteres.");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char current = trimmed.charAt(i);
            if (current < 0x20 || current == 0x7F) {
                throw new DomainException.InvalidMetadata(field + " contiene caracteres de control.");
            }
        }
        return trimmed;
    }
}
