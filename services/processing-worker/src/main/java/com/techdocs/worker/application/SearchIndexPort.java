package com.techdocs.worker.application;

public interface SearchIndexPort {
    void index(StoredDocument document, String content);
}
