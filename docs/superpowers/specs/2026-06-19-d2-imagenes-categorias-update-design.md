# D2 — Imágenes y categorías en el update (Nube/ML) + persistir MLA

**Fecha:** 2026-06-19
**Estado:** Diseño aprobado, pendiente de plan.
**Relacionado:** [Spec A — Nube/ML actualizar al editar](2026-06-19-actualizar-al-editar-canales-d1-design.md) (hecho). D2 extiende el update con imágenes y categorías.

## Objetivo

El update al editar (Spec A) hoy sincroniza título/precio/descripción. D2 agrega:
- **Tienda Nube:** actualizar **categorías** y **reemplazar imágenes** de la publicación existente.
- **Mercado Libre:** **reemplazar imágenes** de la publicación existente, y **persistir el MLA** hallado por búsqueda para no re-buscarlo en futuras ediciones.

## Alcance

### Incluye (D2)
- **Nube — categorías:** re-resolver el árbol de categorías (clasif + tipo) igual que el alta y enviarlas en el `PATCH /products/{id}` como `categories:[…]`.
- **Nube — imágenes (reemplazo total):** listar las imágenes actuales del producto en Nube, borrarlas, y subir las locales actuales (base64).
- **ML — imágenes (reemplazo):** subir las imágenes locales y enviarlas en `PUT /items/{mla}` como `pictures:[{id}]`.
- **ML — persistir MLA:** cuando el MLA se halla por `buscarMlaPorSku` (porque `producto.getMla()` era null), asociarlo al producto (`asegurarYAsociar`) best-effort tras el update.

### NO incluye (fuera de D2)
- **Categoría ML:** no es modificable por la API de ML (la doc de "Sincroniza y modifica publicaciones" no la lista como campo editable de un ítem existente; el predictor solo aplica al alta). Se mantiene la categoría con la que se publicó.
- **Precio ML:** la API está en transición — `PUT /items` con `price` ya no actualiza (restricción del 18/3/2026: solo-`price` → 400; con otros campos → `price` ignorado + warning) y el endpoint nuevo `POST /items/{id}/prices/standard` figura en la doc como **"aún no disponible"**. Queda como pendiente a validar en el smoke del usuario y a implementar cuando ML habilite `/prices/standard`.
- Variaciones, atributos/ficha técnica.

## Contexto del código existente

### Tienda Nube
- **Imágenes (alta):** `TiendaNubeService.subirImagenesProducto(store, productoNubeId, sku)` — `imagenService.resolverArchivosPorSku(sku)` → para cada archivo, `imagenService.leerBase64(filename)` + `NubeImagenPayloadBuilder.construir(filename, base64, position)` → `POST /{storeId}/products/{id}/images`.
- **Endpoints de imágenes (doc Nube, confirmados):** `GET /products/{id}/images` (lista con `id`, `position`), `DELETE /products/{id}/images/{id}`, `POST /products/{id}/images` (`attachment` base64 + `position`), `PUT /products/{id}/images/{id}`.
- **Categorías (alta):** `cargarArbolCategorias(storeName)` → `NubeCategoriaArbol`; `NubeCategoriaResolver.resolver(arbol, rutaNombres, creadorCategoria)` → `List<Long> categoriaIds`, donde `rutaNombres` = nombres de clasif (gral para KT HOGAR / gastro para KT GASTRO) + nombres de tipo. Se ponen en el payload como `categories`.
- **Update actual (Spec A):** `actualizarProductoEnNube(storeName, producto, pvp, pvpInflado, JsonNode existente)` → `actualizarProductoEnNubeCore` arma `PATCH /products/{id}` con `name` + `description` y aplica el precio. NO toca categorías ni imágenes.
- **NubeExportService.exportar** (`@Transactional readOnly`): en el camino de update llama `actualizarProductoEnNube`; el árbol de categorías hoy se carga (`computeIfAbsent`) solo en el camino de creación.
- **NubeRetryHandler:** tiene `get`, `postJson`, `putJson`, `patchJson`. **Verificar si existe un método `delete`**; si no, agregarlo (siguiendo el patrón de los otros, con reintentos).

