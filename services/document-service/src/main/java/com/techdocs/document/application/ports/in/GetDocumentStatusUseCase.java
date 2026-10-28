package com.techdocs.document.application.ports.in;

import com.techdocs.document.application.views.StatusView;

import java.util.UUID;

public interface GetDocumentStatusUseCase {
    StatusView execute(UUID id);
}
