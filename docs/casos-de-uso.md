# Casos de uso y escenarios

> Criterios de aceptación del proyecto, derivados de [`analisis-funcional.md`](analisis-funcional.md).
> Cada escenario cita las reglas (R-xx) y decisiones (DEC-xx) que lo sustentan, y es candidato directo a test funcional.
> Formato: **Dado** (estado inicial) · **Cuando** (acción) · **Entonces** (resultado esperado).

## Estado de referencia

Salvo que el escenario diga otra cosa, se parte de los **datos semilla** y del **usuario 1** (precios al 2023-07-14):

| Dato | Valor |
|---|---|
| Efectivo total | $ 753.000,00 |
| Reservado por compras `NEW` | $ 125.500,00 (LOMA 50 @ 710, BMA 60 @ 1.500) |
| **Pesos disponibles** | **$ 627.500,00** |
| PAMP | 40 acciones, 0 reservadas, último precio $ 925,85 |
| METR | 500 acciones, 0 reservadas, último precio $ 229,50 |
| BMA | −10 acciones (inconsistencia de los datos semilla, DEC-11) |
| Sin market data | PGR, IRCP |

"Identidad" es quién hace el pedido (DEC-17). Salvo indicación, el pedido lo hace el propio usuario 1.

**Errores y mensajes** (RFC TEC-19): todo texto que devuelve la API está en **inglés**. En estas tablas, "Error 'no encontrado'" significa código `NOT_FOUND` (HTTP 404); "Error" en un pedido inválido significa `VALIDATION_ERROR` (HTTP 400); "conflicto" significa `IDEMPOTENCY_CONFLICT` (HTTP 422). Una orden `REJECTED` es una respuesta exitosa (HTTP 201) que incluye `rejection.code` y `rejection.message`.

## Índice

| ID | Caso de uso | Nivel |
|---|---|---|
| CU-01 | Consultar portfolio | 1 · Pedido del challenge |
| CU-02 | Buscar activos | 1 · Pedido del challenge |
| CU-03 | Enviar orden de compra o venta | 1 · Pedido del challenge |
| CU-04 | Cancelar orden | 2 · Extensión de dominio |
| CU-05 | Depositar pesos | 2 · Extensión de dominio |
| CU-06 | Retirar pesos | 2 · Extensión de dominio |
| CU-07 | Consultar historial y orden | 2 · Extensión de dominio |
| CU-I | Herramientas internas | 3 · Interno (solo administrador) |

---

## CU-01 · Consultar portfolio

