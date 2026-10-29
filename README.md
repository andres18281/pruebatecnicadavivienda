# TechDocs

Plataforma de documentacion tecnica: carga asincrona, indexacion en segundo plano, busqueda full-text y aviso en tiempo real.

Implementa las historias HU-01 a HU-04 con **Java 21 / Spring Boot** en los microservicios y **Angular** en el frontend. La arquitectura, la frontera de red y el uso de IA estan en `docs/architecture.md`, `docs/arquitectura-plataforma-documentacion-tecnica.md` y `docs/ia.md`.

El rango de 400 ms a 1000 ms es el SLA de latencia maxima de la busqueda (p95 <= 1 s). Elasticsearch suele responder por debajo de 400 ms. Prometheus alerta si el p95 supera 1 s.

## Como se cumple cada historia

| Historia | Implementacion |
|---|---|
| HU-01 Carga con metadatos | `POST /api/v1/documents` multipart. Valida extension, firma del archivo y tamano (10 MB). Responde `202` con `id` y `estado: PROCESANDO`. |
| HU-02 Busqueda | `search-service` consulta Elasticsearch (`simple_query_string`, analizador en espanol). Paginacion, highlighting y timeout de 900 ms. PostgreSQL no participa en la busqueda. |
| HU-03 Visor | `GET /api/v1/documents/{id}` devuelve metadatos y cuerpo. El frontend renderiza Markdown o el texto del PDF por paginas, sin descargar el archivo. |
| HU-04 Tiempo real | El worker publica `document.progress` y `document.status.changed` en RabbitMQ. `notification-service` los empuja por WebSocket `/ws`. La interfaz se actualiza sin polling. |

## Tecnologias y versiones

Los microservicios se construyen con Maven 3.9.9 y Java 21 (imagen `maven:3.9.9-eclipse-temurin-21`, runtime `eclipse-temurin:21-jre`). El frontend se construye con Node 20 y se sirve con Nginx. No hay llamadas HTTP de un microservicio a otro: el trabajo entre ellos pasa por RabbitMQ.

| Pieza | Version | Uso |
|---|---|---|
| Java | 21 | Runtime de los seis servicios. |
| Spring Boot | 3.4.5 | API, JDBC, AMQP, WebSocket, Actuator. |
| Angular | 19.2.14 | Carga, busqueda, visor y progreso. |
| TypeScript | 5.7 | Frontend. |
| Nginx | 1.27 (unprivileged) | Puerta de enlace y el contenedor del frontend. |
| PostgreSQL | 16 (Alpine) | Metadatos, estado, outbox y telemetria. |
| Elasticsearch | 8.15.3 | Indice full-text. |
| RabbitMQ | 3.13 (management) | Comandos, reintentos, eventos y telemetria. |
| Silo (API S3, compatible con MinIO) | `RELEASE.2026-10-26` | Bucket `documents`. |
| OpenTelemetry Java agent | 2.11.0 | Trazas y logs OTLP de cada JVM. |
| OpenTelemetry Collector | 0.111.0 | Recibe OTLP y reenvia a Jaeger y Loki. |
| Prometheus | 2.55.0 | Metricas y alertas. |
| Grafana | 11.3.0 | Tableros. |
| Loki | 3.2.0 | Logs OTLP. |
| Jaeger | 1.62.0 | Trazas OTLP. |
| cAdvisor | 0.49.1 | CPU y RAM de contenedores. |

### Librerias

| Libreria | Version | Donde |
|---|---|---|
| `elasticsearch-java` y REST client | 8.15.3 | `document-service`, `search-service`, `processing-worker`. |
| Cliente MinIO (`io.minio`) | 8.5.17 | `document-service` y el worker. Habla con Silo por la API S3. |
| Apache PDFBox | 3.0.4 | Extraccion de texto PDF en el worker. |
| Resilience4j | 2.2.0 | Circuit breaker de Elasticsearch y MinIO. Libreria propia `libs/resilience`. |
| Micrometer Prometheus | la que trae Spring Boot 3.4.5 | `GET /metrics` en cada servicio. |
| Driver JDBC PostgreSQL | la que trae Spring Boot 3.4.5 | `document-service`, worker y `telemetry-service`. Consultas con `?`, sin SQL armado por concatenacion. |
| `libs/contracts` | 1.0.0 | Eventos y DTOs compartidos (`document.process`, progreso, estado, logs y trazas). |
| `libs/telemetry-amqp` | 1.0.0 | Appender de Logback y observaciones Micrometer que publican en RabbitMQ. |
| ArchUnit | 1.3.0 | Test de arquitectura hexagonal en `document-service`. |
| DOMPurify | 3.2.6 | El visor limpia el HTML del Markdown. |
| marked | 15.0.12 | Markdown a HTML en el visor. |
| RxJS | 7.8 | Cliente HTTP y WebSocket del frontend. |

