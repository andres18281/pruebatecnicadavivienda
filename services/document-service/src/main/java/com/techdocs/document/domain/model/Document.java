package com.techdocs.document.domain.model;

import com.techdocs.contracts.DocumentStatus;
import com.techdocs.document.domain.errors.DomainException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public final class Document {

    private final UUID id;
    private final String titulo;
    private final String autor;
    private final String categoria;
    private final List<String> etiquetas;
    private final String version;
    private final String nombreArchivo;
    private final String mimeType;
    private final long tamanoBytes;
    private final String hashSha256;
    private final String storageKey;
    private DocumentStatus estado;
    private String errorMsg;
    private int intentos;
    private final OffsetDateTime creadoEn;
    private OffsetDateTime actualizadoEn;

    private Document(
            UUID id,
            String titulo,
            String autor,
            String categoria,
            List<String> etiquetas,
            String version,
            String nombreArchivo,
            String mimeType,
            long tamanoBytes,
            String hashSha256,
            String storageKey,
            DocumentStatus estado,
            String errorMsg,
            int intentos,
            OffsetDateTime creadoEn,
            OffsetDateTime actualizadoEn
    ) {
        this.id = id;
        this.titulo = titulo;
        this.autor = autor;
        this.categoria = categoria;
        this.etiquetas = List.copyOf(etiquetas);
        this.version = version;
        this.nombreArchivo = nombreArchivo;
        this.mimeType = mimeType;
        this.tamanoBytes = tamanoBytes;
        this.hashSha256 = hashSha256;
        this.storageKey = storageKey;
        this.estado = estado;
        this.errorMsg = errorMsg;
        this.intentos = intentos;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    public static Document register(
            UUID id,
            Metadata metadata,
            String nombreArchivo,
            String mimeType,
            long tamanoBytes,
            String hashSha256,
            String storageKey
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new Document(
                id,
                metadata.titulo(),
                metadata.autor(),
                metadata.categoria(),
                metadata.etiquetas(),
                metadata.version(),
                nombreArchivo,
                mimeType,
                tamanoBytes,
                hashSha256,
                storageKey,
                DocumentStatus.PROCESANDO,
                null,
                0,
                now,
                now
        );
    }

    public static Document rehydrate(
            UUID id,
            String titulo,
            String autor,
            String categoria,
            List<String> etiquetas,
            String version,
            String nombreArchivo,
            String mimeType,
            long tamanoBytes,
            String hashSha256,
            String storageKey,
            DocumentStatus estado,
            String errorMsg,
            int intentos,
            OffsetDateTime creadoEn,
            OffsetDateTime actualizadoEn
    ) {
        return new Document(
                id, titulo, autor, categoria, etiquetas, version, nombreArchivo, mimeType,
                tamanoBytes, hashSha256, storageKey, estado, errorMsg, intentos, creadoEn, actualizadoEn
        );
    }

    public void markIndexed() {
        if (estado != DocumentStatus.PROCESANDO) {
            throw new DomainException.InvalidTransition("Solo un documento en PROCESANDO puede pasar a INDEXADO.");
        }
        estado = DocumentStatus.INDEXADO;
        errorMsg = null;
        actualizadoEn = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markFailed(String message, int attempts) {
        if (estado == DocumentStatus.INDEXADO) {
            throw new DomainException.InvalidTransition("Un documento INDEXADO no puede pasar a ERROR.");
        }
        estado = DocumentStatus.ERROR;
        errorMsg = message;
        intentos = attempts;
        actualizadoEn = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID id() { return id; }
    public String titulo() { return titulo; }
    public String autor() { return autor; }
    public String categoria() { return categoria; }
    public List<String> etiquetas() { return etiquetas; }
    public String version() { return version; }
    public String nombreArchivo() { return nombreArchivo; }
    public String mimeType() { return mimeType; }
    public long tamanoBytes() { return tamanoBytes; }
    public String hashSha256() { return hashSha256; }
    public String storageKey() { return storageKey; }
    public DocumentStatus estado() { return estado; }
    public String errorMsg() { return errorMsg; }
    public int intentos() { return intentos; }
    public OffsetDateTime creadoEn() { return creadoEn; }
    public OffsetDateTime actualizadoEn() { return actualizadoEn; }
}
