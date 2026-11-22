# Análisis funcional — Cocos Backend Challenge

> Documento vivo. Registra cómo se entendió el problema **antes** de escribir código:
> dominio, alcance, reglas explícitas, ambigüedades detectadas y las decisiones tomadas para resolverlas.
> No contiene decisiones técnicas (lenguaje, arquitectura, librerías): esas van en un documento aparte.

- Enunciado original: [`referencia/backend-challenge.md`](referencia/backend-challenge.md)
- Script de base provisto: [`referencia/database.sql`](referencia/database.sql)
- Fecha de inicio del análisis: 2026-11-21
- **Estado:** análisis funcional **cerrado** (2026-11-21), pendiente de cualquier ajuste que surja en la etapa técnica. Decisiones DEC-01 a DEC-20; casos de uso y escenarios en [`casos-de-uso.md`](casos-de-uso.md). DEC-11 quedó marcada como revisable.

### Resumen ejecutivo

- **Qué se construye:** una API de broker para consultar el portfolio, buscar activos y enviar órdenes (pedido del challenge), más cancelación, depósitos, retiros e historial de órdenes (extensiones de dominio) y herramientas internas, explícitamente separadas, para la demo.
- **Principio central:** la tabla `orders` es el libro contable. Efectivo, posiciones y reservas se derivan siempre de ella.
- **Invariante de dinero:** ninguna orden aceptada puede dejar pesos ni acciones negativos. Las órdenes pendientes **reservan** lo que comprometen (DEC-01), y una misma orden reenviada **nunca se ejecuta dos veces** (idempotencia, DEC-07 y DEC-09).
- **Rendimiento:** cada posición informa el rendimiento total contra el costo promedio y la variación diaria (DEC-02).
- **Datos provistos:** se respetan tal cual. La inconsistencia detectada (BMA −10) se muestra marcada en lugar de ocultarse (DEC-11).

---

## 1. Dominio del negocio

Cocos es un broker de inversiones. Un **usuario** tiene una **cuenta** en la que deposita pesos y con ellos
compra y vende **instrumentos** (acciones del mercado argentino). La API pedida es el núcleo de esa operatoria:
ver qué tengo, buscar qué comprar y mandar órdenes.

### Glosario

| Término | Significado en este proyecto |
|---|---|
| **Usuario / cuenta** | Titular (`users`). Tiene `accountNumber`. No hay autenticación. |
| **Instrumento / activo** | Algo que se puede tener en cartera (`instruments`). Tipos presentes: `ACCIONES` y `MONEDA`. |
| **ARS / pesos** | El efectivo se modela como un instrumento más, de tipo `MONEDA` (ticker `ARS`, id 66). |
| **Orden** | Instrucción del usuario sobre un instrumento. Es también el **libro contable**: posiciones y efectivo se derivan de ella. |
| **Side** | Dirección de la orden: `BUY`, `SELL`, `CASH_IN` (depósito), `CASH_OUT` (retiro). |
| **Tipo de orden** | `MARKET` (se ejecuta ya al último precio) o `LIMIT` (el usuario fija el precio; queda pendiente). |
| **Estado** | `NEW` (pendiente), `FILLED` (ejecutada), `REJECTED` (rechazada), `CANCELLED` (cancelada por el usuario). |
| **Size** | Cantidad de unidades de la orden. Para pesos, `size` = monto en pesos y `price` = 1. |
| **Posición / tenencia** | Cantidad neta que el usuario tiene de un instrumento, derivada de sus órdenes `FILLED`. |
| **Pesos disponibles** | Efectivo con el que el usuario puede operar: efectivo total menos lo reservado por compras `NEW` (DEC-01). |
| **Reserva** | Pesos o acciones comprometidos por una orden `NEW`; se liberan cuando la orden deja de estar `NEW` (DEC-01). |
| **Identificador de idempotencia** | Identificador único que genera el cliente para cada orden y que permite reconocer un reintento (DEC-07). |
| **Valor total de la cuenta** | Efectivo total (incluye lo reservado) + valor de mercado de todas las posiciones (DEC-01.1, DEC-10). |
| **Market data** | Precios diarios por instrumento (`marketdata`): `open`, `high`, `low`, `close`, `previousClose`. `close` = último precio. |
| **Rendimiento** | Ganancia/pérdida porcentual de una posición (rendimiento total contra costo promedio y variación diaria: ver DEC-02). |

### Actores
- **Usuario inversor**: opera sobre su propia cuenta y **solo puede ver o modificar sus propios datos** (DEC-17). No hay autenticación (fuera de alcance): su identidad se declara en cada pedido.
- **Administrador (demo)**: identidad con permiso especial. Es la única que puede usar las herramientas internas (nivel 3) y acceder a datos de cualquier usuario (DEC-17).
- **Mercado**: no se simula. Solo aporta precios (`marketdata`).

---

## 2. Alcance

### Dentro del alcance (pedido explícito)
1. **Portfolio** de un usuario: valor total de la cuenta, pesos disponibles y listado de activos con cantidad,
   valor monetario de la posición ($) y rendimiento total (%).
2. **Búsqueda de activos** por ticker y/o nombre, devolviendo activos "similares".
3. **Envío de órdenes** `BUY`/`SELL`, tipos `MARKET`/`LIMIT`, por cantidad exacta o por monto en pesos,
   persistidas en `orders` con estado y valores correspondientes.
4. Test funcional sobre el envío de órdenes.
5. Documentar supuestos y decisiones (este documento y los que sigan).

### Opcional
- Colección Postman / Insomnia / REST Client con ejemplos.

### Fuera del alcance (explícito)
- Simular el mercado (no hay matching, ni libro de ofertas, ni evolución de precios).
- Autenticación de usuarios.
- Conversión de fechas y horas a la zona horaria del cliente: la API trabaja siempre en UTC y esa conversión le corresponde a quien consume el servicio, por ejemplo un BFF (DEC-18).

### Catálogo de funcionalidades (DEC-03)

Las funcionalidades se agrupan en tres niveles para que quede claro qué es pedido del challenge y qué no.

**Nivel 1 — Pedido del challenge**

| ID | Funcionalidad |
|---|---|
| F-01 | Consultar portfolio de un usuario |
| F-02 | Buscar activos por ticker y/o nombre |
| F-03 | Enviar orden `BUY` / `SELL` (`MARKET` / `LIMIT`, por cantidad o por monto) |

**Nivel 2 — Extensiones de dominio** (no son endpoints pedidos, pero ejercitan reglas que el enunciado define)

| ID | Funcionalidad | Regla que ejercita |
|---|---|---|
| E-01 | Cancelar una orden `NEW` (libera la reserva) | R-10, DEC-01 |
| E-02 | Depositar pesos (`CASH_IN`) | R-11 |
| E-03 | Retirar pesos (`CASH_OUT`), `REJECTED` si supera el disponible | R-09, R-11 |
| E-04 | Consultar órdenes de un usuario (historial, filtrable por estado) y una orden por id | Ver el efecto de cada operación |

