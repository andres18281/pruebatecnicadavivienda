package com.techdocs.telemetry.infrastructure;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TelemetryController {

    private final TelemetryStore store;

    public TelemetryController(TelemetryStore store) {
        this.store = store;
    }

    @GetMapping("/api/v1/telemetry/logs")
    public List<TelemetryStore.StoredLog> logs(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String traceId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return store.logs(blank(service), blank(traceId), bounded(limit));
    }

    @GetMapping("/api/v1/telemetry/traces")
    public List<TelemetryStore.StoredTrace> traces(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String traceId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return store.traces(blank(service), blank(traceId), bounded(limit));
    }

    private static int bounded(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
