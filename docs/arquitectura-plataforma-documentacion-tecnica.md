# Plataforma de Documentación Técnica: Arquitectura con Docker Compose

Sistema de carga, procesamiento e indexación en tiempo real de documentación técnica (manuales, especificaciones, guías de arquitectura) con búsqueda full-text ultra rápida.

**Stack:** PostgreSQL · Elasticsearch · RabbitMQ · MinIO (bucket local) · Nginx (API Gateway) · Prometheus · Grafana · Loki · Jaeger · OpenTelemetry

---

## 1. Resumen de decisiones

| Requisito | Decisión |
|---|---|
| Carga individual y masiva con metadatos | `document-service` (REST multipart, respuesta `202` asíncrona con estado `PROCESANDO`) |
| Full-Text Search (400 ms a 1000 ms) | **Elasticsearch** con analizador en español, `simple_query_string`, highlighting y paginación |
| Prohibido `LIKE` | PostgreSQL **no se usa para buscar**: solo guarda metadatos, estado y trazabilidad. Toda búsqueda va a Elasticsearch |
| Notificación sin polling | Worker publica evento en RabbitMQ → `notification-service` → **WebSocket** al frontend |
| Procesamiento pesado en segundo plano | Cola **RabbitMQ** con reintentos y Dead Letter Queue |
| Bucket local | **MinIO** (compatible S3) |
| API Gateway con rate limit | **Nginx** (`limit_req` / `limit_conn`) |
| Red privada | Redes Docker `internal: true`; solo el gateway (y Grafana en localhost) tocan el exterior |
| Sin root | Todos los contenedores con `user:` no root, `cap_drop: ALL`, `no-new-privileges`, filesystem de solo lectura donde es posible |
| Monitoreo, métricas y trazas | Prometheus + Grafana + Loki (logs) + Jaeger (trazas) vía OpenTelemetry Collector |
| Arquitectura interna | **Hexagonal** (puertos y adaptadores) + Clean Code en cada microservicio |

> **Sobre el rango "400 ms a 1000 ms":** se interpreta como un SLA de **latencia máxima de 1 s (p95 ≤ 1000 ms)**. Elasticsearch normalmente responde muy por debajo de 400 ms; el rango se vigila con alertas en Prometheus.

---

## 2. Diagrama de arquitectura

```mermaid
flowchart TB
    U[Usuario / Navegador]

    subgraph EDGE["Red edge (única expuesta)"]
        GW["API Gateway<br/>Nginx + Rate Limit"]
    end

    subgraph APP["Red app-net (privada)"]
        FE["frontend<br/>(SPA estática)"]
        DOC["document-service<br/>REST: carga, detalle, estado"]
        SRCH["search-service<br/>REST: búsqueda"]
        NOTI["notification-service<br/>WebSocket"]
        WRK["processing-worker<br/>extracción + indexación"]
    end

    subgraph DATA["Red data-net (privada)"]
        PG[("PostgreSQL<br/>fuente de verdad")]
        ES[("Elasticsearch<br/>índice full-text")]
        MQ{{"RabbitMQ"}}
        S3[("MinIO<br/>bucket local")]
    end

    subgraph OBS["Red obs-net (privada)"]
        OTEL["OTel Collector"]
        PROM["Prometheus"]
        LOKI["Loki"]
        JAE["Jaeger"]
        GRAF["Grafana"]
    end

    U -->|HTTP / WS| GW
    GW --> FE
    GW -->|"/api/v1/documents"| DOC
    GW -->|"/api/v1/search"| SRCH
    GW -->|"/ws"| NOTI

    DOC --> S3
    DOC --> PG
    DOC -->|"document.process"| MQ
    DOC -->|"GET detalle"| ES
    SRCH --> ES
    MQ -->|"documents.process"| WRK
    WRK --> S3
    WRK --> ES
    WRK --> PG
    WRK -->|"document.status.changed"| MQ
    MQ -->|"eventos"| NOTI
    NOTI -->|push| U

    DOC & SRCH & WRK & NOTI -.OTLP.-> OTEL
    OTEL --> LOKI
    OTEL --> JAE
    PROM -.scrape.-> DOC & SRCH & WRK & NOTI & MQ & ES & PG
    GRAF --> PROM & LOKI & JAE
```

### Flujo de carga y notificación

```mermaid
sequenceDiagram
    participant C as Cliente
    participant G as Gateway
    participant D as document-service
    participant S as MinIO
    participant P as PostgreSQL
    participant Q as RabbitMQ
    participant W as worker
    participant E as Elasticsearch
    participant N as notification-service

    C->>G: POST /api/v1/documents (multipart + metadatos)
    G->>D: (rate limit OK)
    D->>D: Validar formato, tamaño y metadatos
    D->>S: Guardar archivo
    D->>P: INSERT documento (estado=PROCESANDO) + outbox
    D->>Q: publicar document.process
    D-->>C: 202 {id, estado: PROCESANDO}
    C->>G: Conexión WebSocket /ws (suscripción al id)
    Q->>W: entregar tarea
    W->>S: Descargar archivo
    W->>W: Extraer texto (TXT/MD/PDF)
    W->>E: Indexar (id idempotente)
    W->>P: UPDATE estado=INDEXADO
    W->>Q: publicar document.status.changed
    Q->>N: evento
    N-->>C: push WS {id, estado: INDEXADO}
```

---

## 3. Estructura del repositorio