**Nivel 3 — Herramientas internas de demo y pruebas** (uso propio: **no** forman parte del pedido ni del producto)

| ID | Funcionalidad | Para qué sirve en la demo |
|---|---|---|
| I-01 | Borrar una orden físicamente | Mostrar que el portfolio se recalcula a partir de `orders` |
| I-02 | Forzar la ejecución de una orden `LIMIT` `NEW` a su precio límite (simula que el mercado la tomó) | Mostrar que la reserva se convierte en posición; es la única vía para llegar a `FILLED` en una LIMIT |
| I-03 | Cargar o modificar el precio (`close` / `previousClose`) de un instrumento | Mostrar cómo cambian valuación, rendimiento y variación diaria |
| I-04 | Restaurar los datos a la semilla original | Repetir la demo desde un estado conocido |
| I-05 | Crear un usuario de prueba | Arrancar escenarios limpios sin depender del usuario 1 |

Reglas de las herramientas internas:
- Deben estar **identificadas explícitamente como internas** en la API y en la documentación y colección de ejemplos, separadas de los niveles 1 y 2. La forma concreta (prefijo de ruta, etiqueta, configuración para desactivarlas) se define en la etapa técnica.
- **Pueden romper invariantes a propósito.** Por ejemplo, borrar un `CASH_IN` deja efectivo negativo. El portfolio tiene que seguir respondiendo con esos datos sin fallar, y el documento de la demo debe advertirlo.
- No cuentan como cumplimiento del challenge; son apoyo para la presentación.

---

## 3. Reglas de negocio explícitas (invariantes)

Cada regla cita el enunciado. Son no negociables.

| ID | Regla |
|---|---|
| R-01 | Los precios de los activos están en **pesos**. |
| R-02 | Toda orden indica `side`: `BUY` o `SELL` (y `CASH_IN` / `CASH_OUT` para transferencias). |
| R-03 | Tipos de orden: `MARKET` y `LIMIT`. |
| R-04 | Una orden `MARKET` **no requiere precio**; se ejecuta al último precio (`close`). |
| R-05 | Una orden `LIMIT` **requiere precio**. |
| R-06 | Una orden `MARKET` se ejecuta inmediatamente → estado `FILLED`. |
| R-07 | Una orden `LIMIT` se registra con estado `NEW`. |
| R-08 | El usuario puede enviar **cantidad exacta** de acciones **o monto total en pesos**. Con monto, se calcula la **cantidad máxima entera** de acciones (no hay fracciones). |
| R-09 | Si el monto de la orden supera lo disponible, la orden se **guarda** con estado `REJECTED`. Compra: validar pesos suficientes. Venta: validar acciones suficientes. |
| R-10 | Solo se pueden cancelar órdenes en estado `NEW`; al cancelarse pasan a `CANCELLED`. |
| R-11 | Depósitos y retiros se modelan como órdenes con side `CASH_IN` / `CASH_OUT`. |
| R-12 | Cuando una orden se ejecuta, las posiciones del usuario deben reflejarlo. |
| R-13 | Tenencia y pesos disponibles se calculan a partir de los movimientos pertinentes de `orders`, usando `size`. |
| R-14 | El efectivo (ARS) es un instrumento de tipo `MONEDA`. |
| R-15 | `close` es el último precio; el retorno diario se calcula con `close` y `previousClose`. |
| R-16 | Valor de mercado, rendimiento y cantidad de cada posición se calculan con las órdenes `FILLED` de ese activo. |
| R-17 | Toda orden enviada queda persistida en `orders` (incluidas las rechazadas). |

### Reglas derivadas (consecuencia lógica de las anteriores)

| ID | Regla | Deriva de |
|---|---|---|
| D-01 | Efectivo = Σ `CASH_IN` − Σ `CASH_OUT` − Σ (size×price de `BUY` FILLED) + Σ (size×price de `SELL` FILLED). | R-11, R-13, R-14 |
| D-02 | Cantidad de una posición = Σ size `BUY` FILLED − Σ size `SELL` FILLED. | R-16 |
| D-03 | Valor de una posición = cantidad × `close` más reciente. | R-15, R-16 |
| D-04 | Valor total de la cuenta = pesos + Σ valor de posiciones. | Resumen del enunciado |
| D-05 | Las órdenes `NEW`, `CANCELLED` y `REJECTED` **no** mueven posiciones ni efectivo (solo `FILLED`). | R-16 |
| D-06 | Una orden `MARKET` guarda como `price` el `close` usado para ejecutarla. | R-04, "estado y valores correspondientes" |
| D-07 | Ningún usuario puede quedar con efectivo ni acciones negativos como resultado de una orden aceptada. | R-09 |

---

## 4. Observaciones sobre los datos provistos

Revisión de `database.sql` (los ids de instrumento son por orden de inserción):

| ID | Observación | Impacto |
|---|---|---|
| O-01 | Solo el usuario 1 tiene órdenes; usuarios 2–4 tienen portfolio vacío. | Casos de prueba de usuario sin movimientos. |
| O-02 | `marketdata` tiene 2 fechas (2023-07-13 y 2023-07-14) para 63 instrumentos. **No hay precios** para PGR (3), IRCP (10) ni ARS (66). | Qué hacer al operar o valuar un activo sin precio (A-09). ARS no necesita precio (vale 1). |
| O-03 | DOME (12) tiene `open/high/low` en `NULL`; solo `close` y `previousClose`. | No afecta si solo usamos `close`. |
| O-04 | **Inconsistencia**: BMA (31) tiene `BUY 20 FILLED` y luego `SELL 30 FILLED` → posición de **−10**. Además hay un `BUY 60 LIMIT NEW` previo. | Rompe D-07 en los datos semilla. Ver A-10. |
| O-05 | Existe una orden `LIMIT` en estado `FILLED` (BMA, 20 @ 1540). | Implica que en el mundo real las LIMIT se ejecutan; nuestra API no lo hace (no hay mercado). |
| O-06 | Los precios en `marketdata` vienen con 8 decimales pero la columna es `NUMERIC(10,2)`. | Redondeo a 2 decimales al guardar. |
| O-07 | El enunciado dice que `marketdata` tiene `datetime`, pero el SQL tiene `date`. | Menor; seguimos al SQL. |
| O-08 | El insert de `marketdata` usa `previousclose` (minúsculas) y la columna se llama `previousClose` sin comillas → en Postgres son la misma columna. | Menor. |

### Estado esperado del usuario 1 con los datos semilla

