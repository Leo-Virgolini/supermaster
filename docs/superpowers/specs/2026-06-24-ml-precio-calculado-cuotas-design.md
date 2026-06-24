# Precio calculado real para el alta/actualización en Mercado Libre

**Fecha:** 2026-06-24
**Estado:** Diseño en revisión

## Problema

Hoy, al publicar un producto en Mercado Libre, el precio se fija como `costo * 5`
(constante `MULTIPLICADOR_PRECIO_ML`). Es un placeholder: no refleja el PVP real del
canal ML, que el sistema ya sabe calcular (motor `calcularPrecioCanalConEnvio`,
incorporando márgenes, comisión, IVA y costo de envío). Se quiere publicar el **precio
final real** del canal.

### Circularidad aparente y por qué NO aplica

Para calcular el precio final hacen falta la **comisión** y el **costo de envío**. A
primera vista eso exige que la publicación (MLA) ya exista. Sin embargo, **ninguna de
las dos consultas necesita el ítem publicado**:

- **Comisión** → `GET /sites/MLA/listing_prices` es un **calculador read-only**: se
  consulta con `category_id` + `price` + `listing_type_id` + `logistic_type`
  (+ `shipping_mode`, `billable_weight`). No lleva `item_id`.
- **Envío** → `calcularCostoEnvioInterno` ya tiene un **fallback con `dimensions=`**
  (`GET /users/{userId}/shipping_options/free?dimensions=...`) que no necesita `item_id`:
  alcanza con dimensiones del paquete + categoría + listing_type + logistic_type + zip
  del vendedor — datos disponibles antes de publicar.

### Restricción de ML que descarta el "crear barato y actualizar después"

Según la doc de ML (*Precios de productos*), **desde el 18/03/2026** las solicitudes que
actualizan **solo el campo `price`** vía `PUT /items` se **rechazan con 400**, y si
`price` viaja junto a otros atributos, **se ignora** (warning). La API que lo reemplaza
(`POST /items/{id}/prices/standard`) **aún no está disponible**. El sistema actualiza
precios justamente con `PUT /items` (`updateItemPrice`). Por lo tanto, un flujo de dos
pasos (crear con semilla → actualizar precio) queda expuesto a esta restricción.

**Conclusión:** calcular el precio final ANTES de crear y publicar en **un solo POST**.

## Objetivo (Fase 1)

Publicar en ML el **PVP real del canal** (mismo que el monitor de precios), calculado de
forma iterativa con comisión + envío **sin depender del ítem**, redondeado a entero,
**para contado**. Aplica al **alta** y a la **actualización**.

Las **cuotas (campañas "Ahora")** quedan fuera de Fase 1 — ver "Fase 2" al final.

## Decisiones de diseño (confirmadas)

| Tema | Decisión |
|------|----------|
| Fuente del precio | PVP del canal ML (`calcularPrecioCanalConEnvio`) |
| Redondeo | A entero, sin centavos (`setScale(0, HALF_UP)`) |
| Momento del cálculo | **Antes** de crear el ítem (comisión y envío no requieren MLA) |
| Publicación | Un solo `POST /items` con el precio final (sin segundo PUT) |
| Alcance | Alta y actualización |
| Fallo de cálculo | **Abortar** con error claro (no publicar precio incorrecto) |
| Cuotas | Fase 1 = solo contado. Campañas "Ahora" → Fase 2 |

## Invariante: la tabla `mlas` se sigue poblando como hoy

El cálculo previo del precio (paso 2 del alta) es **solo para decidir el `price` del POST**
y **no escribe nada en la tabla `mlas`**. La persistencia de `mlas.comisionPorcentaje` y
`mlas.precioEnvio` ocurre **únicamente** en el post-alta, con los métodos actuales
(`obtenerCostoVenta(mlaCode)` y `calcularCostoEnvioGratis(mlaCode)`) sobre el MLA ya creado,
**sin cambios de comportamiento**. El cálculo de envío y comisión que hoy alimenta el
monitor de precios y los recálculos **no se modifica**.

Si se comparte lógica entre el cálculo previo (sin MLA) y el actual (por MLA), será
únicamente cálculo **puro** del PVP estabilizado (sin acceso a BD), garantizando con tests
de regresión que el flujo por-MLA produce resultados idénticos a los actuales. Alternativa
aceptable: implementar el cálculo previo como código separado, sin tocar esos métodos.

## Flujo del alta (Fase 1)

1. **Predecir categoría** (predictor actual) y tomar `listing_type_id`, `logistic_type`,
   `shipping_mode`, zip del vendedor y dimensiones/peso del paquete ML del producto.
2. **Bucle iterativo SIN ítem** (reutiliza la lógica de `calcularCostoEnvioGratis`,
   refactorizada para no depender del MLA):
   - PVP del motor con `(productoId, canalMlId, cuotas = 0, costoEnvio)`.
   - Comisión: `listing_prices` con la categoría + PVP actual → `meli_percentage_fee`.
   - Envío: `shipping_options/free?dimensions=...` con el PVP actual (o tier fijo si el
     PVP no supera el umbral de envío gratis).
   - Repetir hasta estabilizar el envío (tope `MAX_ITERACIONES = 10`, con la misma
     detección de oscilación que hoy).
