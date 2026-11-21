# PRD — API de Portfolio y Órdenes (Cocos Backend Challenge)

| | |
|---|---|
| **Estado** | **Aprobado** (2026-11-21) |
| **Fecha** | 2026-11-21 |
| **Plazo** | 5 días (2026-11-21 → 2026-11-26) |
| **Documentos relacionados** | [Análisis funcional](analisis-funcional.md) (razonamiento y decisiones DEC-xx) · [Casos de uso](casos-de-uso.md) (criterios de aceptación CU-xx) · [Enunciado original](referencia/backend-challenge.md) |

> Este PRD define **qué** se construye y **por qué**, y cómo se mide el éxito. El **cómo** (tecnología, arquitectura,
> herramientas de prueba) se define en el RFC. Las referencias DEC-xx y CU-xx apuntan al detalle en los documentos relacionados.

---

## 1. Contexto y problema

Cocos es un broker de inversiones. Sus usuarios depositan pesos y compran y venden acciones del mercado argentino.
Este proyecto construye la API que sostiene esa operatoria: **ver qué tengo, buscar qué comprar y operar**.

El problema de fondo no es exponer tres endpoints. Es hacerlo **sin perder nunca la consistencia del dinero**.
Un broker no puede dejar a un usuario con saldo negativo, ejecutar dos veces una compra por un reintento de red ni mostrarle a un usuario datos de otro.

El proyecto se desarrolla como práctica por cuenta propia, pero se diseña y evalúa **como si lo revisara un referente técnico de Cocos**.

### 1.1 Desvío consciente del enunciado: Java en lugar de Node.js

El enunciado exige Node.js. Este proyecto se implementa en **Java** por decisión explícita del autor. Uno de los objetivos personales es practicar y dominar el **desarrollo asistido por agentes de IA** con *spec-driven development* (GitHub Spec Kit), BDD y reglas de arquitectura verificables (ArchUnit), y hacerlo en el stack que el autor elige. Un evaluador de Cocos lo consideraría un incumplimiento del requisito técnico; se documenta acá para que quede claro que es una decisión y no un descuido. Todo el análisis funcional, los casos de uso y este PRD son independientes del lenguaje.

**Forma de trabajo:** las definiciones, specs, escenarios BDD y reglas de arquitectura se dejan completas para que **otro agente de IA implemente** el código. Luego, ese código se revisa contra las specs.

## 2. Objetivos

| ID | Objetivo |
|---|---|
| OBJ-1 | Cumplir el 100 % de lo pedido en el enunciado (portfolio, búsqueda, envío de órdenes y test funcional del envío), **salvo el lenguaje**: se implementa en Java en lugar de Node.js (ver §1.1). |
| OBJ-2 | Garantizar la **integridad del dinero**: ninguna operación aceptada deja pesos ni acciones negativos, y ninguna se ejecuta dos veces. |
| OBJ-3 | Mostrar criterio de producto: cada ambigüedad del enunciado tiene una decisión documentada y justificada. |
| OBJ-4 | Demostrar que el diseño **escala** a volúmenes reales de un broker, no solo a los datos de ejemplo. |
| OBJ-5 | Que quien lo evalúe pueda levantarlo, probarlo y entenderlo en minutos. |

### No objetivos
- Simular el mercado: no hay matching ni evolución de precios (enunciado).
- Autenticación real de usuarios (enunciado). El aislamiento entre usuarios sí se implementa (RF-10).
- Interfaz visual o BFF. La API devuelve datos en UTC y la presentación local le corresponde al consumidor.
- Comisiones, impuestos, horarios de mercado e instrumentos que no sean acciones.

## 3. Métricas de éxito

Todas son verificables con pruebas automatizadas. Los tiempos se miden en un **entorno local de referencia** que describe el RFC.
Lo que importa es cumplirlos **con el volumen indicado**, más que el número exacto, que depende de la máquina.

### 3.1 Integridad del dinero (cualquier valor distinto de 0 es un fallo)

