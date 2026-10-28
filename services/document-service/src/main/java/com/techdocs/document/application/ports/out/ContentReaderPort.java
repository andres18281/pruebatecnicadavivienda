package com.techdocs.document.application.ports.out;

import java.util.Optional;
import java.util.UUID;

public interface ContentReaderPort {
    Optional<String> findContent(UUID documentId);
}