3. **Redondear** el PVP final a entero.
4. Si en cualquier punto el motor no puede calcular el PVP (sin márgenes/canal/costo) →
   **abortar** con `"no se pudo calcular el precio del canal ML: <motivo>"`.
5. **Crear** la publicación (`POST /items`) con el precio final, stock 0, imágenes,
   descripción y demás (como hoy). Pausar si el producto está inactivo.
6. **Asociar MLA** al producto y persistir comisión/envío calculados (para el monitor).

## Flujo de la actualización (Fase 1)

`actualizarItemEnMl` recalcula el precio con el mismo procedimiento previo y lo aplica.
Mientras la API `prices/standard` no esté disponible, el PUT de precio se mantiene como
hoy (`actualizarPrecioItemConDeteccionVariaciones`), con la salvedad conocida de que ML
puede rechazarlo/ignorarlo en ítems con automatización de precios activa (se reporta como
advertencia). El alta nueva no sufre esto porque publica el precio en el POST.

## Cambios por capa

### Backend

- **`MercadoLibreService`**:
  - Extraer del bucle de `calcularCostoEnvioGratis` un método que calcule el **PVP final
    estabilizado a partir de datos del producto** (categoría, listing_type, logistic_type,
    zip, dimensiones), **sin requerir el MLA** — usando `listing_prices` (comisión) y
    `shipping_options/free?dimensions=` (envío). El método actual por-MLA pasa a ser un
    wrapper que arma esos datos desde el ítem ya publicado (para recálculos posteriores).
  - `crearItemEnMl` / `crearItemEnMlCore`: el precio deja de ser `costo * 5`; se inyecta
    el **PVP final** calculado. Si no se puede calcular → `ResultadoAltaMl.error(...)`.
  - `actualizarItemEnMl` / `actualizarItemEnMlCore`: ídem.
  - `MULTIPLICADOR_PRECIO_ML`: se elimina (no hay fallback; se aborta ante fallo).
  - Helper de redondeo `pvp → entero` (`setScale(0, HALF_UP)`), reutilizable.
- Confirmar en implementación qué devuelve `PrecioCalculadoDTO.pvp()` (inflado vs sin
  inflar) para subir a ML el valor de venta correcto del canal.

### Frontend

Sin cambios en Fase 1 (no hay select de cuotas todavía; se publica a contado). El checkbox
"Sincronizar con Mercado Libre" sigue igual.

## Testing (Fase 1)

- `crearItemEnMlCore` / `actualizarItemEnMlCore`: el precio publicado es el PVP calculado
  (no `costo * 5`); aborta si el PVP es nulo.
- Redondeo a entero: casos `.49` / `.50` / `.99`.
- Cálculo de precio sin MLA: el bucle converge usando `dimensions` y `listing_prices`
  (mockeando las respuestas de red), e incluye la detección de oscilación.
- El POST de creación incluye `price` = PVP final.

## Fase 2 (futura): cuotas con campañas "Ahora"

Una vez validadas las campañas "Ahora" habilitadas en la cuenta:

- **Frontend**: select de cuota bajo "Sincronizar con Mercado Libre" (patrón de Nube),
  con Contado + las campañas disponibles. Default Contado.
- **Backend**: `MlExportRequestDTO` suma `cuotas`/`campaniaTag`. La cuota elegida se pasa
  como `tags=ahora-N` a `listing_prices` (la comisión pasa a usar `percentage_fee`, que
  incluye `financing_add_on_fee`) y al `POST /items`, de modo que el precio cubra el costo
  real de financiación y el comprador vea las cuotas. El motor incorpora ese costo de
  cuotas en el PVP.

## Envío en el alta (contexto confirmado)

El alta crea el ítem con `shipping.mode = "me2"` y `free_shipping = false`
(`MlItemPayloadBuilder`), y **no fija `logistic_type`** (ML lo infiere). El vendedor opera
**ME2** de forma general. Implicancias para el cálculo previo:

- El **costo de envío solo recae sobre el vendedor cuando el PVP supera el umbral de envío
  gratis** (ahí ML obliga `mandatory_free_shipping`). Si el PVP queda por debajo, el envío
  lo paga el comprador y no afecta el precio. El bucle ya distingue ambos casos (API ML vs
  tiers fijos según el umbral), por lo que esta lógica se conserva.
- Para `listing_prices` y `shipping_options/free` se usa `shipping_mode = me2`. El
  `logistic_type` a asumir en el cálculo previo se toma de `ConfiguracionMl` (configurable),
  con un default razonable de ME2; en recálculos de ítems ya publicados se lee del ítem.

## Riesgos / a validar en implementación

- **Parámetros nuevos de `listing_prices`**: la doc (15/04/2026) marca `logistic_type`,
  `shipping_mode` y `billable_weight` (obligatorio en Argentina) para que el `fixed_fee`
  coincida con lo real. Revisar que la consulta de comisión los envíe (el `billable_weight`
  sale del peso del paquete ML del producto).
- **Disponibilidad de `prices/standard`**: cuando ML la habilite, migrar el PUT de precio
  de la actualización a esa API.
