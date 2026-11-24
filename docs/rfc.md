# RFC — Decisiones técnicas

| | |
|---|---|
| **Estado** | En elaboración |
| **Fecha** | 2026-11-21 |
| **Documentos relacionados** | [PRD](prd.md) · [Análisis funcional](analisis-funcional.md) · [Casos de uso](casos-de-uso.md) |

> Registro de las decisiones técnicas (TEC-xx), con su justificación. Las que son principios no negociables del proyecto
> se trasladan a la **constitución** de Spec Kit (`.specify/memory/constitution.md`), y el detalle de cada funcionalidad
> a su `plan.md`. Este documento conserva el razonamiento completo para la presentación.
>
> **IDs estables.** Cada decisión conserva su ID desde que se crea: los IDs no se renumeran ni se reutilizan, porque
> la constitución, las specs, los planes, los commits y los PRs los citan. Por eso las secciones siguen un orden
> temático y no el numérico (por ejemplo, TEC-32 está en §6 junto al bloqueo pesimista y TEC-31 en §11). Dentro de
> cada tabla, las filas se ordenan por ID.

---

## 1. Forma de trabajo

| ID | Decisión | Justificación |
|---|---|---|
| TEC-01 | **Lenguaje: Java** en lugar de Node.js (desvío consciente del enunciado, PRD §1.1). | El proceso de la entrega se apoya en barreras ejecutables (TEC-04) que en este stack tienen herramientas maduras: Cucumber-JVM, ArchUnit y Testcontainers. El costo (no cumplir el lenguaje pedido) se acepta y se declara en el PRD §1.1; el análisis funcional y el contrato de la API son independientes del lenguaje. |
| TEC-02 | **Spec-driven development con GitHub Spec Kit** (v1.4.x): una constitución para el proyecto y una carpeta `specs/NNN-funcionalidad/` por funcionalidad (spec, plan, data model, contratos, tareas). | Separa el qué (spec), el cómo (plan) y la ejecución (tareas), y lo entienden distintos agentes. |
| TEC-03 | **Implementación y revisión a cargo de agentes distintos.** El código lo escribe un agente de IA distinto de Claude (OpenCode, integrado con Spec Kit: `--integration opencode`, comandos en `.opencode/commands/`; las porciones 004-b2 y 004-c1 las implementó Kimi desde IntelliJ y, al agotarse su cuota, se volvió a OpenCode. El protocolo de porción de `AGENTS.md` no depende del agente). Claude especifica (TEC-05) y revisa: la revisión automática de cada PR (TEC-34) y una revisión manual de las porciones críticas. Si el agente se traba, se detiene y reporta (`AGENTS.md`), y se ajustan la spec o las tareas: Claude nunca implementa lo que después revisa. | **Revisión adversarial:** un modelo que revisa su propio código tiende a validar los mismos supuestos con los que lo escribió. Con un implementador y un revisor distintos, no comparten contexto ni puntos ciegos, y el revisor juzga el código solo contra las specs y la evidencia del PR. Las barreras ejecutables (TEC-04) cubren lo que una revisión puede pasar por alto. |
| TEC-04 | **Barreras ejecutables, no solo specs en prosa:** escenarios BDD completos (`.feature`), reglas de arquitectura como tests de ArchUnit, tareas pequeñas con archivos a tocar y test que deben pasar, y matriz de trazabilidad verificada en el build. | Un agente de IA tiende a declarar terminado código que "parece" correcto; las barreras lo impiden de forma objetiva, sea cual sea el modelo. |
| TEC-05 | **División del trabajo.** Escribe **Claude**: constitución, specs, planes, tareas, archivos `.feature` y reglas de ArchUnit. Escribe **el agente implementador**: los step definitions de BDD, los tests unitarios y de integración, y el código de producción. | Los `.feature` son la spec en lenguaje de negocio y las reglas de ArchUnit son la ley de la arquitectura: si las escribiera el mismo agente que implementa, tendería a ajustarlas a su propio código en lugar de a la spec. |

## 2. Reglas de verificación (reglas de oro)

| ID | Decisión | Justificación |
|---|---|---|
| TEC-06 | **Cobertura total de escenarios.** Cada escenario de [casos de uso](casos-de-uso.md) (CU-xx.yy) tiene **exactamente un escenario BDD** con su mismo ID como etiqueta, y **al menos un test** de la regla que verifica. Ese test es **unitario** si la regla es de dominio, o **de integración contra una base de datos real** si la garantía depende de la infraestructura (concurrencia, idempotencia ante reintentos simultáneos, paginado, persistencia de fechas, volumen). Cada escenario queda clasificado explícitamente como `unitario` o `integración`. | Principio de la entrega: todo lo especificado tiene que estar verificado. Un test unitario con mocks puede pasar mientras el sistema real falla en los casos que dependen de la base. |
| TEC-07 | **Matriz de trazabilidad verificada automáticamente.** El build falla si un escenario CU-xx.yy no tiene su escenario BDD o su test asociado. | La regla de TEC-06 se cumple sola, sin depender de que el agente se acuerde. |
| TEC-08 | **TDD agéntico por funcionalidad.** Para cada funcionalidad, en orden: (1) escribir sus tests (BDD + unitarios o de integración); (2) crear solo los esqueletos mínimos para que compilen y verificar que los tests **fallan**; (3) implementar hasta que pasen; (4) correr **toda** la suite y verificar que nada se rompió; (5) recién entonces pasar a la siguiente funcionalidad. | Primero los tests y después la implementación, para que cada test se vea fallar antes de pasar. Se hace por funcionalidad y no para todo el proyecto, porque en Java un test que no compila impide correr todos los demás, y cientos de errores simultáneos desorientan al agente. |
| TEC-09 | **Los tests aprobados no se modifican para que pasen.** Si el agente considera que un test o un escenario es incorrecto, se detiene y lo reporta; no lo cambia ni lo desactiva. Única excepción (constitución v1.3.6): un cambio que el `tasks.md` de la porción **prescribe para un archivo de test nombrado**, por ejemplo extender la lista de migraciones esperadas de `SeedDataIT` al agregar una migración. Va en el commit de tests y se explica en el PR. | Es el atajo más común de los modelos cuando se traban, e invalida toda la verificación. La excepción la decide el autor de la spec por escrito, no el agente: algunas tareas cambian a propósito un test existente (la porción 005-c lo hizo frenar al agente, con razón, porque la regla no lo contemplaba). |

---

## 3. Arquitectura

