package com.techdocs.contracts;

public record DocumentProgressEvent(
        String type,
        String documentId,
        String stage,
        int percent,
        String label,
        String occurredAt
) {
    public static DocumentProgressEvent of(String documentId, String stage, int percent, String label) {
        return new DocumentProgressEvent(
                "document.progress",
                documentId,
                stage,
                percent,
                label,
                java.time.Instant.now().toString()
        );
    }
}
