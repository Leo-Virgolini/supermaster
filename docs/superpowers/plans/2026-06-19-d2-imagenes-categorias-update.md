# D2 — Imágenes y categorías en el update + persistir MLA — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extender el "actualizar al editar" (Spec A) para que, al sincronizar un producto ya publicado, también se actualicen las **categorías** y **se reemplacen las imágenes** en Tienda Nube, se **reemplacen las imágenes** en Mercado Libre, y se **persista el MLA** hallado por búsqueda.

**Architecture:** Se reusan los núcleos testeables de Spec A (`actualizarProductoEnNubeCore`, `actualizarItemEnMlCore`) extendiéndolos con lambdas/params nuevos (categorías en el PATCH de Nube; resolución+PUT de pictures en ML). El reemplazo de imágenes de Nube (GET→DELETE→POST base64) y la asociación del MLA corren con red, best-effort, fuera de los núcleos. Jackson 3.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven / JUnit5 + AssertJ.

## Global Constraints

- **Nube categorías:** re-resolver el árbol (clasif + tipo, igual que el alta) y enviarlas en el `PATCH /products/{id}` como `categories:[…]`. Si faltan clasif/tipo → omitir `categories` (no borrar las existentes).
- **Nube imágenes (reemplazo total, siempre):** `GET /{storeId}/products/{id}/images` → `DELETE` cada una → subir las locales (base64, reusando la lógica de `subirImagenesProducto`). Best-effort: un fallo agrega advertencia, no marca ERROR.
- **ML imágenes (reemplazo):** subir las locales (`subirImagenItem` → `pictureIds`) y `PUT /items/{mla}` con `{pictures:[{id}]}`. Si no hay imágenes locales, saltear. Best-effort.
- **ML persistir MLA:** cuando el MLA se halló por `buscarMlaPorSku` (porque `producto.getMla()` era null), el resultado lleva `mlau`; `exportar` llama `asegurarYAsociar` best-effort tras `ACTUALIZADO`, fuera de la tx readOnly.
- **Fuera de alcance:** categoría ML (no modificable por la API), precio ML (API en transición), frontend (sin cambios).
- **Transaccionalidad (igual que Spec A):** `NubeExportService.exportar` sigue `@Transactional(readOnly=true)` con I/O dentro; `MlExportService.procesarConProductoCargado` readOnly vía `self.`; la asociación del MLA corre en `exportar` (no transaccional) → `asegurarYAsociar` aporta su propia tx.
- Proyecto usa **Jackson 3** (`tools.jackson.databind.JsonNode`, `asString`/`asInt`/`asLong`, NO `asText`).
- Commits en español, terminando con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

## File Structure

**Backend Nube (modificados):**
- `apis/nube/service/TiendaNubeService.java` — `resolverCategoriaIds(...)`; `categoriaIds` en `actualizarProductoEnNubeCore`/`actualizarProductoEnNube`; `sincronizarImagenesNube(...)`.
- `apis/nube/service/NubeExportService.java` — cargar árbol para ambos caminos; resolver `categoriaIds` y pasarlos al update.
- `apis/nube/NubeRetryHandler.java` — método `delete(...)`.

**Backend ML (modificados):**
- `apis/ml/service/MercadoLibreService.java` — resolución+`putPictures` en `actualizarItemEnMl(Core)`.
- `apis/ml/dto/ResultadoAltaMl.java` — factory `actualizado(itemId, mlau)`.
- `apis/ml/service/MlExportService.java` — `procesarConProductoCargado` adjunta `mlau`; `exportar` asocia el MLA best-effort tras `ACTUALIZADO`.

**Tests (modificados):**
- `src/test/.../apis/nube/ActualizarProductoEnNubeTest.java` — firma con `categoriaIds` + test de `categories`.
- `src/test/.../apis/ml/ActualizarItemEnMlTest.java` — firma con pictures + test de pictures.

---

### Task 1: Nube — categorías en el update

**Files:**
- Modify: `apis/nube/service/TiendaNubeService.java`
- Modify: `apis/nube/service/NubeExportService.java`
- Test: `src/test/java/ar/com/leo/super_master_backend/apis/nube/ActualizarProductoEnNubeTest.java`