| ID | Decisión | Justificación |
|---|---|---|
| TEC-10 | **Arquitectura hexagonal (puertos y adaptadores), en tres capas:** `domain` (modelo y reglas de negocio en Java puro: sin framework, sin persistencia, sin HTTP), `application` (casos de uso y puertos, es decir interfaces de entrada y salida) e `infrastructure` (adaptadores: API REST, persistencia e implementaciones de los puertos). Las dependencias apuntan siempre hacia adentro: `infrastructure → application → domain`. Las reglas se imponen con ArchUnit (sección 4). Se descartaron la arquitectura en capas clásica, que mezcla negocio con framework y base de datos y dificulta los tests unitarios puros de TEC-06, y la hexagonal por funcionalidad, que agrega complejidad que este tamaño de proyecto no justifica. | Las reglas de negocio (reservas, validaciones, costo promedio, idempotencia a nivel de decisión) se prueban como tests unitarios puros, y la arquitectura permite reglas de ArchUnit claras y estrictas. El riesgo de que el agente no sepa dónde va cada cosa se compensa con una plantilla de paquetes explícita y la ruta exacta de cada archivo en cada tarea. |

## 4. Stack base

| ID | Decisión | Justificación |
|---|---|---|
| TEC-11 | **Java 25 (LTS) + Spring Boot 4.1.x + Maven.** Las versiones exactas de cada dependencia se fijan en el plan. La constitución incluye una lista de "así no / así sí" con los cambios de Spring Boot 3 a 4 que más suelen confundir a los modelos. | Al 2026-11-21: Java 25 es LTS (soporte hasta 2030 o más); Spring Boot 4.1 es la versión vigente, con soporte OSS hasta julio de 2027, mientras que la 3.5 ya no tiene soporte OSS (terminó en junio de 2026). Un proyecto nuevo sobre una versión sin soporte sería observado por un referente técnico. El riesgo de que el agente escriba código de Spring Boot 3 se controla fijando dependencias y documentando las diferencias; además, ese error se manifiesta al compilar, no como un bug silencioso. Se eligió Maven porque su configuración declarativa y predecible es menos propensa a errores de los modelos que Gradle. Fuente de las fechas de soporte: endoflife.date, consultado el 2026-11-21. |
| TEC-12 | **PostgreSQL + Flyway + SQL explícito con `JdbcClient` (Spring). Sin ORM** (ni JPA ni Hibernate ni Spring Data JPA). Todo el SQL está escrito a mano y vive **solo** en los adaptadores de persistencia. Los cambios de esquema (CM-01 a CM-08) son migraciones Flyway numeradas, partiendo del esquema y los datos originales. | Control total de las consultas que llegan a la base. Además, las partes críticas del dominio son SQL fino: bloqueo del saldo del usuario (`SELECT ... FOR UPDATE`), idempotencia atómica (`INSERT ... ON CONFLICT`) y agregaciones sobre millones de órdenes, que un ORM esconde o complica. Para el agente implementador, una consulta explícita es más difícil de romper sin darse cuenta que el comportamiento implícito de un ORM. PostgreSQL porque el `database.sql` provisto es de Postgres. Se descartó jOOQ porque suma generación de código, un paso más donde el agente puede equivocarse. |

## 5. Estructura de paquetes y transacciones

### TEC-13 · Estructura de paquetes (aprobada)

Paquete base: `com.cocos.challenge`.

```
src/main/java/com/cocos/challenge/
├── domain/                         ← Java puro: sin Spring, sin SQL, sin HTTP
│   ├── model/                      entidades y objetos de valor inmutables (Order, Money, Quantity, ...)
│   ├── service/                    reglas puras: disponibles y reservas, validación de órdenes,
│   │                               costo promedio, cantidad por monto
│   └── exception/                  excepciones de negocio
├── application/                    ← orquesta casos de uso; solo conoce domain
│   ├── port/in/                    interfaces de casos de uso + comandos y resultados (records)
│   ├── port/out/                   interfaces hacia afuera (repositorios, bloqueo de cuenta, reloj)
│   └── service/                    implementación de cada caso de uso
└── infrastructure/                 ← único lugar con Spring, SQL y HTTP
    ├── adapter/in/rest/            controllers, DTOs, manejo de errores
    ├── adapter/in/rest/internal/   herramientas internas (solo administrador)
    ├── adapter/out/persistence/    implementaciones Jdbc* de los puertos: TODO el SQL vive acá
    └── config/                     registro de beans, reloj, configuración

src/main/resources/db/migration/    migraciones Flyway (V1 = esquema y datos originales; luego CM-01..CM-08)

src/test/java/com/cocos/challenge/
├── domain/          tests unitarios puros
├── application/     tests unitarios de casos de uso con implementaciones falsas de los puertos
├── integration/     tests contra PostgreSQL real (concurrencia, idempotencia, volumen)
├── bdd/             runner de Cucumber + step definitions
├── architecture/    reglas de ArchUnit
└── traceability/    verificación de la matriz CU ↔ BDD ↔ test (TEC-07)
src/test/resources/features/        un .feature por caso de uso
```

**Reglas de dependencia** (se imponen con ArchUnit):
1. `domain` no depende de nada fuera de `domain` y del JDK.
2. `application` depende solo de `domain`. Sus clases no llevan `@Service` ni `@Component`: se registran desde `infrastructure/config`. La única anotación de Spring permitida es `@Transactional` (TEC-14).
3. Solo `infrastructure/adapter/out/persistence` usa `JdbcClient` o SQL.
4. Los controllers solo invocan puertos de entrada (`port/in`), nunca servicios ni repositorios.
5. Los DTOs de la API no salen de `adapter/in/rest`; el dominio nunca se serializa directamente en una respuesta.

### TEC-14 · Límite transaccional: `@Transactional` en los servicios de `application`

**Decisión.** La transacción se abre en el **método público de cada caso de uso** (`application/service`), con la anotación `@Transactional` de Spring. Es la **única** dependencia de Spring permitida en `application`. ArchUnit la controla en los dos sentidos:
- **Permitida solo esa:** cualquier otra dependencia de Spring en `application` rompe el build.
- **Obligatoria:** todo método público de un caso de uso que **modifica datos** (enviar orden, cancelar, depositar, retirar y las herramientas internas) debe tenerla. Si falta, el build falla.

Las consultas (portfolio, búsqueda, historial) usan `@Transactional(readOnly = true)` para leer efectivo, reservas y posiciones de un mismo estado consistente.

**Justificación.** El límite correcto de la transacción es el caso de uso: ahí se valida el disponible, se bloquea la cuenta y se guarda la orden, y todo eso debe ser atómico. Con la anotación, ese límite se ve en el mismo método, todos los modelos la conocen y su ausencia se detecta automáticamente. El costo es una dependencia acotada de `application` hacia una anotación de Spring, aceptado a cambio de una garantía verificable.

**Alternativas descartadas:**