```
tech-docs-platform/
├── docker-compose.yml
├── .env.example
├── scripts/
│   └── init-dirs.sh
├── infra/
│   ├── gateway/default.conf
│   ├── postgres/init.sql
│   ├── elasticsearch/
│   │   ├── init-index.sh
│   │   └── mapping.json
│   ├── otel/config.yaml
│   ├── prometheus/
│   │   ├── prometheus.yml
│   │   └── alerts.yml
│   ├── loki/loki.yaml
│   └── grafana/provisioning/datasources/datasources.yaml
├── data/
│   └── minio/                  # bucket local (bind mount)
├── frontend/                   # SPA (búsqueda, visor, notificaciones)
├── services/
│   ├── document-service/
│   ├── search-service/
│   ├── processing-worker/
│   └── notification-service/
└── libs/
    └── contracts/              # esquemas de eventos y DTOs compartidos
```

---

## 4. `.env.example`

```dotenv
# PostgreSQL
POSTGRES_DB=techdocs
POSTGRES_USER=techdocs_app
POSTGRES_PASSWORD=change_me_pg

# Elasticsearch
ELASTIC_PASSWORD=change_me_es

# RabbitMQ
RABBITMQ_DEFAULT_USER=techdocs
RABBITMQ_DEFAULT_PASS=change_me_mq

# MinIO
MINIO_ROOT_USER=techdocs_minio
MINIO_ROOT_PASSWORD=change_me_minio_min8chars

# Grafana
GRAFANA_ADMIN_PASSWORD=change_me_grafana

# Límites de carga
MAX_UPLOAD_MB=50
```

> Copia a `.env` y **cambia todas las contraseñas**. Para producción usa Docker secrets o un gestor de secretos (Vault, etc.) y crea usuarios de mínimo privilegio por servicio en lugar de reutilizar las credenciales administrativas.

---

## 5. `docker-compose.yml`

