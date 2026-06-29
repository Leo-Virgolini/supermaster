# Review Fix 3 — Backend: 3 arreglos de robustez

## Fix 1 — DuxService.obtenerProductoPorCodigo lanza ante fallo

**Archivo:** `apis/dux/service/DuxService.java`

Cambio: `response == null` → lanza `RuntimeException("No se pudo consultar el ítem en Dux (sin respuesta)")`.
Catch de parseo → lanza `RuntimeException("No se pudo parsear la respuesta de Dux: ...", e)`.
`null` ahora significa exclusivamente "no encontrado" (results vacíos / sin match exacto).

Callers no tocados: `DuxController` (null→404, fallo→500 vía handler global), `verificarSkuLibreEnDux` (su try/catch ya convierte excepción en ConflictException), `estadoDux` (su try/catch ya devuelve `EstadoCanalDTO.ofError()`).

## Fix 2 — mlCategoryNombre resuelto en EstadoPublicacionService.leer

**Archivos:** `apis/ml/service/MercadoLibreService.java`, `dominio/producto/estado/EstadoPublicacionService.java`

- `obtenerCategoriaPath` pasó de package-private a `public`.
- En `leer`: se calcula `mlCatId = MlDatosParser.categoryId(mlItem)` y luego `mlCatNombre = (mlCatId != null && !blank) ? mercadoLibreService.obtenerCategoriaPath(mlCatId) : null`. Se pasan ambos al `DatosCanalDTO` en lugar de (id, null).
- 1 GET extra solo cuando hay categoría; si falla devuelve null sin romper flujo.

## Fix 3 — NubeEstadoParser: parseo defensivo del precio

**Archivo:** `dominio/producto/estado/NubeEstadoParser.java`

Antes: `new BigDecimal(precioStr)` lanzaba `NumberFormatException` si precioStr era `""` o no numérico.
Ahora: guarda `null` silenciosamente con try/catch `NumberFormatException`.

## Verificación

- `mvn -o -q compile` → BUILD SUCCESS (sin errores, solo warnings de Lombok/Unsafe esperados).
- `mvn -o -q -Dtest=NubeSeoParserTest,EstadoPublicacionServiceTest,MlDatosParserTest,DuxEstadoParserTest test` → verde (sin failures).
- En `EstadoPublicacionServiceTest`: el stub de `leerItemRaw` no trae `category_id`, por lo que `mlCatId` es null y `obtenerCategoriaPath` no se llama — no se necesitó stub adicional.

## Concerns

Ninguno. Los cambios son mínimos, no tocan callers existentes y los tests pasan.