| Alternativa | Por qué se descartó |
|---|---|
| **(b) Puerto propio `TransactionRunner`** que `application` invoca y `infrastructure` implementa | Deja `application` sin Spring, pero cada caso de uso debe acordarse de envolver su lógica en la llamada. Si lo olvida (o deja parte de la lógica fuera), ni el compilador ni ArchUnit lo detectan: el error es **silencioso** y solo aparece con concurrencia real. Es el riesgo que menos conviene con un agente implementador débil. |
| **(c) Decoradores transaccionales** registrados en `infrastructure/config` que envuelven cada caso de uso | Es válida y mantiene `application` 100 % puro, pero agrega una clase decoradora por caso de uso, oculta el límite transaccional al leer el servicio, y verificar que ningún caso de uso quedó sin envolver requiere un test que recorra los beans, más complejo y frágil que una regla de ArchUnit. |
| **(d) `@Transactional` en los controllers** | Ubica la transacción en la capa web: queda abierta mientras se procesa HTTP y se serializa la respuesta, y cualquier otro adaptador de entrada (un job, un consumidor de mensajes) quedaría sin transacción. Viola la idea de que el caso de uso es la unidad de consistencia. |
| **(e) Una transacción por método de repositorio** | Validar el disponible y guardar la orden son llamadas distintas: quedarían en transacciones separadas y se perdería la atomicidad. Arreglarlo obligaría a mover la lógica de negocio al SQL, contrario a la arquitectura hexagonal (TEC-10). |

### TEC-15 · Patrón Outbox: evaluado, no se adopta por ahora

**Decisión.** No se implementa el patrón Outbox.

**Justificación.** El Outbox resuelve la **doble escritura**: guardar en la base y publicar un mensaje en un broker, dos sistemas que no comparten transacción. En este proyecto todas las escrituras ocurren en un único sistema (PostgreSQL), así que una transacción local ya garantiza la atomicidad (TEC-14). Las otras garantías se resuelven con herramientas específicas:

| Garantía | Qué evita | Cómo se resuelve |
|---|---|---|
| Atomicidad | Guardar la mitad de una operación | Transacción de PostgreSQL (TEC-14) |
| Aislamiento ante concurrencia | Dos órdenes simultáneas gastan el mismo saldo | Bloqueo de la cuenta del usuario dentro de la transacción (`SELECT ... FOR UPDATE`) |
| Idempotencia | Ejecutar dos veces la misma operación | Restricción única en la base sobre (usuario, clave) |

**Cuándo adoptarlo:** si el sistema tuviera que publicar eventos hacia afuera (por ejemplo, "orden ejecutada" para notificar al usuario o enrutar la orden a un mercado real). En ese caso, el evento se guardaría en una tabla `outbox` dentro de la misma transacción del caso de uso, y un proceso aparte lo publicaría con reintentos.

## 6. Concurrencia e idempotencia

### TEC-16 · Bloqueo pesimista sobre la fila del usuario

**Decisión.** Toda operación que **modifica datos de un usuario** (enviar orden, cancelar, depositar, retirar y las herramientas internas que afectan a un usuario) comienza, dentro de su transacción (TEC-14), bloqueando la fila de ese usuario:

```sql
SELECT id FROM users WHERE id = :userId FOR UPDATE
```

Si el usuario no existe, la operación termina con "no encontrado" (DEC-05, DEC-17). Las operaciones de **un mismo usuario** se ejecutan de a una; las de **usuarios distintos** siguen en paralelo. Las **consultas** (portfolio, historial, búsqueda) no bloquean.

**Flujo de una operación con efecto** (orden, depósito o retiro):
1. **Validación del pedido** (formato, campos obligatorios, clave de idempotencia presente). Si es inválido → error, sin abrir la transacción ni consumir la clave (DEC-05, DEC-07).
2. Abrir la transacción y **bloquear la fila del usuario**.
3. **Buscar la clave de idempotencia** del usuario. Si existe: comparar la huella del pedido (ver abajo). Si es idéntica → devolver la operación original como reintento (DEC-09). Si difiere → error de conflicto (DEC-09, DEC-23). En ambos casos, fin.
4. Calcular disponibles (efectivo y acciones, descontando reservas: DEC-01) desde `orders`.
5. Aplicar las reglas de negocio (dominio) y decidir el estado: `FILLED`, `NEW` o `REJECTED` con motivo.
6. Guardar la orden con su clave y su huella, y confirmar la transacción.

**Huella del pedido.** Para distinguir un reintento idéntico de un conflicto (DEC-09), se guarda junto con la clave una huella (hash) de los datos significativos del pedido normalizados: tipo de operación, instrumento, side, tipo de orden, cantidad o monto y precio. Esto amplía CM-02 (ver análisis, sección 8).

**Red de seguridad.** La restricción única sobre (usuario, clave) se mantiene en la base aunque el bloqueo ya serialice las operaciones: si algún camino de código omitiera el bloqueo, la base igualmente impediría la duplicación.

**Ausencia de deadlocks.** Cada transacción bloquea **una sola** fila de `users`, siempre como primer paso, así que no puede haber esperas circulares entre usuarios. Las herramientas internas que afectan a muchos usuarios a la vez (restaurar datos, I-04) usan su propio mecanismo y se documentan en su plan.

**Justificación.** Es **bloqueo pesimista**: se bloquea antes de leer, y las operaciones concurrentes esperan en lugar de fallar. Conviene acá porque el escenario crítico **es** el conflicto: 50 reintentos simultáneos de la misma orden (M-05) o dos órdenes que compiten por el mismo saldo (M-02). Con el bloqueo, el segundo pedido espera, encuentra la clave ya registrada y devuelve el resultado original, sin código adicional. El costo (las operaciones de un mismo usuario son secuenciales) no limita a un usuario real de un broker.

**Alternativas descartadas:**

| Alternativa | Familia | Por qué se descartó |
|---|---|---|
| **Bloqueo consultivo** (`pg_advisory_xact_lock(userId)`) | Pesimista | Equivalente en efecto, pero es una función específica de PostgreSQL, menos conocida y más fácil de usar mal (por ejemplo, confundir la variante de sesión con la de transacción). Bloquear una fila real es más explícito. |
| **Aislamiento `SERIALIZABLE` con reintentos** | Optimista (lo resuelve la base) | Exige una lógica de reintentos que es fácil de implementar mal y, en el escenario de 50 reintentos simultáneos, produciría abortos en cadena. |
| **Tabla de saldos con número de versión** | Optimista | No existe un registro de saldo que versionar: el saldo se calcula desde `orders`. Crearlo agregaría una segunda fuente de verdad que puede desincronizarse (riesgo para M-01), además de la lógica de reintentos. |

### TEC-32 · Dónde se guarda la clave de idempotencia: columna en `orders`

**Por qué la clave tiene que guardarse.** El contenido de un pedido no alcanza para saber si es *el mismo pedido* o *otro pedido igual*. Un reintento por corte de red ("comprar 10 PAMP" reenviado) y dos órdenes legítimas idénticas (CU-03.41) son byte a byte iguales; lo único que transporta la intención del cliente es la clave: la reusa al reintentar y genera una nueva para otra orden. Sin la clave guardada no se puede responder ninguna de las preguntas que exige la idempotencia:

