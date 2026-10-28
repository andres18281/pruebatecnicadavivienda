package com.techdocs.contracts;

public record LogRecordedEvent(
        String eventId,
        String service,
        String level,
        String logger,
        String message,
        String traceId,
        String spanId,
        String occurredAt
) {
}
