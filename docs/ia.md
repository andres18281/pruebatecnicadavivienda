# Uso de inteligencia artificial en la prueba

La prueba se resolvió en Cursor. El agente del IDE leyó el repositorio, propuso cambios y escribió código. No se usó ChatGPT, GitHub Copilot, Claude ni Gemini como herramienta aparte. El criterio de aceptación lo revisó la persona: la IA no dio por cerrado un cambio hasta que el comportamiento se contrastó en Docker, en Grafana o con tests.

## 1. Herramientas utilizadas

| Herramienta | Uso |
|---|---|
| Cursor | Entorno donde se pidieron los cambios, se leyó el código y se aplicaron los diffs. |
| Agente de Cursor | Exploró servicios, Docker Compose, Prometheus y el frontend, y escribió o corrigió el código. |
| Docker, el navegador y Grafana | Validación humana del resultado, fuera del modelo. |
| Maven en contenedor | Compilación y tests de los módulos tocados por el circuit breaker. |

## 2. Casos de uso

| Etapa | Qué se pidió al agente | Qué quedó en el repositorio |
|---|---|---|
| Integración | Meter `telemetry-service` en Docker Compose y dejar el commit. | Servicio de telemetría publicado en el stack. |
| Resolución de errores | `GET /api/v1/search` respondía 504. | Se revisó el gateway, el timeout de búsqueda y la ruta hacia Elasticsearch. |
| Observabilidad | Dashboards de CPU, RAM, PostgreSQL, Elasticsearch, MinIO, contenedores y colas. | Paneles en `infra/grafana` y reglas en Prometheus. |
| Corrección de una métrica | El disco de MinIO salía en 86 % y en rojo sin estar lleno. | Se corrigió la fórmula del panel para no tratar la capacidad usable como disco agotado. |
| Interfaz | Rediseño del frontend y, después, quitar textos grandes que delataban un texto generado. | UI más sobria, sin el titular tipo eslogan. |
| Tiempo real | Barra de carga de la subida usando el WebSocket que ya existía. | El frontend se suscribe y pinta `document.progress` y `document.status.changed`. |
| Regla de negocio | Límite de 10 MB en la subida, en cliente y en servidor. | Validación en Angular y en `document-service`. |
| Historia de usuario | Visor para leer el documento sin descargarlo. | Vista de metadatos, Markdown y texto del PDF por páginas. |
| Resiliencia | Agregar circuit breaker en `tech-docs-platform`. | Resilience4j en las llamadas a Elasticsearch y MinIO, alerta y panel. |
| Documentación | Redactar `docs/architecture.md` y este archivo a partir del código real. | Estos dos documentos. |

El agente no generó la arquitectura base desde cero en estas sesiones. Partió del código y de `docs/arquitectura-plataforma-documentacion-tecnica.md`, y extendió piezas concretas.

## 3. Prompts clave y cómo se refinaron

Los prompts empezaron cortos. Cuando el resultado no coincidía con lo que se veía en pantalla, el siguiente mensaje aportó el síntoma concreto.

**Observabilidad.** El primer pedido fue amplio: dashboards llamativos de CPU y RAM. El refinamiento nombró las fuentes que faltaban: PostgreSQL, Elasticsearch, espacio de MinIO, estado de cada contenedor y las colas en ejecución. Un tercer mensaje corrigió un dato falso: el panel marcaba el disco de MinIO al 86 % en rojo. Ahí se dejó de pedir “más gráficos” y se pidió revisar por qué esa cifra no correspondía al espacio libre.

**Interfaz.** “Haz un diseño más increíble” produjo una pantalla con titulares grandes. El refinamiento fue explícito: quitar frases como “Deja el documento. Nosotros lo indexamos.” y que no pareciera generado por un modelo. El segundo prompt fijó tono y contenido, no solo estética.

**Visor.** El primer mensaje pegó la historia de usuario (metadatos y cuerpo en una vista dedicada). Se refinó con el criterio que el texto no dejaba claro: el archivo debe previsualizarse en el navegador, sin descarga. Ese añadido cambió el alcance de “mostrar metadatos” a “renderizar Markdown o el texto del PDF”.

**Búsqueda en 504.** No se pidió un rediseño. Se pegó la URL, el método y el status. La siguiente pregunta (“¿cómo abro el tracer y Prometheus?”) sirvió para comprobar la latencia con las herramientas del propio stack, en lugar de aceptar la explicación del agente.

**Circuit breaker.** El prompt fue una sola frase: “en tech-docs-platform agrega circuit breaker”. No indicaba la librería ni el servicio. El agente leyó los adaptadores y lo colocó en las llamadas síncronas a Elasticsearch y MinIO, con `503` en la API y reencolado del worker sin consumir los 3 reintentos. Esa decisión se contrastó con los tests de `libs/resilience` y con la compilación de `search-service`, `document-service` y `processing-worker`.

**Tope de subida.** “Validación de tamaño de 10 MB, tanto en frontend como en backend” evitó un control solo visual. El refinamiento estaba en la misma frase: los dos lados.

## 4. Validación humana

El código generado no se aceptó por el diff. Se comprobó el efecto.

- La búsqueda se llamó en el navegador (`/api/v1/search`). El 504 se trató como fallo real y se volvió a mirar con Jaeger y Prometheus, no solo con la explicación del chat.
- Los paneles de Grafana se abrieron después de recrear las imágenes. El porcentaje de disco de MinIO se cuestionó porque no cuadraba con el espacio asignado, y el panel se corrigió.
- El frontend se revisó en la interfaz. El texto grandilocuente se rechazó y se pidió otro diseño.
- La barra de carga se pidió sobre el WebSocket existente. El commit de ese cambio se separó del commit visual para poder revisarlo solo.
- El límite de 10 MB quedó en los dos extremos para que un cliente que salte la UI no suba el archivo igual.
- El circuit breaker se validó con `CircuitGuardTest` (el circuito abre, la llamada no se ejecuta y la métrica `resilience4j.circuitbreaker.state` pasa a `open`) y con la compilación de los tres servicios. La suite de ArchUnit de `document-service` no se reejecutó en esa pasada: en el bind mount de Docker tardaba minutos resolviendo el classpath. Las reglas de Arquitectura hexagonal no se tocaron; el breaker vive en infraestructura, que es la capa que ya depende de clientes externos.
- Los commits y los push se pidieron por cambio, y en más de un caso se preguntó si el push ya había salido antes de seguir.

Cuando un resultado se veía bien en el chat y mal en el producto, mandaba el producto.
