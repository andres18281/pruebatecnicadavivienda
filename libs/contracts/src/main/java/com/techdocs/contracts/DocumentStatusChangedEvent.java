package com.techdocs.contracts;

public record DocumentStatusChangedEvent(
        String type,
        String documentId,
        String status,
        String error,
        String occurredAt
) {
    public static DocumentStatusChangedEvent of(String documentId, DocumentStatus status, String error) {
        return new DocumentStatusChangedEvent(
                "document.status.changed",
                documentId,
                status.name(),
                error,
                java.time.Instant.now().toString()
        );
    }
}