```yaml
name: tech-docs-platform

# ───────────── Bloques reutilizables ─────────────
x-hardened: &hardened
  restart: unless-stopped
  security_opt:
    - no-new-privileges:true
  cap_drop:
    - ALL
  logging:
    driver: json-file
    options:
      max-size: "10m"
      max-file: "3"

x-app-service: &app-service
  <<: *hardened
  user: "10001:10001"
  read_only: true
  tmpfs:
    - /tmp

x-otel-env: &otel-env
  OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
  OTEL_EXPORTER_OTLP_PROTOCOL: http/protobuf
  OTEL_TRACES_SAMPLER: parentbased_traceidratio
  OTEL_TRACES_SAMPLER_ARG: "1.0"

x-app-healthcheck: &app-healthcheck
  test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:8080/health/ready >/dev/null || exit 1"]
  interval: 10s
  timeout: 3s
  retries: 6
  start_period: 20s

# ───────────── Servicios ─────────────
services:

  # ═════════ Borde ═════════
  gateway:
    <<: *hardened
    image: nginxinc/nginx-unprivileged:1.27-alpine
    user: "101:101"
    read_only: true
    tmpfs:
      - /tmp
    ports:
      - "8080:8080"          # único punto de entrada de la aplicación
    volumes:
      - ./infra/gateway/default.conf:/etc/nginx/conf.d/default.conf:ro
    networks: [edge, app-net]
    depends_on:
      frontend:             { condition: service_healthy }
      document-service:     { condition: service_healthy }
      search-service:       { condition: service_healthy }
      notification-service: { condition: service_healthy }

  frontend:
    <<: *hardened
    build: ./frontend            # build multi-stage → nginx-unprivileged sirviendo /usr/share/nginx/html
    user: "101:101"
    read_only: true
    tmpfs:
      - /tmp
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1"]
      interval: 10s
      timeout: 3s
      retries: 5
    networks: [app-net]

  # ═════════ Microservicios ═════════
  document-service:
    <<: *app-service
    build: ./services/document-service
    environment:
      <<: *otel-env
      OTEL_SERVICE_NAME: document-service
      PORT: "8080"
      DATABASE_URL: postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB}
      RABBITMQ_URL: amqp://${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}@rabbitmq:5672
      S3_ENDPOINT: http://minio:9000
      S3_BUCKET: documents
      S3_ACCESS_KEY: ${MINIO_ROOT_USER}
      S3_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
      ELASTICSEARCH_URL: http://elasticsearch:9200
      ELASTICSEARCH_USER: elastic
      ELASTICSEARCH_PASSWORD: ${ELASTIC_PASSWORD}
      ELASTICSEARCH_INDEX: documents
      MAX_UPLOAD_MB: ${MAX_UPLOAD_MB}
      ALLOWED_MIME_TYPES: text/plain,text/markdown,application/pdf
    healthcheck: *app-healthcheck
    networks: [app-net, data-net, obs-net]
    depends_on:
      postgres:      { condition: service_healthy }
      rabbitmq:      { condition: service_healthy }
      elasticsearch: { condition: service_healthy }
      minio-init:    { condition: service_completed_successfully }

  search-service:
    <<: *app-service
    build: ./services/search-service
    environment:
      <<: *otel-env
      OTEL_SERVICE_NAME: search-service
      PORT: "8080"
      ELASTICSEARCH_URL: http://elasticsearch:9200
      ELASTICSEARCH_USER: elastic
      ELASTICSEARCH_PASSWORD: ${ELASTIC_PASSWORD}
      ELASTICSEARCH_INDEX: documents
      SEARCH_TIMEOUT_MS: "900"       # corta consultas antes del límite de 1 s
      SEARCH_MAX_PAGE_SIZE: "50"
    healthcheck: *app-healthcheck
    networks: [app-net, data-net, obs-net]
    depends_on:
      elasticsearch: { condition: service_healthy }
      es-init:       { condition: service_completed_successfully }

  processing-worker:
    <<: *app-service
    build: ./services/processing-worker
    environment:
      <<: *otel-env
      OTEL_SERVICE_NAME: processing-worker
      METRICS_PORT: "9100"
      DATABASE_URL: postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB}
      RABBITMQ_URL: amqp://${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}@rabbitmq:5672
      RABBITMQ_PREFETCH: "2"         # máx. documentos simultáneos por worker
      MAX_RETRIES: "3"
      S3_ENDPOINT: http://minio:9000
      S3_BUCKET: documents
      S3_ACCESS_KEY: ${MINIO_ROOT_USER}
      S3_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
      ELASTICSEARCH_URL: http://elasticsearch:9200
      ELASTICSEARCH_USER: elastic
      ELASTICSEARCH_PASSWORD: ${ELASTIC_PASSWORD}
      ELASTICSEARCH_INDEX: documents
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:9100/health/ready >/dev/null || exit 1"]
      interval: 10s
      timeout: 3s
      retries: 6
      start_period: 20s
    networks: [data-net, obs-net]
    deploy:
      replicas: 2                    # escalable horizontalmente
    depends_on:
      postgres:      { condition: service_healthy }
      rabbitmq:      { condition: service_healthy }
      elasticsearch: { condition: service_healthy }
      minio-init:    { condition: service_completed_successfully }
      es-init:       { condition: service_completed_successfully }

  notification-service:
    <<: *app-service
    build: ./services/notification-service
    environment:
      <<: *otel-env
      OTEL_SERVICE_NAME: notification-service
      PORT: "8080"
      RABBITMQ_URL: amqp://${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}@rabbitmq:5672
      WS_PATH: /ws
      WS_HEARTBEAT_SECONDS: "25"
    healthcheck: *app-healthcheck
    networks: [app-net, data-net, obs-net]
    depends_on:
      rabbitmq: { condition: service_healthy }

  # ═════════ Datos ═════════
  postgres:
    <<: *hardened
    image: postgres:16-alpine
    user: "70:70"
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./infra/postgres/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks: [data-net]

  elasticsearch:
    <<: *hardened
    image: docker.elastic.co/elasticsearch/elasticsearch:8.15.3
    user: "1000:0"
    environment:
      node.name: es01
      cluster.name: techdocs-cluster
      discovery.type: single-node
      ELASTIC_PASSWORD: ${ELASTIC_PASSWORD}
      xpack.security.enabled: "true"
      xpack.security.http.ssl.enabled: "false"   # tráfico solo dentro de red interna
      ES_JAVA_OPTS: "-Xms1g -Xmx1g"
    ulimits:
      nofile: { soft: 65535, hard: 65535 }
    mem_limit: 2g
    volumes:
      - esdata:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -s -u elastic:$${ELASTIC_PASSWORD} http://127.0.0.1:9200/_cluster/health | grep -Eq '\"status\":\"(green|yellow)\"'"]
      interval: 15s
      timeout: 10s
      retries: 20
      start_period: 40s
    networks: [data-net, obs-net]

  rabbitmq:
    <<: *hardened
    image: rabbitmq:3.13-management
    user: "999:999"
    hostname: rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_DEFAULT_USER}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_DEFAULT_PASS}
    volumes:
      - rabbitdata:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 30s
    networks: [data-net, obs-net]      # métricas en :15692 (plugin rabbitmq_prometheus)

  minio:
    <<: *hardened
    image: quay.io/minio/minio:RELEASE.2024-10-13T13-34-11Z
    user: "1000:1000"
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
      MINIO_PROMETHEUS_AUTH_TYPE: public
    volumes:
      - ./data/minio:/data            # bucket local; ejecutar scripts/init-dirs.sh antes
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks: [data-net, obs-net]

  # ═════════ Jobs de inicialización (one-shot, no root) ═════════
  minio-init:
    image: quay.io/minio/mc:latest
    user: "1000:1000"
    restart: "no"
    read_only: true
    tmpfs:
      - /tmp
    security_opt: ["no-new-privileges:true"]
    cap_drop: [ALL]
    environment:
      MC_CONFIG_DIR: /tmp/.mc
    entrypoint: >
      /bin/sh -c "
      mc alias set local http://minio:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD} &&
      mc mb --ignore-existing local/documents &&
      mc anonymous set none local/documents"
    networks: [data-net]
    depends_on:
      minio: { condition: service_healthy }

  es-init:
    image: curlimages/curl:8.10.1
    user: "100:101"
    restart: "no"
    read_only: true
    security_opt: ["no-new-privileges:true"]
    cap_drop: [ALL]
    environment:
      ELASTIC_PASSWORD: ${ELASTIC_PASSWORD}
    entrypoint: ["/bin/sh", "/init/init-index.sh"]
    volumes:
      - ./infra/elasticsearch:/init:ro
    networks: [data-net]
    depends_on:
      elasticsearch: { condition: service_healthy }

  # ═════════ Observabilidad ═════════
  otel-collector:
    <<: *hardened
    image: otel/opentelemetry-collector-contrib:0.111.0
    user: "10001:10001"
    command: ["--config=/etc/otelcol/config.yaml"]
    volumes:
      - ./infra/otel/config.yaml:/etc/otelcol/config.yaml:ro
    networks: [obs-net]
    depends_on: [loki, jaeger]

  prometheus:
    <<: *hardened
    image: prom/prometheus:v2.55.0
    user: "65534:65534"
    command:
      - --config.file=/etc/prometheus/prometheus.yml
      - --storage.tsdb.path=/prometheus
      - --storage.tsdb.retention.time=15d
    volumes:
      - ./infra/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./infra/prometheus/alerts.yml:/etc/prometheus/alerts.yml:ro
      - promdata:/prometheus
    networks: [obs-net]

  loki:
    <<: *hardened
    image: grafana/loki:3.2.0
    user: "10001:10001"
    command: ["-config.file=/etc/loki/loki.yaml"]
    volumes:
      - ./infra/loki/loki.yaml:/etc/loki/loki.yaml:ro
      - lokidata:/loki
    networks: [obs-net]

  jaeger:
    <<: *hardened
    image: jaegertracing/all-in-one:1.62.0
    user: "10001:10001"
    environment:
      COLLECTOR_OTLP_ENABLED: "true"
      MEMORY_MAX_TRACES: "50000"
    networks: [obs-net]

  grafana:
    <<: *hardened
    image: grafana/grafana:11.3.0
    user: "472:0"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD}
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - grafanadata:/var/lib/grafana
      - ./infra/grafana/provisioning:/etc/grafana/provisioning:ro
    ports:
      - "127.0.0.1:3000:3000"        # solo accesible desde la propia máquina
    networks: [edge, obs-net]
    depends_on: [prometheus, loki, jaeger]

  postgres-exporter:
    <<: *hardened
    image: quay.io/prometheuscommunity/postgres-exporter:v0.15.0
    user: "65534:65534"
    environment:
      DATA_SOURCE_NAME: postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB}?sslmode=disable
    networks: [data-net, obs-net]
    depends_on:
      postgres: { condition: service_healthy }

  elasticsearch-exporter:
    <<: *hardened
    image: quay.io/prometheuscommunity/elasticsearch-exporter:v1.8.0
    user: "65534:65534"
    command:
      - --es.uri=http://elastic:${ELASTIC_PASSWORD}@elasticsearch:9200
      - --es.all
    networks: [data-net, obs-net]
    depends_on:
      elasticsearch: { condition: service_healthy }

# ───────────── Redes ─────────────
networks:
  edge:
    driver: bridge                 # única con salida/entrada desde el host
  app-net:
    driver: bridge
    internal: true                 # gateway ↔ frontend ↔ microservicios
  data-net:
    driver: bridge
    internal: true                 # microservicios ↔ PostgreSQL / ES / RabbitMQ / MinIO
  obs-net:
    driver: bridge
    internal: true                 # telemetría y monitoreo

# ───────────── Volúmenes ─────────────
volumes:
  pgdata:
  esdata:
  rabbitdata:
  promdata:
  lokidata:
  grafanadata:
```