**Interfaces:**
- Consumes: `NubeCategoriaResolver.resolver(arbol, rutaNombres, creador)`, `NubeCategoriaRuta.aplanar(...)`, `crearCategoria(store, parentId, nombre)`, `cargarArbolCategorias(storeName)`.
- Produces: `TiendaNubeService.resolverCategoriaIds(String storeName, Producto producto, NubeCategoriaArbol arbol) → List<Long>`; `actualizarProductoEnNubeCore(...)` y `actualizarProductoEnNube(...)` con un parámetro `List<Long> categoriaIds`.

- [ ] **Step 1: Actualizar los tests existentes a la nueva firma + agregar test de categories (RED)**

En `ActualizarProductoEnNubeTest.java`, el core gana un parámetro `List<Long> categoriaIds` (después de `storeId`). Actualizar las 3 llamadas existentes para pasar `List.of()` y agregar un test nuevo:

```java
@Test
void actualiza_incluyeCategoriasEnElPatch() {
    JsonNode existente = existente("{\"id\":7,\"variants\":[{\"id\":8,\"sku\":\"1234567\"}]}");
    AtomicReference<String> patchBody = new AtomicReference<>();

    TiendaNubeService.actualizarProductoEnNubeCore(
            producto(), new BigDecimal("150"), null, om, "9",
            java.util.List.of(10L, 20L, 30L),
            sku -> existente,
            (uri, body) -> patchBody.set(body),
            (productId, variantId, price, promo) -> true);

    assertThat(patchBody.get()).contains("\"categories\"").contains("10").contains("20").contains("30");
}

@Test
void actualiza_sinCategorias_noIncluyeCategories() {
    JsonNode existente = existente("{\"id\":7,\"variants\":[{\"id\":8,\"sku\":\"1234567\"}]}");
    AtomicReference<String> patchBody = new AtomicReference<>();

    TiendaNubeService.actualizarProductoEnNubeCore(
            producto(), new BigDecimal("150"), null, om, "9",
            java.util.List.of(),
            sku -> existente,
            (uri, body) -> patchBody.set(body),
            (productId, variantId, price, promo) -> true);

    assertThat(patchBody.get()).doesNotContain("\"categories\"");
}
```

Y en los 3 tests existentes (`actualiza_armaPatchYPrecio`, `actualiza_sinInflado_soloPrice`, `actualiza_noExiste_error`), insertar `java.util.List.of(),` como argumento entre `"9"`/`"9"`/`"9"` (el `storeId`) y la lambda `buscador`. Por ejemplo, `actualiza_armaPatchYPrecio` pasa a:

```java
ResultadoAltaNube r = TiendaNubeService.actualizarProductoEnNubeCore(
        producto(), new BigDecimal("150"), new BigDecimal("180"), om, "9",
        java.util.List.of(),
        sku -> existente,
        (uri, body) -> { patchUri.set(uri); patchBody.set(body); },
        (productId, variantId, price, promo) -> {
            precioIds[0] = productId; precioIds[1] = variantId;
            precioPrice.set(price); precioPromo.set(promo); return true;
        });
```

- [ ] **Step 2: Correr los tests (RED)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarProductoEnNubeTest test`
Expected: FAIL de compilación (el core no acepta aún el parámetro `categoriaIds`).

- [ ] **Step 3: Agregar `categoriaIds` al core**

En `TiendaNubeService.actualizarProductoEnNubeCore`, agregar el parámetro `List<Long> categoriaIds` después de `String storeId` y, al armar el `body`, incluir `categories` cuando no esté vacío:

```java
public static ResultadoAltaNube actualizarProductoEnNubeCore(
        Producto producto, BigDecimal pvp, BigDecimal pvpInflado, ObjectMapper om, String storeId,
        List<Long> categoriaIds,
        Function<String, JsonNode> buscador,
        BiConsumer<String, String> patcher,
        ActualizadorPrecioVariante precioFn) {
    // ... (igual hasta armar el body)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", Map.of("es", producto.getTituloNube() != null ? producto.getTituloNube() : ""));
        body.put("description", Map.of("es", NubeDescripcionBuilder.construir(producto)));
        if (categoriaIds != null && !categoriaIds.isEmpty()) {
            body.put("categories", new java.util.ArrayList<>(categoriaIds));
        }
        patcher.accept("/" + storeId + "/products/" + productId, om.writeValueAsString(body));
    // ... (precio y return igual)
}
```

(Mantener las referencias a tipos como ya están en el archivo — FQN si el archivo lo usa así.)

- [ ] **Step 4: Correr los tests (GREEN)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarProductoEnNubeTest test`
Expected: PASS (5 tests).