| Pregunta | Sin la clave guardada |
|---|---|
| ¿Ya procesé este pedido? | Buscar por contenido bloquea órdenes legítimas idénticas; agregar una ventana de tiempo solo cambia qué caso falla |
| ¿El pedido es idéntico al anterior? | No hay "pedido anterior con esta clave" contra el cual comparar la huella |
| ¿Cuál es la respuesta original? | Depende de encontrar el pedido anterior |
| ¿Cómo evito duplicados simultáneos? | El bloqueo del usuario (TEC-16) **ordena** los pedidos pero no los **deduplica**: cada reintento, al no encontrar nada, ejecutaría una orden nueva. Tampoco existiría la restricción única en la base |

Ejemplo: el usuario quiere 10 PAMP, se corta la red, la app reintenta → termina con 20 acciones y $ 18.517 debitados en lugar de $ 9.258,50.

**Decisión.** La clave y la huella del pedido se guardan **en la misma fila de `orders`** que la operación (CM-02), con restricción única `(userid, idempotency_key)`.

**Justificación.** En este dominio toda operación con clave (compra, venta, depósito, retiro) produce **exactamente una fila** de `orders`, y esa fila **es** el resultado del pedido. Guardar la clave ahí da atomicidad sin esfuerzo (un solo `INSERT`: no puede existir la orden sin su clave ni la clave sin su orden), la restricción única como red de seguridad en la base, y la respuesta del reintento se reconstruye desde la propia orden (para eso existe `requested_amount`, CM-12).

**Alternativas evaluadas:**

| Alternativa | Evaluación |
|---|---|
| **Tabla separada `idempotency_keys`** (clave, huella, respuesta guardada, vencimiento), escrita en la misma transacción | **Válida; descartada por ahora.** Separa un dato de la capa HTTP del libro contable, permite purgar claves vencidas y cubre operaciones que no crean una orden. A cambio, cada operación hace dos escrituras y la respuesta guardada puede divergir del estado real de la orden (por ejemplo, tras una cancelación). |
| **Caché en memoria o Redis** | **Descartada.** No es atómica con la base: si la app se cae después de confirmar la orden y antes de guardar la clave, el reintento ejecuta un duplicado; si guarda la clave y la transacción se revierte, el reintento recibe "ya procesado" de una orden inexistente. En memoria, además, se pierde al reiniciar y no se comparte entre instancias. |
| **Deduplicar por contenido** (con o sin ventana de tiempo) | **Descartada.** No distingue un reintento de dos órdenes legítimas idénticas (CU-03.41). |

**Criterio para pasar a la tabla separada:** si aparece una operación con clave que **no** crea una fila en `orders`, si se necesita que las claves venzan (por ejemplo, por volumen), o si la respuesta a repetir deja de poder reconstruirse desde la orden.

---

## 7. Cálculo de saldo, reservas y posiciones

### TEC-17 · Cálculo en tiempo real desde `orders`, detrás de un puerto, con rendimiento verificado

**Decisión.** Efectivo, reservas, posiciones y costo promedio se **calculan en cada operación o consulta a partir de `orders`**, que sigue siendo la única fuente de verdad. El cálculo se accede a través de un puerto de salida (por ejemplo, `AccountStateReader`). Pasar a tablas precalculadas sería un cambio **solo del adaptador de persistencia**, sin tocar el dominio ni los casos de uso.

**Medidas para que el cálculo siga siendo rápido con volumen:**

| Medida | Detalle |
|---|---|
| **Agregar en SQL, no en Java** | Efectivo y reservas se obtienen con **una** consulta de agregación por usuario (`SUM` con `CASE` por side y estado), que devuelve una sola fila. Las cantidades por instrumento salen de una agregación `GROUP BY instrumentid`. Nunca se traen miles de filas a memoria para sumarlas. |
| **Índice cubriente sobre `orders`** | Índice por `(userid, status, instrumentid)` que **incluye** `side`, `size`, `price` y `datetime`, para que las agregaciones de un usuario se resuelvan leyendo solo el índice, sin tocar la tabla (*index-only scan*). Se define en CM-07. |
| **Índice parcial de órdenes pendientes** | Índice por `userid` limitado a `status = 'NEW'`, para calcular reservas leyendo solo las órdenes pendientes. |
| **Costo promedio: datos mínimos y lógica en el dominio** | El costo promedio con reinicio en 0 (DEC-02.1) es un cálculo secuencial que en SQL sería complejo y difícil de probar. Se traen **solo** las órdenes `FILLED` de las posiciones abiertas del usuario, con las columnas mínimas y ya ordenadas por el índice, y el cálculo lo hace el dominio en Java puro (probado con tests unitarios). Con 10.000 órdenes son unos pocos cientos de KB. |
| **Último precio directo** | Acceso al último `close` por instrumento con el índice de CM-06, sin recorrer el historial. |
| **Estadísticas actualizadas** | Después de cargas masivas (datos de prueba de volumen) se ejecuta `ANALYZE` para que el planificador elija los índices. |
| **Guardia de planes de ejecución** | Un test de integración ejecuta `EXPLAIN` sobre las consultas críticas con el volumen de prueba y **falla si alguna recorre `orders` completa** (*sequential scan*). Si el agente escribe una consulta que no usa los índices, el build falla aunque el resultado sea correcto. |
| **Métricas como compuerta** | Las pruebas de volumen (M-08 a M-11) son parte del criterio de aceptación: si no se cumplen, la funcionalidad no está terminada. |

**Qué se puede asegurar y qué no.** Con estas medidas, el diseño queda preparado para cumplir M-08 con margen, y **cualquier regresión se detecta automáticamente** (guardia de planes y pruebas de volumen). Lo que no se puede asegurar sin medir es el número exacto de milisegundos en cada máquina: por eso la verificación es por medición, no por promesa.

**Criterio para pasar a tablas precalculadas:** si M-08 o M-10 no se cumplen con las medidas anteriores, o si el volumen objetivo pasa a ser de cientos de miles de órdenes por usuario. En ese caso, las tablas se actualizan en la misma transacción que la orden, bajo el bloqueo del usuario (TEC-16), y se agrega un test de conciliación contra `orders` para mantener M-01.

**Medición (2026-11-21, spec 009).** Con 10.000.000 de órdenes, 100.000 instrumentos y un usuario con 10.000 órdenes, en PostgreSQL 18.7 (Docker, laptop Apple Silicon), el SQL del portfolio completo tarda ~15 ms (saldo ~3 ms, posiciones ~6 ms, ejecuciones ~5 ms, precios ~1 ms), todas con *index-only scans* sobre `orders_user_status_instrument_idx`; la página del historial ~1 ms; la búsqueda parcial en el peor caso ~21–27 ms. Las metas del PRD (p95 de 200 / 100 / 150 / 100 ms, punta a punta) quedan con amplio margen: **el criterio para pasar a tablas precalculadas no se cumple** y el riesgo RSK-01 no se materializa. La verificación punta a punta queda automatizada en el perfil `-Pvolume` (TEC-24).

**Alternativa descartada por ahora:** tablas precalculadas desde el inicio. Lecturas más rápidas sin importar el historial, pero con dos fuentes de verdad, herramientas internas más complejas (borrar una orden obliga a recalcular) y carga inicial de los datos semilla, un costo que el volumen definido en el PRD no justifica.

