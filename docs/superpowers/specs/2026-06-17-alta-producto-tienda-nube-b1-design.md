# Alta de producto en Tienda Nube — Fase B1 (alta básica) — Diseño

**Fecha:** 2026-06-17
**Estado:** Aprobado
**Sub-proyecto B, Fase B1.** Construye sobre el Sub-proyecto A (3 títulos: usa `tituloNube`).

## Objetivo

Dar de alta un producto en Tienda Nube (Nuvemshop) vía API cuando se marca el canal de venta
correspondiente al guardar el producto. Esta fase B1 cubre el **alta básica**: nombre, descripción
HTML, variante con precio y peso/dimensiones fijos, producto oculto. Las fases siguientes agregan
categorías (B2), imágenes (B3) y SEO con IA (B4).

## Decisiones tomadas (brainstorming)

| Tema | Decisión |
|---|---|
| Tiendas destino | **2 checkboxes** independientes: **KT HOGAR** y **KT GASTRO** (en "Canales de venta"). El usuario marca a cuál(es) subir. |
| Precio | **PVP** de `producto_canal_precios(canal de la tienda, cuota elegida)`; si tiene **`pvp_inflado`** → `price`=pvp_inflado (lista/tachado), `promotional_price`=pvp. Si no → `price`=pvp. |
| Cuota del precio | **Elegible al subir** (selector por tienda), con defaults: KT HOGAR = **Transferencia (-1)**, KT GASTRO = **6 cuotas**. |
| Si el SKU ya existe en esa tienda | **No duplicar**: saltear y reportar "ya existía". B1 = solo ALTA de nuevos. |
| Visibilidad | `published=false` (oculto en la tienda). |
| Productos relacionados | **Descartado** — la API de TN no lo soporta (34 recursos, ninguno de related/cross-sell; "Kit" es para combos). |

## Contexto del sistema (verificado)

- **API TN** (`POST /products`): `name` (multilingüe, requerido), `description` (HTML), `variants`
  (sku, price, promotional_price, cost, weight, depth, width, height, stock), `categories` (array
  ids), `images` (base64/url), `seo_title`/`seo_description`, `published`, `free_shipping`.
  Stock infinito = `stock: ""`.
- **`NubeRetryHandler`** hoy tiene `get`, `getWithHeaders`, `putJson`, `patchJson` — **falta `postJson`**.
- **`TiendaNubeService`** lee/actualiza precios, lista categorías; **no crea productos**. Tiene
  `buscarProductoPorSku(sku, storeName)` (devuelve JsonNode o null). Tiendas: `STORE_HOGAR="KT HOGAR"`,
  `STORE_GASTRO="KT GASTRO"`. Credenciales por tienda (`nube_tokens.json`).
- **`producto_canal_precios`**: columnas `pvp`, `pvp_inflado` (precio tachado), `cuotas`, `obsoleto`.
  Una fila por (producto, canal, cuotas).
- **Canales** (BD): `2 = KT HOGAR`, `3 = KT GASTRO`. Cuotas de KT HOGAR/GASTRO en `canal_concepto_cuota`
  (HOGAR tiene `-1`=Transferencia; GASTRO tiene `6`).
- **Producto** (entidad): `tituloNube`, dimensiones (`capacidad`,`largo`,`ancho`,`alto`,`diamboca`,
  `diambase`,`espesor`), `material` (→nombre), aptos (`productosApto`→`apto.nombre`), `marca`
  (→nombre, jerárquica), `costo`, `sku`.
- **Patrón "exportar a API externa"**: DUX (`subirADux` checkbox → `exportarProductosADuxAPI(skus)` →
  `POST /api/dux/exportar-productos` → `DuxService` carga productos y arma payload). Replicar este
  patrón para Nube.

## Alcance (Fase B1)

### Backend

**1. `NubeRetryHandler.postJson(uri, accessToken, jsonBody)`** — método POST con el mismo manejo de
retry/rate-limit que `putJson`/`patchJson`. Devuelve el body de respuesta (con el `id` del producto
creado).

**2. `TiendaNubeService.crearProductoEnNube(storeName, producto, canalId, cuotas)`** →
`ResultadoAltaNube` (creado | yaExistia | error con motivo):
- `buscarProductoPorSku(producto.sku, storeName)`: si existe → devolver `yaExistia`.
- Si `producto.tituloNube` es vacío → `error("falta Título Nube")`.
- Resolver precio desde `producto_canal_precios(producto, canalId, cuotas, obsoleto=0)`:
  `pvp` y `pvp_inflado`. Si no hay fila → `error("sin precio calculado para canal/cuota")`.
