package com.techdocs.worker.application;

import com.techdocs.contracts.DocumentStatusChangedEvent;

import java.util.Optional;
import java.util.UUID;

public interface DocumentStore {
    Optional<StoredDocument> find(UUID id);

    void markIndexed(StoredDocument document);

    void markError(UUID id, String error, int attempts);

    void recordAttempt(UUID id, int attempts);

    void publishStatus(DocumentStatusChangedEvent event);
}
