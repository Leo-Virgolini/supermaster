# D1 — Actualizar al editar (Tienda Nube + Mercado Libre): upsert básico

**Fecha:** 2026-06-19
**Estado:** Diseño aprobado, pendiente de plan de implementación.

## Objetivo

Cuando se **edita** un producto y se marca un canal de venta (Tienda Nube /
Mercado Libre), el sistema debe **actualizar** la publicación existente en ese
canal en lugar de intentar darla de alta de nuevo. Hoy, en edición, se llama al
mismo flujo de alta que en creación, el cual **saltea** el producto si ya existe
(`YA_EXISTIA`), de modo que nunca se reflejan los cambios.

Esta es la **fase D1**: actualiza **título, precio y descripción**, e implementa
el patrón **upsert** (crear si no existe, actualizar si existe) y los **labels**
"Actualizar en …" en el modal de edición. Imágenes y categorías quedan para una
fase posterior (D2).

## Alcance

### Incluye (D1)
- Patrón **upsert** en los export services de Nube y ML: por SKU, decidir crear
  o actualizar según el producto exista ya en el canal.
- **Tienda Nube:** actualizar nombre (título Nube), descripción y precio de la
  variante de una publicación existente.
- **Mercado Libre:** actualizar título (solo si no tuvo ventas), descripción y
  precio de un ítem existente.
- **Frontend:** labels "Actualizar en …" en el modal de edición (los de Nube/ML;
  Dux ya lo hace).
- **Reporte:** los toasts distinguen creados / actualizados / errores + avisos.

### NO incluye (queda para D2)
- Actualización de **imágenes** en cualquier canal.
- Actualización de **categorías** en cualquier canal.
- Cambios de variaciones, atributos/ficha técnica, garantía.
- DUX: ya hace upsert nativo (`/item/nuevoItem` por `cod_item`), no se toca.

## Contexto del código existente

### Frontend
- `supermaster-frontend/src/app/productos/page.tsx`, handler
  `handleGuardarEdicion` (~líneas 644-741): tras guardar la edición, si están
  marcados los checkboxes de canal dispara `exportarProductosADuxAPI`,
  `exportarProductosANubeAPI`, `exportarProductosAMlAPI`. **Usa las mismas APIs
  que el alta**; no hay distinción crear/editar a nivel frontend.
- El label de Dux ya es condicional ("Actualizar en Dux" en edición). Los de
  Nube/ML son fijos ("Subir a KT HOGAR (Nube)", "Subir a KT GASTRO (Nube)",
  "Subir a Mercado Libre").

### Tienda Nube — infraestructura ya disponible
- `apis/nube/service/TiendaNubeService.java`:
  - `buscarProductoPorSku(sku, storeName)` → `JsonNode` con `id`, `variants[]`
    (cada uno con su `id`, `sku`, `price`, `promotional_price`), `name`,
    `description`, `categories`. Devuelve `null` si no existe (404).
  - `actualizarPrecioVariante(storeName, productId, variantId, price,
    promotionalPrice)` → `boolean`. **Ya existe**; hace `PUT
    /products/{id}/variants/{variantId}`.
  - `NubeRetryHandler.putJson(uri, accessToken, jsonBody)` y
    `patchJson(uri, accessToken, jsonBody)` → `String`. **Ya existen**.
  - El alta es `crearProductoEnNube(...)` que devuelve un `ResultadoAltaNube`
    con estado `CREADO` / `YA_EXISTIA` / `ERROR`.
- `apis/nube/service/NubeExportService.java`, método `exportar(request)`
  (`@Transactional(readOnly = true)`): por cada producto × tienda, valida canal
  y precio (`ProductoCanalPrecio`, debe existir y no estar obsoleto) y llama a
  `crearProductoEnNube`. Acumula `creados` / `yaExistian` / `errores` /
  `advertencias` en `ExportNubeResultDTO`.

