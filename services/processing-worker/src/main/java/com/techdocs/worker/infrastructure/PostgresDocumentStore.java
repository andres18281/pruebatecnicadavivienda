package com.techdocs.worker.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdocs.contracts.DocumentStatus;
import com.techdocs.contracts.DocumentStatusChangedEvent;
import com.techdocs.worker.application.DocumentStore;
import com.techdocs.worker.application.StoredDocument;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresDocumentStore implements DocumentStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresDocumentStore.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    public PostgresDocumentStore(JdbcTemplate jdbc, ObjectMapper objectMapper, RabbitTemplate rabbitTemplate) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public Optional<StoredDocument> find(UUID id) {
        List<StoredDocument> rows = jdbc.query("""
                SELECT id, titulo, autor, categoria, etiquetas, version, nombre_archivo, mime_type,
                       tamano_bytes, estado, error_msg, creado_en
                FROM documents
                WHERE id = ?
                """, (rs, row) -> map(rs), id);
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public void markIndexed(StoredDocument document) {
        jdbc.update("""
                UPDATE documents
                SET estado = ?, error_msg = NULL, actualizado_en = now()
                WHERE id = ?
                """, status(DocumentStatus.INDEXADO), document.id());
        DocumentStatusChangedEvent event = DocumentStatusChangedEvent.of(document.id().toString(), DocumentStatus.INDEXADO, null);
        long outboxId = insertOutbox(document.id(), event);
        afterCommit(outboxId, event);
    }

    @Override
    @Transactional
    public void markError(UUID id, String error, int attempts) {
        jdbc.update("""
                UPDATE documents
                SET estado = ?, error_msg = ?, intentos = ?, actualizado_en = now()
                WHERE id = ?
                """, status(DocumentStatus.ERROR), error, attempts, id);
        DocumentStatusChangedEvent event = DocumentStatusChangedEvent.of(id.toString(), DocumentStatus.ERROR, error);
        long outboxId = insertOutbox(id, event);
        afterCommit(outboxId, event);
    }

    @Override
    public void recordAttempt(UUID id, int attempts) {
        jdbc.update("""
                UPDATE documents
                SET intentos = ?, actualizado_en = now()
                WHERE id = ? AND estado = ?
                """, attempts, id, status(DocumentStatus.PROCESANDO));
    }

    @Override
    public void publishStatus(DocumentStatusChangedEvent event) {
        rabbitTemplate.convertAndSend(RabbitTopologyConfig.EVENTS, RabbitTopologyConfig.STATUS_KEY, event);
    }

    @Scheduled(fixedDelayString = "5000")
    public void relay() {
        List<Pending> pending = jdbc.query("""
                SELECT id, payload::text AS payload
                FROM outbox_events
                WHERE publicado_en IS NULL AND event_type = 'document.status.changed'
                ORDER BY id
                LIMIT 20
                """, (rs, row) -> new Pending(rs.getLong("id"), rs.getString("payload")));
        for (Pending item : pending) {
            try {
                DocumentStatusChangedEvent event = objectMapper.readValue(item.payload(), DocumentStatusChangedEvent.class);
                publishStatus(event);
                jdbc.update("UPDATE outbox_events SET publicado_en = now() WHERE id = ? AND publicado_en IS NULL", item.id());
            } catch (Exception exception) {
                log.warn("No se pudo republicar el estado {}: {}", item.id(), exception.getMessage());
            }
        }
    }

    private void afterCommit(long outboxId, DocumentStatusChangedEvent event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publishStatus(event);
                    jdbc.update("UPDATE outbox_events SET publicado_en = now() WHERE id = ? AND publicado_en IS NULL", outboxId);
                } catch (RuntimeException exception) {
                    log.warn("Estado pendiente en outbox {}: {}", outboxId, exception.getMessage());
                }
            }
        });
    }

    private long insertOutbox(UUID aggregateId, DocumentStatusChangedEvent event) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        String json = write(event);
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO outbox_events (aggregate_id, event_type, payload) VALUES (?, ?, ?)",
                    new String[]{"id"}
            );
            statement.setObject(1, aggregateId);
            statement.setString(2, event.type());
            statement.setObject(3, jsonb(json));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo registrar el evento de estado.");
        }
        return key.longValue();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar el evento.", exception);
        }
    }

    private static PGobject status(DocumentStatus estado) {
        try {
            PGobject value = new PGobject();
            value.setType("document_status");
            value.setValue(estado.name());
            return value;
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static PGobject jsonb(String json) throws SQLException {
        PGobject value = new PGobject();
        value.setType("jsonb");
        value.setValue(json);
        return value;
    }

    private StoredDocument map(ResultSet rs) throws SQLException {
        Array array = rs.getArray("etiquetas");
        String[] tags = array == null ? new String[0] : (String[]) array.getArray();
        Timestamp created = rs.getTimestamp("creado_en");
        return new StoredDocument(
                rs.getObject("id", UUID.class),
                rs.getString("titulo"),
                rs.getString("autor"),
                rs.getString("categoria"),
                Arrays.asList(tags),
                rs.getString("version"),
                rs.getString("nombre_archivo"),
                rs.getString("mime_type"),
                rs.getLong("tamano_bytes"),
                DocumentStatus.valueOf(rs.getString("estado")),
                rs.getString("error_msg"),
                created.toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    private record Pending(long id, String payload) {
    }
}
