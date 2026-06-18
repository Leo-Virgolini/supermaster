# Alta de producto en Mercado Libre — Fase C1 (alta + descripción + imágenes) — Diseño

**Fecha:** 2026-06-18
**Estado:** Aprobado
**Sub-proyecto C, Fase C1.** Primera fase de la integración de publicación a Mercado Libre. Reutiliza
infraestructura ML existente (`MlRetryHandler`, OAuth, `MercadoLibreService`) y el patrón de exportación
de Tienda Nube (`NubeExportService` → endpoint → builders + núcleo testeable).

## Objetivo

Publicar un producto en Mercado Libre (cuenta del seller autenticado, sitio MLA) cuando se marca el
canal correspondiente al guardar el producto. C1 cubre: alta del ítem (título, categoría por predictor,
precio, condición, cantidad, envío, SKU, marca), **imágenes** (obligatorias para publicación clásica) y
**descripción** (texto plano). Fases siguientes: ficha técnica completa (C2), etc.

## Decisiones tomadas (brainstorming)

| Tema | Decisión |
|---|---|
| Canal | **Una sola cuenta** ML (seller autenticado, sitio `MLA`). Front: un checkbox "Subir a Mercado Libre". |
| Título | `producto.tituloMl` (obligatorio; si falta o es muy corto → error). |
| Categoría | **Predictor**: `GET /sites/MLA/domain_discovery/search?q={tituloMl}&limit=1` → primer `category_id`. |
| Precio / moneda | **`costo × 5`**, `currency_id = "ARS"`. |
| Condición | Nuevo: `attributes:[{id:"ITEM_CONDITION", value_id:"2230284"}]`. |
| Cantidad | Intento `available_quantity = 0`; si ML lo rechaza por stock, reintento con `1` y lo informo como aviso. |
| Modalidad / tipo | `buying_mode = "buy_it_now"`, `listing_type_id = "gold_special"` (clásica). |
| Envío | Mercado Envíos: `shipping = {mode: "me2"}` (ML completa la adopción automáticamente — warning 4029). |
| SKU | `attributes:[{id:"SELLER_SKU", value_name: sku}]`. |
| Marca | `attributes:[{id:"BRAND", value_name: marca.nombre}]` (si el producto tiene marca). |
| Imágenes | **Obligatorias** (error 173 para `gold_special`). Subo cada archivo del SKU por multipart y las vinculo. |
| Descripción | Texto plano (`CARACTERÍSTICAS` + bullets `•`, saltos con `\n`), vía `POST /items/{id}/description`. |
| No duplicar | Chequeo por SKU (`buscarMlaPorSku`); si existe → "ya existía", no republico. |
| Producto sin imágenes | Se reporta como **error** ("sin imágenes — obligatorias para publicación clásica"), se sigue con los demás. |
| EAN / variaciones / garantía / género | **Fuera de C1** (no hay campo EAN; el resto en fases posteriores). |
| Atributo obligatorio faltante | ML rechaza (cause `type:error`) → se reporta el mensaje de ML y se sigue. |

## Contexto del sistema (verificado)

- **`MlRetryHandler`** (`apis/ml/MlRetryHandler.java`): `get`, `postForm`, `postJson(uri, tokenSupplier,
  json)`, `putJson`, `delete`. Bearer + refresh OAuth automático (401), retry 429/5xx/conflict, rate limit.
  **Falta un método multipart** para subir imágenes.
- **`MercadoLibreService`**: `buscarMlaPorSku(sku) → MlaPorSku(mla, mlau)` (busca en ML por `SELLER_SKU`),
  `getUserId()`, `isConfigured()`. Token vía `Supplier<String>` con refresh.
- **`ImagenService`** (`dominio/imagen`): `resolverArchivosPorSku(sku) → List<String>` (principal `{sku}` +
  `{sku}_N`, de B3) y `leerBase64`. Para multipart hace falta acceso a los **bytes/Path** del archivo.
- **`Producto`**: `tituloMl`, `costo`, `marca`(→nombre), `material`, `tipo`, dimensiones
  (`capacidad`/`largo`/`ancho`/`alto`/`diamboca`/`diambase`/`espesor`), aptos, `sku`. **No** tiene EAN.
- **API ML** (sitio MLA):
  - Predictor: `GET /sites/MLA/domain_discovery/search?q=…&limit=1`.
  - Crear ítem: `POST /items` (title, category_id, price, currency_id, available_quantity, buying_mode,
    listing_type_id, condition, attributes[], pictures[], shipping). Devuelve `{id: "MLA…"}`.
  - Imágenes: `POST /pictures/items/upload` (multipart `file=@…`) → `{id: "…"}`; luego en el ítem
    `pictures:[{id}]`.
  - Descripción: `POST /items/{id}/description` body `{plain_text: "…"}` (después de crear el ítem).
  - Validaciones: respuesta 400 con `cause:[{type:"warning"|"error", code, message, references}]`. Solo
    bloquea si hay algún `type:"error"`.
- **Patrón export**: `NubeExportService` + `POST /api/nube/exportar-productos` (permiso
  `INTEGRACIONES_EDITAR`, `@Transactional(readOnly=true)`). Se replica para ML.

## Alcance (Fase C1)

### Backend

**1. `MlRetryHandler.postMultipart(uri, tokenSupplier, filename, bytes)` → String** (nuevo)
- `POST` multipart/form-data con la parte `file` (el contenido del archivo). Mismo manejo de retry/rate
  limit/refresh que los otros métodos. Devuelve el body (JSON con el `id` de la imagen).