### Segmentación de red

| Servicio | edge | app-net | data-net | obs-net |
|---|:-:|:-:|:-:|:-:|
| gateway | ✅ | ✅ | | |
| frontend | | ✅ | | |
| document-service | | ✅ | ✅ | ✅ |
| search-service | | ✅ | ✅ | ✅ |
| notification-service | | ✅ | ✅ | ✅ |
| processing-worker | | | ✅ | ✅ |
| PostgreSQL / MinIO / RabbitMQ / Elasticsearch | | | ✅ | (ES, RabbitMQ, MinIO) |
| Prometheus / Loki / Jaeger / OTel | | | | ✅ |
| Grafana | ✅ (solo `127.0.0.1`) | | | ✅ |

- Las bases de datos **nunca** están en una red con salida a internet ni publican puertos.
- El frontend y el worker no se ven entre sí; el worker ni siquiera está en `app-net`.
- Una red `internal: true` no permite publicar puertos, por eso el gateway y Grafana se conectan además a `edge`.

---

## 6. Scripts y configuración de infraestructura

### `scripts/init-dirs.sh`

Los bind mounts deben pertenecer al UID del contenedor no root (MinIO usa `1000`).

```bash
#!/usr/bin/env bash
set -euo pipefail
mkdir -p data/minio
sudo chown -R 1000:1000 data/minio
echo "Directorios listos."
# Elasticsearch requiere en el host:
#   sudo sysctl -w vm.max_map_count=262144
```

### `infra/gateway/default.conf` (API Gateway + Rate Limit)

```nginx
# ── Zonas de rate limiting ──
limit_req_zone $binary_remote_addr zone=rl_general:10m rate=20r/s;
limit_req_zone $binary_remote_addr zone=rl_search:10m  rate=10r/s;

# Solo cuenta las cargas (POST); GET de detalle usa la zona general
map $request_method $upload_key {
    POST    $binary_remote_addr;
    default "";
}
limit_req_zone $upload_key zone=rl_upload:10m rate=2r/s;

limit_conn_zone $binary_remote_addr zone=conn_ws:10m;

limit_req_status  429;
limit_conn_status 429;

# ── Upstreams (red privada app-net) ──
upstream frontend      { server frontend:8080; keepalive 8; }
upstream document_svc  { server document-service:8080; keepalive 16; }
upstream search_svc    { server search-service:8080; keepalive 32; }
upstream notify_svc    { server notification-service:8080; }

map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

server {
    listen 8080;
    server_tokens off;

    # Tamaño máximo = archivo (50 MB) + metadatos/overhead multipart.
    # Para carga masiva, subir en lotes pequeños o aumentar este límite.
    client_max_body_size 55m;

    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Referrer-Policy "no-referrer" always;

    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Request-Id $request_id;

    # ── Búsqueda: tiempo máximo acotado ──
    location /api/v1/search {
        limit_req zone=rl_search burst=20 nodelay;
        proxy_read_timeout 2s;
        proxy_pass http://search_svc;
    }

    # ── Carga / detalle / estado de documentos ──
    location /api/v1/documents {
        limit_req zone=rl_upload  burst=5  nodelay;
        limit_req zone=rl_general burst=40 nodelay;
        proxy_read_timeout 60s;
        proxy_request_buffering on;
        proxy_pass http://document_svc;
    }

    # ── WebSocket de notificaciones ──
    location /ws {
        limit_conn conn_ws 10;                 # máx. 10 conexiones WS por IP
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_read_timeout 3600s;
        proxy_pass http://notify_svc;
    }

    # ── Frontend estático ──
    location / {
        limit_req zone=rl_general burst=40 nodelay;
        proxy_pass http://frontend;
    }
}
```