## 8. API

### TEC-18 · Identidad del solicitante y acceso del administrador

**Decisión: estilo "mis datos".** En los endpoints de usuario, el usuario se obtiene **solo** del header `X-User-Id`; las URLs no permiten nombrar a otro usuario.

```
GET  /api/v1/portfolio                 X-User-Id: 1
GET  /api/v1/orders/{orderId}          X-User-Id: 1   ← se busca por id Y por usuario: si es de otro, "no encontrado"
```

El **administrador** usa endpoints propios dentro de las herramientas internas, donde el usuario sí va en la URL:

```
GET  /api/internal/users/{userId}/portfolio            X-User-Id: 99   X-Role: ADMIN
GET  /api/internal/users/{userId}/orders
GET  /api/internal/users/{userId}/orders/{orderId}
     + herramientas I-01 a I-05
```

**Reglas:**
1. Los casos de uso reciben el `userId` como parámetro y no saben quién los invoca. Solo cambia el adaptador REST: el controller de usuario toma el id del header; el controller interno verifica el rol y toma el id de la URL. **No se duplica lógica.**
2. Las lecturas del administrador son **herramientas internas (nivel 3)**: el challenge no pide un administrador.
3. El administrador **consulta y usa herramientas internas, pero no opera en nombre de un usuario** (no envía órdenes, depósitos ni retiros por otro).
4. El rol se declara con `X-Role: ADMIN` y, como `X-User-Id`, **no se verifica** (limitación documentada en PRD SUP-01; en producción ambos vendrían de un token). Sin el rol, los endpoints internos responden "no encontrado".
5. Los endpoints internos se **activan por configuración**: habilitados en el perfil de demo y deshabilitados por defecto. En cualquier otro despliegue no existen.

**Justificación.** El aislamiento entre usuarios (DEC-17) se cumple **por construcción**: no hay ningún endpoint de usuario que acepte el id de otro, así que no depende de que cada endpoint se acuerde de comparar. Es además como funcionan las APIs de brokers con token: el token dice quién sos y la URL no lo repite. Al incorporar autenticación real, el header se reemplaza por el token sin cambiar ninguna URL.

**Alternativa descartada:** usuario en la URL y solicitante en un header (`/api/v1/users/{userId}/portfolio` + `X-User-Id`). Exige comparar ambos en cada endpoint; un olvido es una fuga de datos entre usuarios.

### TEC-19 · Convenciones de la API, errores y mensajes

**Idioma: todo texto que devuelve la API está SIEMPRE en inglés.** Mensajes de error, de éxito, motivos de rechazo, nombres de campos y valores enumerados. La documentación del proyecto sigue en español; la API no.

**Convenciones generales:**

| Tema | Convención |
|---|---|
| Versión | En la URL: `/api/v1/...`; herramientas internas en `/api/internal/...` |
| Idempotencia | Header estándar `Idempotency-Key` (obligatorio en operaciones con efecto) |
| Identidad | Headers `X-User-Id` y, para el administrador, `X-Role: ADMIN` (TEC-18) |
| Fechas | ISO 8601 en UTC con zona explícita: `2026-11-21T14:36:00Z` (DEC-18) |
| Paginado | Por cursor opaco (`cursor`, `limit`); la respuesta incluye `nextCursor` (CM-08) |
| Nombres JSON | `camelCase` en inglés |
| Campos desconocidos | Se rechazan con `VALIDATION_ERROR` nombrando el campo (DEC-24) |

**Errores: formato RFC 9457 (Problem Details) con código y mensaje.** Toda respuesta de error incluye un **código estable** (para que el cliente decida qué hacer) y un **mensaje legible en inglés con los valores concretos** (para que una persona entienda qué pasó):

```json
{
  "type": "https://api.cocos.example/errors/idempotency-conflict",
  "title": "Idempotency key conflict",
  "status": 422,
  "code": "IDEMPOTENCY_CONFLICT",
  "detail": "Idempotency key 'k1' was already used for a different request.",
  "instance": "/api/v1/orders"
}
```

Los mensajes nunca revelan datos de otros usuarios. Por ejemplo, el error por un recurso ajeno es idéntico al de un recurso inexistente.

**Una orden `REJECTED` no es un error HTTP.** El pedido fue válido y la orden **se guardó** (DEC-05), así que la respuesta es exitosa (`201 Created`) y devuelve la orden con su estado y su motivo de rechazo, también con código y mensaje:

```json
{
  "orderId": 1234,
  "status": "REJECTED",
  "rejection": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Insufficient available cash in account 10001: required ARS 925850.00, available ARS 627500.00."
  }
}
```

**Catálogo de errores HTTP:**

| Código | HTTP | Cuándo | Mensaje de ejemplo |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Pedido inválido (DEC-05): campos faltantes, cantidad ≤ 0, `LIMIT` sin precio, `MARKET` con precio, cantidad y monto a la vez, más de 2 decimales, etc. Incluye la lista de campos con error. | `Request validation failed: 'price' is required for LIMIT orders.` |
| `MISSING_IDEMPOTENCY_KEY` | 400 | Operación con efecto sin `Idempotency-Key` | `The 'Idempotency-Key' header is required for this operation.` |
| `SEARCH_TERM_TOO_SHORT` | 400 | Filtro de nombre con 1 o 2 caracteres (DEC-21) | `The name filter requires at least 3 characters.` |
| `NOT_FOUND` | 404 | Usuario, orden o instrumento inexistente, recurso de otro usuario, o endpoint interno sin rol de administrador | `Order 1234 was not found.` |
| `IDEMPOTENCY_CONFLICT` | 422 | Misma clave con otro contenido o en otro tipo de operación (DEC-09, DEC-23) | `Idempotency key 'k1' was already used for a different request.` |
| `ORDER_NOT_EXECUTABLE` | 409 | Herramienta interna: forzar la ejecución de una orden que no está `NEW` *(agregado en la spec 009)* | `Order 2 cannot be executed because its status is FILLED. Only NEW orders can be executed.` |
| `ORDER_NOT_CANCELLABLE` | 409 | Cancelar una orden `FILLED` o `REJECTED` (DEC-15) | `Order 1234 cannot be cancelled because its status is FILLED. Only NEW orders can be cancelled.` |
| `METHOD_NOT_ALLOWED` | 405 | Método HTTP no soportado por el recurso *(agregado en la spec 003)* | `Method DELETE is not supported for this resource.` |
| `NOT_ACCEPTABLE` | 406 | El cliente pide un formato de respuesta no soportado *(spec 003)* | `The requested media type is not supported.` |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | El cuerpo no es JSON *(spec 003)* | `Content type 'text/plain' is not supported.` |
| `REQUEST_ERROR` | otro 4xx | Cualquier otro error del cliente detectado por el framework *(spec 003)* | Mensaje estándar del framework |
| `INTERNAL_ERROR` | 500 | Error inesperado; nunca expone detalles internos *(spec 003)* | `An unexpected error occurred.` |