**Actor:** usuario (sobre su cuenta) o administrador. **Reglas:** R-13, R-15, R-16, DEC-01, DEC-01.1, DEC-02, DEC-10, DEC-11, DEC-17.

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| CU-01.01 | Datos semilla | El usuario 1 consulta su portfolio | Valor total $ 889.756,00; efectivo $ 753.000; reservado $ 125.500; disponible $ 627.500; PAMP, METR y BMA con cantidad, reservadas, precio, valor, costo promedio, rendimiento total y variación diaria (ver boceto, sección 7 del análisis). |
| CU-01.02 | Datos semilla | Se consulta el portfolio | PAMP muestra rendimiento total −0,45 % (contra costo promedio $ 930) y variación diaria +0,44 %. |
| CU-01.03 | Datos semilla | Se consulta el portfolio | BMA aparece con −10, valor −$ 15.028 incluido en el total, y **marcada como inconsistente**. |
| CU-01.04 | El usuario 1 tiene una compra `LIMIT` `NEW` de LOMA y ninguna orden `FILLED` de LOMA | Se consulta el portfolio | LOMA **no** aparece en el listado; su efecto se ve solo en "reservado" y "disponible". |
| CU-01.05 | El usuario 1 vendió todas sus PAMP | Se consulta el portfolio | PAMP no aparece (cantidad 0). |
| CU-01.06 | El usuario tiene una posición en PGR (sin market data) | Se consulta el portfolio | PGR aparece con su cantidad; precio, valor y rendimientos "sin dato"; no suma al total; la respuesta advierte que el total excluye activos sin precio. |
| CU-01.07 | El usuario 2 no tiene órdenes | El usuario 2 consulta su portfolio | Todo en 0 y listado vacío. No es un error. |
| CU-01.08 | — | Se consulta el portfolio de un usuario inexistente | Error "no encontrado". |
| CU-01.09 | — | El usuario 2 pide el portfolio del usuario 1 | Error "no encontrado" (no revela que existe). |
| CU-01.10 | — | El administrador pide el portfolio del usuario 1 | Responde igual que CU-01.01. |
| CU-01.11 | El usuario 1 acaba de ejecutar una compra `MARKET` de 10 PAMP | Se consulta el portfolio | PAMP muestra 50 acciones y el efectivo ya refleja el débito (R-12). |
| CU-01.12 | El usuario 1 compró PAMP a $ 930 (50), vendió 10 y luego compra 10 más a $ 925,85 | Se consulta el portfolio | Costo promedio = (50 × 930 + 10 × 925,85) / 60 = $ 929,31; la venta no modificó el costo (DEC-02.1). |
| CU-01.13 | El usuario 1 vende sus 40 PAMP (`MARKET`, posición en 0); el administrador cambia el precio de PAMP a $ 1.000 (I-03); el usuario compra 10 PAMP (`MARKET`) | Consulta su portfolio | PAMP: 10 acciones, costo promedio **$ 1.000,00**. Las compras anteriores al cierre en 0 no se mezclan con la nueva posición (DEC-02.1). |
| CU-01.14 | Órdenes `FILLED` cuyo orden por `id` no coincide con el orden por fecha y hora (solo reproducible manipulando datos: por la API la fecha la asigna el servidor) | Se consulta el portfolio | El costo promedio se calcula procesando las órdenes por fecha y hora ascendente; si dos órdenes tienen la misma fecha y hora, se desempata por `id` (DEC-02.1). |

---

## CU-02 · Buscar activos

**Actor:** cualquiera (datos públicos del mercado). **Reglas:** DEC-08, DEC-19, DEC-21.

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| CU-02.01 | — | Se busca "gal" | Devuelve GGAL (coincidencia parcial en el nombre "Grupo Financiero Galicia"). |
| CU-02.02 | — | Se busca "rio" | Devuelve, entre otros, MOLI ("Molinos **Río** de la Plata"): no distingue acentos. También BHIP, RICH y OEST, cuyos nombres contienen "rio". |
| CU-02.03 | — | Se busca "BANCO" o "banco" | Mismo resultado (no distingue mayúsculas): BPAT, BBAR, BHIP, BMA. |
| CU-02.04 | — | Se busca "pamp" | PAMP primero (ticker exacto). |
| CU-02.05 | — | Se busca "ban" | Primero los tickers que empiezan con "BAN" (ninguno), luego el resto de coincidencias, alfabético: BBAR, BHIP, BMA, BPAT (por nombre) y GBAN (ticker y nombre "Gas Natural **Ban** S.A."). *(Corregido al escribir la spec 004: la versión anterior omitía GBAN.)* |
| CU-02.05b | — | Se busca "b" o "ba" (1 o 2 caracteres) | Lista vacía: ningún ticker es exactamente "B" o "BA". **No** devuelve todo lo que contenga esas letras (DEC-21). |
| CU-02.05c | Existe un instrumento con ticker TS | Se busca "ts" | Devuelve TS (ticker exacto); no devuelve otros que contengan "ts". |
| CU-02.06 | — | Se busca con filtro de ticker "bma" y filtro de nombre "banco" | Solo los que cumplen ambos: BMA. |
| CU-02.07 | — | Se busca sin texto ni filtros | Listado de los 65 instrumentos operables, alfabético por ticker, paginado. |
| CU-02.08 | — | Se busca "ars" o "pesos" | Lista vacía: los instrumentos `MONEDA` no se devuelven. |
| CU-02.09 | — | Se busca "xyz123" | Lista vacía, no error. |
| CU-02.10 | — | Se busca "PGR" | Devuelve PGR con último precio y variación "sin dato". |
| CU-02.11 | — | Se busca "pamp" | El resultado incluye ticker, nombre, tipo, último precio ($ 925,85) y variación diaria (+0,44 %). |
| CU-02.12 | La búsqueda tiene más resultados que el tamaño de página | Se pide la página siguiente | Devuelve los siguientes, sin repetir ni omitir. |
| CU-02.13 | — | Se busca con filtro de nombre "b" o "ba" (1 o 2 caracteres) | **Error de validación**: `SEARCH_TERM_TOO_SHORT`, *"The name filter requires at least 3 characters."*. No se ejecuta la búsqueda (DEC-21). |
| CU-02.14 | — | Término general "banco" + filtro de ticker "bma" | Solo BMA (cumple ambos criterios, DEC-08). |
| CU-02.15 | — | Término general "banco" + filtro de ticker "bm" | Lista vacía: "bm" tiene 2 caracteres y no existe el ticker exacto "BM" (DEC-21). |