### `infra/postgres/init.sql`

PostgreSQL guarda metadatos y estado. **No se usa para búsqueda.**

```sql
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
    storage_key    TEXT          NOT NULL UNIQUE,   -- clave del objeto en MinIO
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

-- Patrón Transactional Outbox: garantiza que el evento se publique
-- si y solo si el cambio de estado fue confirmado en la base de datos.
CREATE TABLE outbox_events (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       JSONB        NOT NULL,
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_en  TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pendientes ON outbox_events (id) WHERE publicado_en IS NULL;
```

### `infra/elasticsearch/mapping.json`

```json
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "refresh_interval": "1s",
    "analysis": {
      "filter": {
        "spanish_stop":    { "type": "stop",    "stopwords": "_spanish_" },
        "spanish_stemmer": { "type": "stemmer", "language": "light_spanish" }
      },
      "analyzer": {
        "es_tecnico": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "asciifolding", "spanish_stop", "spanish_stemmer"]
        }
      }
    }
  },
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "id":             { "type": "keyword" },
      "titulo":         { "type": "text", "analyzer": "es_tecnico",
                          "fields": { "raw": { "type": "keyword", "ignore_above": 300 } } },
      "autor":          { "type": "text", "analyzer": "es_tecnico",
                          "fields": { "raw": { "type": "keyword" } } },
      "categoria":      { "type": "keyword" },
      "etiquetas":      { "type": "keyword" },
      "version":        { "type": "keyword" },
      "nombre_archivo": { "type": "keyword" },
      "mime_type":      { "type": "keyword" },
      "tamano_bytes":   { "type": "long" },
      "estado":         { "type": "keyword" },
      "creado_en":      { "type": "date" },
      "contenido":      { "type": "text", "analyzer": "es_tecnico", "index_options": "offsets" }
    }
  }
}
```

> En un cluster de 3 nodos usa `number_of_replicas: 1` o más. Con un solo nodo, `0` evita el estado `yellow`.

### `infra/elasticsearch/init-index.sh`

Crea el índice versionado y el alias `documents`, lo que permite reindexar sin downtime (`documents_v2` → mover alias).

```sh
#!/bin/sh
set -e
ES="http://elasticsearch:9200"
AUTH="elastic:${ELASTIC_PASSWORD}"

if curl -sf -u "$AUTH" -o /dev/null "$ES/_alias/documents"; then
  echo "Alias 'documents' ya existe. Nada que hacer."
  exit 0
fi

curl -sf -u "$AUTH" -X PUT "$ES/documents_v1" \
  -H 'Content-Type: application/json' -d @/init/mapping.json

curl -sf -u "$AUTH" -X POST "$ES/_aliases" \
  -H 'Content-Type: application/json' \
  -d '{"actions":[{"add":{"index":"documents_v1","alias":"documents"}}]}'

echo "Índice y alias creados."
```

### `infra/otel/config.yaml`

```yaml
receivers:
  otlp:
    protocols:
      grpc: { endpoint: 0.0.0.0:4317 }
      http: { endpoint: 0.0.0.0:4318 }

processors:
  memory_limiter:
    check_interval: 1s
    limit_mib: 256
  batch: {}

exporters:
  otlp/jaeger:
    endpoint: jaeger:4317
    tls: { insecure: true }
  otlphttp/loki:
    endpoint: http://loki:3100/otlp

service:
  telemetry:
    metrics:
      address: 0.0.0.0:8888
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlp/jaeger]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlphttp/loki]
```

> Los servicios envían **trazas y logs por OTLP**, de modo que no hace falta montar el socket de Docker ni leer `/var/lib/docker` (lo que obligaría a usar root). Los logs también salen por stdout en JSON, con rotación.

### `infra/loki/loki.yaml`

```yaml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  path_prefix: /loki
  replication_factor: 1
  ring:
    kvstore: { store: inmemory }
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules

schema_config:
  configs:
    - from: "2024-01-01"
      store: tsdb
      object_store: filesystem
      schema: v13
      index: { prefix: index_, period: 24h }

limits_config:
  allow_structured_metadata: true      # requerido para ingesta OTLP
  retention_period: 168h               # 7 días

compactor:
  working_directory: /loki/compactor
  retention_enabled: true
  delete_request_store: filesystem
```

### `infra/prometheus/prometheus.yml`

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - /etc/prometheus/alerts.yml