| Concepto | Cálculo | Resultado |
|---|---|---|
| Efectivo (solo FILLED) | 1.000.000 − 46.500 + 9.400 − 100.000 − 30.800 − 125.000 + 45.900 | **$ 753.000** |
| Efectivo si las LIMIT `NEW` de compra reservan fondos | 753.000 − 35.500 (LOMA 50@710) − 90.000 (BMA 60@1500) | **$ 627.500** |
| PAMP (47) | 50 − 10 = 40 × 925,85 | $ 37.034 |
| METR (54) | 500 × 229,50 | $ 114.750 |
| BMA (31) | 20 − 30 = **−10** × 1.502,80 | −$ 15.028 (ver A-10) |

Estos números sirven como caso de prueba de referencia. Con las decisiones tomadas: disponible **$ 627.500**, valor total **$ 889.756** (ver sección 7).

---

## 5. Ambigüedades y casos no cubiertos

Cada punto lista las opciones y, cuando aplica, la recomendación. El estado pasa a **Decidido** con la
justificación en la sección 6.

| ID | Pregunta | Estado |
|---|---|---|
| A-01 | ¿Las órdenes `LIMIT` en `NEW` **reservan** pesos (compra) o acciones (venta)? | **Decidido** (DEC-01) |
| A-02 | ¿Qué es el "rendimiento total (%)" de una posición? | **Decidido** (DEC-02) |
| A-03 | ¿Se expone un endpoint para **cancelar** órdenes? | **Decidido** (DEC-03) |
| A-04 | ¿Se exponen **depósitos y retiros** por API? ¿Se valida el retiro contra el disponible? | **Decidido** (DEC-03, DEC-04) |
| A-05 | Orden por **monto**: ¿qué precio se usa para calcular la cantidad en una `LIMIT`? ¿Qué pasa si da 0 acciones? | **Decidido** (DEC-05, DEC-06) |
| A-06 | ¿Qué errores se **persisten como `REJECTED`** y cuáles se responden como error sin guardar nada? | **Decidido** (DEC-05) |
| A-07 | Búsqueda: ¿qué significa "similar"? (parcial, mayúsculas, acentos), ¿se incluye ARS?, ¿qué datos devuelve? | **Decidido** (DEC-08) |
| A-08 | Portfolio: ¿se muestran posiciones en 0?, ¿se incluye ARS en el listado?, ¿se muestran órdenes pendientes? | **Decidido** (DEC-10) |
| A-09 | ¿Qué pasa al operar o valuar un activo **sin market data**? | **Decidido** (DEC-05 operar, DEC-10 valuar) |
| A-10 | ¿Cómo se trata la **posición negativa** de BMA en los datos semilla? | **Decidido, revisable** (DEC-11) |
| A-11 | ¿Se valida que el usuario y el instrumento existan? ¿Se puede operar ARS con BUY/SELL? | **Decidido** (DEC-05) |
| A-12 | ¿Una `LIMIT` con precio "mejor que el mercado" se ejecuta inmediatamente? | **Decidido** (DEC-06) |
| A-13 | ¿Hay comisiones, límites de precio o redondeos especiales? | **Decidido** (DEC-06) |
| A-14 | ¿Qué garantía se da ante **dos órdenes simultáneas** que compiten por el mismo saldo? | **Decidido** (DEC-06, DEC-07) |
| A-15 | **Idempotencia**: ¿qué pasa si llega dos veces la misma orden (reintento, doble clic, timeout de red)? | **Decidido** (DEC-07, DEC-09) |
| A-16 | ¿Se guarda el **motivo** por el que una orden quedó `REJECTED`? | **Decidido** (DEC-12) |
| A-17 | Depósitos y retiros con **centavos**: `size` es entero y para el efectivo representa el monto. | **Decidido** (DEC-13) |
| A-18 | ¿Se permite **vender por monto** en pesos? | **Decidido** (DEC-14) |
| A-19 | ¿Qué responde **cancelar una orden ya cancelada**? | **Decidido** (DEC-15) |
| A-20 | Detalles operativos: portfolio vacío, historial, efecto de las herramientas internas sobre la idempotencia. | **Decidido** (DEC-16) |
| A-21 | ¿Un usuario puede ver o modificar datos de otro usuario? | **Decidido** (DEC-17) |
| A-22 | ¿En qué zona horaria se registran y devuelven fechas y horas? | **Decidido** (DEC-18) |
| A-23 | Búsqueda de activos **sin texto**: ¿error o listado completo del mercado? | **Decidido** (DEC-19) |
| A-24 | ¿Qué cambios necesita el modelo para soportar **millones de instrumentos**? | **Propuesto** (DEC-20, sección 8) |
| A-25 | ¿Longitud mínima del texto de búsqueda, para evitar consultas del tipo "todo lo que contenga A"? | **Decidido** (DEC-21) |
| A-26 | El código `ZERO_QUANTITY` es ambiguo: no dice que el monto no alcanza para una acción. | **Decidido** (DEC-22) |
| A-27 | ¿La clave de idempotencia es única por usuario entre **todas** las operaciones o por tipo de operación? | **Decidido** (DEC-23) |
| A-28 | ¿Qué hace la API con campos desconocidos en el cuerpo de un pedido (por ejemplo `"userId": 1`)? | **Decidido** (DEC-24) |

### Detalle

**A-01 — Reserva de fondos de órdenes LIMIT pendientes.**
El enunciado solo cuenta `FILLED` para posiciones (R-16), pero dice que los pesos disponibles usan "todos los
movimientos pertinentes" (R-13). Si una LIMIT de compra no reserva, el usuario puede enviar más compras pendientes
que dinero tiene. Opciones: (a) no reservar — disponible = efectivo de FILLED; (b) reservar — disponible =
efectivo − compras NEW, y acciones disponibles = tenencia − ventas NEW.

**A-02 — Rendimiento total (%).**
Opciones: (a) contra **costo promedio ponderado** de compra: `(close − costo promedio) / costo promedio`;
(b) **retorno diario** `(close − previousClose) / previousClose`, que es el único cálculo de rendimiento que el
enunciado describe; (c) devolver ambos.

**A-03 — Cancelación.** La regla existe (R-10) y el estado `CANCELLED` existe, pero el resumen no lista el endpoint.

**A-04 — Transferencias.** Están modeladas (R-11) pero no hay endpoint pedido. Si se exponen, un `CASH_OUT`
mayor al disponible debería ser `REJECTED` por analogía con R-09.

**A-05 — Orden por monto.** `MARKET`: cantidad = ⌊monto / close⌋. `LIMIT`: lo natural es ⌊monto / precio límite⌋.
Si la cantidad resultante es 0: ¿`REJECTED` guardada o error de validación?

**A-06 — Rechazo vs. error de validación.** R-09 obliga a guardar `REJECTED` por fondos/acciones insuficientes.
Faltan casos: cantidad ≤ 0, LIMIT sin precio, MARKET con precio, instrumento inexistente, usuario inexistente,
envío de cantidad y monto a la vez.

