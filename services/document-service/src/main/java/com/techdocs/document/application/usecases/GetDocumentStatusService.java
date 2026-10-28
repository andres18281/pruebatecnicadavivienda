package com.techdocs.document.application.usecases;

import com.techdocs.document.application.ports.in.GetDocumentStatusUseCase;
import com.techdocs.document.application.ports.out.DocumentRepository;
import com.techdocs.document.application.views.StatusView;
import com.techdocs.document.domain.errors.DomainException;
import com.techdocs.document.domain.model.Document;

import java.util.UUID;

public final class GetDocumentStatusService implements GetDocumentStatusUseCase {

    private final DocumentRepository documents;

    public GetDocumentStatusService(DocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public StatusView execute(UUID id) {
        Document document = documents.findById(id)
                .orElseThrow(() -> new DomainException.NotFound("No existe el documento " + id));
        return new StatusView(document.id(), document.estado().name(), document.errorMsg());
    }
}