### Mercado Libre — infraestructura ya disponible
- `apis/ml/service/MercadoLibreService.java`:
  - `getItemByMLA(itemId)` → objeto `Producto` (de ML) con `id`, `title`,
    `description`, `price`, etc. (incluye `sold_quantity` en el JSON del ítem).
  - `updateItemPrice(itemId, price)` → `boolean`; hace `PUT /items/{itemId}`
    con `{"price": <entero>}`. **Ya existe.**
  - `buscarMlaPorSku(...)` y `isConfigured()` preexistentes.
  - `MlRetryHandler.putJson(uri, tokenSupplier, jsonBody)` → `String`. **Ya
    existe** (también `postJson`, `postMultipart`, `get`, `delete`).
  - El alta es `crearItemEnMl(...)` / `crearItemEnMlCore(...)`. Tras el alta,
    `MlaService.asegurarYAsociar(productoId, itemId, mlau)` asocia el `Mla` al
    producto (`producto.getMla()` → `Mla`, con `Mla.getMla()` = código MLA).
- `apis/ml/service/MlExportService.java`: `exportar(request)` NO es
  `@Transactional`; corre el alta vía `self.altaConProductoCargado(productoId)`
  (`@Transactional readOnly`) y un post-alta best-effort fuera de transacción.

## Diseño

### Principio: upsert en el backend

El frontend **no cambia su llamada** en edición (sigue invocando las APIs de
export). El backend, por SKU, detecta si el producto ya existe en el canal y
decide crear o actualizar. Razón: evita un flag "modo edición" que pueda
desincronizarse del estado real del canal, y replica el comportamiento que Dux
ya tiene de forma nativa.

### Tienda Nube

Nuevo método en `TiendaNubeService`:

```
ResultadoAltaNube actualizarProductoEnNube(String storeName, Producto producto,
                                           BigDecimal pvp, BigDecimal pvpInflado)
```

(Reutiliza el record `ResultadoAltaNube` agregando/contemplando un estado
`ACTUALIZADO`; ver "Tipos de resultado".)

Pasos:
1. `JsonNode existente = buscarProductoPorSku(sku, storeName)`.
   - Si es `null` → devuelve estado que indica "no existe" para que el caller
     haga el alta (ver flujo en `NubeExportService`). En la práctica el caller
     decide antes de llamar a este método (ver más abajo), de modo que este
     método asume que el producto existe; si por una condición de carrera no
     existe, devuelve `ERROR` con motivo "no encontrado al actualizar".
2. `long productId = existente.path("id").asLong(0)`.
3. Ubicar la variante por SKU dentro de `existente.path("variants")` y tomar su
   `id` (`long variantId`).
4. Construir el cuerpo del `PATCH /products/{productId}`:
   - `name`: `{ "es": <tituloNube del producto> }` (solo si `tituloNube` no es
     nulo/vacío).
   - `description`: el texto de descripción que hoy usa el alta (mismo origen
     que `crearProductoEnNube`; en D1 es el `tituloNube` u origen equivalente —
     se debe usar exactamente la misma fuente que el alta para consistencia).
   - Enviar con `retryHandler.patchJson(...)`.
5. Precio: `actualizarPrecioVariante(storeName, productId, variantId,
   pvp.toString(), pvpInflado != null ? pvpInflado.toString() : null)`.
6. Si todo ok → estado `ACTUALIZADO`. Si el PATCH o el precio fallan → `ERROR`
   con motivo.

`NubeExportService.exportar` cambia su bucle interno (por producto × tienda),
manteniendo todas las validaciones actuales (canal existe, precio existe y no
obsoleto):

