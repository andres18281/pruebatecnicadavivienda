package com.techdocs.document.bootstrap;

import com.techdocs.document.application.ports.in.GetDocumentDetailUseCase;
import com.techdocs.document.application.ports.in.GetDocumentFileUseCase;
import com.techdocs.document.application.ports.in.GetDocumentStatusUseCase;
import com.techdocs.document.application.ports.in.UploadBatchUseCase;
import com.techdocs.document.application.ports.in.UploadDocumentUseCase;
import com.techdocs.document.application.ports.out.ContentReaderPort;
import com.techdocs.document.application.ports.out.DocumentRepository;
import com.techdocs.document.application.ports.out.FileStoragePort;
import com.techdocs.document.application.usecases.GetDocumentDetailService;
import com.techdocs.document.application.usecases.GetDocumentFileService;
import com.techdocs.document.application.usecases.GetDocumentStatusService;
import com.techdocs.document.application.usecases.UploadBatchService;
import com.techdocs.document.application.usecases.UploadDocumentService;
import com.techdocs.document.domain.policy.UploadPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    UploadPolicy uploadPolicy(@Value("${app.max-upload-mb}") int maxUploadMb) {
        return new UploadPolicy(maxUploadMb);
    }

    @Bean
    UploadDocumentUseCase uploadDocumentUseCase(
            UploadPolicy policy,
            FileStoragePort storage,
            DocumentRepository documents
    ) {
        return new UploadDocumentService(policy, storage, documents);
    }

    @Bean
    UploadBatchUseCase uploadBatchUseCase(UploadDocumentUseCase uploadDocumentUseCase) {
        return new UploadBatchService(uploadDocumentUseCase);
    }

    @Bean
    GetDocumentDetailUseCase getDocumentDetailUseCase(DocumentRepository documents, ContentReaderPort content) {
        return new GetDocumentDetailService(documents, content);
    }

    @Bean
    GetDocumentFileUseCase getDocumentFileUseCase(DocumentRepository documents, FileStoragePort storage) {
        return new GetDocumentFileService(documents, storage);
    }

    @Bean
    GetDocumentStatusUseCase getDocumentStatusUseCase(DocumentRepository documents) {
        return new GetDocumentStatusService(documents);
    }
}