### Mercado Libre
- **Imágenes (alta):** `subirImagenItem(filename)` → `POST /pictures/items/upload` (multipart) → `pictureId`. Se ponen en el alta como `pictures:[{id}]` (`MlItemPayloadBuilder`).
- **Update actual (Spec A):** `actualizarItemEnMl(producto, mla)` → `actualizarItemEnMlCore(producto, mla, soldQtyFn, putTitle, putDesc, updatePrice)`. NO toca pictures.
- **MLA:** `MlaService.asegurarYAsociar(productoId, mlaCode, mlau)` (`@Transactional`) crea/asocia el `Mla` al producto. `buscarMlaPorSku(sku)` → `MlaPorSku(mla, mlau)`.
- **MlExportService.procesarConProductoCargado** (`@Transactional readOnly`, vía `self.`): decide crear vs actualizar; obtiene el MLA de `producto.getMla()` o `buscarMlaPorSku`. El post-alta (incluye `asegurarYAsociar`) corre SOLO en el camino de creación.
- **ResultadoAltaMl:** `actualizado(String itemId)` (mlau queda null). El record tiene campos `itemId` y `mlau`.

### Imágenes (común)
- `ImagenService.resolverArchivosPorSku(sku)` → archivos locales (`{sku}.ext` principal, `{sku}_2.ext`… adicionales). `leerBase64(filename)` (Nube), `leerBytes(filename)` (ML multipart).

## Diseño

### Tienda Nube — categorías en el update

`NubeExportService.exportar` resuelve `categoriaIds` para el producto en el camino de update (igual que el alta: cargar el árbol de la tienda y `NubeCategoriaResolver.resolver` con la ruta clasif+tipo) y los pasa a `actualizarProductoEnNube(...)`. El árbol pasa a cargarse para AMBOS caminos (mover el `computeIfAbsent` antes de decidir crear/actualizar, o cargarlo en el update igual que en el create).

`actualizarProductoEnNubeCore` recibe los `categoriaIds` y, si no están vacíos, los agrega al `PATCH` body como `categories:[id…]` (junto a `name`/`description`). Si faltan clasif/tipo (lista vacía), se omite `categories` (no se borran las categorías existentes en Nube).

> Reutilizar la lógica de resolución del alta (no duplicar): extraer, si hace falta, un helper que calcule `categoriaIds` a partir del producto + árbol, usable por el alta y el update.

### Tienda Nube — reemplazo de imágenes

Nuevo método `sincronizarImagenesNube(store, productoNubeId, sku)`:
1. `GET /{storeId}/products/{id}/images` → parsear la lista de `id`.
2. Por cada imagen existente, `DELETE /{storeId}/products/{id}/images/{imageId}`.
3. Subir las imágenes locales actuales (la misma lógica que `subirImagenesProducto`: `resolverArchivosPorSku` → `leerBase64` → `NubeImagenPayloadBuilder` → `POST`). Reutilizar `subirImagenesProducto` o un helper común.

Se invoca desde `actualizarProductoEnNube` tras el `PATCH`. Best-effort: un fallo de borrado/subida no aborta el update; se acumula una advertencia (p. ej. "N de M imágenes sincronizadas"). Requiere `NubeRetryHandler.delete` (agregar si no existe).

### Mercado Libre — reemplazo de imágenes

Extender `actualizarItemEnMlCore` con una lambda `putPictures(mla, pictureIds)` y un resolutor de imágenes:
- En `actualizarItemEnMl`, resolver los archivos locales (`resolverArchivosPorSku`) y subirlos (`subirImagenItem` → `pictureIds`).
- Si hay al menos una imagen, `PUT /items/{mla}` con `{pictures:[{id:…}]}` (reemplaza el array de imágenes del ítem).
- Si no hay imágenes locales, saltear (no enviar pictures) — no romper.
- Best-effort: un fallo de imágenes no aborta el update (se agrega advertencia).