```
JsonNode existente = tiendaNubeService.buscarProductoPorSku(sku, tienda);
ResultadoAltaNube r;
if (existente != null) {
    r = tiendaNubeService.actualizarProductoEnNube(tienda, producto, pvp, pvpInflado);
} else {
    r = tiendaNubeService.crearProductoEnNube(tienda, producto, pvp, pvpInflado, arbol);
}
switch (r.estado()) {
    case CREADO     -> creados++;        // + advertencia si corresponde
    case ACTUALIZADO-> actualizados++;   // + advertencia si corresponde
    case YA_EXISTIA -> yaExistian.add(etiqueta);  // (camino legacy, no debería darse en upsert)
    case ERROR      -> errores.add(etiqueta + ": " + r.motivo());
}
```

> Nota: con el upsert, `buscarProductoPorSku` se llama una vez por SKU×tienda
> aquí, y `crearProductoEnNube` internamente vuelve a chequear existencia. Es
> una llamada extra aceptable en D1 (el volumen por edición es 1 SKU). No se
> optimiza prematuramente.

### Mercado Libre

Nuevo método en `MercadoLibreService`:

```
ResultadoAltaMl actualizarItemEnMl(Producto producto, double precio)
```

(Reutiliza el record `ResultadoAltaMl` contemplando estado `ACTUALIZADO`; ver
"Tipos de resultado".)

Pasos:
1. Obtener el código MLA: `producto.getMla()` (si no nulo, `getMla().getMla()`);
   si el producto no tiene `Mla` asociado, intentar `buscarMlaPorSku`. Si no se
   puede determinar el ítem → devolver señal "no existe" para que el caller haga
   el alta.
2. Leer el ítem: `getItemByMLA(mla)` para conocer `sold_quantity`.
3. **Título:** si `sold_quantity == 0` y `tituloMl` no vacío →
   `PUT /items/{mla}` con `{"title": "<tituloMl>"}` vía
   `retryHandler.putJson(...)`. Si `sold_quantity > 0` → **no** actualizar
   título y agregar aviso "título no actualizado (la publicación tuvo ventas)".
4. **Descripción:** `PUT /items/{mla}/description` con
   `{"plain_text": "<descripción texto plano>"}` (mismo builder de descripción
   que el alta, `MlDescripcionBuilder`, texto plano sin HTML).
5. **Precio:** `updateItemPrice(mla, precio)`.
6. Devolver `ACTUALIZADO` (+ avisos) o `ERROR` con motivo.

`MlExportService.exportar` cambia: por SKU, si el producto ya tiene ítem en ML
(MLA asociado o hallado por `buscarMlaPorSku`) → `actualizarItemEnMl`; si no →
alta actual. El post-alta best-effort (asociar MLA, comisión, envío) **solo
aplica al camino de creación**; la actualización no lo ejecuta.

⚠️ **Riesgo a validar en implementación (precio ML):** ML introdujo (marzo 2026)
una restricción por la cual un `PUT /items/{id}` que envía **únicamente** `price`
puede ser rechazado. `updateItemPrice` hace exactamente eso. Durante la
implementación se debe verificar el comportamiento real; si la restricción
aplica, usar la **API de precios** (`/items/{id}/prices`) o incluir el precio
junto con otros campos en un mismo PUT. El plan de implementación debe incluir un
paso explícito para validar esto y la rama alternativa.

### Frontend — labels en edición

En `productos/page.tsx`, en el modal de creación/edición, los labels de los
checkboxes de Nube y ML pasan a ser condicionales según se esté editando
(`editandoProductoId` truthy):
- "Subir a KT HOGAR (Nube)" → "Actualizar en KT HOGAR (Nube)"
- "Subir a KT GASTRO (Nube)" → "Actualizar en KT GASTRO (Nube)"
- "Subir a Mercado Libre" → "Actualizar en Mercado Libre"

Dux ya muestra "Actualizar en Dux" en edición; se sigue el mismo patrón. En alta
los labels siguen diciendo "Subir a …".

### Reporte al usuario

- `ExportNubeResultDTO` agrega el contador `actualizados` (junto a `creados`,
  `yaExistian`, `errores`, `advertencias`).
