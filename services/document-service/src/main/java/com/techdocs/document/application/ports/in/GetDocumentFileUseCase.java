package com.techdocs.document.application.ports.in;

import com.techdocs.document.application.views.DocumentFile;

import java.util.UUID;

public interface GetDocumentFileUseCase {
    DocumentFile execute(UUID id);
}