scrape_configs:
  - job_name: prometheus
    static_configs: [{ targets: ["localhost:9090"] }]

  - job_name: document-service
    metrics_path: /metrics
    static_configs: [{ targets: ["document-service:8080"], labels: { service: document-service } }]

  - job_name: search-service
    metrics_path: /metrics
    static_configs: [{ targets: ["search-service:8080"], labels: { service: search-service } }]

  - job_name: notification-service
    metrics_path: /metrics
    static_configs: [{ targets: ["notification-service:8080"], labels: { service: notification-service } }]

  - job_name: processing-worker
    metrics_path: /metrics
    dns_sd_configs:                       # descubre todas las réplicas del worker
      - names: ["processing-worker"]
        type: A
        port: 9100

  - job_name: rabbitmq
    static_configs: [{ targets: ["rabbitmq:15692"] }]

  - job_name: rabbitmq-queues             # métricas por cola
    metrics_path: /metrics/detailed
    params:
      family: [queue_coarse_metrics]
    static_configs: [{ targets: ["rabbitmq:15692"] }]

  - job_name: postgres
    static_configs: [{ targets: ["postgres-exporter:9187"] }]

  - job_name: elasticsearch
    static_configs: [{ targets: ["elasticsearch-exporter:9114"] }]

  - job_name: minio
    metrics_path: /minio/v2/metrics/cluster
    static_configs: [{ targets: ["minio:9000"] }]

  - job_name: otel-collector
    static_configs: [{ targets: ["otel-collector:8888"] }]
```

### `infra/prometheus/alerts.yml`

```yaml
groups:
  - name: techdocs-sla
    rules:
      - alert: SearchLatencyP95Alta
        expr: |
          histogram_quantile(0.95,
            sum by (le) (rate(http_server_request_duration_seconds_bucket{service="search-service"}[5m]))
          ) > 1
        for: 2m
        labels: { severity: critical }
        annotations:
          summary: "p95 de búsqueda supera 1 s (SLA incumplido)"

      - alert: SearchLatencyP99Alta
        expr: |
          histogram_quantile(0.99,
            sum by (le) (rate(http_server_request_duration_seconds_bucket{service="search-service"}[5m]))
          ) > 1
        for: 5m
        labels: { severity: warning }

      - alert: ColaProcesamientoAcumulada
        expr: rabbitmq_detailed_queue_messages_ready{queue="documents.process"} > 500
        for: 5m
        labels: { severity: warning }

      - alert: DeadLetterQueueConMensajes
        expr: rabbitmq_detailed_queue_messages{queue="documents.process.dlq"} > 0
        for: 1m
        labels: { severity: warning }
        annotations:
          summary: "Hay documentos que fallaron definitivamente (estado ERROR)"

      - alert: ServicioCaido
        expr: up == 0
        for: 1m
        labels: { severity: critical }
```

### `infra/grafana/provisioning/datasources/datasources.yaml`

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
  - name: Jaeger
    type: jaeger
    access: proxy
    url: http://jaeger:16686
```

Dashboards sugeridos: latencia p50/p95/p99 de búsqueda, throughput de carga, profundidad de colas, documentos por estado, tiempo de procesamiento por documento, salud de ES/PG/MinIO, y 429 emitidos por el gateway.

---

## 7. Arquitectura hexagonal por microservicio

Cada servicio aplica **puertos y adaptadores** con la regla de dependencia: `infrastructure → application → domain` (nunca al revés).

```
services/<nombre>/
├── Dockerfile
└── src/
    ├── domain/                       # Núcleo: sin frameworks ni librerías de I/O
    │   ├── model/                    #   Document, DocumentStatus, Metadata (value objects)
    │   ├── events/                   #   DocumentUploaded, DocumentStatusChanged
    │   └── errors/                   #   InvalidMetadataError, UnsupportedFormatError...
    ├── application/                  # Casos de uso
    │   ├── ports/
    │   │   ├── in/                   #   UploadDocumentUseCase, SearchDocumentsUseCase...
    │   │   └── out/                  #   DocumentRepository, FileStoragePort,
    │   │                             #   SearchIndexPort, EventPublisherPort,
    │   │                             #   TextExtractorPort
    │   └── usecases/                 #   Implementaciones (orquestan dominio + puertos)
    ├── infrastructure/               # Adaptadores
    │   ├── in/
    │   │   ├── http/                 #   Controladores REST, validación DTO, /health, /metrics
    │   │   ├── amqp/                 #   Consumidor RabbitMQ (worker)
    │   │   └── websocket/            #   Gateway WS (notification-service)
    │   └── out/
    │       ├── postgres/             #   PostgresDocumentRepository + Outbox
    │       ├── elasticsearch/        #   ElasticsearchIndexAdapter / SearchAdapter
    │       ├── storage/              #   MinioStorageAdapter (S3)
    │       ├── messaging/            #   RabbitEventPublisher
    │       └── extractors/           #   TxtExtractor, MarkdownExtractor, PdfExtractor
    └── bootstrap/                    # Composition root: instancia adaptadores e inyecta puertos
```

### Responsabilidad de cada microservicio

| Servicio | Casos de uso | Adaptadores de entrada | Adaptadores de salida |
|---|---|---|---|
| **document-service** | `UploadDocument`, `UploadBatch`, `GetDocumentDetail`, `GetDocumentStatus` | REST | PostgreSQL, MinIO, RabbitMQ, Elasticsearch (solo lectura por id) |
| **search-service** | `SearchDocuments` | REST | Elasticsearch |
| **processing-worker** | `ProcessDocument` (extraer → indexar → actualizar estado → emitir evento) | Consumidor AMQP | MinIO, Elasticsearch, PostgreSQL, RabbitMQ |
| **notification-service** | `ForwardStatusEvent`, `SubscribeToDocument` | WebSocket + consumidor AMQP | (ninguno persistente) |

`search-service` está aislado a propósito: la ruta crítica del SLA no comparte CPU ni memoria con la carga de archivos.

### Ejemplo de puerto y caso de uso (TypeScript, adaptable a Java/Go/Python)