> Las imágenes ML se pueden actualizar siempre (la doc: "siempre podés agregar o reemplazar imágenes"), sin la restricción de ventas que aplica al título.

### Mercado Libre — persistir el MLA

`procesarConProductoCargado` distingue el origen del MLA:
- Si `producto.getMla() != null` → ya está asociado; no hace falta persistir.
- Si el MLA vino de `buscarMlaPorSku` (porque `producto.getMla()` era null) → el resultado del update lleva `mla` + `mlau` (usar `ResultadoAltaMl.actualizado(itemId, mlau)` — extender la factory para aceptar `mlau`; cuando ya estaba asociado, `mlau` = null).

`MlExportService.exportar`, tras un `ACTUALIZADO`, si `r.mlau() != null` (señal de "recién hallado"), llama `mlaService.asegurarYAsociar(productoId, r.itemId(), r.mlau())` best-effort, **fuera** de la tx readOnly (su propia tx, como el post-alta). Un fallo agrega aviso, no frena.

## Manejo de errores
- Imágenes (Nube y ML) y categorías son best-effort: un fallo agrega advertencia al resultado pero no marca el SKU como ERROR (el título/precio/descripción ya se actualizaron).
- Nube: si el `GET`/`DELETE` de imágenes falla, se intenta subir igual (o se reporta "no se pudieron sincronizar imágenes").
- ML: si falla la subida de imágenes o el `PUT pictures`, se agrega aviso.
- Persistir MLA: best-effort en su propia tx; un fallo agrega aviso "no se pudo asociar el MLA".
- Se mantiene la transaccionalidad de Spec A (Nube `@Transactional readOnly` con I/O dentro; ML `procesarConProductoCargado` readOnly + asociación del MLA fuera de la tx).

## Pruebas
- **ML (núcleo testeable):** extender `ActualizarItemEnMlTest` — con imágenes locales presentes → se llama `putPictures` con los `pictureIds`; sin imágenes → no se llama; el resto (título/desc/precio) sigue igual. Lambdas POJO, sin red.
- **Nube (núcleo testeable):** test del armado del `PATCH` body incluyendo `categories` cuando hay `categoriaIds`, y sin `categories` cuando la lista está vacía.
- **Nube imágenes / ML pictures (red):** el reemplazo (GET/DELETE/POST en Nube, PUT en ML) se valida en el smoke, no en unit test.
- **Persistir MLA:** test de que el resultado lleva `mlau` cuando el MLA vino por búsqueda y null cuando ya estaba asociado (lógica en `procesarConProductoCargado` / la factory).

## Archivos afectados (resumen)

**Backend Nube:**
- `apis/nube/service/TiendaNubeService.java` — `categoriaIds` en `actualizarProductoEnNubeCore`/`actualizarProductoEnNube`; nuevo `sincronizarImagenesNube`; helper común de resolución de categorías/subida de imágenes.
- `apis/nube/service/NubeExportService.java` — resolver `categoriaIds` en el camino de update; cargar el árbol para ambos caminos.
- `apis/nube/NubeRetryHandler.java` — `delete` (si no existe).

**Backend ML:**
- `apis/ml/service/MercadoLibreService.java` — `putPictures` + resolución de imágenes en `actualizarItemEnMl(Core)`.
- `apis/ml/dto/ResultadoAltaMl.java` — `actualizado(itemId, mlau)`.
- `apis/ml/service/MlExportService.java` — `procesarConProductoCargado` devuelve `mlau` cuando halló por búsqueda; `exportar` asocia el MLA best-effort tras ACTUALIZADO.

**Tests:** `ActualizarItemEnMlTest` (pictures), `ActualizarProductoEnNubeTest` (categories en el PATCH).

## Fuera de alcance / pendientes
- **Precio ML:** API en transición (PUT bloqueado; `/items/{id}/prices/standard` aún no disponible). Validar en smoke; implementar `POST /items/{id}/prices/standard` cuando ML lo habilite.
- **Categoría ML:** no modificable por la API.
- **Frontend:** sin cambios (el toast ya reporta `actualizados` + avisos).
