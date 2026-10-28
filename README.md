# TechDocs

Plataforma de documentacion tecnica: carga asincrona, indexacion en segundo plano, busqueda full-text y aviso en tiempo real.

Implementa las historias HU-01 a HU-04 sobre la arquitectura de `docs/arquitectura-plataforma-documentacion-tecnica.md`, con **Java 21 / Spring Boot** en los microservicios y **Angular** en el frontend.

## Como se cumple cada historia

| Historia | Implementacion |
|---|---|
| HU-01 Carga con metadatos | `POST /api/v1/documents` multipart. Valida extension, firma del archivo y tamano. Responde `202` con `id` y `estado: PROCESANDO`. |
| HU-02 Busqueda | `search-service` consulta Elasticsearch (`simple_query_string`, analizador en espanol). Paginacion, highlighting y timeout de 900 ms. PostgreSQL no participa en la busqueda. |
| HU-03 Visor | `GET /api/v1/documents/{id}` devuelve metadatos y cuerpo. El frontend renderiza Markdown o el texto del PDF por paginas. |
| HU-04 Tiempo real | El worker publica `document.status.changed` en RabbitMQ. `notification-service` lo empuja por WebSocket `/ws`. La interfaz se actualiza sin polling. |

El rango de 400 ms a 1000 ms se trata como SLA de latencia maxima (p95 <= 1 s), tal como indica el documento de arquitectura. Elasticsearch suele responder por debajo de 400 ms; Prometheus alerta si el p95 supera 1 s.

## Arranque

Requisitos: Docker Desktop con al menos 6 GB de RAM. En Linux, Elasticsearch necesita `sudo sysctl -w vm.max_map_count=262144`.

```bash
cp .env.example .env
docker compose up -d --build
```

| Recurso | URL |
|---|---|
| Aplicacion | http://localhost:8080 |
| Grafana (solo localhost) | http://localhost:3000 |

Usuario de Grafana: `admin`. Contrasena: la variable `GRAFANA_ADMIN_PASSWORD`.

## Ejemplo de carga

```bash
curl -F "file=@scripts/sample-arquitectura.txt;type=text/plain" ^
  -F "titulo=Guia de arquitectura" ^
  -F "autor=Ana Ruiz" ^
  -F "categoria=arquitectura" ^
  -F "etiquetas=microservicios,elasticsearch" ^
  -F "version=1.0" ^
  http://localhost:8080/api/v1/documents
```

Despues de recibir el evento `INDEXADO`:

```bash
curl "http://localhost:8080/api/v1/search?q=arquitectura&page=0&size=10"
```

Carga masiva: `POST /api/v1/documents/batch` con `files` y un campo `manifest` (JSON) que incluye `nombreArchivo`, `titulo`, `autor`, `categoria`, `etiquetas` y `version`.

## Servicios

- `document-service`: recibe el archivo, lo guarda en MinIO, persiste el estado en PostgreSQL y publica el comando por el outbox.
- `processing-worker`: extrae TXT, Markdown o PDF, indexa en Elasticsearch y emite el cambio de estado. Reintenta hasta 3 veces y luego marca `ERROR`.
- `search-service`: unica ruta de busqueda. Aislada de la carga.
- `notification-service`: WebSocket. El cliente se suscribe con `{"action":"subscribe","documentId":"..."}`.

Cada servicio de aplicacion sigue puertos y adaptadores: el dominio no depende de Spring, Elasticsearch, RabbitMQ ni MinIO.

## Desarrollo local del frontend

Con la plataforma levantada en el puerto 8080:

```bash
cd frontend
npm install
npm start
```

`ng serve` proxifica `/api` y `/ws` hacia el gateway.