- El resultado de ML agrega igualmente la noción de "actualizados" (junto a
  creados/errores/advertencias del DTO de resultado de ML).
- El frontend (`reportarExportToast` / equivalentes) muestra, según corresponda:
  "N creados, M actualizados, K errores" + los avisos (p. ej. "ML: título no
  actualizado por tener ventas").

## Tipos de resultado

Para no romper los consumidores actuales, se contempla un estado `ACTUALIZADO`
en los records de resultado:
- Tienda Nube: `ResultadoAltaNube` (hoy `CREADO`/`YA_EXISTIA`/`ERROR`) suma
  `ACTUALIZADO`. El `enum`/sealed de estado se extiende; los `switch` existentes
  deben cubrir el nuevo caso.
- Mercado Libre: `ResultadoAltaMl` (hoy con `creado(itemId, mlau)` etc.) suma una
  variante/factory `actualizado(itemId, advertencia)` y su estado `ACTUALIZADO`.

Los DTOs de respuesta HTTP (`ExportNubeResultDTO`, el de ML) suman el contador
`actualizados`. Los DTOs de **request** (`ExportNubeRequestDTO`,
`MlExportRequestDTO`) **no cambian** (el upsert se infiere en backend).

## Manejo de errores

- Toda llamada de actualización va envuelta en try/catch como el alta; un fallo
  de un SKU no frena el resto (se acumula en `errores`).
- Nube: un fallo del `PATCH` o del precio → `ERROR` para ese SKU×tienda con
  motivo legible.
- ML: el título con ventas no es error, es **aviso**; un fallo de
  descripción/precio sí es error. Si el ítem no se puede leer (`getItemByMLA`
  null) en el camino de actualización, se reporta error "no se pudo leer el ítem
  para actualizar".
- Se mantiene la transaccionalidad actual: `NubeExportService.exportar` sigue
  `@Transactional(readOnly = true)` (solo lee entidades, las llamadas HTTP son
  externas); `MlExportService.exportar` sigue sin `@Transactional`, con la carga
  del producto en su método `self.*` readOnly.

## Pruebas

Siguiendo el patrón de C1/B-fases (núcleos testeables con lambdas, sin red):
- **Tienda Nube:** test del armado del cuerpo `PATCH` (name/description) y de la
  selección de la variante por SKU desde un `JsonNode` de ejemplo; test del
  ramo upsert en `NubeExportService` (existe → actualizar; no existe → crear),
  con un `TiendaNubeService` simulado.
- **Mercado Libre:** test de `actualizarItemEnMl` con `sold_quantity == 0`
  (actualiza título) vs `> 0` (saltea título + aviso), con lectura de ítem y
  llamadas HTTP simuladas; test del ramo upsert en `MlExportService`.
- **Reporte:** test de que los contadores `creados`/`actualizados`/`errores` se
  acumulan correctamente.

## Archivos afectados (resumen)

- `apis/nube/service/TiendaNubeService.java` — nuevo `actualizarProductoEnNube`.
- `apis/nube/service/NubeExportService.java` — ramo upsert + contador
  `actualizados`.
- `apis/nube/dto/ExportNubeResultDTO.java` — campo `actualizados`.
- (Nube) record de estado `ResultadoAltaNube` — estado `ACTUALIZADO`.
- `apis/ml/service/MercadoLibreService.java` — nuevo `actualizarItemEnMl`.
- `apis/ml/service/MlExportService.java` — ramo upsert.
- (ML) DTO de resultado + record `ResultadoAltaMl` — `actualizados`/`ACTUALIZADO`.
- `supermaster-frontend/src/app/productos/page.tsx` — labels condicionales +
  reporte de "actualizados".

## Notas de seguimiento (D2)

D2 cubrirá la actualización de **imágenes** y **categorías** en ambos canales,
reusando los builders/resolución del alta (multipart de imágenes en ML, árbol de
categorías en Nube), aplicados sobre la publicación existente.
