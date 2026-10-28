package com.techdocs.document.application.ports.in;

import com.techdocs.document.application.commands.BatchUploadCommand;
import com.techdocs.document.application.views.BatchReceipt;

import java.util.List;

public interface UploadBatchUseCase {
    List<BatchReceipt> execute(BatchUploadCommand command);
}