| ID | Métrica | Meta |
|---|---|---|
| M-01 | Diferencias entre efectivo y posiciones reportados y lo que surge de recalcular desde `orders` | **0** |
| M-02 | Saldos o tenencias negativas causados por la API, incluso con 50 órdenes concurrentes del mismo usuario | **0** |
| M-03 | Órdenes `REJECTED` sin motivo | **0** |

### 3.2 Idempotencia

| ID | Métrica | Meta |
|---|---|---|
| M-04 | 1.000 reenvíos secuenciales del mismo pedido | Exactamente **1** orden y **1** movimiento de saldo; las 1.000 respuestas devuelven la misma orden |
| M-05 | 50 reenvíos **simultáneos** del mismo pedido | Exactamente **1** ejecución |
| M-06 | Reuso de la clave con otro contenido o en otro tipo de operación | **100 %** detectados como conflicto, **0** procesados |
| M-07 | Reintentos de depósitos, retiros y cancelaciones | **0** efectos duplicados |

### 3.3 Escala y rendimiento

| ID | Condición | Operación | p95 | p99 |
|---|---|---|---|---|
| M-08 | 10.000.000 de órdenes en total; usuario con 10.000 órdenes | Portfolio | < 200 ms | < 400 ms |
| M-09 | Igual | Página del historial | < 100 ms | < 200 ms |
| M-10 | Igual | Enviar orden (incluye validar el disponible) | < 150 ms | < 300 ms |
| M-11 | 100.000 instrumentos | Búsqueda parcial | < 100 ms | < 200 ms |

*p95: el 95 % de los pedidos responde por debajo de ese tiempo.*

### 3.4 Calidad

| ID | Métrica | Meta |
|---|---|---|
| M-12 | Escenarios de [casos de uso](casos-de-uso.md) cubiertos por tests automatizados | **100 %** de los niveles 1 y 2; como mínimo los escenarios críticos del nivel 3 |
| M-13 | Test funcional del envío de órdenes (requisito del enunciado) | Incluido |
| M-14 | Decisiones de producto y técnicas documentadas con justificación | **100 %** |

### 3.5 Experiencia de quien evalúa

| ID | Métrica | Meta |
|---|---|---|
| M-15 | Tiempo desde clonar el repositorio hasta tener la API respondiendo | **< 5 minutos**, con un solo comando |
| M-16 | Colección de pedidos de ejemplo | Cubre todas las funcionalidades, con las internas en una carpeta aparte |

## 4. Usuarios y actores

| Actor | Necesidad | Acceso |
|---|---|---|
| **Usuario inversor** | Ver su cartera, encontrar activos, operar, depositar y retirar | Solo sus propios datos (RF-10) |
| **Administrador (demo)** | Preparar y repetir demostraciones | Herramientas internas y datos de cualquier usuario |
| **Consumidor de la API** (BFF o frontend) | Contratos claros, errores distinguibles, reintentos seguros | — |
| **Evaluador técnico** | Entender qué se hizo y por qué, y verificarlo rápido | — |

## 5. Alcance

| Nivel | Funcionalidades | Prioridad |
|---|---|---|
| **1 · Pedido del challenge** | Portfolio, búsqueda de activos, envío de órdenes | **Obligatorio** |
| **Transversal** | Reservas, idempotencia, concurrencia segura, aislamiento entre usuarios, motivos de rechazo | **Obligatorio** |
| **2 · Extensiones de dominio** | Cancelar orden, depositar, retirar, historial y consulta de orden | Deseable |
| **3 · Herramientas internas** (no forman parte del pedido) | Borrar orden, forzar ejecución de una LIMIT, cambiar precio, restaurar datos, crear usuario | Opcional |
| **Observabilidad** | Métricas, trazas y logs con OpenTelemetry; tableros de salud y de negocio | Deseable |

Las herramientas internas están **explícitamente separadas e identificadas** como de uso propio para demo y pruebas (DEC-03).

