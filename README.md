# Cocos Broker API

[![CI](https://github.com/WallKsys/cocos-backend-challenge/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/WallKsys/cocos-backend-challenge/actions/workflows/ci.yml)
[![PR size](https://github.com/WallKsys/cocos-backend-challenge/actions/workflows/pr-size.yml/badge.svg)](https://github.com/WallKsys/cocos-backend-challenge/actions/workflows/pr-size.yml)
![Java 25](https://img.shields.io/badge/Java-25-blue)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-336791)

API REST de un broker de inversiones: **portfolio** del usuario, **búsqueda de activos** y **envío de órdenes** de
compra y venta (`MARKET` y `LIMIT`, por cantidad o por monto), más depósitos, retiros, cancelación e historial.
Resuelve el [challenge de backend de Cocos](docs/referencia/backend-challenge.md).

---

## Contenido

- [Estado](#estado)
- [Qué resuelve](#qué-resuelve)
- [Inicio rápido](#inicio-rápido)
- [API](#api)
- [Convenciones de la API](#convenciones-de-la-api)
- [Arquitectura](#arquitectura)
- [Modelo de datos](#modelo-de-datos)
- [Calidad y testing](#calidad-y-testing)
- [Observabilidad](#observabilidad)
- [Configuración](#configuración)
- [Supuestos y limitaciones](#supuestos-y-limitaciones)
- [Proceso de ingeniería](#proceso-de-ingeniería)
- [Documentación](#documentación)
- [Estructura del repositorio](#estructura-del-repositorio)

---

## Estado

La implementación avanza por funcionalidades (*specs*), cada una entregada en pull requests chicos y revisados.

| # | Funcionalidad | Estado |
|---|---|---|
| 001 | Esqueleto del servicio y barreras de calidad (build, CI, ArchUnit, BDD, trazabilidad) | ✅ Completa |
| 002 | Base de datos: esquema provisto + migraciones justificadas | ✅ Completa |
| 003 | Convenciones de la API (errores, identidad, montos, idempotencia) | ✅ Completa |
| 004 | Búsqueda de activos | ✅ Completa |
| 005 | Envío de órdenes | ✅ Completa |
| 006 | Portfolio | ✅ Completa |
| 007 | Depósitos y retiros | ✅ Completa |
| 008 | Cancelación e historial | ✅ Completa |
| 009 | Herramientas internas de demo y pruebas de volumen | ⏳ Pendiente |
| 010 | Observabilidad (métricas, trazas, logs y tableros) | ⏳ Pendiente |

Las secciones siguientes describen el servicio completo; donde una capacidad depende de una funcionalidad pendiente,
se indica la spec.

---

## Qué resuelve

El enunciado pide tres endpoints. El problema de fondo es **que el dinero cuadre siempre**: un broker no puede dejar
a un usuario con saldo negativo, ejecutar dos veces una compra por un reintento de red ni mostrarle datos de otro
usuario. El diseño se apoya en cinco garantías:

| Garantía | Cómo se logra |
|---|---|
| **Una sola fuente de verdad** | La tabla `orders` es el libro contable: efectivo, posiciones y reservas se calculan siempre desde ahí |
| **Nadie opera más de lo que tiene** | Las órdenes pendientes **reservan** el dinero o las acciones que comprometen, y toda validación usa el saldo *disponible* |
| **Una operación se ejecuta una sola vez** | Idempotencia por clave del cliente (`Idempotency-Key`), persistida junto con la orden y protegida por una restricción única |
| **Órdenes simultáneas nunca gastan el mismo saldo** | Bloqueo pesimista de la fila del usuario dentro de la transacción de cada operación |
| **Aislamiento entre usuarios por construcción** | Ninguna URL pública permite nombrar a otro usuario: la identidad sale solo del header `X-User-Id` |

---

## Inicio rápido

**Requisitos:** Java 25 y Docker.

```bash
git clone https://github.com/WallKsys/cocos-backend-challenge.git
cd cocos-backend-challenge

docker compose up --build        # PostgreSQL + API (perfil demo) con los datos del challenge
```

| Servicio | URL |
|---|---|
| API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Swagger UI *(spec 003)* | http://localhost:8080/swagger-ui/index.html |
| Grafana *(spec 010)* | http://localhost:3000 |

Primeras llamadas (usuario de ejemplo `1`; los datos del challenge se cargan automáticamente):

```bash
# Portfolio
curl -s http://localhost:8080/api/v1/portfolio -H 'X-User-Id: 1'

# Buscar activos
curl -s "http://localhost:8080/api/v1/instruments?q=banco"

# Comprar PAMP por un monto en pesos (54 acciones a 925.85; el sobrante queda disponible)
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' -H 'X-User-Id: 1' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"instrumentId": 47, "side": "BUY", "type": "MARKET", "amount": "50000.00"}'
```

La carpeta [`http/`](http/) contiene una colección de pedidos de ejemplo para el cliente HTTP de IntelliJ, con las
herramientas internas en un archivo separado.

---

## API

Contrato completo (OpenAPI 3.1):
[`specs/009-internal-tools-volume/contracts/openapi.yaml`](specs/009-internal-tools-volume/contracts/openapi.yaml),
publicado por el servicio en `/openapi/openapi.yaml` y navegable en Swagger UI.

### Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/portfolio` | Valor total, efectivo (total, reservado, disponible) y posiciones con valor, costo promedio, rendimiento total y variación diaria |
| `GET` | `/api/v1/instruments` | Búsqueda por término general, ticker y/o nombre; sin distinguir mayúsculas ni acentos; paginada |
| `POST` | `/api/v1/orders` | Envía una orden `BUY`/`SELL`, `MARKET`/`LIMIT`, por cantidad entera o por monto |
| `GET` | `/api/v1/orders` | Historial de órdenes y movimientos de efectivo, de lo más reciente a lo más antiguo, filtrable por estado |
| `GET` | `/api/v1/orders/{orderId}` | Una orden del usuario |
| `POST` | `/api/v1/orders/{orderId}/cancel` | Cancela una orden `NEW` y libera su reserva |
| `POST` | `/api/v1/cash/deposits` | Depósito de pesos |
| `POST` | `/api/v1/cash/withdrawals` | Retiro de pesos contra el saldo disponible |

### Herramientas internas (solo demo)

Fuera del alcance del challenge. Existen **solo** con el perfil `demo`, requieren `X-Role: ADMIN` y, si no se
cumplen ambas condiciones, responden `404` igual que una ruta inexistente.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/internal/users/{userId}/portfolio` | Portfolio de cualquier usuario |
| `GET` | `/api/internal/users/{userId}/orders` | Historial de cualquier usuario |
| `POST` | `/api/internal/orders/{orderId}/execute` | Fuerza la ejecución de una orden `LIMIT` pendiente a su precio límite |
| `DELETE` | `/api/internal/orders/{orderId}` | Borra una orden |
| `PUT` | `/api/internal/instruments/{instrumentId}/market-data` | Cambia el último precio de un instrumento |
| `POST` | `/api/internal/reset` | Restaura los datos originales del challenge |
| `POST` | `/api/internal/users` | Crea un usuario de prueba |

### Estados de una orden

```text
                 ┌──────────── MARKET válida ─────────────────────────────► FILLED
  POST /orders ──┼──────────── LIMIT válida ──► NEW ──┬─ cancel ───────────► CANCELLED
                 │                                    └─ ejecución (demo) ─► FILLED
                 └──── sin saldo / sin precio / monto insuficiente ────────► REJECTED (con motivo)

  Pedido inválido (formato, campos, instrumento inexistente) → 400, no se guarda nada.
```

---

## Convenciones de la API

| Tema | Convención |
|---|---|
| Identidad | Header `X-User-Id` (no hay autenticación en el alcance del challenge; ver [limitaciones](#supuestos-y-limitaciones)) |
| Idempotencia | Header `Idempotency-Key` obligatorio en órdenes, depósitos y retiros (la cancelación es idempotente por naturaleza y no lo lleva). El mismo pedido con la misma clave devuelve el resultado original (`200` + `Idempotent-Replayed: true`); la misma clave con otro contenido, `422 IDEMPOTENCY_CONFLICT` |
| Montos y precios | Texto decimal: las respuestas llevan exactamente 2 decimales (`"925.85"`) y los pedidos aceptan hasta 2 (`"925.8"`); las cantidades de acciones son enteros. Nunca punto flotante |
| Fechas | ISO 8601 en UTC (`2026-11-21T14:36:00Z`); la conversión a hora local es responsabilidad del consumidor |
| Errores | [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (Problem Details) con un `code` estable y un mensaje en inglés con valores concretos |
| Idioma | Todo texto de la API está en inglés |
| JSON estricto | Campos desconocidos y coerciones (`1.5` como cantidad, `10` como texto) se rechazan con `VALIDATION_ERROR` |
| Paginado | Por cursor opaco (`cursor`, `limit`, `nextCursor`) |
| Correlación | Header `X-Request-Id`, generado si no viene, presente en la respuesta y en cada línea de log |

Una orden rechazada **no** es un error HTTP: se guarda y se devuelve con `201` y su motivo.

```json
{
  "orderId": 13,
  "status": "REJECTED",
  "quantity": 1000,
  "price": "925.85",
  "rejection": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Insufficient available cash in account 10001: required ARS 925850.00, available ARS 627500.00."
  }
}
```

Un pedido inválido sí es un error, y no deja rastro en la base:

```json
{
  "type": "https://api.cocos.example/errors/validation-error",
  "title": "Validation error",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "detail": "Request validation failed: 'price' is required for LIMIT orders.",
  "instance": "/api/v1/orders"
}
```

---

## Arquitectura

Arquitectura **hexagonal** (puertos y adaptadores). Las dependencias apuntan siempre hacia adentro y se verifican
con [ArchUnit](https://www.archunit.org/) en cada build.

```text
              ┌─────────────────────── infrastructure ───────────────────────┐
  HTTP ─────► │ adapter/in/rest  (controllers, DTOs, errores RFC 9457)       │
              │         │                                                    │
              │         ▼                                                    │
              │ ┌────────────────────── application ───────────────────────┐ │
              │ │ casos de uso · puertos de entrada y de salida            │ │
              │ │ ┌─────────────────────── domain ───────────────────────┐ │ │
              │ │ │ reglas de negocio en Java puro                       │ │ │
              │ │ └──────────────────────────────────────────────────────┘ │ │
              │ └─────────────────────────────┬────────────────────────────┘ │
              │                               ▼                              │
              │ adapter/out/persistence (Jdbc*, SQL explícito)               │──► PostgreSQL
              │ adapter/out/metrics (Micrometer) · config                    │
              └──────────────────────────────────────────────────────────────┘
```

| Capa | Contiene | Puede depender de |
|---|---|---|
| `domain` | Modelo y reglas: disponibles y reservas, evaluación de órdenes, costo promedio | Solo el JDK |
| `application` | Casos de uso, puertos de entrada y de salida | `domain` y `@Transactional` |
| `infrastructure` | REST, persistencia con `JdbcClient`, métricas, configuración | Todo lo anterior y Spring |

Decisiones técnicas destacadas (detalle y alternativas evaluadas en el [RFC](docs/rfc.md)):

- **SQL explícito, sin ORM** (`JdbcClient`): el bloqueo de cuenta, la idempotencia atómica y las agregaciones del
  libro contable se escriben y se prueban tal como se ejecutan (TEC-12).
- **Bloqueo pesimista por usuario** (`SELECT ... FOR UPDATE`): los pedidos concurrentes del mismo usuario esperan en
  lugar de fallar; los de distintos usuarios siguen en paralelo (TEC-16).
- **Saldos calculados en el momento desde `orders`**, con índices cubrientes. Medido con 10 millones de órdenes:
  el portfolio de un usuario con 10.000 órdenes se resuelve en ~15 ms de SQL (TEC-17).
- **Contrato primero:** el OpenAPI se escribe antes que el código y un test exige que rutas y contrato coincidan
  (TEC-22).

---

## Modelo de datos

El esquema y los datos provistos por el challenge se cargan **sin cambios** como migración Flyway `V1`. Los cambios
posteriores son migraciones separadas y justificadas:

| Migración | Cambios |
|---|---|
| `V1` | `database.sql` del challenge, byte a byte |
| `V2` | Restricciones de integridad, ticker único, fechas en `TIMESTAMPTZ` (UTC) |
| `V3` | Motivo de rechazo, clave de idempotencia y huella del pedido, montos `NUMERIC(18,2)` |
| `V4` | Columnas normalizadas (sin acentos) e índices trigram para la búsqueda |
| `V5` | Índices para saldos, reservas e historial con cursor |
| `V6` | Monto pedido en órdenes por monto (para responder reintentos idénticos) |

Esquema completo: [`specs/002-database/data-model.md`](specs/002-database/data-model.md). Justificación de cada
cambio: [análisis funcional §8](docs/analisis-funcional.md).

---

## Calidad y testing

```bash
./mvnw verify                    # suite completa: unitarios, integración, BDD, ArchUnit, trazabilidad
./mvnw test -Dgroups=unit        # solo unitarios (rápidos, sin Docker)
./mvnw verify -Pvolume           # pruebas de volumen: 10M órdenes, p95/p99 por endpoint (spec 009)
```

| Nivel | Herramienta | Qué verifica |
|---|---|---|
| Unitarios | JUnit + AssertJ | Reglas de dominio sin Spring ni base de datos |
| Integración | Testcontainers (PostgreSQL 18 real) | SQL, bloqueos, idempotencia ante reintentos simultáneos, paginado |
| Aceptación | Cucumber (BDD) | **128 escenarios** de [casos de uso](docs/casos-de-uso.md), con valores calculados sobre los datos del challenge |
| Arquitectura | ArchUnit | Capas, dependencias, `@Transactional`, sin ORM, sin `double` para dinero |
| Trazabilidad | Test propio | Cada escenario tiene su escenario Gherkin y al menos un test; si falta uno, el build falla |
| Planes de ejecución | `EXPLAIN` en el build | Ninguna consulta crítica puede perder sus índices sin romper el build |
| Volumen | Perfil `-Pvolume` | Metas del PRD: portfolio p95 < 200 ms, historial < 100 ms, orden < 150 ms, búsqueda < 100 ms |

En cada pull request, GitHub Actions ejecuta:

- **`verify`**: la suite completa (obligatorio para mergear).
- **`pr-size`**: máximo 400 líneas revisables por PR, salvo justificación (obligatorio).
- **`review`**: revisión automática contra las specs, el plan y las reglas del proyecto.

---

## Observabilidad

*(spec 010)* El servicio exporta métricas, trazas y logs con **OpenTelemetry** a una pila Grafana local
(`grafana/otel-lgtm`) que levanta el mismo `docker compose up`:

- **Cocos · Application health**: tráfico, errores 4xx/5xx, latencia p95/p99 por endpoint, JVM, conexiones, CPU.
- **Cocos · Business**: operaciones por minuto, órdenes por estado, rechazos por motivo, reintentos idempotentes,
  conflictos y tiempo de espera del bloqueo; y totales históricos calculados desde el libro contable con un
  usuario de solo lectura.
- Cada log lleva `requestId` y `trace_id`: desde una línea de log en Loki se llega a la traza del pedido en Tempo.

Los logs de consola son JSON estructurado (formato ECS).

---

## Configuración

| Variable | Default | Descripción |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/cocos` | URL JDBC de PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | `cocos` / `cocos` | Credenciales de la base |
| `SPRING_PROFILES_ACTIVE` | — | `demo` habilita las herramientas internas y la exportación de telemetría |
| `OTLP_BASE_URL` | `http://localhost:4318` | Destino OTLP/HTTP de métricas, trazas y logs (perfil `demo`) |

| Puerto | Servicio |
|---|---|
| 8080 | API |
| 5432 | PostgreSQL |
| 3000 | Grafana *(spec 010)* |

---

## Supuestos y limitaciones

- **Sin autenticación** (fuera del alcance del challenge): la identidad del solicitante se declara con `X-User-Id` y
  no se verifica. El aislamiento entre usuarios está implementado y probado; en producción la identidad vendría de un
  token, sin cambiar ninguna URL.
- **Precios estáticos:** se usan los de la tabla `marketdata` del challenge (último día: 2023-07-14). No se simula el
  mercado, por lo que una orden `LIMIT` permanece `NEW` hasta que se cancela (o se ejecuta con la herramienta de demo).
- **Datos provistos con una inconsistencia:** el usuario 1 tiene una posición de **−10 BMA** (vendió más de lo que
  compró). Se respeta el dato original y el portfolio la muestra marcada como inconsistente.
- **Sin comisiones**; los montos se redondean a 2 decimales con *half-up*.
- **Pedidos mal formados a nivel HTTP** (por ejemplo, un carácter de control en un header): los rechaza el servidor
  (Tomcat) con su propio 400 antes de llegar a la aplicación, así que esa respuesta no tiene el formato de error de
  la API.
- **Lenguaje:** el enunciado propone Node.js; esta entrega usa **Java 25 + Spring Boot 4.1**. Es una decisión
  deliberada y documentada ([PRD §1.1](docs/prd.md), [RFC TEC-01](docs/rfc.md)); el análisis funcional y el contrato
  de la API son independientes del lenguaje.

---

## Proceso de ingeniería

El proyecto se construye con **spec-driven development** ([GitHub Spec Kit](https://github.com/github/spec-kit)):

1. **Análisis funcional** del enunciado: reglas explícitas, ambigüedades y 25 decisiones de producto justificadas.
2. **PRD** con objetivos y métricas de éxito; **RFC** con 34 decisiones técnicas y sus alternativas descartadas.
3. **Constitución** del proyecto: reglas no negociables (arquitectura, dinero, concurrencia, testing, tamaño de PR).
4. **Una spec por funcionalidad**, con plan, contrato OpenAPI, escenarios Gherkin y tareas divididas en PRs
   revisables.
5. **Implementación test-first** por un agente de desarrollo asistido por IA que sigue las specs, con revisión
   automática y manual de cada PR. Los tests aprobados no se modifican para que pasen.

Todas las reglas que guían al agente, con dónde se decidió cada una, por qué y qué la hace cumplir (build, merge o
revisión), están reunidas en [Reglas y barreras](docs/reglas-y-barreras.md).

---

## Documentación

La documentación de `docs/` también se publica como sitio navegable, con buscador, en
**[wallksys.github.io/cocos-backend-challenge](https://wallksys.github.io/cocos-backend-challenge/)**. Se regenera en
cada merge a `main` y se previsualiza localmente con `pip install -r requirements-docs.txt && zensical serve`.

| Documento | Contenido |
|---|---|
| [Análisis funcional](docs/analisis-funcional.md) | Dominio, reglas del enunciado, ambigüedades y decisiones de producto (DEC-xx) |
| [Casos de uso](docs/casos-de-uso.md) | 128 escenarios de aceptación (dado / cuando / entonces) con valores reales |
| [PRD](docs/prd.md) | Objetivos, alcance, requisitos y métricas de éxito |
| [RFC](docs/rfc.md) | Decisiones técnicas (TEC-xx) con alternativas evaluadas |
| [Constitución](.specify/memory/constitution.md) | Reglas de ingeniería no negociables |
| [Reglas y barreras](docs/reglas-y-barreras.md) | Índice de todas las reglas, dónde se decidieron y qué las hace cumplir |
| [Specs](specs/) | Spec, plan, contrato, escenarios y tareas de cada funcionalidad |
| [Enunciado original](docs/referencia/backend-challenge.md) | Challenge y `database.sql` provistos |

---

## Estructura del repositorio

```text
.
├── src/main/java/com/cocos/challenge/   código del servicio (domain · application · infrastructure)
├── src/main/resources/                  configuración y migraciones Flyway
├── src/test/                            tests unitarios, de integración, BDD (features) y de arquitectura
├── http/                                colección de pedidos de ejemplo
├── observability/                       tableros y aprovisionamiento de Grafana (spec 010)
├── specs/                               una carpeta por funcionalidad
├── docs/                                análisis, casos de uso, PRD, RFC y material del challenge
├── .specify/                            Spec Kit: constitución, plantillas y scripts
├── .github/                             workflows (CI, tamaño de PR, revisión) y plantilla de PR
├── compose.yaml · Dockerfile            ejecución local
└── pom.xml · mvnw                       build (Maven)
```
