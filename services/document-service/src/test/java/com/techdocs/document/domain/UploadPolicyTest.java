package com.techdocs.document.domain;

import com.techdocs.contracts.DocumentStatus;
import com.techdocs.document.domain.errors.DomainException;
import com.techdocs.document.domain.model.Document;
import com.techdocs.document.domain.model.Metadata;
import com.techdocs.document.domain.policy.UploadPolicy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadPolicyTest {

    private final UploadPolicy policy = new UploadPolicy(1);

    @Test
    void acceptsPlainTextWithMetadata() {
        byte[] content = "Guia de arquitectura".getBytes(StandardCharsets.UTF_8);
        UploadPolicy.Normalized normalized = policy.normalize(
                "guia.txt", content, "Guia", "Ana", "arquitectura", "microservicios, hexagonal", "1.0"
        );
        assertEquals("text/plain", normalized.mimeType());
        assertEquals("guia.txt", normalized.safeFileName());
        assertEquals(2, normalized.metadata().etiquetas().size());
    }

    @Test
    void rejectsBinaryDisguisedAsText() {
        byte[] content = new byte[]{0x00, 0x01, 0x02};
        assertThrows(DomainException.UnsupportedFormat.class, () ->
                policy.normalize("virus.txt", content, "Titulo", "Ana", "seguridad", "", "1"));
    }

    @Test
    void rejectsPdfWithoutSignature() {
        byte[] content = "no es un pdf".getBytes(StandardCharsets.UTF_8);
        assertThrows(DomainException.UnsupportedFormat.class, () ->
                policy.normalize("manual.pdf", content, "Titulo", "Ana", "manual", "", "1"));
    }

    @Test
    void rejectsOversizedFile() {
        byte[] content = new byte[2 * 1024 * 1024];
        content[0] = 'a';
        assertThrows(DomainException.FileTooLarge.class, () ->
                policy.normalize("grande.txt", content, "Titulo", "Ana", "manual", "", "1"));
    }

    @Test
    void rejectsBlankTitle() {
        byte[] content = "hola".getBytes(StandardCharsets.UTF_8);
        assertThrows(DomainException.InvalidMetadata.class, () ->
                Metadata.create("  ", "Ana", "cat", "", "1"));
    }

    @Test
    void indexedTransitionIsOneWayFromProcessing() {
        Document document = Document.register(
                UUID.randomUUID(),
                Metadata.create("Titulo", "Ana", "cat", "", "1"),
                "a.txt",
                "text/plain",
                4,
                "ab",
                "2026/10/key"
        );
        assertEquals(DocumentStatus.PROCESANDO, document.estado());
        document.markIndexed();
        assertEquals(DocumentStatus.INDEXADO, document.estado());
        assertThrows(DomainException.InvalidTransition.class, document::markIndexed);
    }
}
