package com.techdocs.document.application.usecases;

import com.techdocs.contracts.DocumentProcessCommand;
import com.techdocs.contracts.DocumentStatus;
import com.techdocs.document.application.commands.UploadCommand;
import com.techdocs.document.application.ports.in.UploadDocumentUseCase;
import com.techdocs.document.application.ports.out.DocumentRepository;
import com.techdocs.document.application.ports.out.FileStoragePort;
import com.techdocs.document.application.views.UploadReceipt;
import com.techdocs.document.domain.model.Document;
import com.techdocs.document.domain.policy.UploadPolicy;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

public final class UploadDocumentService implements UploadDocumentUseCase {

    private final UploadPolicy policy;
    private final FileStoragePort storage;
    private final DocumentRepository documents;

    public UploadDocumentService(UploadPolicy policy, FileStoragePort storage, DocumentRepository documents) {
        this.policy = policy;
        this.storage = storage;
        this.documents = documents;
    }

    @Override
    public UploadReceipt execute(UploadCommand command) {
        UploadPolicy.Normalized normalized = policy.normalize(
                command.originalFilename(),
                command.content(),
                command.titulo(),
                command.autor(),
                command.categoria(),
                command.etiquetas(),
                command.version()
        );
        UUID id = UUID.randomUUID();
        String storageKey = storageKey(id, normalized.safeFileName());
        Document document = Document.register(
                id,
                normalized.metadata(),
                normalized.safeFileName(),
                normalized.mimeType(),
                normalized.content().length,
                sha256(normalized.content()),
                storageKey
        );
        storage.save(storageKey, normalized.content(), normalized.mimeType());
        documents.saveNew(document, processCommand(document, command.traceparent()));
        return new UploadReceipt(id, DocumentStatus.PROCESANDO.name());
    }

    private static DocumentProcessCommand processCommand(Document document, String traceparent) {
        return new DocumentProcessCommand(
                UUID.randomUUID().toString(),
                "document.process",
                document.id().toString(),
                document.storageKey(),
                document.mimeType(),
                Instant.now().toString(),
                traceparent
        );
    }

    private static String storageKey(UUID id, String safeName) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return now.getYear() + "/" + String.format("%02d", now.getMonthValue()) + "/" + id + "-" + safeName;
    }

    static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible", exception);
        }
    }
}
