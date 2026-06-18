# Imágenes en el alta de Tienda Nube — Fase B3 — Diseño

**Fecha:** 2026-06-18
**Estado:** Aprobado
**Sub-proyecto B, Fase B3.** Construye sobre B1 (alta básica) y B2 (categorías).

## Objetivo

Al dar de alta un producto en Tienda Nube (KT HOGAR / KT GASTRO), subirle las imágenes que ya
existen en la carpeta local resueltas por su SKU. B1 creaba el producto sin imágenes; B3 sube las
imágenes vía `POST /products/{id}/images` (base64), después de crear el producto.

## Decisiones tomadas (brainstorming)

| Tema | Decisión |
|---|---|
| Cuántas imágenes | **Todas** las del SKU: la principal `{sku}.{ext}` (posición 1) más las adicionales `{sku}_2.{ext}`, `{sku}_3.{ext}`, … en orden ascendente de N. |
| Convención de nombres | Principal sin sufijo; adicionales con sufijo `_N` (guion bajo + entero ≥ 2). |
| Match | **Case-insensitive** (igual que la resolución actual). Ante varias extensiones de un mismo "slot", gana la prioridad existente (jpg > jpeg > png > gif > webp > bmp > svg). |
| Sin imagen | El producto se **crea igual** (la imagen es opcional). Se **avisa** en el resumen ("creado sin imagen"). |
| Falla de subida | El producto queda creado; se avisa ("N de M imágenes subidas"); **no revierte** el alta. Un fallo de una imagen no frena las demás. |
| Fuente | Carpeta local `app.imagenes-dir`, leída como **base64** (`attachment`); sin URL pública. |

## Contexto del sistema (verificado)

- **`ImagenService`** (`dominio/imagen/service/ImagenService.java`): resuelve `{sku}.{ext}` desde
  `app.imagenes-dir` con índice cacheado (TTL `app.imagenes-index-ttl-ms`, default 60s),
  case-insensitive (`sku.trim().toLowerCase(Locale.ROOT)`), con prioridad de extensión ante colisión.
  Hoy resuelve **una** imagen por SKU (`resolverArchivoPorSku(sku) → String`).
- **API TN** `POST /products/{id}/images`: acepta `attachment` (base64 del archivo) + `filename`, y
  `position` (orden). No requiere URL pública.
- **Alta a TN** (`TiendaNubeService.crearProductoEnNubeCore`): hoy el `poster` hace `POST /products` y
  **descarta** el body de respuesta (que incluye el `id` del producto creado). Para subir imágenes hay
  que capturar ese `id`.
- **`ResultadoAltaNube`** (record): hoy `(Estado{CREADO,YA_EXISTIA,ERROR}, String motivo)`. No lleva id
  ni advertencias.
- **`ExportNubeResultDTO`** (record): hoy `(int creados, List<String> yaExistian, List<String> errores)`.
- **`NubeRetryHandler.postJson(uri, accessToken, jsonBody)`** ya existe (reutilizable para imágenes).
- **Frontend**: el toast del resumen muestra creados / ya existían / errores. El front no manda imágenes
  (se resuelven 100% en backend por SKU).

## Alcance (Fase B3)

### Backend

**1. `ImagenService.resolverArchivosPorSku(sku)` → `List<String>`** (nuevo)
- Devuelve todos los archivos del SKU ordenados: primero la principal (`{sku}` sin sufijo), luego los
  adicionales (`{sku}_N`) por N ascendente. Lista vacía si no hay ninguno.
- Un archivo pertenece al SKU si su nombre sin extensión, en minúsculas, es exactamente `{sku}` o
  `{sku}_N` con N entero ≥ 2.
- Por cada "slot" (principal y cada `_N`), si hay varias extensiones, aplica la prioridad de extensión
  existente.
- Se apoya en el índice/escaneo de la carpeta ya existente (no escanea disco en cada llamada más de lo
  que ya hace el TTL).

