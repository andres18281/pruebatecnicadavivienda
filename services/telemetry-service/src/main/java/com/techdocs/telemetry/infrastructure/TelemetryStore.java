package com.techdocs.telemetry.infrastructure;

import com.techdocs.contracts.LogRecordedEvent;
import com.techdocs.contracts.TraceRecordedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TelemetryStore {

    private final JdbcTemplate jdbc;

    public TelemetryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveLog(LogRecordedEvent event) {
        jdbc.update("""
                INSERT INTO telemetry_logs (event_id, service, level, logger, message, trace_id, span_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                UUID.fromString(event.eventId()),
                event.service(),
                event.level(),
                event.logger(),
                event.message() == null || event.message().isBlank() ? "(sin mensaje)" : event.message(),
                event.traceId(),
                event.spanId(),
                OffsetDateTime.parse(event.occurredAt())
        );
    }

    public void saveTrace(TraceRecordedEvent event) {
        jdbc.update("""
                INSERT INTO telemetry_traces
                    (event_id, service, trace_id, span_id, parent_span_id, name, status, duration_ms, started_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                UUID.fromString(event.eventId()),
                event.service(),
                event.traceId(),
                blankToNull(event.spanId()),
                blankToNull(event.parentSpanId()),
                event.name(),
                event.status(),
                event.durationMs(),
                OffsetDateTime.parse(event.startedAt())
        );
    }

    public List<StoredLog> logs(String service, String traceId, int limit) {
        Query query = filter("telemetry_logs", service, traceId, limit);
        return jdbc.query(query.sql(), (rs, row) -> new StoredLog(
                rs.getLong("id"),
                rs.getString("service"),
                rs.getString("level"),
                rs.getString("logger"),
                rs.getString("message"),
                rs.getString("trace_id"),
                rs.getString("span_id"),
                instant(rs, "occurred_at")
        ), query.args());
    }

    public List<StoredTrace> traces(String service, String traceId, int limit) {
        Query query = filter("telemetry_traces", service, traceId, limit);
        String sql = query.sql().replace("occurred_at", "started_at");
        return jdbc.query(sql, (rs, row) -> new StoredTrace(
                rs.getLong("id"),
                rs.getString("service"),
                rs.getString("trace_id"),
                rs.getString("span_id"),
                rs.getString("parent_span_id"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getLong("duration_ms"),
                instant(rs, "started_at")
        ), query.args());
    }

    private static Query filter(String table, String service, String traceId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table).append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (service != null) {
            sql.append(" AND service = ?");
            args.add(service);
        }
        if (traceId != null) {
            sql.append(" AND trace_id = ?");
            args.add(traceId);
        }
        sql.append(" ORDER BY occurred_at DESC LIMIT ?");
        args.add(limit);
        return new Query(sql.toString(), args.toArray());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Query(String sql, Object[] args) {
    }

    public record StoredLog(
            long id,
            String service,
            String level,
            String logger,
            String message,
            String traceId,
            String spanId,
            Instant occurredAt
    ) {
    }

    public record StoredTrace(
            long id,
            String service,
            String traceId,
            String spanId,
            String parentSpanId,
            String name,
            String status,
            long durationMs,
            Instant startedAt
    ) {
    }
}