```ts
// application/ports/out/file-storage.port.ts
export interface FileStoragePort {
  save(key: string, content: Buffer, mimeType: string): Promise<void>;
  get(key: string): Promise<Buffer>;
}

// application/ports/out/event-publisher.port.ts
export interface EventPublisherPort {
  publish(event: DomainEvent): Promise<void>;
}

// application/usecases/upload-document.usecase.ts
export class UploadDocumentService implements UploadDocumentUseCase {
  constructor(
    private readonly files: FileStoragePort,
    private readonly documents: DocumentRepository,
    private readonly events: EventPublisherPort,
    private readonly validator: UploadPolicy,
  ) {}

  async execute(cmd: UploadDocumentCommand): Promise<UploadReceipt> {
    this.validator.assertAllowed(cmd.file);               // formato y tamaño
    const document = Document.register(cmd.metadata, cmd.file); // estado PROCESANDO
    await this.files.save(document.storageKey, cmd.file.content, cmd.file.mimeType);
    await this.documents.saveWithOutbox(document, new DocumentUploaded(document.id));
    return { id: document.id, status: document.status };  // respuesta inmediata
  }
}
```

### Principios de Clean Code aplicados

- **Dominio puro:** reglas (transiciones de estado válidas `PROCESANDO → INDEXADO | ERROR`, validación de metadatos) sin dependencias técnicas.
- **Inversión de dependencias:** los casos de uso dependen de interfaces, no de Elasticsearch, RabbitMQ ni MinIO.
- **Un caso de uso = una responsabilidad**, funciones pequeñas, nombres que expresan intención.
- **Errores de dominio tipados**, traducidos a HTTP solo en el adaptador de entrada.
- **Idempotencia:** el worker indexa en ES con `_id = documentId`; reprocesar el mismo mensaje no duplica.
- **Configuración por variables de entorno** (12-factor), validada al arrancar.
- **Contratos compartidos** en `libs/contracts` (esquemas JSON de eventos y DTOs).
- **Pruebas:** unitarias de dominio y casos de uso con dobles de los puertos; integración de adaptadores con Testcontainers; pruebas de contrato de eventos; pruebas de arquitectura (dependency-cruiser / ArchUnit) que fallan si `domain` importa de `infrastructure`.

---

## 8. Topología de RabbitMQ

Cada servicio declara de forma idempotente las colas y exchanges que usa al arrancar.

| Elemento | Tipo | Detalle |
|---|---|---|
| `documents.commands` | exchange `direct` | routing key `document.process` |
| `documents.process` | cola durable | consumida por el worker; `x-dead-letter-exchange: documents.retry` |
| `documents.retry` | exchange `direct` | envía a `documents.process.retry` |
| `documents.process.retry` | cola con `x-message-ttl: 30000` | al expirar vuelve a `documents.commands` (reintento diferido) |
| `documents.process.dlq` | cola durable | tras `MAX_RETRIES` el mensaje se aparta aquí; el worker marca `ERROR` |
| `documents.events` | exchange `topic` | routing keys `document.status.changed` |
| cola exclusiva por instancia | cola anónima | cada `notification-service` enlaza la suya a `document.status.#` (permite escalar horizontalmente) |

**Mensaje de comando**

```json
{
  "eventId": "7c1f...",
  "type": "document.process",
  "documentId": "b3d1c0a2-...",
  "storageKey": "2026/10/b3d1c0a2-manual.pdf",
  "mimeType": "application/pdf",
  "occurredAt": "2026-10-28T15:04:05Z",
  "traceparent": "00-4bf92f35..."
}
```

**Evento de estado (llega al frontend por WebSocket)**

```json
{
  "type": "document.status.changed",
  "documentId": "b3d1c0a2-...",
  "status": "INDEXADO",
  "error": null,
  "occurredAt": "2026-10-28T15:04:09Z"
}
```

Buenas prácticas activadas: `prefetch` limitado, `ack` manual tras indexar y actualizar PG, *publisher confirms*, mensajes persistentes y propagación de `traceparent` para ver la traza completa en Jaeger.

---

## 9. Contratos de API

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `POST` | `/api/v1/documents` | Carga individual `multipart/form-data` (`file` + `titulo`, `autor`, `categoria`, `etiquetas`, `version`) | `202 {id, estado: "PROCESANDO"}` |
| `POST` | `/api/v1/documents/batch` | Carga masiva: `files[]` + `manifest` JSON con metadatos por archivo | `202 [{id, estado, nombreArchivo}]` |
| `GET` | `/api/v1/documents/{id}` | Detalle: metadatos (PostgreSQL) + contenido (Elasticsearch por id) | `200` |
| `GET` | `/api/v1/documents/{id}/status` | Estado puntual (uso excepcional, **no** para polling) | `200` |
| `GET` | `/api/v1/search?q=&page=&size=&categoria=&etiqueta=` | Búsqueda con paginación y highlighting | `200` |
| `WS` | `/ws` | Suscripción a eventos de estado | push de eventos |

**Validaciones en la carga:** extensión y *magic bytes* (TXT, MD, PDF), tamaño máximo (`MAX_UPLOAD_MB`), longitud y obligatoriedad de metadatos, cantidad de etiquetas, sanitización del nombre de archivo y cálculo de hash SHA-256. Errores: `400`, `413`, `415`, `422`, `429` (rate limit).

### Consulta de búsqueda en Elasticsearch (sin `LIKE`)