---

## CU-03 · Enviar orden de compra o venta

**Actor:** usuario (sobre su cuenta). **Reglas:** R-04 a R-09, R-12, R-17, DEC-01, DEC-05, DEC-06, DEC-07, DEC-09, DEC-23, DEC-12, DEC-14, DEC-17, DEC-18.

### Órdenes MARKET

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| CU-03.01 | Disponible $ 627.500 | `BUY` `MARKET` 10 PAMP | `FILLED`, `price` 925,85, se debitan $ 9.258,50; disponible $ 618.241,50; PAMP 50. |
| CU-03.02 | Disponible $ 627.500 | `BUY` `MARKET` por $ 50.000 de PAMP | `FILLED` por **54** acciones; monto real $ 49.995,90; sobrante $ 4,10 (sigue disponible). La respuesta informa cantidad, monto real y sobrante. |
| CU-03.03 | Disponible $ 627.500 | `BUY` `MARKET` 1.000 PAMP ($ 925.850) | `REJECTED` con motivo `INSUFFICIENT_FUNDS`; se guarda; no cambian efectivo ni posiciones. |
| CU-03.04 | Efectivo total $ 753.000, disponible $ 627.500 | `BUY` `MARKET` 700 PAMP ($ 648.095) | `REJECTED` (`INSUFFICIENT_FUNDS`): alcanza con el total pero **no** con el disponible, porque hay $ 125.500 reservados (DEC-01). |
| CU-03.05 | PAMP 40 | `SELL` `MARKET` 10 PAMP | `FILLED`, se acreditan $ 9.258,50; PAMP 30. |
| CU-03.06 | PAMP 40 | `SELL` `MARKET` 41 PAMP | `REJECTED` (`INSUFFICIENT_SHARES`). |
| CU-03.07 | Sin posición en GGAL | `SELL` `MARKET` 1 GGAL | `REJECTED` (`INSUFFICIENT_SHARES`). |
| CU-03.08 | BMA −10 | `SELL` `MARKET` 1 BMA | `REJECTED` (`INSUFFICIENT_SHARES`). |
| CU-03.09 | PAMP 40 | `SELL` `MARKET` por $ 50.000 de PAMP | Calcula 54 acciones > 40 disponibles → `REJECTED` (`INSUFFICIENT_SHARES`); **no** se ajusta a 40. |
| CU-03.10 | PAMP 40 | `SELL` `MARKET` por $ 20.000 de PAMP | `FILLED` por 21 acciones; se acreditan $ 19.442,85. |
| CU-03.11 | — | `BUY` `MARKET` por $ 500 de PAMP | No alcanza para una acción → `REJECTED` (`AMOUNT_BELOW_PRICE`), mensaje *"The amount ARS 500.00 is not enough to buy one share of PAMP at ARS 925.85."* (DEC-22). |
| CU-03.12 | PGR no tiene market data | `BUY` `MARKET` 1 PGR | `REJECTED` (`NO_MARKET_PRICE`), `price` vacío. |