- [ ] **Step 5: `resolverCategoriaIds` + propagar `categoriaIds` al método con red**

En `TiendaNubeService`, agregar un método público que reusa la lógica de categorías del alta:

```java
/** Resuelve los ids de categoría de Nube (clasif + tipo) para un producto, creando las faltantes. */
public List<Long> resolverCategoriaIds(String storeName, Producto producto, NubeCategoriaArbol arbol) {
    StoreCredentials store;
    try { verificarCredenciales(); store = getStore(storeName); }
    catch (Exception e) { return List.of(); }
    if (store == null || arbol == null) return List.of();

    boolean esGastro = STORE_GASTRO.equalsIgnoreCase(storeName);
    ClasifGral clasifGral = producto.getClasifGral();
    ClasifGastro clasifGastro = producto.getClasifGastro();
    Tipo tipo = producto.getTipo();
    List<String> clasifNombres = esGastro
            ? (clasifGastro == null ? null : NubeCategoriaRuta.aplanar(clasifGastro, ClasifGastro::getPadre, ClasifGastro::getNombre))
            : (clasifGral == null ? null : NubeCategoriaRuta.aplanar(clasifGral, ClasifGral::getPadre, ClasifGral::getNombre));
    List<String> tipoNombres = tipo == null ? null
            : NubeCategoriaRuta.aplanar(tipo, Tipo::getPadre, Tipo::getNombre);
    if (clasifNombres == null || clasifNombres.isEmpty() || tipoNombres == null || tipoNombres.isEmpty()) {
        return List.of();
    }
    List<String> ruta = new java.util.ArrayList<>(clasifNombres);
    ruta.addAll(tipoNombres);
    return NubeCategoriaResolver.resolver(arbol, ruta, (parentId, nombre) -> crearCategoria(store, parentId, nombre));
}
```

Y en el overload `actualizarProductoEnNube(storeName, producto, pvp, pvpInflado, JsonNode existente)`, agregar el parámetro `List<Long> categoriaIds` y pasarlo al core:

```java
public ResultadoAltaNube actualizarProductoEnNube(
        String storeName, Producto producto, BigDecimal pvp, BigDecimal pvpInflado,
        JsonNode existente, List<Long> categoriaIds) {
    StoreCredentials store;
    try { verificarCredenciales(); store = getStore(storeName); }
    catch (Exception e) { return ResultadoAltaNube.error("Tienda Nube no configurada: " + e.getMessage()); }
    if (store == null) return ResultadoAltaNube.error("Tienda '" + storeName + "' no configurada");
    return actualizarProductoEnNubeCore(
            producto, pvp, pvpInflado, objectMapper, store.getStoreId(),
            categoriaIds,
            sku -> existente,
            (uri, body) -> retryHandler.patchJson(uri, store.getAccessToken(), body),
            (productId, variantId, price, promo) -> actualizarPrecioVariante(storeName, productId, variantId, price, promo));
}
```

(Mantener la convención de FQN/imports tal como el archivo ya la usa para `ResultadoAltaNube`/`Producto`.)

- [ ] **Step 6: `NubeExportService` carga el árbol para ambos caminos y resuelve categorías en el update**

En `NubeExportService.exportar`, cargar el árbol antes de decidir crear/actualizar y resolver `categoriaIds` para el update:

```java
            // Upsert: si ya existe en la tienda, actualizar; si no, crear.
            ResultadoAltaNube r;
            JsonNode existenteEnNube = tiendaNubeService.buscarProductoPorSku(producto.getSku(), tienda);
            NubeCategoriaArbol arbol = arbolesPorTienda.computeIfAbsent(
                    tienda, tiendaNubeService::cargarArbolCategorias);
            if (existenteEnNube != null) {
                List<Long> categoriaIds = tiendaNubeService.resolverCategoriaIds(tienda, producto, arbol);
                r = tiendaNubeService.actualizarProductoEnNube(
                        tienda, producto, precio.get().getPvp(), precio.get().getPvpInflado(), existenteEnNube, categoriaIds);
            } else {
                r = tiendaNubeService.crearProductoEnNube(
                        tienda, producto, precio.get().getPvp(), precio.get().getPvpInflado(), arbol);
            }
```

