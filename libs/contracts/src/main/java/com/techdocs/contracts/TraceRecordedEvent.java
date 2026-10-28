package com.techdocs.contracts;

public record TraceRecordedEvent(
        String eventId,
        String service,
        String traceId,
        String spanId,
        String parentSpanId,
        String name,
        String status,
        long durationMs,
        String startedAt
) {
}
