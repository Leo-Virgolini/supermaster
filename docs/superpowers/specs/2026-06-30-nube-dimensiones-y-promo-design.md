# Peso/dimensiones de Nube editables + precio promocional en tarjetas — Diseño

**Fecha:** 2026-06-30
**Estado:** aprobado para plan

## Objetivo

Dos mejoras sobre Tienda Nube en el modal de producto:

1. **Peso/dimensiones de Nube editables (externalizados):** dejar de mandar el placeholder hardcodeado (`weight 0.050 / depth 8 / width 5 / height 5`) y permitir editarlos. En **alta** arrancan con ese default fijo; en **edición** se traen los valores guardados en Tienda Nube (de la variante); al guardar se mandan a Nube.
2. **Precio promocional en las tarjetas de "Canales de venta":** donde la tarjeta muestra "Precio", si el canal tiene **precio promocional**, mostrarlo también.

## Alcance

Backend (`supermaster-backend`) + frontend (`supermaster-frontend`). **Sin columnas nuevas en BD** (las dims de Nube son transient/externalizadas, mismo patrón que descripción/SEO/atributos ML).

**Fuera de alcance:** reutilizar el "Paquete para envío" de ML (son inputs separados); promo en Mercado Libre (ML no maneja `promotional_price` igual); variantes/atributos de Nube; persistir las dims en la BD del sistema.

## Global Constraints

- Backend Java 25 / Spring Boot 4; `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`). Jackson 3 (`node.path("x").asString(null)`).
- Frontend Next.js/React/TS; `npx tsc --noEmit` exit 0.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos (hay WIP en el working tree que NO se toca), nunca `-A` ni `.superpowers/`.
- **Records DTO:** `EstadoCanalDTO` y `DatosCanalDTO` son `record`; agregar componentes rompe los constructores posicionales (incluidos los factory `noPublicado()`/`ofError()` y los tests) → actualizar TODOS los call sites.
- Default fijo de dims Nube: **`weight = "0.050"`, `depth = "8.00"`, `width = "5.00"`, `height = "5.00"`** (los actuales del builder).

## Estado actual

- `NubeProductoPayloadBuilder.construir` hardcodea la variante: `weight "0.050"`, `depth "8.00"`, `width "5.00"`, `height "5.00"`, `stock ""` (líneas 44-48).
- `NubeEstadoParser` ya lee de `variants[0]`: `price`, `weight`, `height`, `width`, `depth` (los formatea en `EstadoCanalDTO.peso`/`dimensiones`), pero **no** lee `promotional_price` ni expone los valores crudos.
- `EstadoCanalDTO` (record): `publicado, estado, precio, stock, peso, dimensiones, error` + factories `noPublicado()`/`ofError()`.
- `DatosCanalDTO` (record): datos editables del canal pre-cargados (descripciones, SEO, mlCategory, mlAtributos, mlaResuelto).
- El modal ya externaliza descripción/SEO de Nube: se leen en el `.then` de `getEstadoPublicacionAPI` y se mandan en `exportarProductosANubeAPI`. El export setea `@Transient` por tienda en `NubeExportService` (patrón `descripcionNube`/`equipamientoGastro`).
- La tarjeta de canal (sección unificada "Canales de venta") muestra `canal.precio` vía `renderEstadoBody`.

## Parte 1 — Peso/dimensiones de Nube editables

### Lectura (edición)
- `NubeEstadoParser`/`leerNubePanel` exponen los **valores crudos** de la variante: `weight`, `depth`, `width`, `height` (strings tal como vienen de Nube).
- `DatosCanalDTO` suma 4 campos: **`nubePeso`, `nubeProfundidad`, `nubeAncho`, `nubeAlto`** (String, nullable). Se completan desde la tienda **HOGAR**; si HOGAR no tiene producto, desde **GASTRO**; si ninguna, quedan null.
- Frontend: en el `.then` de `getEstadoPublicacionAPI`, si vienen valores → pre-cargan los inputs; si null → se usa el default fijo.

### Edición de inputs (frontend)
- 4 inputs nuevos (Peso kg, Profundidad/Largo cm, Ancho cm, Alto cm) en un bloque **"Paquete de envío · Tienda Nube"**, visible cuando `subirKtHogar || subirKtGastro`.
- **Alta** (`producto == null`): default **0.050 / 8.00 / 5.00 / 5.00**.
- Estados nuevos: `nubePeso`, `nubeProfundidad`, `nubeAncho`, `nubeAlto` (strings).

### Envío (al guardar)
- `exportarProductosANubeAPI` pasa las 4 dims (en `DestinoNube` o como campos del request) al backend.
- `Producto` suma `@Transient` `nubePeso`/`nubeProfundidad`/`nubeAncho`/`nubeAlto`; `NubeExportService` los setea antes de construir el payload (igual que `descripcionNube`).
- `NubeProductoPayloadBuilder` usa esos valores en la variante (`weight`/`depth`/`width`/`height`) en vez de los hardcodeados; si alguno viene null/blank, cae al default fijo correspondiente.
- Se mandan **iguales a ambas tiendas** (HOGAR y GASTRO).

## Parte 2 — Precio promocional en las tarjetas

- `EstadoCanalDTO` suma componente **`promo`** (`BigDecimal`, nullable). Actualizar `noPublicado()`/`ofError()` (promo = null) y todos los `new EstadoCanalDTO(...)` (ML/Nube/Dux parsers + tests).
- `NubeEstadoParser` lee `variants[0].promotional_price` → `promo` (null si vacío/0). `MlEstadoParser`/`DuxEstadoParser` pasan `promo = null` (no aplica).
- Frontend: el tipo `EstadoCanal` suma `promo: number | null`; `renderEstadoBody` muestra, junto a "Precio", el **precio promocional** si `promo != null` (p. ej. precio de lista + "Promo: $X"). Aplica a la tarjeta de Nube; en ML/Dux `promo` es null y no se muestra.

## Manejo de errores / bordes

- **Alta sin Nube tildado:** el bloque de dims no se muestra (o se muestra deshabilitado, según `subirKt*`); igual el default se manda si se exporta a Nube.
- **Edición donde Nube no tiene el producto:** dims null → inputs con el default fijo.
- **Dims null/blank al exportar:** el builder cae al default fijo (nunca manda vacío).
- **HOGAR vs GASTRO con dims distintas:** se lee de HOGAR (o GASTRO) y se mandan iguales a ambas; se acepta esa simplificación (es el mismo paquete del producto).
- **`promo` vacío/0 en Nube:** se trata como null (no se muestra).

## Testing

- **Backend `mvn -o test` verde:**
  - `NubeEstadoParser`: parsea `promotional_price` → `promo`; expone weight/depth/width/height crudos. Test con variante con y sin promo/dims.
  - `EstadoPublicacionServiceTest`: ajustar a la nueva firma de `EstadoCanalDTO`/`DatosCanalDTO` (componentes nuevos); aserción de `nubePeso`/promo donde aplique.
  - `NubeProductoPayloadBuilder` (si tiene test): con dims provistas usa esas; con null usa el default fijo.
- **Frontend `npx tsc --noEmit` exit 0.** Verificación manual:
  - Alta con Nube tildado → inputs en 0.050/8/5/5; se mandan.
  - Editar un producto publicado en Nube → los inputs traen las dims de Nube; cambiarlas y guardar → se actualizan en Nube.
  - Producto con precio promocional → la tarjeta de Nube muestra precio + promo.