(El árbol ahora se carga para todos; el `computeIfAbsent` se ejecuta una vez por tienda igual.)

- [ ] **Step 7: Compilar + correr el grupo Nube**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarProductoEnNubeTest,CrearProductoEnNubeTest test`
Expected: PASS. Luego `./mvnw -q -DskipTests compile` → BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add supermaster-backend/src/main/java/.../apis/nube supermaster-backend/src/test/java/.../apis/nube/ActualizarProductoEnNubeTest.java
git commit -m "feat(nube): actualizar categorias en el PATCH al sincronizar producto existente"
```

---

### Task 2: Nube — reemplazo total de imágenes en el update

**Files:**
- Modify: `apis/nube/NubeRetryHandler.java` (método `delete`)
- Modify: `apis/nube/service/TiendaNubeService.java` (`sincronizarImagenesNube` + llamada en `actualizarProductoEnNube`)

**Interfaces:**
- Consumes: `imagenService.resolverArchivosPorSku(sku)`, `imagenService.leerBase64(filename)`, `NubeImagenPayloadBuilder.construir(...)`, `retryHandler.get(...)`, `retryHandler.postJson(...)`.
- Produces: `NubeRetryHandler.delete(String uri, String accessToken)`; `TiendaNubeService.sincronizarImagenesNube(StoreCredentials store, Long productoNubeId, String sku) → String advertencia` (null si todo ok).

> Nota: el reemplazo de imágenes es 100% I/O contra Nube; se valida en el smoke del usuario (no unit test). Best-effort.

- [ ] **Step 1: `delete` en `NubeRetryHandler`**

Agregar `delete` copiando el patrón de `patchJson` (reintentos 401/404/429/5xx/conexión), usando `restClient.delete()`:

```java
public String delete(String uri, String accessToken) {
    int normalRetries = 0;
    int rateLimitRetries = 0;
    while (true) {
        try {
            rateLimiter.acquire();
            return restClient.delete()
                    .uri(uri)
                    .header("Authentication", "bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 401) { log.error("NUBE - 401 Unauthorized (DELETE {}) - Token inválido", uri); throw e; }
            if (status == 404) throw e;
            if (status == 429) {
                if (rateLimitRetries >= MAX_RETRIES_RATE_LIMIT) throw e;
                rateLimitRetries++;
                long waitMs = Math.min(parseRetryAfter(e.getResponseHeaders(), baseWaitMs * 2), MAX_WAIT_MS);
                log.warn("NUBE - 429 (DELETE {}). Retry en {}s... ({}/{})", uri, waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                notifyRetryListener(String.format("Nube rate limit - reintentando en %ds... (%d/%d)", waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT));
                sleep(waitMs);
                continue;
            }
            throw e;
        } catch (HttpServerErrorException e) {
            normalRetries++;
            if (normalRetries >= MAX_RETRIES) throw e;
            long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
            log.warn("NUBE - 5xx {} (DELETE {}). Retry en {}ms... ({}/{})", e.getStatusCode().value(), uri, waitMs, normalRetries, MAX_RETRIES);
            sleep(waitMs);
        } catch (ResourceAccessException e) {
            normalRetries++;
            if (normalRetries >= MAX_RETRIES) throw e;
            long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
            log.warn("NUBE - Error conexión (DELETE {}). Retry en {}ms... ({}/{}): {}", uri, waitMs, normalRetries, MAX_RETRIES, e.getMessage());
            sleep(waitMs);
        }
    }
}
```

- [ ] **Step 2: `sincronizarImagenesNube` en `TiendaNubeService`**

Reemplazo total: listar → borrar → subir las locales. Reutiliza la subida de `subirImagenesProducto`.