**A-07 — Búsqueda.** Ejemplos: "gal" → GGAL, "banco" → BPAT, BBAR, BHIP, BMA; "rio" vs "Río".

**A-08 — Contenido del portfolio.** Por ejemplo PAMP vendida por completo: ¿aparece con 0?

**A-09 — Sin market data.** PGR e IRCP no tienen precio: una MARKET no puede ejecutarse; una LIMIT sí podría
registrarse. En el portfolio, una posición sin precio no puede valuarse.

**A-10 — Posición negativa en los datos.** Opciones: mostrarla tal cual, ocultarla, o corregir el dato semilla y
documentarlo.

**A-11 — Validación de entidades.** ¿Operar `ARS` con BUY/SELL tiene sentido? (no).

**A-12 — LIMIT marketable.** En un mercado real, una compra LIMIT a precio ≥ último precio se ejecuta al instante.
El enunciado dice que toda LIMIT queda `NEW`.

**A-13 — Comisiones / redondeo.** No se mencionan comisiones. Montos a 2 decimales.

**A-14 — Concurrencia.** Funcionalmente: dos órdenes simultáneas no deben poder gastar el mismo saldo (D-07).

**A-15 — Idempotencia.** Surgió durante el análisis (no figura en el enunciado). Un reintento de la misma orden
no debe ejecutarla dos veces. Hay que distinguirlo de dos órdenes distintas con igual contenido.

---

## 6. Registro de decisiones

