package com.techdocs.document.application.usecases;

import com.techdocs.document.application.commands.BatchUploadCommand;
import com.techdocs.document.application.commands.UploadCommand;
import com.techdocs.document.application.ports.in.UploadBatchUseCase;
import com.techdocs.document.application.ports.in.UploadDocumentUseCase;
import com.techdocs.document.application.views.BatchReceipt;
import com.techdocs.document.application.views.UploadReceipt;
import com.techdocs.document.domain.errors.DomainException;

import java.util.ArrayList;
import java.util.List;

public final class UploadBatchService implements UploadBatchUseCase {

    private final UploadDocumentUseCase uploadDocument;

    public UploadBatchService(UploadDocumentUseCase uploadDocument) {
        this.uploadDocument = uploadDocument;
    }

    @Override
    public List<BatchReceipt> execute(BatchUploadCommand command) {
        if (command.files() == null || command.files().isEmpty()) {
            throw new DomainException.InvalidMetadata("La carga masiva necesita al menos un archivo.");
        }
        if (command.files().size() > 20) {
            throw new DomainException.InvalidMetadata("La carga masiva admite como maximo 20 archivos.");
        }
        List<BatchReceipt> receipts = new ArrayList<>();
        for (UploadCommand file : command.files()) {
            UploadReceipt receipt = uploadDocument.execute(file);
            receipts.add(new BatchReceipt(receipt.id(), receipt.estado(), file.originalFilename()));
        }
        return receipts;
    }
}
