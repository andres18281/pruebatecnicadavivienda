package com.techdocs.worker.application;

public interface TextExtractorPort {
    String extract(byte[] content, String mimeType, String fileName);
}
