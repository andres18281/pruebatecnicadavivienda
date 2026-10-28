package com.techdocs.contracts;

public record DocumentProcessCommand(
        String eventId,
        String type,
        String documentId,
        String storageKey,
        String mimeType,
        String occurredAt,
        String traceparent
) {
}