```java
/** Reemplaza las imágenes del producto en Nube por las locales actuales (GET lista -> DELETE -> POST). Best-effort. */
private String sincronizarImagenesNube(StoreCredentials store, Long productoNubeId, String sku) {
    // 1) Listar y borrar las imágenes actuales.
    try {
        String body = retryHandler.get(
                "/" + store.getStoreId() + "/products/" + productoNubeId + "/images", store.getAccessToken());
        if (body != null) {
            JsonNode imgs = objectMapper.readTree(body);
            if (imgs.isArray()) {
                for (JsonNode img : imgs) {
                    long imgId = img.path("id").asLong(0);
                    if (imgId <= 0) continue;
                    try {
                        retryHandler.delete("/" + store.getStoreId() + "/products/" + productoNubeId + "/images/" + imgId,
                                store.getAccessToken());
                    } catch (Exception e) {
                        log.warn("NUBE - Falló borrar imagen {} del producto {}: {}", imgId, productoNubeId, e.getMessage());
                    }
                }
            }
        }
    } catch (Exception e) {
        log.warn("NUBE - No se pudieron listar/borrar imágenes del producto {}: {}", productoNubeId, e.getMessage());
    }
    // 2) Subir las locales actuales (misma lógica que el alta).
    return subirImagenesProducto(store, productoNubeId, sku);
}
```

- [ ] **Step 3: Llamar `sincronizarImagenesNube` tras el update**

En `actualizarProductoEnNube(storeName, producto, pvp, pvpInflado, existente, categoriaIds)` (el overload con red de la Task 1), tras obtener el resultado del core y antes de devolverlo, sincronizar imágenes si el update fue exitoso y agregar la advertencia:

```java
    ResultadoAltaNube r = actualizarProductoEnNubeCore(
            producto, pvp, pvpInflado, objectMapper, store.getStoreId(),
            categoriaIds,
            sku -> existente,
            (uri, body) -> retryHandler.patchJson(uri, store.getAccessToken(), body),
            (productId, variantId, price, promo) -> actualizarPrecioVariante(storeName, productId, variantId, price, promo));

    if (r.estado() == ResultadoAltaNube.Estado.ACTUALIZADO && r.productoNubeId() != null && r.productoNubeId() > 0) {
        String advertencia = sincronizarImagenesNube(store, r.productoNubeId(), producto.getSku());
        if (advertencia != null) r = r.conAdvertencia(advertencia);
    }
    return r;
```

(Reemplaza el `return actualizarProductoEnNubeCore(...)` directo por capturar `r`, sincronizar imágenes y devolver.)

- [ ] **Step 4: Compilar**

Run: `cd supermaster-backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Verificar (smoke, manual del usuario)**

Editar un producto ya publicado en Nube con imágenes en la carpeta local y sincronizar: en Nube deben quedar exactamente las imágenes locales (las viejas borradas, las nuevas subidas). Un fallo parcial reporta advertencia ("N de M imágenes subidas").

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/.../apis/nube
git commit -m "feat(nube): reemplazar imagenes al sincronizar producto existente (GET/DELETE/POST)"
```

---

### Task 3: Mercado Libre — reemplazo de imágenes en el update

**Files:**
- Modify: `apis/ml/service/MercadoLibreService.java`
- Test: `src/test/java/ar/com/leo/super_master_backend/apis/ml/ActualizarItemEnMlTest.java`

**Interfaces:**
- Consumes: `imagenService.resolverArchivosPorSku(sku)`, `subirImagenItem(filename)`, `retryHandler.putJson(...)`.
- Produces: `actualizarItemEnMlCore(...)` con un `Function<String, List<String>> resolverPictureIds` y un `BiConsumer<String, List<String>> putPictures` (el SKU se toma de `producto.getSku()`).

- [ ] **Step 1: Extender el test (RED)**

En `ActualizarItemEnMlTest.java`, el core gana dos lambdas: `resolverPictureIds(sku) → List<String>` y `putPictures(mla, pictureIds)`. Actualizar los 3 tests existentes para pasar `sku -> java.util.List.of()` y `(mla, pics) -> {}`, y agregar:

```java
@Test
void conImagenes_reemplazaPictures() {
    AtomicReference<java.util.List<String>> picsPuestas = new AtomicReference<>();

    ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
            producto(), "MLA444",
            mla -> 0,
            (mla, t) -> {},
            (mla, d) -> {},
            (mla, p) -> true,
            sku -> java.util.List.of("pic1", "pic2"),
            (mla, pics) -> picsPuestas.set(pics));

    assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
    assertThat(picsPuestas.get()).containsExactly("pic1", "pic2");
}

@Test
void sinImagenes_noLlamaPutPictures() {
    AtomicReference<java.util.List<String>> picsPuestas = new AtomicReference<>();

    MercadoLibreService.actualizarItemEnMlCore(
            producto(), "MLA555",
            mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
            sku -> java.util.List.of(),
            (mla, pics) -> picsPuestas.set(pics));

    assertThat(picsPuestas.get()).isNull(); // no se llamó putPictures
}
```

