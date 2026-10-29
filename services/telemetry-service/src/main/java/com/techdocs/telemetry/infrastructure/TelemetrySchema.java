package com.techdocs.telemetry.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
class TelemetrySchema {

    private final JdbcTemplate jdbc;

    TelemetrySchema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void create() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS telemetry_logs (
                    id          BIGSERIAL PRIMARY KEY,
                    event_id    UUID NOT NULL UNIQUE,
                    service     VARCHAR(100) NOT NULL,
                    level       VARCHAR(20) NOT NULL,
                    logger      VARCHAR(300),
                    message     TEXT NOT NULL,
                    trace_id    VARCHAR(64),
                    span_id     VARCHAR(32),
                    occurred_at TIMESTAMPTZ NOT NULL
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_logs_trace ON telemetry_logs (trace_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_logs_occurred ON telemetry_logs (occurred_at DESC)");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS telemetry_traces (
                    id             BIGSERIAL PRIMARY KEY,
                    event_id       UUID NOT NULL UNIQUE,
                    service        VARCHAR(100) NOT NULL,
                    trace_id       VARCHAR(64) NOT NULL,
                    span_id        VARCHAR(32),
                    parent_span_id VARCHAR(32),
                    name           VARCHAR(200) NOT NULL,
                    status         VARCHAR(20) NOT NULL,
                    duration_ms    BIGINT NOT NULL,
                    started_at     TIMESTAMPTZ NOT NULL
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_traces_trace ON telemetry_traces (trace_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_traces_started ON telemetry_traces (started_at DESC)");
    }
}