## 6. Requisitos funcionales

El detalle y los escenarios de cada requisito están en [casos de uso](casos-de-uso.md).

| ID | Requisito | Decisiones | Escenarios |
|---|---|---|---|
| RF-01 | **Portfolio**: valor total; pesos (efectivo total, reservado, disponible); por activo: cantidad, reservadas, último precio, valor, costo promedio, rendimiento total (%) y variación diaria (%). Solo posiciones con cantidad ≠ 0. Activos sin precio se muestran "sin dato" y no suman al total. Posiciones inconsistentes se marcan. | DEC-01, DEC-02, DEC-10, DEC-11 | CU-01 |
| RF-02 | **Búsqueda de activos**: término general y filtros por ticker y nombre; coincidencia parcial desde 3 caracteres, ticker exacto con 1 o 2; sin distinguir mayúsculas ni acentos; excluye ARS; sin texto lista todo el mercado; paginada. | DEC-08, DEC-19, DEC-21 | CU-02 |
| RF-03 | **Envío de órdenes** `BUY`/`SELL`, `MARKET`/`LIMIT`, por cantidad entera o por monto (se usa el monto real). `MARKET` se ejecuta al último precio (`FILLED`); `LIMIT` queda `NEW` y reserva lo que compromete. Fondos o acciones insuficientes → `REJECTED` con motivo. Pedidos inválidos → error sin guardar. | DEC-01, DEC-05, DEC-06, DEC-12, DEC-14, DEC-22 | CU-03 |
| RF-04 | **Cancelación** de órdenes `NEW`, liberando la reserva; idempotente. | DEC-15 | CU-04 |
| RF-05 | **Depósito** de pesos con hasta 2 decimales. | DEC-04, DEC-13 | CU-05 |
| RF-06 | **Retiro** de pesos contra el disponible; si no alcanza → `REJECTED`. | DEC-04, DEC-13 | CU-06 |
| RF-07 | **Historial** de órdenes paginado y filtrable por estado, y **consulta** de una orden. | DEC-16 | CU-07 |
| RF-08 | **Herramientas internas**, solo para administrador. | DEC-03, DEC-16 | CU-I |
| RF-09 | **Idempotencia** transversal: toda operación lleva una clave del cliente, única por usuario entre todas las operaciones. Un reintento idéntico devuelve el resultado original; otro contenido con la misma clave devuelve un conflicto. | DEC-07, DEC-09, DEC-23 | CU-03.31–42, CU-05, CU-06 |
| RF-10 | **Aislamiento**: ningún usuario ve ni modifica datos de otro; los recursos ajenos responden "no encontrado". El administrador es la excepción. | DEC-17 | CU-01.09, CU-03.30, CU-04.06, CU-05.06, CU-06.05, CU-07.05 |

### Invariantes del negocio (resumen)
1. `orders` es el libro contable: efectivo, posiciones y reservas se derivan de ella.
2. Solo las órdenes `FILLED` mueven efectivo y posiciones; las `NEW` reservan.
3. Ninguna operación aceptada deja pesos ni acciones disponibles negativos.
4. Una operación se ejecuta como máximo una vez, sin importar cuántas veces se reenvíe.
5. Solo se cancelan órdenes `NEW`.
6. Solo se operan acciones enteras.

## 7. Requisitos no funcionales