En los 3 tests existentes, agregar al final de cada llamada a `actualizarItemEnMlCore` los dos argumentos `sku -> java.util.List.of(), (mla, pics) -> {}`. Por ejemplo `sinVentas_actualizaTituloDescripcionYPrecio`:

```java
ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
        producto(), "MLA111",
        mla -> 0,
        (mla, t) -> titulo.set(t),
        (mla, d) -> desc.set(d),
        (mla, p) -> { precio[0] = p; return true; },
        sku -> java.util.List.of(),
        (mla, pics) -> {});
```

- [ ] **Step 2: Correr el test (RED)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarItemEnMlTest test`
Expected: FAIL de compilación (el core no acepta aún las lambdas de pictures).

- [ ] **Step 3: Extender el core**

En `actualizarItemEnMlCore`, agregar los parámetros y, tras descripción/precio, reemplazar pictures si hay imágenes:

```java
public static ResultadoAltaMl actualizarItemEnMlCore(
        Producto producto, String mla,
        Function<String, Integer> soldQtyFn,
        BiConsumer<String, String> putTitle,
        BiConsumer<String, String> putDesc,
        ActualizadorPrecioItem updatePrice,
        Function<String, List<String>> resolverPictureIds,
        BiConsumer<String, List<String>> putPictures) {
    try {
        if (producto.getTituloMl() == null || producto.getTituloMl().isBlank())
            return ResultadoAltaMl.error("Falta Título ML");
        if (producto.getCosto() == null)
            return ResultadoAltaMl.error("Falta costo");

        String advertencia = null;
        int soldQty = soldQtyFn.apply(mla);
        if (soldQty == 0) {
            putTitle.accept(mla, producto.getTituloMl());
        } else {
            advertencia = "título no actualizado (la publicación tuvo ventas)";
        }

        putDesc.accept(mla, MlDescripcionBuilder.construir(producto));

        double price = producto.getCosto().multiply(java.math.BigDecimal.valueOf(5)).doubleValue();
        updatePrice.actualizar(mla, price);

        List<String> pictureIds = resolverPictureIds.apply(producto.getSku());
        if (pictureIds != null && !pictureIds.isEmpty()) {
            putPictures.accept(mla, pictureIds);
        }

        ResultadoAltaMl r = ResultadoAltaMl.actualizado(mla);
        return advertencia == null ? r : r.conAdvertencia(advertencia);
    } catch (Exception e) {
        return ResultadoAltaMl.error(e.getMessage());
    }
}
```

(Mantener el FQN de `Producto` en la firma como ya lo usa el archivo.)

- [ ] **Step 4: Correr el test (GREEN)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarItemEnMlTest test`
Expected: PASS (5 tests).

- [ ] **Step 5: Cablear las lambdas reales en `actualizarItemEnMl`**

En `actualizarItemEnMl`, agregar el resolver (sube las imágenes locales → pictureIds) y el `putPictures` (PUT del array):

```java
public ResultadoAltaMl actualizarItemEnMl(Producto producto, String mla) {
    if (!isConfigured()) return ResultadoAltaMl.error("Mercado Libre no configurado");
    verificarTokens();
    return actualizarItemEnMlCore(
            producto, mla,
            this::leerSoldQuantity,
            (m, title) -> {
                try { retryHandler.putJson("/items/" + m, () -> tokens.accessToken,
                        objectMapper.writeValueAsString(Map.of("title", title))); }
                catch (Exception e) { throw new RuntimeException("título: " + e.getMessage(), e); }
            },
            (m, plainText) -> {
                try { retryHandler.putJson("/items/" + m + "/description", () -> tokens.accessToken,
                        objectMapper.writeValueAsString(Map.of("plain_text", plainText))); }
                catch (Exception e) { throw new RuntimeException("descripción: " + e.getMessage(), e); }
            },
            this::updateItemPrice,
            sku -> {
                List<String> ids = new ArrayList<>();
                for (String filename : imagenService.resolverArchivosPorSku(sku)) {
                    String picId = subirImagenItem(filename);
                    if (picId != null && !picId.isBlank()) ids.add(picId);
                }
                return ids;
            },
            (m, pictureIds) -> {
                try {
                    List<Map<String, Object>> pics = new ArrayList<>();
                    for (String id : pictureIds) pics.add(Map.of("id", id));
                    retryHandler.putJson("/items/" + m, () -> tokens.accessToken,
                            objectMapper.writeValueAsString(Map.of("pictures", pics)));
                } catch (Exception e) { throw new RuntimeException("imágenes: " + e.getMessage(), e); }
            });
}
```

