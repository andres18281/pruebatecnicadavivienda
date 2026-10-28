package com.techdocs.document.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdocs.contracts.DocumentProcessCommand;
import com.techdocs.contracts.DocumentProgressEvent;
import com.techdocs.contracts.DocumentStatus;
import com.techdocs.document.application.ports.out.DocumentRepository;
import com.techdocs.document.domain.errors.DomainException;
import com.techdocs.document.domain.model.Document;
import com.techdocs.document.infrastructure.messaging.RabbitEventPublisher;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
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
public class PostgresDocumentRepository implements DocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresDocumentRepository.class);

    private static final String INSERT_DOCUMENT = """
            INSERT INTO documents (
                id, titulo, autor, categoria, etiquetas, version,
                nombre_archivo, mime_type, tamano_bytes, hash_sha256,
                storage_key, estado, error_msg, intentos, creado_en, actualizado_en
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_OUTBOX = """
            INSERT INTO outbox_events (aggregate_id, event_type, payload)
            VALUES (?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, titulo, autor, categoria, etiquetas, version, nombre_archivo, mime_type,
                   tamano_bytes, hash_sha256, storage_key, estado, error_msg, intentos,
                   creado_en, actualizado_en
            FROM documents
            WHERE id = ?
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RabbitEventPublisher publisher;

    public PostgresDocumentRepository(JdbcTemplate jdbc, ObjectMapper objectMapper, RabbitEventPublisher publisher) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
    }

    @Override
    @Transactional
    public void saveNew(Document document, DocumentProcessCommand command) {
        try {
            jdbc.update(connection -> bindDocument(connection.prepareStatement(INSERT_DOCUMENT), document));
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException.Conflict("Ya existe un documento con la misma clave de almacenamiento.");
        }
        long outboxId = insertOutbox(document.id(), "document.process", command);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishNow(outboxId, command);
            }
        });
    }

    @Override
    public Optional<Document> findById(UUID id) {
        List<Document> rows = jdbc.query(SELECT_BY_ID, (rs, rowNum) -> map(rs), id);
        return rows.stream().findFirst();
    }

    private void publishNow(long outboxId, DocumentProcessCommand command) {
        try {
            publisher.publishProcess(command);
            jdbc.update(
                    "UPDATE outbox_events SET publicado_en = now() WHERE id = ? AND publicado_en IS NULL",
                    outboxId
            );
        } catch (RuntimeException exception) {
            log.warn("El evento {} quedo pendiente en el outbox: {}", outboxId, exception.getMessage());
            return;
        }
        try {
            publisher.publishProgress(DocumentProgressEvent.of(
                    command.documentId(),
                    "QUEUED",
                    15,
                    "Documento en cola de procesamiento"
            ));
        } catch (RuntimeException exception) {
            log.debug("No se pudo publicar el progreso inicial de {}: {}", command.documentId(), exception.getMessage());
        }
    }

    private long insertOutbox(UUID aggregateId, String eventType, Object payload) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        String json = toJson(payload);
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT_OUTBOX, new String[]{"id"});
            statement.setObject(1, aggregateId);
            statement.setString(2, eventType);
            statement.setObject(3, jsonb(json));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo registrar el evento de outbox.");
        }
        return key.longValue();
    }

    private PreparedStatement bindDocument(PreparedStatement statement, Document document) throws SQLException {
        statement.setObject(1, document.id());
        statement.setString(2, document.titulo());
        statement.setString(3, document.autor());
        statement.setString(4, document.categoria());
        statement.setArray(5, textArray(statement, document.etiquetas()));
        statement.setString(6, document.version());
        statement.setString(7, document.nombreArchivo());
        statement.setString(8, document.mimeType());
        statement.setLong(9, document.tamanoBytes());
        statement.setString(10, document.hashSha256());
        statement.setString(11, document.storageKey());
        statement.setObject(12, status(document.estado()));
        statement.setString(13, document.errorMsg());
        statement.setInt(14, document.intentos());
        statement.setObject(15, document.creadoEn());
        statement.setObject(16, document.actualizadoEn());
        return statement;
    }

    private Array textArray(PreparedStatement statement, List<String> etiquetas) throws SQLException {
        return statement.getConnection().createArrayOf("text", etiquetas.toArray(String[]::new));
    }

    private static PGobject status(DocumentStatus estado) throws SQLException {
        PGobject value = new PGobject();
        value.setType("document_status");
        value.setValue(estado.name());
        return value;
    }

    private static PGobject jsonb(String json) throws SQLException {
        PGobject value = new PGobject();
        value.setType("jsonb");
        value.setValue(json);
        return value;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar el evento.", exception);
        }
    }

    private Document map(ResultSet rs) throws SQLException {
        Array array = rs.getArray("etiquetas");
        String[] tags = array == null ? new String[0] : (String[]) array.getArray();
        Timestamp created = rs.getTimestamp("creado_en");
        Timestamp updated = rs.getTimestamp("actualizado_en");
        return Document.rehydrate(
                rs.getObject("id", UUID.class),
                rs.getString("titulo"),
                rs.getString("autor"),
                rs.getString("categoria"),
                Arrays.asList(tags),
                rs.getString("version"),
                rs.getString("nombre_archivo"),
                rs.getString("mime_type"),
                rs.getLong("tamano_bytes"),
                rs.getString("hash_sha256").trim(),
                rs.getString("storage_key"),
                DocumentStatus.valueOf(rs.getString("estado")),
                rs.getString("error_msg"),
                rs.getInt("intentos"),
                created.toInstant().atOffset(ZoneOffset.UTC),
                updated.toInstant().atOffset(ZoneOffset.UTC)
        );
    }
}