| ID | Decisión | Justificación | Fecha |
|---|---|---|---|
| DEC-01 | Toda orden en estado `NEW` **reserva** lo que compromete. Compra pendiente: reserva `size × price` en pesos. Venta pendiente: reserva `size` acciones del instrumento. La reserva se libera cuando la orden deja de estar `NEW` (por ejemplo, al cancelarla). **Pesos disponibles** = efectivo de órdenes FILLED − compras NEW. **Acciones disponibles** = tenencia FILLED − ventas NEW. Las validaciones de R-09 se hacen contra los **disponibles**, no contra el total. | Evitar que un usuario comprometa más pesos o acciones de los que realmente tiene (D-07). Coincide con "todos los movimientos pertinentes" de R-13. Para el usuario 1, el disponible pasa a ser $ 627.500. | 2026-11-21 |
| DEC-01.1 | Lo reservado sigue siendo del usuario: el **valor total de la cuenta** usa el efectivo total ($ 753.000 para el usuario 1), no el disponible. El portfolio informa el disponible y también el monto reservado. | Una reserva no es un gasto. Si el total usara el disponible, el valor de la cuenta caería cada vez que el usuario deja una orden pendiente. | 2026-11-21 |
| DEC-02 | Cada posición informa **dos métricas**: **Rendimiento total (%)** = `(close − costo promedio) / costo promedio × 100`, que es la ganancia o pérdida desde la compra. **Variación diaria (%)** = `(close − previousClose) / previousClose × 100`, que es el movimiento del activo en la última rueda. Ejemplo PAMP (usuario 1): rendimiento total −0,45 %, variación diaria +0,44 %. | "Rendimiento **total**" se lee como ganancia acumulada del usuario, y el enunciado describe explícitamente el retorno diario con `close` y `previousClose`. Informar ambas cubre las dos lecturas sin apostar a una sola. | 2026-11-21 |
| DEC-02.1 | **Costo promedio: método de promedio móvil.** Las órdenes `FILLED` de cada instrumento se procesan en orden cronológico (`datetime`; a igual fecha y hora, por `id`). Una **compra** recalcula el costo promedio con lo que **queda en cartera**: `(cantidad actual × costo promedio + cantidad comprada × precio) / (cantidad actual + cantidad comprada)`. Una **venta** reduce la cantidad y **no cambia** el costo promedio. Si la cantidad llega **exactamente a 0**, el costo se reinicia y la próxima compra arranca un promedio nuevo. Caso solo alcanzable con datos inconsistentes: una compra con cantidad actual ≤ 0 reinicia el promedio al precio de esa compra. *(Revisado el 2026-11-21 al escribir la spec 006: la versión anterior decía "Σ size×price / Σ size de las compras", que promedia también acciones ya vendidas y contradecía la regla de que las ventas no cambian el costo.)* | Es el método de costo promedio de los brokers: el costo refleja solo las acciones que el usuario tiene. Ejemplo (CU-01.12): compra 50 a $ 930, vende 10, compra 10 a $ 925,85 → (40 × 930 + 10 × 925,85) / 50 = **$ 929,17** (la fórmula anterior daba $ 929,31 porque seguía contando las 10 vendidas). Con los datos semilla no cambia ningún valor. | 2026-11-21 |
| DEC-03 | Se agregan las funcionalidades de los niveles 2 y 3 del catálogo (sección 2). Las de nivel 3 se marcan explícitamente como **internas**, de uso propio para demo y pruebas. | Nivel 2: el enunciado define cancelación y transferencias pero no el endpoint; sin cancelación no hay forma de liberar una reserva (DEC-01), y sin depósitos los usuarios 2–4 no pueden operar. Nivel 3: permite mostrar en vivo que el portfolio se deriva de `orders`. Separarlas evita confundir al evaluador sobre qué es el pedido. | 2026-11-21 |
| DEC-03.1 | La clasificación en niveles (qué es extensión de dominio y qué es interno), así como E-04, I-02, I-03, I-04 e I-05, surgieron como propuesta del análisis y fueron aprobadas por el usuario. | El usuario pidió borrado de órdenes y "cualquier otro que ayude a mostrar el funcionamiento"; el resto se agregó con ese criterio. | 2026-11-21 |
| DEC-04 | `CASH_OUT` se valida contra los **pesos disponibles** (descontando reservas) y, si no alcanza, se guarda `REJECTED`. `CASH_IN` y `CASH_OUT` se ejecutan inmediatamente (`FILLED`), con `price` = 1 y `size` = monto, igual que en los datos semilla. | Analogía directa con R-09 y DEC-01. Formato idéntico al de los datos provistos. | 2026-11-21 |
| DEC-05 | **En `orders` figura todo lo que efectivamente se operó**, sea cual sea su estado. Una orden que ni siquiera pudo procesarse **no se guarda**: se responde con error. **Se guarda como `REJECTED`** (pedido válido que el negocio no puede cumplir): pesos o acciones disponibles insuficientes (contando reservas, DEC-01); orden por monto que alcanza para 0 acciones; orden `MARKET` sobre un instrumento sin market data. **Error sin persistir** (pedido inválido): usuario o instrumento inexistente; cantidad o monto ≤ 0; `LIMIT` sin precio o con precio ≤ 0; `MARKET` con precio; cantidad y monto enviados a la vez, o ninguno de los dos; side o tipo inválidos; `BUY` / `SELL` sobre un instrumento `MONEDA` (ARS). | `orders` es el libro contable de la cuenta: refleja lo que el usuario operó, no los pedidos malformados. Una orden necesita al menos usuario e instrumento válidos para tener sentido como registro. | 2026-11-21 |
| DEC-06 | **Flujo de envío de órdenes.** (1) Precio de referencia: `MARKET` usa el `close` de la fecha más reciente del instrumento; `LIMIT` usa el precio límite. (2) Orden por monto: `size = ⌊monto / precio de referencia⌋`. (3) Validación contra disponibles: compra → `size × precio ≤ pesos disponibles`; venta → `size ≤ acciones disponibles`. (4) Resultado: `MARKET` válida → `FILLED`; `LIMIT` válida → `NEW` **siempre**, aunque su precio sea igual o mejor que el mercado; si no pasa la validación → `REJECTED`. (5) Se guarda el `size` calculado y el `price` de referencia (en una `REJECTED` por falta de precio, `price` queda vacío). (6) Sin comisiones; montos redondeados a 2 decimales. (7) Dos órdenes simultáneas del mismo usuario nunca pueden gastar el mismo disponible: la segunda se evalúa con el saldo que dejó la primera. | (1)(4) Literal del enunciado (R-04, R-06, R-07), sin simular el mercado. (2) Cantidad máxima entera (R-08). (6) El enunciado no menciona comisiones; la base usa `NUMERIC(10,2)`. (7) Garantiza D-07 bajo concurrencia; el cómo es técnico. | 2026-11-21 |
| DEC-07 | **Idempotencia del envío de órdenes.** Hay que distinguir dos situaciones. **Órdenes distintas** (dos pedidos diferentes, aunque tengan el mismo contenido): se procesan una después de la otra y la segunda se evalúa con el disponible que dejó la primera (DEC-06 punto 7). **La misma orden recibida dos veces** (reintento, doble clic, corte de red): se ejecuta **una sola vez**, y la segunda recepción no genera una nueva fila en `orders` ni vuelve a mover saldo o posiciones. **Identificación:** el `id` de `orders` lo asigna el sistema al guardar, así que el cliente no lo conoce antes de enviar. Por eso la orden debe llevar un **identificador generado por el cliente**. El sistema reconoce "la misma orden" por la combinación **usuario + identificador**: el mismo identificador enviado por dos usuarios distintos corresponde a dos órdenes distintas, y nunca se devuelve una orden de otro usuario como respuesta a un reintento. **Reglas (confirmadas):** (a) si el mismo identificador llega con **otro contenido** (otro instrumento, cantidad, precio, etc.) → error, no se procesa; (b) el resultado original queda fijo: si la primera fue `REJECTED`, el reintento no la reevalúa aunque ahora haya saldo; (c) un pedido inválido que no se guardó (DEC-05) **no consume** el identificador y puede reintentarse corregido; (d) aplica también a depósitos y retiros (E-02, E-03). | Decisión del usuario: un reintento de red no puede duplicar una compra ni un débito. Es un invariante de dinero, no un detalle técnico. El mecanismo concreto (encabezado, columna, restricción única) se define en la etapa técnica. | 2026-11-21 |
| DEC-08 | **Búsqueda de activos.** Admite un **término general** que busca en ticker y nombre a la vez, y además **filtros específicos** opcionales por ticker y por nombre; si se envían varios, el activo debe cumplir todos. Coincidencia **parcial**, sin distinguir mayúsculas ni acentos. **Excluye** instrumentos `MONEDA` (ARS). **Orden:** ticker exacto → ticker que empieza con el texto → resto de coincidencias (ticker o nombre que contienen el texto); dentro de cada grupo, alfabético por ticker. Resultados **paginados** (DEC-19). Cada resultado trae ticker, nombre, tipo y último precio con variación diaria (si hay market data). | El término general es la experiencia natural de un buscador de broker; los filtros cubren la lectura literal de "ticker y/o nombre". El costo de soportar ambos es marginal. | 2026-11-21 |
| DEC-09 | **Respuesta ante una orden repetida.** Hay dos casos con respuestas **distintas**. **Mismo identificador y contenido idéntico** (reintento legítimo): se responde con **la orden original y su estado actual**, como si fuera la primera respuesta, más un indicador de que es un reintento ya procesado. No se crea nada nuevo. **Mismo identificador y contenido distinto** (cambia monto, cantidad, precio, instrumento, side o tipo): se responde con un **error específico de conflicto** ("identificador ya usado con otros datos"), distinto de cualquier otro error, y no se procesa. | El caso típico de reintento es que el cliente perdió la primera respuesta: devolverle el resultado original le permite seguir sin lógica extra (patrón habitual en APIs de pagos y brokers). El conflicto, en cambio, es casi siempre un bug del cliente, y un error propio permite detectarlo en lugar de confundirlo con un reintento. | 2026-11-21 |
| DEC-10 | **Contenido del portfolio.** (1) **Valor total** = efectivo total + Σ valor de posiciones con precio. (2) **Pesos** en un bloque propio, no como ítem del listado: efectivo total, reservado por compras `NEW` y disponible para operar. (3) **Listado de activos**: solo posiciones con cantidad ≠ 0; por cada una: ticker, nombre, cantidad, acciones reservadas por ventas `NEW`, último precio, valor de la posición, costo promedio, rendimiento total (%) y variación diaria (%). (4) Las **órdenes pendientes no se listan**: su efecto se ve en los reservados; el detalle está en el historial (E-04). (5) **Activo sin market data**: se muestra con su cantidad, pero precio, valor y rendimientos quedan "sin dato", no suma al total, y la respuesta advierte que el total excluye esos activos. | (2) El enunciado separa "pesos disponibles" del "listado de activos". (3) "Activos que posee" excluye las posiciones cerradas. (5) Inventar un precio falsearía la valuación; es preferible ser explícito. Ver boceto en la sección 7. | 2026-11-21 |
| DEC-11 *(revisable)* | **Posición negativa en los datos semilla (BMA −10).** No se modifican los datos provistos. El portfolio muestra la posición tal cual (−10, valor −$ 15.028, incluido en el total) y la **marca como inconsistente** con un aviso visible en esa fila. Lo mismo aplica a cualquier posición o efectivo negativo que aparezca (por ejemplo, tras usar herramientas internas, DEC-03). Las reglas de DEC-01 y DEC-05 impiden que una orden enviada por la API genere esta situación. | Respeta el material del evaluador (los números coinciden con su `database.sql`), no oculta la anomalía y evidencia que fue detectada. El usuario se reserva cambiar esta decisión más adelante. | 2026-11-21 |
| DEC-12 | **Motivo de rechazo.** Toda orden `REJECTED` registra un **motivo**, que se devuelve en la respuesta del envío y queda **guardado en la orden**, visible en el historial (E-04). Para el resto de los estados el motivo queda vacío. Catálogo de motivos (propuesta): `INSUFFICIENT_FUNDS` (pesos disponibles insuficientes, incluye `CASH_OUT`), `INSUFFICIENT_SHARES` (acciones disponibles insuficientes), `AMOUNT_BELOW_PRICE` (el monto no alcanza para una acción; ver DEC-22), `NO_MARKET_PRICE` (orden `MARKET` sobre un instrumento sin market data). Cada motivo tiene un código estable y un mensaje legible **en inglés** con valores concretos (catálogo completo en RFC TEC-19). **Implica modificar la tabla `orders`** (CM-01, sección 8). | Un rechazo sin explicación no le sirve al usuario ni a soporte. El enunciado pide guardar la orden "con el estado y **valores correspondientes**", y el motivo es justamente el valor que explica el estado `REJECTED`. El enunciado permite modificar tablas si se justifica el cambio. | 2026-11-21 |
| DEC-13 | **Montos con centavos en depósitos y retiros.** `CASH_IN` y `CASH_OUT` aceptan montos con hasta **2 decimales**. Un monto con más decimales es un pedido inválido (error, no se guarda; DEC-05). Las órdenes `BUY` / `SELL` siguen operando **solo cantidades enteras de acciones**: esa restricción la impone la regla de negocio R-08, no el tipo de dato. **Implica modificar `orders.size`** para admitir 2 decimales (CM-03, sección 8). | Con `size` entero, el usuario no puede depositar ni retirar centavos, pero su saldo sí los tiene porque surge de operar a precios con decimales: nunca podría retirar todo su efectivo. El cambio es mínimo, mantiene la convención de los datos provistos (efectivo con `price` = 1 y `size` = monto) y deja válidas todas las filas semilla. Se descartó guardar el efectivo en centavos porque obliga a reinterpretar los datos existentes. | 2026-11-21 |
| DEC-14 | **Órdenes por monto: compras y ventas, solo acciones enteras, se usa el monto real.** (1) El monto en pesos se admite **tanto en `BUY` como en `SELL`**. (2) Cantidad = ⌊monto / precio de referencia⌋ (DEC-06): **siempre un número entero de acciones, nunca fracciones**, en compras y en ventas. (3) **El monto real de la orden es `cantidad × precio`**, no el monto pedido. La diferencia (el sobrante que no alcanza para otra acción) nunca se debita, se reserva ni se acredita: queda disponible. (4) Ese monto real es el que se usa para validar el disponible, para debitar o acreditar en una `MARKET` y para **reservar** en una compra `LIMIT` (DEC-01). En una venta `LIMIT` se reservan las **acciones** calculadas. (5) Si la cantidad calculada supera lo disponible, la orden queda `REJECTED` (`INSUFFICIENT_FUNDS` / `INSUFFICIENT_SHARES`); nunca se ajusta automáticamente a lo que el usuario tiene. (6) La respuesta informa la cantidad calculada, el monto real y el sobrante. **Ejemplos** (precio 925,85): compra por $ 50.000 → 54 acciones; monto real **$ 49.995,90**, que es lo que se debita (`MARKET`) o se reserva (`LIMIT` a 925,85); sobrante $ 4,10, sigue disponible. Venta por $ 50.000 → 54 acciones; se acreditan $ 49.995,90 (`MARKET`) o se reservan 54 acciones (`LIMIT`). | El enunciado habla de la cantidad que se quiere "comprar **o vender**" y prohíbe fracciones de acciones. Reservar o debitar el monto pedido en lugar del real bloquearía pesos que la orden nunca va a usar y descuadraría el disponible. Ajustar solo una orden a lo disponible ejecutaría algo distinto de lo que el usuario pidió. | 2026-11-21 |
| DEC-15 | **Cancelación idempotente.** Cancelar una orden `NEW` la pasa a `CANCELLED` y libera su reserva (DEC-01). Cancelar una orden **ya `CANCELLED`** responde **éxito** con la orden cancelada y un indicador de que ya lo estaba; no cambia nada ni libera la reserva de nuevo. Cancelar una orden `FILLED` o `REJECTED` responde **error** ("solo se pueden cancelar órdenes `NEW`") y no cambia nada. | Mismo criterio que DEC-09: un reintento (por ejemplo, tras un corte de red) recibe el resultado de lo que ya pidió en lugar de un error engañoso. Repetir la cancelación no tiene efectos secundarios. La regla R-10 se respeta: ninguna orden que no esté `NEW` cambia de estado. | 2026-11-21 |
| DEC-16 | **Detalles operativos.** (1) **Portfolio de un usuario sin movimientos**: valor total, pesos y reservado en 0 y listado vacío; usuario inexistente → error. (2) **Historial de órdenes** (E-04): de la más reciente a la más antigua, **paginado**, con filtro opcional por estado. (3) **Herramientas internas e idempotencia**: borrar una orden (I-01) o restaurar los datos (I-04) también elimina sus identificadores de idempotencia; reenviar después la misma orden la **ejecuta de nuevo**. Es comportamiento intencional de demo y debe advertirse. | (1) Un usuario nuevo es un caso válido, no un error. (2) El historial crece sin límite. (3) Las herramientas internas existen para volver a un estado anterior; conservar identificadores de órdenes borradas impediría repetir la demo. | 2026-11-21 |
| DEC-17 | **Aislamiento de datos entre usuarios.** Ningún usuario puede **ver ni modificar** datos de otro usuario, en **todas** las funcionalidades: portfolio, envío y cancelación de órdenes, historial, consulta de una orden, depósitos y retiros. Pedir un recurso de otro usuario (por ejemplo, su orden por id) responde **"no encontrado"**, igual que si no existiera, para no revelar que existe. La **única excepción** es una identidad con **permiso de administrador**, que puede acceder a datos de cualquier usuario y es la **única habilitada para las herramientas internas** (nivel 3). La búsqueda de activos y los precios son datos públicos del mercado y no tienen dueño. **Detalle propuesto:** cada pedido declara **quién lo hace** (identidad del solicitante) de forma separada de los datos del pedido, y esa identidad se compara con el dueño del recurso. **Limitación documentada:** como no hay autenticación (fuera del alcance), la identidad declarada no se verifica; en producción vendría de un token emitido por el sistema de autenticación. El aislamiento queda implementado y probado, listo para conectarse a esa autenticación real. | Decisión del usuario como mecanismo de seguridad: aun sin autenticación, el diseño no debe permitir que un usuario obtenga datos de otro. Responder "no encontrado" evita confirmar la existencia de recursos ajenos. | 2026-11-21 |
| DEC-18 | **Fechas y horas en UTC.** Toda fecha y hora se **registra en UTC** y la API la **devuelve en UTC** con la zona explícita (formato ISO 8601, por ejemplo `2026-11-21T14:36:00Z`). Convertirla a la hora local del usuario es responsabilidad de quien presenta los datos (normalmente un BFF o el frontend, que no forman parte de este proyecto). Ejemplo: una compra hecha a las 11:36 en Argentina (UTC−3) se guarda como **14:36 UTC**; en España (UTC+1 en noviembre) se mostraría como 15:36. Las fechas y horas de los **datos semilla** (que no traen zona) se interpretan como UTC. Las fechas de `marketdata` son días calendario, sin hora. | Decisión del usuario. Una única referencia horaria evita ambigüedades entre clientes de distintos países y con cambios de horario de verano; la presentación local es un problema de la capa de presentación. | 2026-11-21 |
| DEC-19 | **Búsqueda sin texto = listado del mercado.** Si la búsqueda llega sin término ni filtros, devuelve **todos los instrumentos operables** (excluye `MONEDA`), ordenados alfabéticamente por ticker. Toda respuesta de búsqueda es **paginada**, con o sin texto. Una búsqueda sin coincidencias devuelve una lista vacía, no un error. | "Ver todo lo que hay para comprar" es un uso legítimo del buscador de un broker; es lo que un usuario espera al no escribir nada. El paginado mantiene acotada la respuesta cuando el mercado crezca. | 2026-11-21 |
| DEC-20 *(propuesta)* | **Preparar el modelo para escala** (millones de instrumentos y de órdenes). Se proponen los cambios CM-04 a CM-08 de la sección 8: ticker único, datos de búsqueda normalizados e indexados, acceso directo al último precio, índices de órdenes por usuario y paginado por cursor. Ninguno cambia el comportamiento funcional: solo garantizan que siga siendo correcto y rápido con volumen. | Pedido del usuario: el diseño no debe depender de que hoy haya 66 instrumentos y 11 órdenes. Con millones de filas, una búsqueda parcial sin índice o un cálculo de portfolio que recorre toda la tabla dejan de ser viables. | 2026-11-21 |
| DEC-21 | **Longitud mínima de búsqueda.** Con **3 o más caracteres** la búsqueda funciona por coincidencia parcial (DEC-08). Con **1 o 2 caracteres** solo devuelve el instrumento cuyo **ticker es exactamente** ese texto (por ejemplo, "ts" → TS si existe), nunca "todo lo que contenga" esas letras. Aplica al término general y al filtro de ticker. El **filtro de nombre** con 1 o 2 caracteres responde un **error de validación** que indica que se requieren al menos 3 caracteres para buscar por nombre: un nombre no tiene "coincidencia exacta de ticker", así que ese filtro nunca podría encontrar nada y devolver una lista vacía escondería el error de quien llama (ajuste posterior, a pedido del usuario). La búsqueda **vacía** sigue devolviendo el listado paginado (DEC-19). | Propuesta del usuario para evitar consultas costosas y poco útiles como "todo lo que contenga A". Un mínimo estricto de 3 caracteres impediría encontrar tickers reales de 1 o 2 letras (TS, KO, C, V). Además, los índices de búsqueda parcial (CM-05) son eficientes a partir de 3 caracteres. | 2026-11-21 |
| DEC-22 | **Código de rechazo por monto insuficiente para una acción:** `AMOUNT_BELOW_PRICE` (reemplaza a `ZERO_QUANTITY`). Mensaje legible **en inglés** (RFC TEC-19) con los valores concretos, por ejemplo: *"The amount ARS 500.00 is not enough to buy one share of PAMP at ARS 925.85."* Aplica a compras y ventas por monto (en la venta: "...to sell one share..."). | Observación del usuario: `ZERO_QUANTITY` describe el resultado del cálculo, no la causa. `AMOUNT_BELOW_PRICE` dice que el monto es menor que el precio de una acción, sin ser un código excesivamente largo. | 2026-11-21 |
| DEC-23 | **Alcance de la clave de idempotencia:** una sola por usuario para **todas** las operaciones (órdenes `BUY`/`SELL`, depósitos y retiros). Reusar una clave ya usada en otro tipo de operación es un **conflicto** (DEC-09), aunque el pedido sea válido. | Todas las operaciones terminan en la misma tabla `orders`; una única regla es más simple de entender y de garantizar (CM-02). Permitir la misma clave en un depósito y en una compra no aporta nada y genera confusión sobre a qué operación se refiere un reintento. Surgió al contrastar una ampliación de escenarios propuesta por el usuario. | 2026-11-21 |
| DEC-24 | **Los campos desconocidos en el cuerpo de un pedido se rechazan** con `VALIDATION_ERROR`, nombrando el campo (`Request validation failed: 'userId' is not a recognized field.`), en toda la API. Nada se guarda. Como consecuencia, los escenarios que planteaban "operar a nombre de otro usuario" (CU-03.30, CU-05.06, CU-06.05) pasan a verificar que **no hay forma de nombrar a otro usuario**: un `userId` en el cuerpo es un campo desconocido y se rechaza. | Surgió al escribir la spec 005: con el estilo "mis datos" (RFC TEC-18) la situación original ya no puede ocurrir. En una API de dinero, un campo ignorado en silencio es un riesgo: el cliente cree haber enviado algo que el servidor nunca leyó (por ejemplo `"pricee"` en lugar de `"price"`). Rechazarlo detecta el error en el momento y nombrando el campo. Misma filosofía que rechazar `1.5` como cantidad (spec 003). Verificado: en Spring Boot 4.1 se activa con `spring.jackson.deserialization.fail-on-unknown-properties: true`. | 2026-11-21 |
| DEC-25 | **Textos de error para parámetros de URL inválidos o faltantes.** Cuando un parámetro de la URL (por ejemplo, el término o el número de página de una búsqueda) tiene un tipo o formato inválido, el error de validación (`VALIDATION_ERROR`) dice `Request validation failed: '<nombre>' has an invalid type or format.` Cuando falta un parámetro obligatorio, dice `Request validation failed: '<nombre>' is required.` Los textos están **en inglés** (RFC TEC-19). Hasta que un endpoint real use parámetros de URL no hay tests de estos textos: el primero que los use (la búsqueda, spec 004) debe verificarlos. | El plan de la spec 003 fijaba el formato del mensaje (`'<nombre>' <motivo>`) pero no el motivo. El agente que implementó la porción 003-b2 (PR #15) eligió estos textos y lo dejó documentado. El usuario los aceptó a partir de la revisión manual. Reusan las frases que la API ya usa en el cuerpo del pedido: `has an invalid type or format` para un tipo inválido, y la forma de los mensajes de validación para un dato faltante. Así el cliente ve un solo estilo de error. | 2026-11-22 |

---

## 7. Boceto del portfolio (usuario 1, datos semilla)

Ilustra DEC-01, DEC-02, DEC-10 y DEC-11. Precios al 2023-07-14.

```
Valor total de la cuenta     $ 889.756,00   (efectivo total + posiciones)
Pesos
  Efectivo total             $ 753.000,00
  Reservado (órdenes NEW)    $ 125.500,00   (LOMA 50@710 + BMA 60@1500)
  Disponible para operar     $ 627.500,00

Activo  Cant.  Reserv.  Precio    Valor        Costo prom.  Rend. total  Var. diaria
PAMP      40      0     925,85     37.034,00     930,00       -0,45 %      +0,44 %
METR     500      0     229,50    114.750,00     250,00       -8,20 %      -1,08 %
BMA      -10      0   1.502,80    -15.028,00   1.540,00       -2,42 %      -1,15 %   ⚠ inconsistente: posición negativa
```

---

## 8. Cambios al modelo de datos provisto

El enunciado permite modificar o agregar tablas justificándolo. Estos son los cambios que **exigen las decisiones funcionales**. Su forma técnica (nombre de columna, tipo, índice, migración) se define en la etapa técnica. El archivo original se conserva sin cambios en [`referencia/database.sql`](referencia/database.sql) para poder comparar.

| ID | Cambio | Tabla | Origen | Justificación |
|---|---|---|---|---|
| CM-01 | Nuevos datos: **motivo de rechazo** (código) y **mensaje de rechazo** (texto en inglés con los valores del momento), opcionales; solo se completan en órdenes `REJECTED`. El mensaje se guarda porque incluye valores que cambian (por ejemplo, el disponible al momento del rechazo) y no podría reconstruirse después. | `orders` | DEC-12 | La tabla no tiene dónde explicar un rechazo; sin él, el historial no permite entender una `REJECTED`. |
| CM-02 | Nuevos datos: **identificador de idempotencia** del cliente, único por combinación usuario + identificador, y **huella** (hash) del pedido, para distinguir un reintento idéntico de un conflicto (DEC-09, RFC TEC-16). | `orders` | DEC-07, DEC-09 | Sin persistirlo no se puede reconocer un reintento ni garantizar que una orden se ejecute una sola vez. |
| CM-03 | `size` pasa de entero a **numérico con 2 decimales**. | `orders` | DEC-13 | Permite depositar y retirar montos con centavos (por ejemplo, retirar el saldo completo). Las acciones siguen siendo enteras por regla de negocio (R-08). Las filas existentes no cambian. |
| CM-09 | Columnas de dinero (`orders.price`, `orders.size` y precios de `marketdata`) pasan de `NUMERIC(10,2)` a **`NUMERIC(18,2)`**. | `orders`, `marketdata` | RFC TEC-20 | `NUMERIC(10,2)` admite como máximo $ 99.999.999,99: con los montos actuales en pesos argentinos, un depósito, un retiro o una orden grande lo superarían. `NUMERIC(18,2)` admite hasta diez billones de pesos con centavos exactos. Las filas existentes no cambian. |
| CM-10 | **Restricciones de integridad**: `NOT NULL` en las columnas obligatorias; `CHECK` de valores permitidos de `side`, `type`, `status` y tipo de instrumento; cantidad no negativa; **acciones enteras** en `BUY`/`SELL`; motivo de rechazo solo en órdenes `REJECTED` y de un catálogo cerrado; clave de idempotencia y huella presentes juntas; número de cuenta único; un solo precio por instrumento y día. | todas | Spec 002 | Defensa en profundidad: aunque el código tenga un error, la base rechaza filas que rompen las reglas de negocio (R-08, DEC-12, DEC-23). Todas las filas semilla las cumplen. |
| CM-11 | `orders.datetime` pasa de `TIMESTAMP` a **`TIMESTAMPTZ`**, interpretando las fechas semilla como UTC. | `orders` | DEC-18 | Guardar el instante con zona elimina la ambigüedad horaria: la base devuelve siempre el mismo instante sin importar la zona de la sesión. |
| CM-12 | Nuevo dato: **monto pedido** (`requested_amount`), solo en órdenes `BUY`/`SELL` enviadas por monto. | `orders` | DEC-09, DEC-14 | Para que un reintento devuelva exactamente la respuesta original, incluido el sobrante (monto pedido − monto real). Sin guardarlo, el sobrante no se podría reconstruir. Surgió al escribir la spec 005. |

### Cambios para escala (DEC-20, propuestos)

| ID | Cambio | Tabla | Para qué |
|---|---|---|---|
| CM-04 | **Ticker único** (restricción de unicidad). | `instruments` | Hoy nada impide dos instrumentos con el mismo ticker; la búsqueda por "ticker exacto" y la operatoria asumen que es único. |
| CM-05 | **Ticker y nombre normalizados** (minúsculas, sin acentos) guardados junto al original, con un **índice apto para búsqueda parcial** ("contiene"). | `instruments` | Una búsqueda parcial sin acentos sobre millones de nombres no puede normalizar y recorrer toda la tabla en cada consulta. |
| CM-06 | **Acceso directo al último precio por instrumento**: índice por instrumento + fecha descendente en `marketdata`, o una tabla/vista con solo el último precio de cada instrumento. | `marketdata` | Con historial diario de millones de instrumentos, "el `close` más reciente" no puede resolverse recorriendo todas las fechas; lo usan el portfolio, la búsqueda y cada orden `MARKET`. |
| CM-07 | **Índices de órdenes** por usuario (y por usuario + instrumento, usuario + estado, usuario + fecha). | `orders` | El portfolio, las reservas, las validaciones y el historial filtran siempre por usuario; sin índices, cada consulta recorre las órdenes de todos los usuarios. |
| CM-08 | **Paginado por cursor** (continuar "desde el último elemento visto") en lugar de "saltear N filas". | Búsqueda e historial | Saltear N filas se vuelve más lento cuanto más avanza la página y puede repetir u omitir elementos si entran datos nuevos mientras se pagina. |

Otras optimizaciones posibles (por ejemplo, guardar posiciones precalculadas en lugar de derivarlas de `orders` en cada consulta) se evalúan en la etapa técnica, ya que cambian **cómo** se calcula pero no **qué** se calcula.

Las filas existentes de los datos semilla quedan con ambos datos vacíos: no tienen motivo registrado ni identificador del cliente, y siguen siendo válidas. En particular, la orden semilla `REJECTED` (venta de 100 PAMP) queda sin motivo.

---

## 9. Casos de uso y escenarios

El detalle de cada funcionalidad, con sus escenarios en formato *dado / cuando / entonces*, está en
[`casos-de-uso.md`](casos-de-uso.md). Esos escenarios son los criterios de aceptación del proyecto y la base de los tests funcionales.

---

## 10. Notas para la etapa técnica (no decididas)
- El enunciado exige **Node.js**, pero el proyecto se implementa en **Java** por decisión del autor (desvío consciente, ver PRD §1.1).
- Los cambios al modelo de datos (sección 8) se implementan como cambios de esquema justificados.
