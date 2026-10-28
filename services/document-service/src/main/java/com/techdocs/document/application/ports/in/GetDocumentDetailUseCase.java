package com.techdocs.document.application.ports.in;

import com.techdocs.document.application.views.DocumentDetail;

import java.util.UUID;

public interface GetDocumentDetailUseCase {
    DocumentDetail execute(UUID id);
}