### Órdenes LIMIT

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| CU-03.13 | Disponible $ 627.500 | `BUY` `LIMIT` 100 PAMP a $ 900 | `NEW`; se reservan $ 90.000; disponible $ 537.500; la posición de PAMP **no** cambia. |
| CU-03.14 | Disponible $ 627.500 | `BUY` `LIMIT` por $ 50.000 de PAMP a $ 925,85 | `NEW` por 54 acciones; se reserva el monto real **$ 49.995,90**, no $ 50.000. |
| CU-03.15 | PAMP 40 | `SELL` `LIMIT` 30 PAMP a $ 950 | `NEW`; se reservan 30 acciones; PAMP disponible para vender: 10. |
| CU-03.16 | Tras CU-03.15 (10 PAMP disponibles) | `SELL` `MARKET` 11 PAMP | `REJECTED` (`INSUFFICIENT_SHARES`). |
| CU-03.17 | Último precio PAMP $ 925,85 | `BUY` `LIMIT` 10 PAMP a $ 1.000 (mejor que el mercado) | `NEW`, no se ejecuta inmediatamente (DEC-06). |
| CU-03.18 | Disponible $ 627.500 | `BUY` `LIMIT` 1.000 PAMP a $ 900 ($ 900.000) | `REJECTED` (`INSUFFICIENT_FUNDS`). |
| CU-03.19 | PGR no tiene market data | `BUY` `LIMIT` 10 PGR a $ 50 | `NEW` (una `LIMIT` no necesita market data). |
| CU-03.38 | PAMP 40 | `SELL` `LIMIT` por $ 20.000 de PAMP a $ 950 | `NEW` por **21** acciones (⌊20.000 / 950⌋); se reservan 21 acciones (quedan 19 disponibles); la respuesta informa monto real $ 19.950,00 y sobrante $ 50,00 (DEC-14). |
| CU-03.39 | PAMP 40, último precio $ 925,85 | `SELL` `MARKET` por $ 500 de PAMP | `REJECTED` (`AMOUNT_BELOW_PRICE`), mensaje *"The amount ARS 500.00 is not enough to sell one share of PAMP at ARS 925.85."* (DEC-22). |
| CU-03.40 | Disponible $ 627.500, último precio PAMP $ 925,85 | `BUY` `LIMIT` por $ 800 de PAMP a $ 1.000 | `REJECTED` (`AMOUNT_BELOW_PRICE`): la cantidad se calcula con el **precio límite** ($ 1.000), no con el `close` (DEC-06, DEC-22). |

### Pedidos inválidos (error, **no** se guardan · DEC-05)

| ID | Cuando | Entonces |
|---|---|---|
| CU-03.20 | Usuario inexistente | Error; no se guarda. |
| CU-03.21 | Instrumento inexistente | Error; no se guarda. |
| CU-03.22 | Cantidad o monto ≤ 0 | Error; no se guarda. |
| CU-03.23 | Cantidad no entera (por ejemplo, 1,5 acciones) | Error; no se guarda (R-08). |
| CU-03.24 | `LIMIT` sin precio, o con precio ≤ 0 | Error; no se guarda. |
| CU-03.25 | `MARKET` con precio | Error; no se guarda. |
| CU-03.26 | Cantidad y monto a la vez, o ninguno | Error; no se guarda. |
| CU-03.27 | Side o tipo inválidos (o `CASH_IN`/`CASH_OUT` en este caso de uso) | Error; no se guarda. |
| CU-03.28 | `BUY` o `SELL` de ARS | Error; no se guarda. |
| CU-03.29 | Falta el identificador de idempotencia | Error; no se guarda. |
| CU-03.30 | El usuario 2 envía una orden con `"userId": 1` en el cuerpo, intentando operar a nombre del usuario 1 | `VALIDATION_ERROR` (HTTP 400): *"Request validation failed: 'userId' is not a recognized field."*; no se guarda nada. La orden solo puede pertenecer al usuario del header `X-User-Id` (DEC-17, DEC-24, TEC-18). |

