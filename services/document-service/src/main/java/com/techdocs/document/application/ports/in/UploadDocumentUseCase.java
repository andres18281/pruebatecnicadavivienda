package com.techdocs.document.application.ports.in;

import com.techdocs.document.application.commands.UploadCommand;
import com.techdocs.document.application.views.UploadReceipt;

public interface UploadDocumentUseCase {
    UploadReceipt execute(UploadCommand command);
}
