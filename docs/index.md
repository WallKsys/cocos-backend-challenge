# Cocos Broker API · Documentación

API de un broker de inversiones: portfolio, búsqueda de activos y envío de órdenes de compra y venta, más
depósitos, retiros y cancelaciones. El problema de fondo no es exponer endpoints, sino hacerlo **sin perder nunca
la consistencia del dinero**: ningún saldo negativo, ninguna orden ejecutada dos veces por un reintento y ningún
usuario viendo datos de otro.

Esta documentación explica qué se construye, por qué se decidió cada cosa y cómo se garantiza la calidad de un
código que escribe un agente de IA.

## Por dónde empezar

| Si querés saber... | Leé |
|---|---|
| Qué pide el negocio, qué era ambiguo en el enunciado y cómo se resolvió | [Análisis funcional](analisis-funcional.md) |
| Qué tiene que pasar exactamente en cada situación, con valores reales | [Casos de uso](casos-de-uso.md) (128 escenarios) |
| Objetivos, alcance y métricas de éxito | [PRD](prd.md) |
| Las decisiones técnicas y las alternativas descartadas | [RFC](rfc.md) |
| Qué reglas sigue el agente, dónde se decidieron y qué las hace cumplir | [Reglas y barreras](reglas-y-barreras.md) |

Orden recomendado para una evaluación: **análisis funcional → PRD → RFC → reglas y barreras**. Los casos de uso
sirven como referencia.

## Cómo se relacionan los documentos

Los IDs son el vínculo entre documentos y son estables:

| ID | Qué identifica | Dónde vive |
|---|---|---|
| `R-xx`, `DEC-xx`, `CM-xx` | Reglas del enunciado, decisiones de producto, cambios de modelo | Análisis funcional |
| `CU-xx.yy` | Escenarios de aceptación | Casos de uso, con un escenario Gherkin y tests por cada uno |
| `RF-xx`, `RNF-xx`, `M-xx` | Requisitos y métricas | PRD |
| `TEC-xx` | Decisiones técnicas | RFC |
| `ARQ-xx`, `PRO-xx`, `REV-xx`, ... | Reglas de ingeniería y su cumplimiento | Reglas y barreras |

## Fuera de este sitio

El código, las specs de cada funcionalidad (en inglés, para el agente) y la constitución del proyecto están en
el [repositorio](https://github.com/WallKsys/cocos-backend-challenge). El menú **En GitHub** los enlaza.

> **Fuente única.** Este sitio se genera automáticamente desde la carpeta `docs/` del repositorio en cada merge a
> `main`. No tiene contenido propio: lo que se ve acá es lo mismo que se revisa en los pull requests.
