package com.techdocs.search.bootstrap;

import com.techdocs.search.application.SearchDocumentsService;
import com.techdocs.search.application.SearchDocumentsUseCase;
import com.techdocs.search.application.SearchIndexPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    SearchDocumentsUseCase searchDocumentsUseCase(
            SearchIndexPort index,
            @Value("${app.max-page-size}") int maxPageSize,
            @Value("${app.search-timeout-ms}") int timeoutMs
    ) {
        return new SearchDocumentsService(index, maxPageSize, timeoutMs);
    }
}