(Asegurar imports de `java.util.ArrayList`/`List`/`Map` si faltan.)

- [ ] **Step 6: Compilar + correr el grupo ML**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarItemEnMlTest,CrearItemEnMlTest test`
Expected: PASS. Luego `./mvnw -q -DskipTests compile` → BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add supermaster-backend/src/main/java/.../apis/ml/service/MercadoLibreService.java supermaster-backend/src/test/java/.../apis/ml/ActualizarItemEnMlTest.java
git commit -m "feat(ml): reemplazar imagenes (pictures) al sincronizar item existente"
```

---

### Task 4: Mercado Libre — persistir el MLA hallado por búsqueda

**Files:**
- Modify: `apis/ml/dto/ResultadoAltaMl.java`
- Modify: `apis/ml/service/MlExportService.java`
- Test: `src/test/java/ar/com/leo/super_master_backend/apis/ml/ResultadoAltaMlTest.java` (nuevo)

**Interfaces:**
- Consumes: `mlaService.asegurarYAsociar(productoId, mlaCode, mlau)`; `mercadoLibreService.buscarMlaPorSku(sku) → MlaPorSku(mla, mlau)`.
- Produces: `ResultadoAltaMl.actualizado(String itemId, String mlau)`.

- [ ] **Step 1: Test de la factory (RED)**

Crear `ResultadoAltaMlTest.java`:

```java
package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultadoAltaMlTest {

    @Test
    void actualizadoConMlau_llevaItemIdYMlau() {
        ResultadoAltaMl r = ResultadoAltaMl.actualizado("MLA1", "MLAU9");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.itemId()).isEqualTo("MLA1");
        assertThat(r.mlau()).isEqualTo("MLAU9");
    }

    @Test
    void actualizadoSinMlau_mlauNull() {
        ResultadoAltaMl r = ResultadoAltaMl.actualizado("MLA1");
        assertThat(r.mlau()).isNull();
    }
}
```

- [ ] **Step 2: Correr el test (RED)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ResultadoAltaMlTest test`
Expected: FAIL de compilación (`actualizado(String, String)` no existe).

- [ ] **Step 3: Agregar la factory `actualizado(itemId, mlau)`**

En `ResultadoAltaMl.java`, agregar el overload manteniendo el existente:

```java
public static ResultadoAltaMl actualizado(String itemId) { return new ResultadoAltaMl(Estado.ACTUALIZADO, null, itemId, null, null); }
public static ResultadoAltaMl actualizado(String itemId, String mlau) { return new ResultadoAltaMl(Estado.ACTUALIZADO, null, itemId, mlau, null); }
```

- [ ] **Step 4: Correr el test (GREEN)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ResultadoAltaMlTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: `procesarConProductoCargado` adjunta el `mlau` cuando lo halló por búsqueda**

En `MlExportService.procesarConProductoCargado`, capturar el `mlau` de `buscarMlaPorSku` y, si el update fue `ACTUALIZADO`, devolver el resultado con el `mlau` (señal para asociar):

```java
@Transactional(readOnly = true)
public ResultadoAltaMl procesarConProductoCargado(Integer productoId) {
    Producto p = productoRepository.findById(productoId).orElse(null);
    if (p == null) return ResultadoAltaMl.error("Producto no encontrado");

    String mla = (p.getMla() != null) ? p.getMla().getMla() : null;
    String mlauHallado = null;
    if (mla == null) {
        var encontrado = mercadoLibreService.buscarMlaPorSku(p.getSku());
        if (encontrado != null) { mla = encontrado.mla(); mlauHallado = encontrado.mlau(); }
    }
    if (mla != null && !mla.isBlank()) {
        ResultadoAltaMl r = mercadoLibreService.actualizarItemEnMl(p, mla);
        // Si el MLA lo hallamos por búsqueda (no estaba asociado) y el update fue OK,
        // adjuntar el mlau para que exportar lo asocie al producto.
        if (mlauHallado != null && r.estado() == ResultadoAltaMl.Estado.ACTUALIZADO) {
            ResultadoAltaMl conMlau = ResultadoAltaMl.actualizado(mla, mlauHallado);
            return r.advertencia() != null ? conMlau.conAdvertencia(r.advertencia()) : conMlau;
        }
        return r;
    }
    return mercadoLibreService.crearItemEnMl(p);
}
```

- [ ] **Step 6: `exportar` asocia el MLA best-effort tras `ACTUALIZADO`**

En `MlExportService.exportar`, en el `case ACTUALIZADO`, si el resultado trae `mlau`, asociar el MLA (best-effort, su propia tx vía `asegurarYAsociar`):

```java
            case ACTUALIZADO -> {
                actualizados.add(etiqueta);
                if (r.advertencia() != null) advertencias.add(etiqueta + ": " + r.advertencia());
                if (r.mlau() != null) {
                    try {
                        mlaService.asegurarYAsociar(productoId, r.itemId(), r.mlau());
                    } catch (Exception e) {
                        log.warn("ML - No se pudo asociar el MLA {} al producto {}: {}", r.itemId(), productoId, e.getMessage());
                        advertencias.add(etiqueta + ": no se pudo asociar el MLA");
                    }
                }
            }
