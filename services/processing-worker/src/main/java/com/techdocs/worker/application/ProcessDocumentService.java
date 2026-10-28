package com.techdocs.worker.application;

import com.techdocs.contracts.DocumentProcessCommand;
import com.techdocs.contracts.DocumentStatus;
import com.techdocs.contracts.DocumentStatusChangedEvent;

import java.util.UUID;

public final class ProcessDocumentService {

    private final DocumentStore store;
    private final FileStoragePort storage;
    private final TextExtractorPort extractor;
    private final SearchIndexPort index;

    public ProcessDocumentService(
            DocumentStore store,
            FileStoragePort storage,
            TextExtractorPort extractor,
            SearchIndexPort index
    ) {
        this.store = store;
        this.storage = storage;
        this.extractor = extractor;
        this.index = index;
    }

    public void process(DocumentProcessCommand command) {
        StoredDocument document = load(command);
        if (document.estado() == DocumentStatus.INDEXADO || document.estado() == DocumentStatus.ERROR) {
            store.publishStatus(DocumentStatusChangedEvent.of(
                    document.id().toString(),
                    document.estado(),
                    document.error()
            ));
            return;
        }
        byte[] content = storage.get(command.storageKey());
        String text = extractor.extract(content, document.mimeType(), document.nombreArchivo());
        index.index(document, text);
        store.markIndexed(document);
    }

    public void fail(DocumentProcessCommand command, String error, int attempts) {
        String message = error == null || error.isBlank() ? "No se pudo procesar el documento." : error;
        if (message.length() > 2000) {
            message = message.substring(0, 2000);
        }
        store.markError(UUID.fromString(command.documentId()), message, attempts);
    }

    public void recordAttempt(DocumentProcessCommand command, int attempts) {
        store.recordAttempt(UUID.fromString(command.documentId()), attempts);
    }

    private StoredDocument load(DocumentProcessCommand command) {
        return store.find(UUID.fromString(command.documentId()))
                .orElseThrow(() -> new IllegalStateException("No existe el documento " + command.documentId()));
    }
}