**2. `ImagenService` — acceso a bytes** (nuevo)
- `byte[] leerBytes(String filename)` (o reutilizar la lectura existente): los bytes crudos del archivo
  `{imagenesDir}/{filename}`, para el multipart. (Complementa `leerBase64` de B3.)

**3. `MlDescripcionBuilder.construir(Producto) → String`** (nuevo, puro)
- Texto plano: `CARACTERÍSTICAS\n` + bullets `• Dimensiones: …\n`, `• Material: …\n`, `• Aptos: …\n`,
  `• Marca: …\n`. Omite los vacíos. Sin HTML ni negrita; saltos con `\n`. Misma información que
  `NubeDescripcionBuilder` pero en texto plano.

**4. `MlItemPayloadBuilder.construir(Producto, categoryId, price, availableQuantity, pictureIds) → Map`**
(nuevo, puro)
- `{ title: tituloMl, category_id, price, currency_id:"ARS", available_quantity, buying_mode:"buy_it_now",
  listing_type_id:"gold_special", attributes:[ITEM_CONDITION=Nuevo, BRAND(si hay marca), SELLER_SKU=sku],
  shipping:{mode:"me2"}, pictures:[{id:pictureId}…] }`.

**5. Predictor de categoría** (en `MercadoLibreService`): `predecirCategoria(titulo) → String categoryId`
(o null si no hay predicción) usando `domain_discovery/search`.

**6. `MercadoLibreService.crearItemEnMl(Producto) → ResultadoAltaMl`** (creado con `itemId` / yaExistia /
error con motivo):
- Valida `tituloMl` no vacío → si no, error.
- `buscarMlaPorSku(sku)`: si existe → `yaExistia`.
- `resolverArchivosPorSku(sku)`: si vacío → error "sin imágenes (obligatorias para publicación clásica)".
- Sube cada imagen (`postMultipart`) → `pictureIds`. Si falla alguna, se cuenta; si no queda ninguna
  imagen válida → error.
- `predecirCategoria(tituloMl)` → `categoryId`; si null → error "no se pudo predecir categoría".
- Arma payload (`price = costo*5`, `availableQuantity = 0`), serializa, `POST /items`.
  - Parsea la respuesta: si trae `cause` con algún `type:error`, error con esos mensajes. Si el error es
    por stock (cantidad 0 no permitida), reintenta una vez con `availableQuantity = 1` y marca aviso.
- Con el `item_id` creado, `POST /items/{id}/description` (texto plano). Un fallo de descripción no
  revierte el alta; se informa como aviso.
- Núcleo `crearItemEnMlCore(...)` testeable con lambdas (predictor, buscador, subidorImagen, poster,
  posterDescripcion) — sin red, igual que `crearProductoEnNubeCore`.

**7. `ResultadoAltaMl`** (record): `(Estado{CREADO, YA_EXISTIA, ERROR}, String motivo, String itemId,
String advertencia)` con factories análogos a `ResultadoAltaNube`.

**8. `MlExportService.exportar(MlExportRequestDTO) → MlExportResultDTO`** + endpoint
`POST /api/ml/exportar-productos` (`@Transactional(readOnly=true)`, `INTEGRACIONES_EDITAR`):
- `request`: `{ skus: [...] }` (una sola cuenta, sin tiendas).
- Por SKU: carga producto, llama `crearItemEnMl`, acumula `creados / yaExistian / errores / advertencias`.
- `MlExportResultDTO`: `(int creados, List<String> yaExistian, List<String> errores, List<String> advertencias)`.

### Frontend (`productos/page.tsx`, `productosService.ts`)

- Checkbox **"Subir a Mercado Libre"** en "Canales de venta" (estado `subirMl`).
- Al guardar (crear/editar) con el checkbox marcado, llamar `exportarProductosAMlAPI([sku])` →
  `POST /api/ml/exportar-productos`, y mostrar el resumen (creados / ya existían / errores / avisos) en un
  toast, igual que Nube. Visible con permiso `INTEGRACIONES_EDITAR`.

## Manejo de errores

- Sin título, sin imágenes, sin categoría predicha, o ML devuelve `cause` con `type:error` → se reporta por
  SKU en el resumen, sin abortar el resto.
- Cantidad 0 rechazada por stock → reintento con 1 + aviso. Descripción fallida → ítem creado + aviso.
- Warnings de ML (`me2_adoption_mandatory`, marca no acreditada, atributos autocompletados/normalizados) no
  frenan el alta.
- Todo pasa por `MlRetryHandler` (retry + rate limit + refresh OAuth).

## Testing (sin llamadas reales a Mercado Libre)

- **`MlDescripcionBuilder`**: texto plano con bullets, omite vacíos, sin HTML.
- **`MlItemPayloadBuilder`**: campos correctos (title, category_id, price, currency ARS, listing_type
  gold_special, attributes ITEM_CONDITION/BRAND/SELLER_SKU, shipping me2, pictures).
- **Núcleo `crearItemEnMlCore`** con lambdas: sin título→error; ya existe→yaExistia; sin imágenes→error;
  ok→creado con itemId (predictor/subidor/poster/descripcion como lambdas).
- La subida real (multipart, POST /items, descripción) y el predictor real **no** se testean contra ML
  (consistente con el resto de los wrappers de red).

## Fuera de alcance (fases siguientes)

- **C2:** ficha técnica / atributos por categoría (mapear marca/material/dimensiones/género a los
  `attributes` de la categoría predicha, leyendo `/categories/{id}/attributes`).
- EAN/GTIN (requiere agregar el campo al producto), variaciones, garantía, official store.
- No re-publica ni actualiza ítems existentes (el alta saltea los que ya existen por SKU).