| ID | Requisito |
|---|---|
| RNF-01 | **Consistencia bajo concurrencia**: operaciones simultáneas del mismo usuario se resuelven como si fueran secuenciales respecto de su disponible (M-02, M-05). |
| RNF-02 | **Precisión monetaria**: los cálculos de dinero son exactos, al centavo, sin errores de redondeo acumulados. Redondeo a 2 decimales, mitad hacia arriba *(supuesto, ver §9)*. |
| RNF-03 | **Escalabilidad**: cumplir M-08 a M-11 con el volumen indicado. |
| RNF-04 | **Trazabilidad**: toda operación es rastreable por su id y su clave de idempotencia; todo rechazo tiene motivo. |
| RNF-05 | **Fechas en UTC** con zona explícita; la conversión a hora local es del consumidor (DEC-18). |
| RNF-06 | **Errores distinguibles**: cada tipo de error (validación, no encontrado, conflicto de idempotencia, estado inválido para cancelar) tiene un código propio y estable. |
| RNF-07 | **Operabilidad**: se levanta con un solo comando y con datos de ejemplo cargados (M-15). |
| RNF-08 | **Documentación**: README, decisiones de producto (análisis) y técnicas (RFC), y colección de pedidos (M-14, M-16). |
| RNF-09 | **Observabilidad**: métricas técnicas y de negocio (tiempo real e histórico), trazas y logs correlacionados, con tableros de salud y de negocio que se levantan junto con la aplicación (RFC TEC-31). |

## 8. Cambios al modelo de datos

El enunciado permite modificar el modelo justificándolo. Los cambios que exigen estos requisitos están detallados en la
[sección 8 del análisis](analisis-funcional.md): motivo de rechazo (CM-01), clave de idempotencia (CM-02), montos con
centavos (CM-03) y cambios para escala (CM-04 a CM-08). Su forma técnica se define en el RFC.

## 9. Supuestos y dependencias

| ID | Supuesto |
|---|---|
| SUP-01 | La identidad del solicitante se declara en cada pedido y no se verifica: en producción vendría de un sistema de autenticación. |
| SUP-02 | Los precios son los de `marketdata` provista (último día: 2023-07-14) y solo cambian mediante la herramienta interna I-03. |
| SUP-03 | Las fechas de los datos semilla, que no traen zona, se interpretan como UTC. |
| SUP-04 | Redondeo monetario a 2 decimales, mitad hacia arriba. Puede revisarse en el RFC. |
| SUP-05 | Los datos semilla se respetan tal como vienen, con su inconsistencia (DEC-11). |

## 10. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| RSK-01 | Calcular portfolio y reservas desde `orders` no alcanza M-08 con 10.000 órdenes por usuario. | El RFC evalúa índices y, si hace falta, posiciones precalculadas; M-08 lo valida. |
| RSK-02 | Reintentos simultáneos ejecutan una orden dos veces. | M-05 lo prueba explícitamente; el RFC define el mecanismo de exclusión. |
| RSK-03 | Ausencia de autenticación interpretada como agujero de seguridad. | Aislamiento implementado y probado (RF-10), limitación documentada (SUP-01). |
| RSK-04 | La inconsistencia de los datos semilla (BMA −10) confunde al evaluador. | Se marca en el portfolio y está documentada (DEC-11, revisable). |
| RSK-05 | 5 días no alcanzan para todo el alcance. | Prioridades (§5) y orden de recorte (§11). |

## 11. Hitos

| Día | Entregable |
|---|---|
| 1 | PRD y RFC aprobados |
| 2 | Núcleo del challenge: portfolio, búsqueda y órdenes con reservas |
| 3 | Idempotencia, concurrencia y aislamiento, con sus tests |
| 4 | Extensiones de dominio y herramientas internas |
| 5 | Pruebas de volumen, observabilidad, colección de ejemplos, README y repaso final |

**Orden de recorte** si el tiempo no alcanza: (1) herramientas internas → (2) tableros y backend de observabilidad (los contadores de negocio se mantienen) → (3) pruebas de volumen → (4) historial.
**No se recortan**: el nivel 1, las reservas, la idempotencia, la concurrencia ni el aislamiento.

## 12. Preguntas abiertas

| ID | Pregunta | Dónde se resuelve |
|---|---|---|
| PA-01 | DEC-11 (tratamiento de la posición negativa de BMA) quedó revisable. | A pedido del usuario |
| PA-02 | ¿Posiciones calculadas en cada consulta o precalculadas? | RFC (depende de RSK-01) |
| PA-03 | Entorno local de referencia para medir M-08 a M-11. | RFC |