**2. `ImagenService.leerBase64(filename)` → `String`** (nuevo)
- Lee el archivo `{imagenesDir}/{filename}` y devuelve su contenido codificado en Base64. Lanza/propaga
  si el archivo no existe o no se puede leer (el llamador lo cuenta como fallo de esa imagen).

**3. `NubeImagenPayloadBuilder.construir(filename, base64, position)` → `Map<String,Object>`** (nuevo, puro)
- `{ "filename": filename, "attachment": base64, "position": position }`.

**4. Captura del `id` del producto creado** (en `crearProductoEnNubeCore`)
- Tras `poster.apply(uri, body)`, parsear el `id` de la respuesta (`objectMapper.readTree(resp).path("id").asLong()`)
  y devolverlo en `ResultadoAltaNube` (estado CREADO con `productoNubeId`).

**5. `TiendaNubeService.subirImagenesProducto(store, productoNubeId, sku)` → resumen** (nuevo, I/O)
- `resolverArchivosPorSku(sku)`; si vacío → "sin imagen".
- Por cada archivo (en orden, `position` = 1,2,3…): `leerBase64`, armar payload, `postJson("/{storeId}/products/{id}/images", token, body)`.
- Cuenta subidas OK y fallidas (lectura o POST). Un fallo no frena las demás.
- Devuelve un pequeño resultado (subidas, total, sinImagen) que el llamador traduce a advertencia.

**6. Flujo en `crearProductoEnNube` (público)**
- Si el core devuelve CREADO con `productoNubeId`, llamar `subirImagenesProducto(store, id, sku)` y, según
  el resumen, enriquecer `ResultadoAltaNube` con una `advertencia`:
  - sin imagen → "creado sin imagen";
  - todas OK → sin advertencia;
  - parcial → "N de M imágenes subidas".
- La resolución y subida (I/O disco + red) viven acá, fuera del core testeable.

**7. DTOs**
- `ResultadoAltaNube` suma `Long productoNubeId` y `String advertencia` (factory `creado(id)` y un
  `withAdvertencia(adv)` o `creado(id, adv)`); `error`/`yaExistia` quedan con esos campos en null.
- `ExportNubeResultDTO` suma `List<String> advertencias`.
- `NubeExportService`: en CREADO, `creados++`; si `r.advertencia()!=null` → `advertencias.add(etiqueta + ": " + r.advertencia())`.

### Frontend (`productos/page.tsx`, `productosService.ts`)

- `ExportNubeResultDTO` (tipo TS) suma `advertencias: string[]`.
- El toast del resumen del alta muestra también las advertencias (sección "Avisos", además de creados /
  ya existían / errores).

## Manejo de errores

- Producto sin imágenes → CREADO con advertencia "creado sin imagen" (no es error).
- Falla de lectura o de POST de una imagen → se cuenta como no subida; el alta sigue CREADO con
  advertencia "N de M imágenes subidas". No revierte el producto.
- Las llamadas a TN pasan por `NubeRetryHandler` (retry + rate limit).

## Testing (sin llamadas reales a Tienda Nube)

- **`resolverArchivosPorSku`** con carpeta temporal: principal + `{sku}_2`/`{sku}_3` → orden correcto;
  match case-insensitive; SKU sin archivos → lista vacía; no mezcla otros SKUs.
- **`NubeImagenPayloadBuilder`**: filename/attachment/position correctos.
- **Core**: el `poster` devuelve `{"id":5}` → `ResultadoAltaNube` CREADO con `productoNubeId == 5`.
- La subida real (I/O de disco + red contra TN) no se testea unitariamente (consistente con el resto de
  los wrappers de red de `TiendaNubeService`).

## Fuera de alcance (fase siguiente / descartado)

- **B4:** SEO con IA (seo_title / seo_description).
- No re-sube imágenes a productos ya existentes en TN (el alta saltea existentes, igual que B1/B2).
- No edita ni borra imágenes en TN; solo agrega en el alta.