### Idempotencia y concurrencia

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| CU-03.31 | Se envió `BUY` `MARKET` 10 PAMP con identificador `k1` y quedó `FILLED` | Se reenvía exactamente el mismo pedido con `k1` | Responde la orden original con su estado actual y un indicador de reintento; **no** se crea otra orden ni se debita de nuevo (DEC-09). |
| CU-03.32 | Igual que CU-03.31 | Se envía `k1` con cantidad 20 | `IDEMPOTENCY_CONFLICT` (HTTP 422), *"Idempotency key 'k1' was already used for a different request."*; no se procesa. |
| CU-03.33 | El usuario 1 usó `k1` | El usuario 2 envía una orden con `k1` | Se procesa como orden nueva del usuario 2 (la unicidad es usuario + identificador). |
| CU-03.34 | Una orden con `k2` quedó `REJECTED` por fondos; luego el usuario deposita | Se reenvía el mismo pedido con `k2` | Responde la orden `REJECTED` original; no se reevalúa. |
| CU-03.35 | Un pedido con `k3` fue inválido (no se guardó) | Se reenvía corregido con `k3` | Se procesa normalmente: el identificador no se había consumido. |
| CU-03.36 | Disponible $ 627.500 | Llegan **a la vez** dos órdenes distintas `BUY` `MARKET` 400 PAMP ($ 370.340 cada una) | Una queda `FILLED` y la otra `REJECTED` (`INSUFFICIENT_FUNDS`); el efectivo nunca queda negativo. |
| CU-03.41 | Disponible $ 627.500 | Dos órdenes `BUY` `MARKET` de 10 PAMP con **identificadores distintos** (`k10`, `k11`) | Ambas `FILLED`: son órdenes distintas aunque tengan el mismo contenido. Se debitan $ 18.517,00 en total y PAMP pasa a 60 (DEC-07). |
| CU-03.42 | El usuario 1 hizo un depósito con identificador `d1` | Envía una `BUY` con `d1` | Error de conflicto: la clave de idempotencia es única por usuario **entre todas las operaciones** (DEC-23). |
| CU-03.37 | Un usuario en Argentina (UTC−3) envía una orden a las 11:36 hora local | Se registra la orden y luego se consulta | Se guarda y se devuelve como `…T14:36:00Z` (UTC, con zona explícita). La API **no** convierte a la hora local: mostrar 11:36 (Argentina) o 15:36 (España) es responsabilidad de quien consume el servicio, por ejemplo un BFF (DEC-18). |

---

## CU-04 · Cancelar orden

