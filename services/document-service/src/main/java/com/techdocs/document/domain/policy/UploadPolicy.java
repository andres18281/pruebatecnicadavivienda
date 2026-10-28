package com.techdocs.document.domain.policy;

import com.techdocs.document.domain.errors.DomainException;
import com.techdocs.document.domain.model.Metadata;

import java.util.Locale;
import java.util.Set;

public final class UploadPolicy {

    private static final Set<String> ALLOWED = Set.of("txt", "md", "markdown", "pdf");
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private final long maxBytes;

    public UploadPolicy(int maxUploadMb) {
        if (maxUploadMb < 1) {
            throw new IllegalArgumentException("MAX_UPLOAD_MB debe ser positivo.");
        }
        this.maxBytes = maxUploadMb * 1024L * 1024L;
    }

    public Normalized normalize(
            String originalFilename,
            byte[] content,
            String titulo,
            String autor,
            String categoria,
            String etiquetas,
            String version
    ) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new DomainException.InvalidMetadata("El archivo es obligatorio.");
        }
        if (content == null || content.length == 0) {
            throw new DomainException.InvalidMetadata("El archivo esta vacio.");
        }
        if (content.length > maxBytes) {
            throw new DomainException.FileTooLarge("El archivo supera el maximo de " + (maxBytes / 1024 / 1024) + " MB.");
        }
        String safeName = sanitize(originalFilename);
        String extension = extensionOf(safeName);
        if (!ALLOWED.contains(extension)) {
            throw new DomainException.UnsupportedFormat("Formato no admitido. Usa TXT, Markdown o PDF.");
        }
        assertMagic(extension, content);
        Metadata metadata = Metadata.create(titulo, autor, categoria, etiquetas, version);
        return new Normalized(metadata, content, mimeOf(extension), safeName);
    }

    private static void assertMagic(String extension, byte[] content) {
        boolean pdf = startsWith(content, PDF_MAGIC);
        if ("pdf".equals(extension)) {
            if (!pdf) {
                throw new DomainException.UnsupportedFormat("El archivo no tiene la firma de un PDF.");
            }
            return;
        }
        if (pdf) {
            throw new DomainException.UnsupportedFormat("La extension no coincide con un contenido PDF.");
        }
        int limit = Math.min(content.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (content[i] == 0) {
                throw new DomainException.UnsupportedFormat("El archivo de texto contiene bytes nulos.");
            }
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    static String sanitize(String original) {
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.length() > 120) {
            String extension = extensionOf(name);
            int keep = Math.max(1, 110 - extension.length());
            name = name.substring(0, keep) + (extension.isEmpty() ? "" : "." + extension);
        }
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw new DomainException.InvalidMetadata("El nombre del archivo no es valido.");
        }
        return name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String mimeOf(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "md", "markdown" -> "text/markdown";
            default -> throw new DomainException.UnsupportedFormat("Formato no admitido.");
        };
    }

    public record Normalized(Metadata metadata, byte[] content, String mimeType, String safeFileName) {
    }
}