**Catálogo de motivos de rechazo** (dentro de la orden, no como error HTTP):

| Código | Cuándo | Mensaje de ejemplo |
|---|---|---|
| `INSUFFICIENT_FUNDS` | Compra o retiro que supera los pesos disponibles | `Insufficient available cash in account 10001: required ARS 925850.00, available ARS 627500.00.` |
| `INSUFFICIENT_SHARES` | Venta que supera las acciones disponibles | `Insufficient available shares of PAMP in account 10001: required 41, available 40.` |
| `AMOUNT_BELOW_PRICE` | Orden por monto que no alcanza para una acción (DEC-22) | `The amount ARS 500.00 is not enough to buy one share of PAMP at ARS 925.85.` |
| `NO_MARKET_PRICE` | Orden `MARKET` sobre un instrumento sin market data | `No market price is available for PGR; MARKET orders cannot be executed.` |
| `PRICE_OUT_OF_RANGE` | Orden `LIMIT` con un precio fuera de la banda de ±20 % del último precio (DEC-29) | `The limit price ARS 1111.03 is outside the allowed range for PAMP: ARS 740.68 to ARS 1111.02 (20% around the last price ARS 925.85).` |
| `QUANTITY_TOO_LARGE` | Orden por monto que calcula más de 2.147.483.647 acciones (DEC-30) | `The amount ARS 9999999999.00 would buy more than 2147483647 shares of PGR at ARS 0.01.` |

**Respuestas de éxito relevantes:**

| Situación | HTTP | Detalle |
|---|---|---|
| Orden, depósito o retiro nuevo (cualquier estado, incluido `REJECTED`) | 201 | Devuelve la operación creada |
| Reintento idéntico (DEC-09) | 200 | Devuelve la operación original y el header `Idempotent-Replayed: true` |
| Cancelación | 200 | Devuelve la orden; si ya estaba cancelada, incluye `"alreadyCancelled": true` (DEC-15) |

**Justificación.** El código permite que un cliente reaccione sin interpretar texto; el mensaje con valores concretos permite que una persona (usuario, soporte, evaluador) entienda el problema sin consultar la base. RFC 9457 es el estándar de la industria y Spring lo soporta de forma nativa. Distinguir "orden rechazada" (respuesta exitosa, operación registrada) de "pedido inválido" (error HTTP, nada registrado) refleja exactamente DEC-05. Usar 422 para el conflicto de clave sigue la propuesta de estándar de la IETF para `Idempotency-Key`. El inglés en la API es la convención de la industria y facilita la integración con cualquier cliente.

### TEC-20 · Montos y precios como texto decimal en el JSON

**Decisión.** Todo monto y precio viaja como **texto decimal**. Las respuestas llevan siempre **exactamente 2 decimales**: `"price": "925.85"`, `"amount": "50000.00"`. Los pedidos aceptan **hasta 2 decimales** (`"925"`, `"925.8"` y `"925.85"` son válidos; en el contrato, el esquema `MoneyInput`), igual que los depósitos y retiros (DEC-13). Las cantidades de acciones son enteros JSON (`"quantity": 54`). Los porcentajes también van como texto con 2 decimales (`"totalReturnPct": "-0.45"`). Un monto con formato inválido o más de 2 decimales es un `VALIDATION_ERROR`. Internamente, el dinero se maneja siempre con un tipo decimal exacto (nunca `double` ni `float`), redondeando mitad hacia arriba (PRD SUP-04).

**Justificación.** Muchos clientes (JavaScript, por ejemplo) convierten los números JSON a punto flotante, donde los montos dejan de ser exactos (`0.1 + 0.2 ≠ 0.3`). En una API de dinero, perder centavos en el cliente es un bug real. Es la convención de muchas APIs financieras.

**Alternativa descartada:** montos como número JSON (`925.85`). Más cómodo de leer, pero no garantiza exactitud en todos los clientes.

## 9. División en funcionalidades (Spec Kit)

### TEC-21 · Specs, implementadas en este orden (10 desde TEC-31)

| # | Spec | Alcance | Criterio de terminado |
|---|---|---|---|
| 001 | Esqueleto y barreras | Proyecto Maven, estructura de paquetes (TEC-13), reglas de ArchUnit, verificación de trazabilidad (TEC-07), entorno de tests (Cucumber + PostgreSQL real), arranque con un solo comando | Compila; ArchUnit y trazabilidad se ejecutan; la aplicación levanta |
| 002 | Base de datos | Migraciones Flyway: V1 con esquema y datos originales; CM-01 a CM-09 | Tests con SQL directo verifican el estado esperado del usuario 1 (efectivo $ 753.000, disponible $ 627.500, etc.) |
| 003 | Convenciones de la API | Errores RFC 9457 y catálogo (TEC-19), identidad y rol por header (TEC-18), montos como texto (TEC-20), fechas UTC, validación de `Idempotency-Key`, activación de endpoints internos, puerto `BusinessMetrics` con su adaptador Micrometer (TEC-31) | Tests de cada convención sobre un endpoint mínimo |
| 004 | Búsqueda de activos | CU-02 | Escenarios CU-02 en verde |
| 005 | Envío de órdenes | CU-03: reservas, idempotencia, bloqueo y concurrencia | Escenarios CU-03 en verde |
| 006 | Portfolio | CU-01 | Escenarios CU-01 en verde |
| 007 | Depósitos y retiros | CU-05, CU-06 | Escenarios CU-05 y CU-06 en verde |
| 008 | Cancelación e historial | CU-04, CU-07 | Escenarios CU-04 y CU-07 en verde |
| 009 | Herramientas internas y volumen | CU-I, endpoints de administrador, pruebas de volumen (M-08 a M-11) y guardia de planes (TEC-17) | Escenarios CU-I en verde y métricas de volumen cumplidas |
| 010 | Observabilidad | Exportación OpenTelemetry, backend `grafana/otel-lgtm` en el compose, correlación de trazas y logs, tableros de salud y de negocio (TEC-31). El puerto `BusinessMetrics` se crea antes, en la 003 | Tableros aprovisionados y con datos al levantar el compose; tests verifican que cada operación registra sus métricas |

**Justificación.** Specs chicas y con un criterio de terminado verificable son más fáciles de cumplir para un agente implementador. La base se dividió en tres porque mezcla temas independientes: las **barreras** (001) son lo más importante del proyecto, porque una vez que funcionan todo lo demás se autocontrola, y se revisan antes de seguir; los **datos** (002) quedan verificados antes de que exista lógica de negocio; y las **convenciones** (003) evitan que cada funcionalidad invente su propio formato de errores o de montos. El orden va de lo más simple (búsqueda, para ejercitar el ciclo de TDD) al corazón del sistema (órdenes), y luego a lo que depende de él. Se evaluó juntar 002 y 003 (8 specs) y se prefirió mantenerlas separadas.

