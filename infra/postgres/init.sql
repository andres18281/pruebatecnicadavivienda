CREATE TYPE document_status AS ENUM ('PROCESANDO', 'INDEXADO', 'ERROR');

CREATE TABLE documents (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo         VARCHAR(300)  NOT NULL,
    autor          VARCHAR(200)  NOT NULL,
    categoria      VARCHAR(100)  NOT NULL,
    etiquetas      TEXT[]        NOT NULL DEFAULT '{}',
    version        VARCHAR(50)   NOT NULL,
    nombre_archivo VARCHAR(255)  NOT NULL,
    mime_type      VARCHAR(100)  NOT NULL,
    tamano_bytes   BIGINT        NOT NULL,
    hash_sha256    CHAR(64)      NOT NULL,
    storage_key    TEXT          NOT NULL UNIQUE,
    estado         document_status NOT NULL DEFAULT 'PROCESANDO',
    error_msg      TEXT,
    intentos       INT           NOT NULL DEFAULT 0,
    creado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_estado    ON documents (estado);
CREATE INDEX idx_documents_hash      ON documents (hash_sha256);
CREATE INDEX idx_documents_categoria ON documents (categoria);
CREATE INDEX idx_documents_creado    ON documents (creado_en DESC);

CREATE TABLE outbox_events (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       JSONB        NOT NULL,
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_en  TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pendientes ON outbox_events (id) WHERE publicado_en IS NULL;

CREATE TABLE telemetry_logs (
    id          BIGSERIAL PRIMARY KEY,
    event_id    UUID NOT NULL UNIQUE,
    service     VARCHAR(100) NOT NULL,
    level       VARCHAR(20) NOT NULL,
    logger      VARCHAR(300),
    message     TEXT NOT NULL,
    trace_id    VARCHAR(64),
    span_id     VARCHAR(32),
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_telemetry_logs_trace ON telemetry_logs (trace_id);
CREATE INDEX idx_telemetry_logs_occurred ON telemetry_logs (occurred_at DESC);

CREATE TABLE telemetry_traces (
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
);
CREATE INDEX idx_telemetry_traces_trace ON telemetry_traces (trace_id);
CREATE INDEX idx_telemetry_traces_started ON telemetry_traces (started_at DESC);
