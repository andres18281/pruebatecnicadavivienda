package com.techdocs.worker.bootstrap;

import com.techdocs.worker.application.DocumentStore;
import com.techdocs.worker.application.FileStoragePort;
import com.techdocs.worker.application.ProcessDocumentService;
import com.techdocs.worker.application.SearchIndexPort;
import com.techdocs.worker.application.TextExtractorPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    ProcessDocumentService processDocumentService(
            DocumentStore store,
            FileStoragePort storage,
            TextExtractorPort extractor,
            SearchIndexPort index
    ) {
        return new ProcessDocumentService(store, storage, extractor, index);
    }
}