```

- [ ] **Step 7: Compilar + correr el grupo ML**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ResultadoAltaMlTest,ActualizarItemEnMlTest test`
Expected: PASS. Luego `./mvnw -q -DskipTests compile` → BUILD SUCCESS.

- [ ] **Step 8: Verificar (smoke, manual del usuario)**

Editar un producto que tenga publicación en ML pero SIN MLA asociado en la base (forzar `buscarMlaPorSku`), sincronizar a ML: el update debe funcionar y el producto debe quedar con el MLA asociado (verificar en la tabla MLAs / el producto). Una segunda edición ya no debería re-buscar (usa `producto.getMla()`).

- [ ] **Step 9: Commit**

```bash
git add supermaster-backend/src/main/java/.../apis/ml supermaster-backend/src/test/java/.../apis/ml/ResultadoAltaMlTest.java
git commit -m "feat(ml): persistir el MLA hallado por busqueda al sincronizar (asociar al producto)"
```

---

## Self-Review

**Spec coverage:**
- Nube categorías en el `PATCH` (re-resolver árbol) → Task 1. ✅
- Nube reemplazo de imágenes (GET/DELETE/POST) + `delete` en RetryHandler → Task 2. ✅
- ML reemplazo de imágenes (`PUT pictures`) → Task 3. ✅
- ML persistir MLA (`actualizado(itemId, mlau)` + asociar best-effort) → Task 4. ✅
- Fuera de alcance (categoría/precio ML, frontend) → respetado. ✅
- Transaccionalidad (Nube readOnly con I/O dentro; ML readOnly + asociación fuera de la tx) → Tasks 1, 4. ✅

**Placeholder scan:** sin TBD/TODO; cada step trae el código real.

**Type consistency:** `resolverCategoriaIds(String,Producto,NubeCategoriaArbol)→List<Long>`; `actualizarProductoEnNubeCore(...,List<Long> categoriaIds,...)`; `actualizarProductoEnNube(...,JsonNode,List<Long>)`; `sincronizarImagenesNube(StoreCredentials,Long,String)→String`; `NubeRetryHandler.delete(String,String)`; `actualizarItemEnMlCore(...,Function<String,List<String>>,BiConsumer<String,List<String>>)`; `ResultadoAltaMl.actualizado(String,String)`. Consistentes entre tasks.

**Cambios de aridad que rompen tests (manejados en el mismo task):** el core de Nube gana `categoriaIds` (Task 1 actualiza sus 3 tests) y el core de ML gana 2 lambdas (Task 3 actualiza sus 3 tests). Cada task corre `mvnw test` del grupo afectado (no solo compile) para detectarlo.

**Riesgos:** el reemplazo de imágenes (Nube y ML) y la asociación del MLA son I/O — se validan en el smoke del usuario, no en unit test. El precio ML queda fuera (API en transición).