Maven Compiler Plugin 3.13.0 y Surefire 3.5.2. Node del frontend: 20.

### Como se comunican

El navegador solo habla con Nginx, por HTTP y por WebSocket, en el puerto 8080. Nginx reparte `/api/v1/documents`, `/api/v1/search`, `/api/v1/telemetry` y `/ws`. No hay Feign ni HTTP entre microservicios.

| De | A | Medio |
|---|---|---|
| Navegador | Nginx | HTTP y WebSocket. |
| Nginx | frontend, document, search, notification, telemetry | HTTP interno de Docker. El worker no esta en esa ruta. |
| `document-service` | PostgreSQL | JDBC. |
| `document-service` | Silo | Cliente S3 (`io.minio`). |
| `document-service` | Elasticsearch | Cliente Java, solo para el cuerpo del visor. La busqueda no pasa por este servicio. |
| `document-service` | RabbitMQ | Outbox: el comando `document.process` sale de PostgreSQL, no en el mismo hilo del `POST`. |
| RabbitMQ | `processing-worker` | Cola `documents.process`. Reintento a los 30 s. Dead letter al tercer fallo. |
| Worker | Silo, Elasticsearch, PostgreSQL | S3, cliente Elasticsearch y JDBC. |
| Worker | RabbitMQ | `document.progress` y `document.status.changed`. |
| RabbitMQ | `notification-service` | Esos eventos. El servicio los empuja por WebSocket. |
| `search-service` | Elasticsearch | Cliente Java. `simple_query_string`, timeout 900 ms. |
| Cada JVM (salvo telemetry) | RabbitMQ | Exchange `platform.telemetry`. Claves `telemetry.log` y `telemetry.trace`. |
| RabbitMQ | `telemetry-service` | Colas `telemetry.logs` y `telemetry.traces`. El servicio las guarda en PostgreSQL. |
| Agente OpenTelemetry | Collector | OTLP HTTP en el puerto 4318. |
| Collector | Jaeger y Loki | OTLP. |
| Prometheus | servicios, Postgres, Elasticsearch, cAdvisor | Scrape HTTP de `/metrics`. |

Los servicios se encuentran por el nombre de Docker (`postgres`, `elasticsearch`, `rabbitmq`, `minio`) dentro de `app-net`, `data-net` y `obs-net`. Esas redes son `internal: true`.

## Componentes

El navegador solo entra por Nginx (`8080`). El resto de los contenedores vive en redes Docker internas (`app-net`, `data-net`, `obs-net`) y no publica puertos. Grafana es la otra excepcion y queda atado a `127.0.0.1:3000`.

### Aplicacion

| Componente | Rol |
|---|---|
| `gateway` | Nginx. Sirve el frontend y enruta `/api/v1/documents`, `/api/v1/search`, `/api/v1/telemetry` y `/ws`. Aplica rate limit y cabeceras de seguridad. |
| `frontend` | Angular. Carga, busqueda, visor y barra de progreso. No tiene puerto propio en el host. |
| `document-service` | Recibe el archivo, lo guarda en MinIO, persiste el estado en PostgreSQL y publica el comando por el outbox. |
| `processing-worker` | Extrae TXT, Markdown o PDF, indexa en Elasticsearch y emite el progreso. Dos replicas. Reintenta hasta 3 veces y luego marca `ERROR`. |
| `search-service` | Unica ruta de busqueda. Aislada de la carga. |
| `notification-service` | WebSocket `/ws`. El cliente se suscribe con `{"action":"subscribe","documentId":"..."}`. |
| `telemetry-service` | Guarda logs y trazas recibidos por RabbitMQ. `GET /api/v1/telemetry/logs` y `GET /api/v1/telemetry/traces`. |

Cada servicio de aplicacion sigue puertos y adaptadores: el dominio no depende de Spring, Elasticsearch, RabbitMQ ni MinIO. Las llamadas sincronas a Elasticsearch y MinIO pasan por un circuit breaker. Con el circuito abierto la API responde `503` y el worker reencola el documento sin consumir los 3 reintentos.

### Datos

| Componente | Rol |
|---|---|
| PostgreSQL 16 | Fuente de verdad: metadatos, estado, intentos y outbox. No busca texto. |
| Elasticsearch 8.15 | Indice full-text `documents`, analizador en espanol. |
| RabbitMQ 3.13 | Colas `documents.process`, reintento a los 30 s y dead letter. Exchange de eventos hacia el WebSocket. |
| MinIO (API S3) | Bucket `documents` con el archivo original. |

### Observabilidad

Prometheus scrapea los servicios, Postgres, Elasticsearch y cAdvisor. Grafana trae la carpeta **TechDocs**: plataforma (SLA, CPU/RAM, colas, circuit breakers), PostgreSQL, Elasticsearch, MinIO, contenedores y colas. Loki recibe logs, Jaeger recibe trazas y el collector de OpenTelemetry las reenvia. Alertas: p95 de busqueda por encima de 1 s, Postgres o Elasticsearch caidos, circuit breaker abierto, documentos en `ERROR`, DLQ con mensajes.