**Actor:** usuario (dueño de la orden). **Reglas:** R-10, DEC-01, DEC-15, DEC-17.

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| CU-04.01 | Compra `LIMIT` `NEW` de BMA 60 @ 1.500 | El usuario 1 la cancela | Pasa a `CANCELLED`; se liberan $ 90.000; disponible $ 717.500. |
| CU-04.02 | Venta `LIMIT` `NEW` de 30 PAMP (CU-03.15) | Se cancela | `CANCELLED`; PAMP disponible para vender vuelve a 40. |
| CU-04.03 | La orden ya está `CANCELLED` | Se cancela de nuevo | Éxito con la orden cancelada e indicador "ya estaba cancelada"; la reserva no se libera dos veces. |
| CU-04.04 | Orden `FILLED` | Se intenta cancelar | `ORDER_NOT_CANCELLABLE` (HTTP 409), *"Order {id} cannot be cancelled because its status is FILLED. Only NEW orders can be cancelled."*; nada cambia. |
| CU-04.05 | Orden `REJECTED` | Se intenta cancelar | `ORDER_NOT_CANCELLABLE` (HTTP 409); nada cambia. |
| CU-04.06 | Orden `NEW` del usuario 1 | El usuario 2 intenta cancelarla | Error "no encontrado"; nada cambia. |
| CU-04.07 | — | Se cancela un id inexistente | Error "no encontrado". |
| CU-04.08 | Orden `NEW` | Llegan a la vez la cancelación y una ejecución forzada (I-02) | Solo una se aplica; la otra encuentra la orden fuera de `NEW` y responde según corresponda. |

---

## CU-05 · Depositar pesos

**Actor:** usuario. **Reglas:** R-11, DEC-04, DEC-07, DEC-13.

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| CU-05.01 | Usuario 2 sin movimientos | Deposita $ 100.000 | Orden `CASH_IN` `FILLED`, `size` 100.000, `price` 1; disponible $ 100.000. |
| CU-05.02 | — | Deposita $ 1.234,56 | `FILLED` con centavos. |
| CU-05.03 | — | Deposita $ 10,555 (3 decimales) | Error; no se guarda. |
| CU-05.04 | — | Deposita $ 0 o un monto negativo | Error; no se guarda. |
| CU-05.05 | Depósito ya procesado con identificador `d1` | Se reenvía idéntico | Responde el depósito original; el saldo **no** se acredita dos veces. |
| CU-05.06 | — | El usuario 2 envía un depósito con `"userId": 1` en el cuerpo | `VALIDATION_ERROR` (HTTP 400) nombrando `userId`; no se acredita ni se guarda nada (DEC-17, DEC-24). |
| CU-05.07 | Depósito procesado con `d1` por $ 100.000 | Se reenvía `d1` con $ 200.000, o se usa `d1` para un retiro | Error de conflicto; no se procesa (DEC-09, DEC-23). |
| CU-05.08 | — | Depósito sin identificador de idempotencia | Error; no se guarda (DEC-05, DEC-07). |

---

## CU-06 · Retirar pesos

**Actor:** usuario. **Reglas:** R-09, R-11, DEC-01, DEC-04, DEC-12, DEC-13.

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| CU-06.01 | Disponible $ 627.500 | Retira $ 100.000 | `CASH_OUT` `FILLED`; disponible $ 527.500. |
| CU-06.02 | Efectivo $ 753.000, disponible $ 627.500 | Retira $ 700.000 | `REJECTED` (`INSUFFICIENT_FUNDS`): lo reservado no se puede retirar. |
| CU-06.03 | Disponible con centavos (por ejemplo, $ 618.241,50 tras CU-03.01) | Retira $ 618.241,50 | `FILLED`; disponible $ 0. |
| CU-06.04 | Retiro ya procesado con identificador `r1` | Se reenvía idéntico | Responde el retiro original; **no** se debita dos veces. |
| CU-06.05 | — | El usuario 2 envía un retiro con `"userId": 1` en el cuerpo | `VALIDATION_ERROR` (HTTP 400) nombrando `userId`; no se debita ni se guarda nada (DEC-17, DEC-24). |
| CU-06.06 | Retiro procesado con `r1` por $ 100.000 | Se reenvía `r1` con $ 50.000 | Error de conflicto; no se procesa (DEC-09). |
| CU-06.07 | — | Retiro de $ 0 o monto negativo | Error; **no** se guarda (a diferencia del retiro sin fondos, que se guarda `REJECTED`; DEC-05). |
| CU-06.08 | — | Retiro de $ 10,555 (más de 2 decimales) | Error; no se guarda (DEC-13). |
| CU-06.09 | — | Retiro sin identificador de idempotencia | Error; no se guarda (DEC-07). |

