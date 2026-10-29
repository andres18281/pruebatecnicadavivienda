package com.techdocs.document.application.usecases;

import com.techdocs.document.application.ports.in.GetDocumentFileUseCase;
import com.techdocs.document.application.ports.out.DocumentRepository;
import com.techdocs.document.application.ports.out.FileStoragePort;
import com.techdocs.document.application.views.DocumentFile;
import com.techdocs.document.domain.errors.DomainException;
import com.techdocs.document.domain.model.Document;

import java.util.UUID;

public final class GetDocumentFileService implements GetDocumentFileUseCase {

    private final DocumentRepository documents;
    private final FileStoragePort storage;

    public GetDocumentFileService(DocumentRepository documents, FileStoragePort storage) {
        this.documents = documents;
        this.storage = storage;
    }

    @Override
    public DocumentFile execute(UUID id) {
        Document document = documents.findById(id)
                .orElseThrow(() -> new DomainException.NotFound("No existe el documento " + id));
        return new DocumentFile(
                document.nombreArchivo(),
                document.mimeType(),
                storage.get(document.storageKey())
        );
    }
}
