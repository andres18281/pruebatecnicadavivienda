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
