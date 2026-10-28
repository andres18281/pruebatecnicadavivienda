package com.techdocs.worker.application;

public interface FileStoragePort {
    byte[] get(String key);
}
