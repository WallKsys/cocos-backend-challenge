# Cocos Broker API — Backend Challenge

API de un broker de inversiones: **portfolio** del usuario, **búsqueda de activos** y **envío de órdenes** de
compra y venta (MARKET y LIMIT), más depósitos, retiros, cancelación e historial. Resuelve el
[challenge de backend de Cocos](docs/referencia/backend-challenge.md).

> **Estado del proyecto:** análisis funcional, PRD y decisiones técnicas **cerrados**; constitución del proyecto
> aprobada; specs de implementación **en elaboración** (spec 001 aprobada). **Todavía no hay código de
> producción.** Ver [Hoja de ruta](#hoja-de-ruta).

---

## Por qué este proyecto es distinto

El challenge pide tres endpoints. El problema de fondo es otro: **que el dinero cuadre siempre**. Un broker no
puede dejar a un usuario con saldo negativo, ejecutar dos veces una compra por un reintento de red ni mostrarle
datos de otro usuario. Todo el diseño gira alrededor de eso:

- **`orders` es el libro contable.** Efectivo, posiciones y reservas se calculan siempre desde ahí: una sola
  fuente de verdad.
- **Las órdenes pendientes reservan** el dinero o las acciones que comprometen, para que nadie opere más de lo
  que tiene.
- **Idempotencia:** una operación reenviada (reintento, doble clic, corte de red) se ejecuta **una sola vez**.
- **Concurrencia segura:** dos órdenes simultáneas nunca gastan el mismo saldo (bloqueo pesimista por usuario).
- **Aislamiento entre usuarios por construcción:** ninguna URL de usuario permite nombrar a otro.

## Cómo se está construyendo: desarrollo asistido por agentes de IA

El proyecto es también un ejercicio de **desarrollo con agentes de IA**, con un reparto explícito de roles:

| Etapa | Quién | Resultado |
|---|---|---|
| Análisis, PRD, decisiones técnicas, specs, escenarios BDD y reglas de arquitectura | Autor + Claude | Documentos y barreras ejecutables ("cancha marcada") |
| Implementación | Otro agente (OpenCode con un modelo gratuito) | Código y tests, spec por spec |
| Revisión | Claude | Auditoría del código contra specs, escenarios y reglas |

Las herramientas que hacen que un modelo gratuito no pueda "hacer trampa":

- **Spec-driven development** con [GitHub Spec Kit](https://github.com/github/spec-kit): una constitución con las
  reglas no negociables y una spec por funcionalidad (spec, plan, contrato OpenAPI y tareas).
- **BDD:** cada uno de los **124 escenarios de aceptación** tiene su escenario Gherkin y al menos un test; el
  build falla si alguno queda sin cubrir.
- **TDD agéntico:** el agente escribe primero los tests, confirma que fallan y recién después implementa. Tiene
  prohibido modificar un test para que pase.
- **ArchUnit:** las reglas de la arquitectura hexagonal son tests; violarlas rompe el build.
- **PRs de 400 líneas como máximo**, verificados por una GitHub Action, para que una persona pueda revisarlos.

## Documentación

| Documento | Para qué leerlo |
|---|---|
| [Análisis funcional](docs/analisis-funcional.md) | Dominio, reglas del enunciado, ambigüedades detectadas y **23 decisiones de producto** justificadas |
| [Casos de uso](docs/casos-de-uso.md) | **124 escenarios** de aceptación (dado / cuando / entonces) con números reales |
| [PRD](docs/prd.md) | Objetivos, alcance, requisitos y **métricas de éxito** (integridad, idempotencia, escala) |
| [RFC](docs/rfc.md) | **31 decisiones técnicas** con alternativas evaluadas y descartadas |
| [Constitución](.specify/memory/constitution.md) | Reglas no negociables que sigue el agente (en inglés) |
| [AGENTS.md](AGENTS.md) | Instrucciones de trabajo para el agente implementador |
| [specs/](specs/) | Una carpeta por funcionalidad: spec, plan, contrato y tareas |

## Desvío consciente del enunciado

El enunciado pide **Node.js**; este proyecto se implementa en **Java** por decisión explícita del autor, para
practicar el desarrollo asistido por agentes en su stack. Está documentado en el [PRD §1.1](docs/prd.md). Todo
el análisis funcional es independiente del lenguaje.

## Stack

| | |
|---|---|
| Lenguaje y framework | Java 25 · Spring Boot 4.1 |
| Base de datos | PostgreSQL 18 · migraciones con Flyway · **SQL explícito con `JdbcClient`, sin ORM** |
| Arquitectura | Hexagonal (domain · application · infrastructure), verificada con ArchUnit |
| Tests | JUnit · AssertJ · Cucumber (BDD) · Testcontainers (PostgreSQL real) · ArchUnit |
| API | REST · contratos OpenAPI · errores RFC 9457 · montos como texto decimal · fechas en UTC |
| Observabilidad | Micrometer · OpenTelemetry · Grafana (métricas, trazas y logs) con tableros de salud y de negocio |
| Ejecución local | Docker Compose |

## Funcionalidades

| Nivel | Funcionalidades |
|---|---|
| **Pedido del challenge** | Portfolio · búsqueda de activos · envío de órdenes (MARKET / LIMIT, por cantidad o por monto) |
| **Extensiones de dominio** | Cancelar orden pendiente · depositar · retirar · historial de órdenes |
| **Herramientas internas** (solo demo, deshabilitadas por defecto) | Borrar orden · forzar ejecución de una LIMIT · cambiar precio · restaurar datos · crear usuario |

Convenciones de la API (detalle en el [RFC §8](docs/rfc.md)):

- El usuario se identifica con el header `X-User-Id` (no hay autenticación en el alcance del challenge).
- Las operaciones con efecto requieren el header `Idempotency-Key`.
- Todos los mensajes de la API están en inglés; los errores tienen un código estable y un mensaje con valores
  concretos.

Los contratos exactos de cada endpoint se publican en `specs/NNN-*/contracts/` a medida que se escribe cada spec.

## Cómo ejecutarlo

> Disponible cuando se implemente la spec 001.

Requisitos: Java 25 y Docker.

```bash
docker compose up --build          # PostgreSQL + aplicación con datos de ejemplo
./mvnw verify                      # todos los tests: unitarios, integración, BDD, ArchUnit y trazabilidad
```

La colección de ejemplos está en `http/` (cliente HTTP de IntelliJ).

## Hoja de ruta

| # | Spec | Estado |
|---|---|---|
| — | Análisis funcional, casos de uso, PRD, RFC, constitución | ✅ Cerrado |
| 001 | Esqueleto y barreras (build, ArchUnit, entorno de tests, trazabilidad) | ✅ Spec aprobada · implementación pendiente |
| 002 | Base de datos (esquema original + cambios justificados) | ⏳ Pendiente |
| 003 | Convenciones de la API (errores, identidad, montos, idempotencia) | ⏳ Pendiente |
| 004 | Búsqueda de activos | ⏳ Pendiente |
| 005 | Envío de órdenes | ⏳ Pendiente |
| 006 | Portfolio | ⏳ Pendiente |
| 007 | Depósitos y retiros | ⏳ Pendiente |
| 008 | Cancelación e historial | ⏳ Pendiente |
| 009 | Herramientas internas y pruebas de volumen | ⏳ Pendiente |
| 010 | Observabilidad | ⏳ Pendiente |

## Estructura del repositorio

```text
.
├── AGENTS.md                  instrucciones para el agente implementador
├── docs/                      análisis, casos de uso, PRD, RFC y material original del challenge
├── specs/                     una carpeta por spec (spec, plan, contrato, tareas)
├── .specify/                  Spec Kit: constitución, plantillas y scripts
├── .opencode/                 comandos de Spec Kit para OpenCode
├── .github/                   plantilla de PR y GitHub Action de tamaño de PR
└── scripts/check-pr-size.sh   verificación local del límite de 400 líneas por PR
```