## 10. Herramientas y operación

| ID | Decisión | Justificación |
|---|---|---|
| TEC-22 | **Contract-first con OpenAPI.** Cada spec con endpoints incluye su contrato **acumulativo** `openapi.yaml` en `specs/NNN-*/contracts/`, escrito antes de implementar. El último se copia literal a `src/main/resources/static/openapi/openapi.yaml` y Swagger UI muestra **ese** archivo (springdoc solo aporta la UI; el `/v3/api-docs` que genera no es el contrato). Tests verifican que el archivo publicado es idéntico al de la última spec y que las rutas de la aplicación coinciden exactamente con las del contrato; los tests de cada endpoint verifican sus cuerpos y códigos. | El agente implementa contra un contrato exacto en lugar de inventar nombres de campos, códigos o formatos. |
| TEC-23 | **Stack de tests:** JUnit y AssertJ (versiones gestionadas por Spring Boot 4.1), Cucumber-JVM integrado con Spring, Testcontainers con PostgreSQL y ArchUnit. | Estándar del ecosistema Java; Testcontainers usa el mismo motor de base que producción, condición de TEC-06 para los tests de integración. |
| TEC-24 | **Pruebas de volumen en un perfil de Maven separado (`-Pvolume`).** Los datos (10.000.000 de órdenes, un usuario con 10.000, 100.000 instrumentos) se generan con SQL (`generate_series`) dentro de Testcontainers, seguido de `ANALYZE`. Se hacen llamadas HTTP de calentamiento y luego se miden y calculan p95 y p99 contra las metas M-08 a M-11. | Todo queda dentro de JUnit, sin sumar una herramienta de carga (Gatling, k6) que el agente debería aprender. Queda fuera del build normal porque tarda. |
| TEC-25 | **Arranque con un solo comando: `docker compose up`.** Levanta PostgreSQL y la aplicación (imagen construida con un Dockerfile multi-etapa), con los datos de ejemplo cargados por Flyway y el perfil de demo activo (herramientas internas habilitadas). | Cumple M-15 sin requerir Java ni PostgreSQL instalados. |
| TEC-26 | **Colección de ejemplos en archivos `.http`** (cliente HTTP de IntelliJ), uno por funcionalidad, con las herramientas internas en un archivo separado. | Viven en el repositorio, se versionan y se ejecutan desde el IDE (M-16). |
| TEC-27 | **Logs estructurados en JSON** (soporte nativo de Spring Boot) con un id de correlación por pedido (header `X-Request-Id`, generado si no viene). Cada operación con efecto registra `orderId`, `userId`, clave de idempotencia y resultado. | Trazabilidad de punta a punta (PRD RNF-04). |
| TEC-28 | **Git con rama `main` y una rama por spec** (las crea Spec Kit). | Cada spec queda aislada y revisable, lo que también facilita la auditoría final. |
| TEC-29 | **Idioma de los artefactos para el agente: inglés.** Constitución, `AGENTS.md`, specs, planes, tareas, contratos y archivos `.feature` se escriben en inglés. La documentación para personas (análisis, PRD, RFC) sigue en español. Los IDs (CU-xx.yy, DEC-xx, TEC-xx, CM-xx) se mantienen idénticos en ambos idiomas. | Los modelos siguen instrucciones con más precisión en inglés, y el código, la API y los tests ya están en inglés (TEC-19): un escenario y su test usan los mismos términos sin traducción intermedia. Los IDs garantizan la trazabilidad entre la documentación en español y los artefactos en inglés. |
| TEC-30 | **Pull requests de 400 líneas como máximo.** Se cuentan las líneas agregadas y eliminadas de todo archivo modificado, **tests incluidos**, excepto: Maven Wrapper, `.specify/`, `.opencode/`, `specs/`, `docs/`, archivos `.feature`, la migración V1 (copia literal del `database.sql` provisto) y archivos Markdown. Cada `tasks.md` viene dividido en **porciones de PR** (`NNN-a`, `NNN-b`, ...) con una separación lógica, y cada porción deja la suite en verde (el ciclo de TDD se aplica por porción). Si una porción se pasa del límite, el agente la divide; si es imposible, abre el PR con una sección **"Size justification"** y quien revisa decide si la acepta o exige reducirlo. Verificación: `scripts/check-pr-size.sh` (local, obligatorio antes de abrir el PR), plantilla `.github/pull_request_template.md` y GitHub Action `PR size`, que falla si se supera el límite sin justificación. Una rama por porción (`NNN-nombre-letra`), creada desde `main` actualizado. | Los PRs deben poder seguirse por una persona, no solo por el revisor automático. Está documentado que la efectividad de una revisión cae marcadamente por encima de ~400 líneas. Las exclusiones apuntan a lo que no se revisa línea por línea (generado, copiado o escrito antes como spec); los tests se cuentan porque también se revisan. Planificar las porciones desde las tareas evita que el agente tenga que decidir solo cómo cortar. La excepción con justificación mantiene la flexibilidad sin volver la regla opcional. |
| TEC-33 | **Integración continua con GitHub Actions**: el workflow `CI` corre `./mvnw verify` (tests unitarios, de integración con Testcontainers, BDD, ArchUnit y trazabilidad) en cada PR y en cada push a `main`. Su check `verify` es obligatorio para mergear en `main`, junto con `pr-size`. `main` está protegida: PR obligatorio también para administradores, rama al día, conversaciones resueltas, sin force push; 0 aprobaciones requeridas porque el autor no puede aprobar sus propios PRs y el agente los abre con su cuenta. El workflow se agrega en la porción 001-a; el check se vuelve obligatorio al mergearla. | Sin CI, nada impide mergear un PR con tests rotos si el agente se equivoca o declara en la descripción un resultado que no obtuvo: la evidencia tiene que venir de una ejecución independiente. Los runners de GitHub incluyen Docker, así que Testcontainers funciona sin configuración extra. |
| TEC-34 | **Revisión automática de cada PR por Claude** con la GitHub Action oficial `anthropics/claude-code-action@v1`, autenticada con el token de la suscripción del dueño del repositorio (secret `CLAUDE_CODE_OAUTH_TOKEN`, generado con `claude setup-token`). Se dispara al abrir, actualizar, reabrir o pasar a listo un PR; no revisa borradores ni PRs de forks; un push nuevo cancela la revisión en curso. Las instrucciones de revisión están versionadas en `.github/claude/pr-review.md`: identificar la spec y la porción, leer constitución, plan, tareas y escenarios, revisar el diff con 17 chequeos (tareas completas, copias literales idénticas, tests no modificados, evidencia de test-first, trazabilidad, arquitectura, SQL, dinero, mensajes, concurrencia, dependencias, tamaño, CI, plantilla, **orden de los commits** —tests antes que la implementación— y **coherencia entre lo que declara la descripción y la evidencia real**, por ejemplo que "CI en verde" no se haya escrito antes de que la CI terminara; estos dos se agregaron después de comparar la primera revisión automática con una revisión manual; y el 17: un PR de porción no toca las barreras, y todo cambio de reglas actualiza `docs/reglas-y-barreras.md`) y publicar **una** revisión en español con veredicto, hallazgos bloqueantes con archivo y regla, y tabla de verificación. Herramientas mínimas (lectura, `gh pr` de lectura y revisión, `git` de lectura). Las instrucciones de revisión y el script de tamaño se toman **de la rama base**, no del PR (también en el check `pr-size`): un PR no puede cambiar las reglas con las que se lo evalúa. La revisión **espera a que termine el check `verify` de la CI** del mismo commit (hasta 20 minutos, sin consumir uso del modelo), para evaluar su resultado real en lugar de verlo pendiente. El contenido del PR se trata como datos no confiables (prevención de *prompt injection*). Desde TEC-35 su veredicto es un check obligatorio: si no aprueba el commit, el PR no se mergea. | Que la revisión ocurra sola, sin depender de que una sesión local esté abierta. Es la opción documentada y estable (frente a las tareas programadas en *research preview* o una vigilancia local que se corta al cerrar la sesión) y queda versionada en el repositorio. La aprobación o el rechazo **formal** del PR no está documentado por la acción: se intenta con `gh pr review` y, si GitHub lo rechaza, la misma revisión se publica como comentario. Cada revisión consume uso de la suscripción y minutos de Actions. |
| TEC-35 | **Merge automático con compuerta de revisión manual.** Checks obligatorios de `main`: `verify`, `pr-size`, `review` (falla si la revisión automática no aprobó el último commit del PR) y `manual-review`. Este último lee del `tasks.md` **de `main`** si la porción del PR (deducida del nombre de la rama) está marcada `**Manual review**: required`; si lo está, exige la etiqueta `manual-review-approved`, que agrega el revisor del dueño después de la revisión manual, y un push nuevo la quita. El agente activa el auto-merge al abrir el PR (`gh pr merge --auto --squash`); nunca mergea a mano ni toca etiquetas. | El dueño deja de esperar y mergear a mano las porciones sin revisión manual (unas dos de cada tres), sin perder controles: en esas porciones el bot ya era el único filtro, y las críticas siguen esperando la revisión manual, ahora garantizado por GitHub y no por la memoria de nadie. Limitaciones: un PR que cambia el workflow de revisión saltea la revisión y queda bloqueado hasta que el dueño ajuste la protección; el agente, el revisor y el dueño usan la misma cuenta de GitHub, así que GitHub no distingue quién puso la etiqueta: la prohibición al agente es una instrucción, auditable en el historial del PR. |