---

## CU-07 · Consultar historial y orden

**Actor:** usuario (sus órdenes) o administrador. **Reglas:** DEC-12, DEC-16, DEC-17, DEC-18.

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| CU-07.01 | Datos semilla | El usuario 1 consulta su historial | 11 órdenes, de la más reciente a la más antigua, paginadas, con fechas en UTC. |
| CU-07.02 | Datos semilla | Filtra por estado `NEW` | Las 2 compras `LIMIT` pendientes (LOMA, BMA). |
| CU-07.03 | Una orden quedó `REJECTED` por la API | Se consulta | Muestra su motivo (por ejemplo, `INSUFFICIENT_FUNDS`). |
| CU-07.04 | Datos semilla | Se consulta la venta `REJECTED` de 100 PAMP | Aparece sin motivo (dato previo al cambio CM-01). |
| CU-07.05 | — | El usuario 2 consulta una orden del usuario 1 por id | Error "no encontrado". |
| CU-07.06 | — | El administrador consulta el historial del usuario 1 | Lo obtiene. |
| CU-07.07 | El usuario 1 tiene 11 órdenes | Pide el historial con páginas de 5 y avanza a la 2 y la 3 | Devuelve 5, 5 y 1 órdenes, de la más reciente a la más antigua, sin duplicados ni omisiones (DEC-16, CM-08). |
| CU-07.08 | Datos semilla | El usuario 1 filtra por `CANCELLED` | 1 orden: la compra `LIMIT` de 50 PAMP a $ 920. |
| CU-07.09 | El usuario 2 no tiene órdenes | Filtra por `CANCELLED` | Lista vacía con datos de paginado válidos, no un error (DEC-16). |

---

## CU-I · Herramientas internas (solo administrador)

**Nivel 3: no forman parte del pedido del challenge.** Existen para demo y pruebas (DEC-03) y pueden romper invariantes a propósito.

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| CU-I.01 | — | Un usuario sin permiso de administrador usa cualquier herramienta interna | Error; nada cambia (DEC-17). |
| CU-I.02 | Compra `LIMIT` `NEW` de LOMA 50 @ 710 | El administrador fuerza su ejecución (I-02) | Pasa a `FILLED` a $ 710; la reserva de $ 35.500 se convierte en débito; LOMA aparece en el portfolio con 50 acciones. |
| CU-I.03 | Orden `FILLED` o `CANCELLED` | Se fuerza su ejecución | Error; solo aplica a órdenes `NEW`. |
| CU-I.04 | Datos semilla | El administrador borra el `CASH_IN` inicial del usuario 1 (I-01) | La orden desaparece; el portfolio se recalcula con efectivo negativo y lo marca como inconsistente (DEC-11), sin fallar. |
| CU-I.05 | Una orden con identificador `k1` fue borrada | Se reenvía el pedido con `k1` | Se ejecuta de nuevo (DEC-16): comportamiento intencional de demo. |
| CU-I.06 | PAMP a $ 925,85 | El administrador cambia su precio a $ 1.000 (I-03) | El portfolio y la búsqueda reflejan el nuevo precio, valor y rendimientos. |
| CU-I.07 | Datos modificados | El administrador restaura los datos (I-04) | Todo vuelve al estado semilla, incluidas las órdenes y los identificadores de idempotencia. |
| CU-I.08 | — | El administrador crea un usuario de prueba (I-05) | Usuario nuevo con portfolio vacío (CU-01.07). |
| CU-I.09 | Compra `LIMIT` `NEW` de 10 PGR a $ 50 (sin market data, CU-03.19) | El administrador fuerza su ejecución (I-02) | `FILLED` a $ 50 (usa el precio límite, no necesita market data); PGR aparece en el portfolio con 10 acciones y valuación "sin dato" (CU-01.06). |
