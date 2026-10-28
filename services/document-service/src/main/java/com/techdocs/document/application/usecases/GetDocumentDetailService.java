package com.techdocs.document.application.usecases;

import com.techdocs.contracts.DocumentStatus;
import com.techdocs.document.application.ports.in.GetDocumentDetailUseCase;
import com.techdocs.document.application.ports.out.ContentReaderPort;
import com.techdocs.document.application.ports.out.DocumentRepository;
import com.techdocs.document.application.views.DocumentDetail;
import com.techdocs.document.domain.errors.DomainException;
import com.techdocs.document.domain.model.Document;

import java.util.UUID;

public final class GetDocumentDetailService implements GetDocumentDetailUseCase {

    private final DocumentRepository documents;
    private final ContentReaderPort content;

    public GetDocumentDetailService(DocumentRepository documents, ContentReaderPort content) {
        this.documents = documents;
        this.content = content;
    }

    @Override
    public DocumentDetail execute(UUID id) {
        Document document = documents.findById(id)
                .orElseThrow(() -> new DomainException.NotFound("No existe el documento " + id));
        String body = null;
        if (document.estado() == DocumentStatus.INDEXADO) {
            body = content.findContent(id).orElse(null);
        }
        return DocumentDetail.of(document, body);
    }
}