## 11. Observabilidad

### TEC-31 · OpenTelemetry con métricas, trazas y logs correlacionados

**Decisión.**
- **Instrumentación:** Spring Boot Actuator + Micrometer, exportando métricas, trazas y logs por **OpenTelemetry (OTLP)** con el soporte nativo de Spring Boot 4.
- **Backend local:** la imagen todo-en-uno `grafana/otel-lgtm` (colector OpenTelemetry, Prometheus, Loki, Tempo y Grafana en **un solo contenedor**) dentro del `docker compose` (TEC-25). Las trazas y los logs comparten el id de traza, así que desde un pico en una métrica se llega a la traza y a los logs del pedido exacto.
- **Métricas de negocio desde dos fuentes:**

| Fuente | Qué muestra | Por qué |
|---|---|---|
| **Contadores y timers de Micrometer** registrados en cada operación | Tiempo real: operaciones por minuto, % aceptadas, rechazadas y pendientes, rechazos por motivo, reintentos idempotentes, conflictos de idempotencia, tiempo de espera del bloqueo de usuario | Detecta problemas *ahora*. Se reinicia con la aplicación |
| **Consultas SQL de Grafana a `orders`** (fuente de datos PostgreSQL con un usuario de solo lectura) | Histórico exacto: transacciones totales, % de éxito, % de rechazos, rechazos por motivo, volumen operado por side, depósitos y retiros | `orders` es el libro contable: números exactos, incluidos los datos semilla, que no se pierden al reiniciar |

- **Etiquetas permitidas:** operación, side, tipo de orden, estado, motivo de rechazo y resultado. **Prohibido** usar como etiqueta `userId`, id de orden, clave de idempotencia o ticker: crearían millones de series distintas (alta cardinalidad) y saturarían el backend de métricas.
- **Arquitectura:** `domain` y `application` no conocen Micrometer ni OpenTelemetry. `application` registra los hechos de negocio a través del puerto de salida **`BusinessMetrics`** (`application.port.out`), implementado con Micrometer en `infrastructure.adapter.out.metrics`. ArchUnit lo controla.
- **Tableros versionados** en el repositorio (JSON de Grafana, aprovisionados automáticamente al levantar el compose):
    - **Salud de la aplicación:** disponibilidad (health), latencia p95/p99 por endpoint, tasa de errores 4xx/5xx, pool de conexiones, JVM.
    - **Negocio:** las métricas de la tabla anterior.
- **Distribución en specs:** el puerto `BusinessMetrics` y su adaptador se crean en la spec 003, para que cada funcionalidad registre sus métricas desde su propia spec. El backend, la exportación OTLP, la correlación de trazas y logs y los tableros se hacen en la **spec 010**.

**Justificación.** Un broker necesita detectar en tiempo real anomalías de negocio (por ejemplo, un pico de rechazos por `INSUFFICIENT_FUNDS` o de conflictos de idempotencia) además de la salud técnica. OpenTelemetry es el estándar actual de la industria y Spring Boot 4 lo soporta de forma nativa; la imagen todo-en-uno mantiene el arranque en un solo comando. Separar el tiempo real (contadores) del histórico (SQL sobre el libro contable) da números exactos sin duplicar datos. Definir el puerto desde la spec 003 evita retocar funcionalidades ya terminadas.

**Verificación (2026-11-21, spec 010).** Probado punta a punta con Spring Boot 4.1.3 y `grafana/otel-lgtm:0.41.1`: las métricas llegan a Prometheus (`cocos_orders_processed_total` con sus etiquetas), los logs a Loki con `requestId` y `trace_id`, y la traza del mismo pedido a Tempo; los dos tableros se aprovisionan y la fuente de datos de solo lectura ejecuta las consultas. Hallazgos que quedaron documentados para el agente: Spring Boot 4 renombró las propiedades OTLP de trazas y logs; Spring Boot no conecta Logback con OpenTelemetry (hace falta `opentelemetry-logback-appender-1.0` **en la versión compilada contra el OpenTelemetry que gestiona Spring Boot**: 2.28.0-alpha; la última versión rompe en ejecución); y con un `logback-spring.xml` propio hay que incluir `structured-console-appender.xml` para conservar los logs JSON.

**Alternativa descartada:** solo métricas con Prometheus + Grafana. Más simple, pero sin trazas ni logs correlacionados, que es lo que permite investigar un problema puntual.
