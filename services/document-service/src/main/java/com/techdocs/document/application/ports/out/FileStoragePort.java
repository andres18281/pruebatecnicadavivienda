package com.techdocs.document.application.ports.out;

public interface FileStoragePort {
    void save(String key, byte[] content, String mimeType);
}
