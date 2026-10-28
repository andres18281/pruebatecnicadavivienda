package com.techdocs.document.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdocs.contracts.DocumentProcessCommand;
import com.techdocs.document.infrastructure.messaging.RabbitEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RabbitEventPublisher publisher;

    public OutboxRelay(JdbcTemplate jdbc, ObjectMapper objectMapper, RabbitEventPublisher publisher) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.outbox-delay-ms:5000}")
    public void relay() {
        List<Pending> pending = jdbc.query("""
                SELECT id, payload::text AS payload
                FROM outbox_events
                WHERE publicado_en IS NULL AND event_type = 'document.process'
                ORDER BY id
                LIMIT 20
                """, (rs, rowNum) -> new Pending(rs.getLong("id"), rs.getString("payload")));
        for (Pending item : pending) {
            try {
                DocumentProcessCommand command = objectMapper.readValue(item.payload(), DocumentProcessCommand.class);
                publisher.publishProcess(command);
                jdbc.update(
                        "UPDATE outbox_events SET publicado_en = now() WHERE id = ? AND publicado_en IS NULL",
                        item.id()
                );
            } catch (Exception exception) {
                log.warn("No se pudo republicar el outbox {}: {}", item.id(), exception.getMessage());
            }
        }
    }

    private record Pending(long id, String payload) {
    }
}
