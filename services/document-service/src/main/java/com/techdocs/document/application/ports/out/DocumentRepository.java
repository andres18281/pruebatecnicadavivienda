package com.techdocs.document.application.ports.out;

import com.techdocs.contracts.DocumentProcessCommand;
import com.techdocs.document.domain.model.Document;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    void saveNew(Document document, DocumentProcessCommand command);

    Optional<Document> findById(UUID id);
}