- Construir payload (ver abajo) y `postJson("/{storeId}/products", token, body)`.

**3. Constructor del payload** (helper, p. ej. `NubeProductoPayloadBuilder`):
- `name`: `{ "es": tituloNube }`.
- `description`: HTML (ver formato).
- `published`: `false`. `free_shipping`: `false`.
- `variants`: `[{ "sku": sku, "price": <lista>, "promotional_price": <pvp si hay inflado>,
  "cost": costo, "weight": "0.050", "depth": "8.00", "width": "5.00", "height": "5.00", "stock": "" }]`.
  - Si `pvp_inflado != null && > pvp`: `price` = pvp_inflado, `promotional_price` = pvp.
  - Si no: `price` = pvp, sin `promotional_price`.
- (Sin `categories`, `images`, `seo_*` en B1.)

**4. Descripción HTML** (`NubeDescripcionBuilder`):
```html
<p><b>CARACTERÍSTICAS</b></p>
<ul>
  <li>Dimensiones: {dimensiones físicas no vacías, formateadas}</li>
  <li>Material: {material.nombre}</li>
  <li>Aptos: {aptos unidos por coma}</li>
  <li>Marca: {marca.nombre}</li>
</ul>
```
Cada bullet se omite si el dato está vacío. Las "dimensiones" combinan los campos físicos presentes
(capacidad/largo/ancho/alto/diamboca/diambase/espesor) con etiquetas legibles.

**5. Endpoint** `POST /api/nube/exportar-productos` (en un `TiendaNubeController` o el existente):
- Body: `{ skus: [...], tiendas: [{ tienda: "KT HOGAR"|"KT GASTRO", cuotas: int }] }`.
- Carga productos por SKU; por cada producto × tienda marcada llama `crearProductoEnNube`.
- Devuelve `{ creados: int, yaExistian: [sku/tienda], errores: [{sku, tienda, motivo}] }`.
- Permiso `INTEGRACIONES_EDITAR`.

### Frontend (`productos/page.tsx`, sección "Canales de venta")

- Los checkboxes **KT HOGAR** y **KT GASTRO** dejan de ser placeholders: se habilitan, con estado
  (`subirKtHogar`, `subirKtGastro`). (El de ML sigue placeholder.)
- Al marcar cada uno, mostrar un **selector de cuota** para esa tienda (las cuotas disponibles del
  canal correspondiente), con default: HOGAR=Transferencia(-1), GASTRO=6.
- Al **guardar** el producto (crear/editar) con alguno marcado, llamar a un nuevo
  `exportarProductosANubeAPI(sku, tiendas)` → `POST /api/nube/exportar-productos`, y mostrar el
  resumen (creados / ya existían / errores) en un toast, igual que DUX.
- Visible con permiso `INTEGRACIONES_EDITAR`.

## Manejo de errores
- Producto sin Título Nube, sin precio para canal/cuota, o ya existente → se reporta por SKU/tienda en
  el resumen, sin abortar el resto.
- Fallos de la API de TN pasan por `NubeRetryHandler` (retry + rate limit). Un fallo de una tienda no
  impide intentar la otra.

## Testing
- **Backend:** test de `NubeProductoPayloadBuilder`/`NubeDescripcionBuilder` (descripción HTML con
  bullets omitiendo vacíos; precio con y sin `pvp_inflado` → price/promotional_price correctos; stock
  `""`). Test de `crearProductoEnNube` con `TiendaNubeService` dependencias mockeadas: caso "ya
  existe" → no postea; caso sin título → error; caso OK → arma payload y postea.
- **Frontend:** typecheck + build; verificación manual con una tienda de prueba.

## Fuera de alcance (fases siguientes / descartado)
- **B2:** categorías (mapear/crear jerarquía clasif→tipo en TN).
- **B3:** imágenes (base64 desde la carpeta por SKU).
- **B4:** SEO con IA (seo_title/seo_description).
- **Descartado:** productos relacionados (API de TN no lo soporta); actualizar productos existentes
  (B1 solo da de alta nuevos).