```json
GET /documents/_search
{
  "timeout": "900ms",
  "track_total_hits": 10000,
  "from": 0,
  "size": 10,
  "_source": { "excludes": ["contenido"] },
  "query": {
    "bool": {
      "must": {
        "simple_query_string": {
          "query": "\"guía de arquitectura\" microservicios",
          "fields": ["titulo^3", "etiquetas^2", "autor", "categoria", "contenido"],
          "default_operator": "and"
        }
      },
      "filter": [
        { "term": { "estado": "INDEXADO" } },
        { "term": { "categoria": "arquitectura" } }
      ]
    }
  },
  "highlight": {
    "pre_tags": ["<mark>"],
    "post_tags": ["</mark>"],
    "fields": {
      "titulo":    { "number_of_fragments": 0 },
      "contenido": { "fragment_size": 180, "number_of_fragments": 3 }
    }
  }
}
```

- `simple_query_string` soporta términos sueltos y **frases entre comillas**.
- Se excluye `contenido` del `_source` y se usa solo el highlighting, reduciendo el payload.
- `timeout` de 900 ms mantiene la respuesta dentro del SLA; si se agota, se devuelven resultados parciales con una marca en la respuesta.
- Para paginación profunda usar `search_after` en lugar de `from` grande.

---

## 10. Cumplimiento de requisitos

| Historia / regla | Cómo se cumple |
|---|---|
| **HU-01** Carga con metadatos | `document-service` con multipart, validaciones de formato/tamaño, respuesta `202` con ID de seguimiento y estado `PROCESANDO` |
| **HU-02** Búsqueda avanzada | `search-service` + Elasticsearch, `simple_query_string`, paginación, highlighting, `timeout` y alertas de p95 ≤ 1 s |
| **Restricción: sin `LIKE`** | Búsqueda exclusivamente en el motor de búsqueda; en CI se agrega una regla que falla si el código contiene `LIKE`/`ILIKE` en consultas SQL |
| **HU-03** Visor y detalle | `GET /documents/{id}`; el frontend renderiza Markdown sanitizado (DOMPurify) y texto de PDF por secciones junto a los metadatos completos |
| **HU-04** Tiempo real sin polling | Worker → RabbitMQ → `notification-service` → **WebSocket**; el frontend actualiza el estado de forma reactiva al recibir `INDEXADO` o `ERROR` |
| Red privada | Redes `internal: true`; solo el gateway publica puerto |
| Rate limit | Nginx: zonas separadas para búsqueda, carga y WebSocket (`429`) |
| Sin root | `user:` no root, `cap_drop: ALL`, `no-new-privileges`, `read_only` en servicios propios |
| Monitoreo y trazas | Prometheus + Grafana (métricas), Loki (logs), Jaeger (trazas) vía OpenTelemetry |

### Nota sobre el frontend (HU-03 y HU-04)

- Mantiene una conexión WebSocket única con reconexión exponencial y reenvío de suscripciones.
- Tras `POST` de carga, se suscribe al `documentId` y actualiza la UI al recibir el evento; no usa `setInterval` ni consultas periódicas.
- Si el WebSocket se reconecta, hace **una** consulta puntual de estado para resincronizar (no es polling).

---

## 11. Puesta en marcha

```bash
# 1. Variables
cp .env.example .env              # editar contraseñas

# 2. Preparar host
./scripts/init-dirs.sh
sudo sysctl -w vm.max_map_count=262144     # requerido por Elasticsearch

# 3. Levantar
docker compose up -d --build

# 4. Verificar
docker compose ps
curl -i http://localhost:8080/api/v1/search?q=arquitectura
```

| Recurso | URL |
|---|---|
| Aplicación (vía gateway) | http://localhost:8080 |
| Grafana (solo local) | http://localhost:3000 |

Prometheus, Jaeger, Loki, RabbitMQ, MinIO, PostgreSQL y Elasticsearch **no** son accesibles desde fuera; se consultan a través de Grafana o con `docker compose exec`.

### Dockerfile de ejemplo (no root, multi-stage)

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build && npm prune --omit=dev

FROM node:20-alpine
ENV NODE_ENV=production
WORKDIR /app
RUN addgroup -g 10001 app && adduser -D -u 10001 -G app app
COPY --from=build --chown=10001:10001 /app/dist ./dist
COPY --from=build --chown=10001:10001 /app/node_modules ./node_modules
USER 10001:10001
EXPOSE 8080
CMD ["node", "dist/main.js"]
```

---

## 12. Limitaciones y pasos hacia producción

- **Elasticsearch de un solo nodo:** sin alta disponibilidad. En producción usar 3 nodos, `number_of_replicas ≥ 1`, y crear usuarios/roles de mínimo privilegio (no usar `elastic` en las aplicaciones).
- **TLS interno:** desactivado dentro de las redes privadas por simplicidad. Para producción, habilitar TLS entre servicios y en el gateway (certificados válidos).
- **Autenticación y autorización:** no están en los requisitos; se recomienda JWT/OIDC en el gateway y control de acceso por documento, además de autenticar la conexión WebSocket.
- **Jaeger en memoria:** las trazas se pierden al reiniciar. Para persistir, usar almacenamiento en Elasticsearch/Badger o migrar a Tempo.
- **RabbitMQ y PostgreSQL de nodo único:** para alta disponibilidad, añadir cluster/quorum queues y réplicas de lectura con Patroni.
- **Imágenes de terceros:** fijar versiones por *digest*, escanear con Trivy/Grype y actualizarlas periódicamente.
- **Secretos:** migrar de `.env` a Docker secrets o Vault.
- **Pruebas de carga:** validar el SLA de 1 s con k6 o JMeter sobre un volumen realista de documentos antes de dar el sistema por aceptado.
- **Permisos de volúmenes:** los volúmenes con nombre heredan el propietario del directorio de la imagen; si algún contenedor falla por permisos, ajustar con `chown` en el host usando el UID indicado en `user:`.