## Manual de arranque

Requisitos:

- Docker Desktop con al menos 6 GB de RAM asignados.
- En Linux, Elasticsearch necesita `sudo sysctl -w vm.max_map_count=262144` antes del primer arranque.
- Puertos libres: `8080` (aplicacion) y `3000` (Grafana, solo localhost).

Desde la raiz de `tech-docs-platform`:

```bash
cp .env.example .env
docker compose up -d --build
```

El `.env` de ejemplo ya trae contrasenas de desarrollo. Cambialas si el equipo va a salir de tu maquina. La primera vez construye las imagenes Java y Angular: puede tardar varios minutos. Elasticsearch y los healthchecks de los servicios suman otro par de minutos.

Comprueba que quedaron sanos:

```bash
docker compose ps
```

La columna de estado debe decir `healthy` (o `running` en los exporters). Cuando `gateway`, `document-service`, `search-service` y `elasticsearch` esten healthy:

| Recurso | URL |
|---|---|
| Aplicacion | http://localhost:8080 |
| Grafana | http://localhost:3000 |

Usuario de Grafana: `admin`. Contrasena: el valor de `GRAFANA_ADMIN_PASSWORD` en `.env` (`change_me_grafana` si no lo cambiaste).

### Operacion diaria

```bash
docker compose ps
docker compose logs -f search-service
docker compose up -d --build document-service
docker compose down
```

`docker compose down` apaga el stack y conserva los volumenes (documentos, indice, dashboards). Para borrar tambien los datos:

```bash
docker compose down -v
```

### Variables de entorno

| Variable | Uso |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base `techdocs`. |
| `ELASTIC_PASSWORD` | Usuario `elastic` de Elasticsearch. |
| `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS` | Broker. |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | Bucket local. La contrasena debe tener al menos 8 caracteres. |
| `GRAFANA_ADMIN_PASSWORD` | Entrada a Grafana. |
| `MAX_UPLOAD_MB` | Tope de subida. Por defecto 10. El gateway acepta hasta 12 MB de cuerpo. |

## Probar la carga y la busqueda

En PowerShell usa `curl.exe` (el alias `curl` no envia multipart). En cmd, se puede partir el comando con `^`.

```bash
curl.exe -F "file=@scripts/sample-arquitectura.txt;type=text/plain" -F "titulo=Guia de arquitectura" -F "autor=Ana Ruiz" -F "categoria=arquitectura" -F "etiquetas=microservicios,elasticsearch" -F "version=1.0" http://localhost:8080/api/v1/documents
```

La respuesta es `202` con un `id`. El estado pasa a `INDEXADO` por el WebSocket, o se puede consultar:

```bash
curl.exe http://localhost:8080/api/v1/documents/{id}/status
```

Despues:

```bash
curl.exe "http://localhost:8080/api/v1/search?q=arquitectura&page=0&size=10"
```

En el navegador, http://localhost:8080 permite subir el archivo, ver la barra de progreso y abrir el visor desde un resultado.

Carga masiva: `POST /api/v1/documents/batch` con `files` y un campo `manifest` (JSON) que incluye `nombreArchivo`, `titulo`, `autor`, `categoria`, `etiquetas` y `version`.

### API publica

| Metodo | Ruta | Respuesta |
|---|---|---|
| `POST` | `/api/v1/documents` | `202` y el documento en `PROCESANDO`. |
| `POST` | `/api/v1/documents/batch` | `202` por cada archivo del manifiesto. |
| `GET` | `/api/v1/documents/{id}` | Metadatos y cuerpo para el visor. |
| `GET` | `/api/v1/documents/{id}/file` | Bytes del archivo original. |
| `GET` | `/api/v1/documents/{id}/status` | `PROCESANDO`, `INDEXADO` o `ERROR`. |
| `GET` | `/api/v1/search?q=&page=0&size=10` | Pagina, total y fragmentos. Filtros opcionales: `categoria`, `etiqueta`. |
| `GET` | `/api/v1/telemetry/logs` | Logs guardados por el servicio de telemetria. |
| `GET` | `/api/v1/telemetry/traces` | Trazas guardadas por el servicio de telemetria. |
| `WS` | `/ws` | `{"action":"subscribe","documentId":"..."}`. |

Formatos aceptados: TXT, Markdown y PDF. Por encima de 10 MB la API responde que el archivo supera el tope.

## Desarrollo local del frontend

Con la plataforma ya levantada en el puerto 8080:

```bash
cd frontend
npm install
npm start
```

`ng serve` abre http://localhost:4200 y proxifica `/api` y `/ws` hacia el gateway. El resto del stack sigue en Docker. Node 20 o superior.
