# Estado de publicación por canal (activar/inactivar) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el flag `Producto.activo` se refleje como estado de publicación en Tienda Nube (`published`) y Mercado Libre (`status` active/paused) al sincronizar, sin borrar la publicación (reversible). Dux ya lo hace.

**Architecture:** Se extiende el upsert existente. En Nube, el booleano `published = producto.activo` viaja en el payload del alta (`NubeProductoPayloadBuilder`) y en el `PATCH` del update (`actualizarProductoEnNubeCore`). En ML, se agrega un paso best-effort `PUT /items/{mla}` con `{"status":"active"|"paused"}` en `actualizarItemEnMlCore` (vía nueva lambda) y, en el alta, un `PUT status=paused` si el producto nace inactivo.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven; Jackson 3 (`tools.jackson`); JUnit 5 + AssertJ. Tests de núcleo sin red (lambdas POJO).

## Global Constraints

- Trabajar directo en `main` (sin ramas salvo pedido explícito).
- Ejecutar Maven offline: `./mvnw -o ...` desde `supermaster-backend/`.
- Estado ML en **minúscula**: `"active"` / `"paused"` (la API distingue mayúsculas).
- El cambio de estado es **best-effort**: un fallo agrega una **advertencia** al resultado y NO marca el SKU como ERROR.
- El Nube core (`actualizarProductoEnNubeCore`) NO cambia su firma (solo se agrega `published` al body). El ML core (`actualizarItemEnMlCore`) SÍ cambia su firma (nueva lambda al final) → hay que actualizar los 7 tests existentes de `ActualizarItemEnMlTest` que lo construyen posicionalmente.
- `producto.getActivo()` es `Boolean` (puede ser null en teoría): usar siempre `Boolean.TRUE.equals(producto.getActivo())` para el booleano de publicación/activación.
- Commits terminan con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: Nube — `published = activo` en el alta

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java:20`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/CrearProductoEnNubeTest.java`

**Interfaces:**
- Consumes: `NubeProductoPayloadBuilder.construir(Producto p, BigDecimal pvp, BigDecimal pvpInflado, List<Long> categoriaIds) -> Map<String,Object>`
- Produces: el payload de alta lleva `"published"` = `Boolean.TRUE.equals(p.getActivo())`.

- [ ] **Step 1: Escribir el test que falla**

Agregar al final de `CrearProductoEnNubeTest` (dentro de la clase):

```java
    @org.junit.jupiter.api.Test
    void alta_published_reflejaActivo() {
        ar.com.leo.super_master_backend.dominio.producto.entity.Producto p =
                new ar.com.leo.super_master_backend.dominio.producto.entity.Producto();
        p.setSku("1234567");
        p.setTituloNube("Olla acero 24cm");

        p.setActivo(true);
        var activo = ar.com.leo.super_master_backend.apis.nube.service.NubeProductoPayloadBuilder
                .construir(p, new java.math.BigDecimal("100"), null, java.util.List.of());
        org.assertj.core.api.Assertions.assertThat(activo.get("published")).isEqualTo(true);

        p.setActivo(false);
        var inactivo = ar.com.leo.super_master_backend.apis.nube.service.NubeProductoPayloadBuilder
                .construir(p, new java.math.BigDecimal("100"), null, java.util.List.of());
        org.assertj.core.api.Assertions.assertThat(inactivo.get("published")).isEqualTo(false);
    }
```

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `./mvnw -o test -Dtest=CrearProductoEnNubeTest`
Expected: FAIL en `alta_published_reflejaActivo` — `published` es `false` también con `activo=true` (hoy está hardcodeado).

- [ ] **Step 3: Implementar el cambio mínimo**

En `NubeProductoPayloadBuilder.java`, reemplazar la línea 20:

```java
        payload.put("published", false);
```

por:

```java
        payload.put("published", Boolean.TRUE.equals(p.getActivo()));
```

- [ ] **Step 4: Correr el test para verificar que pasa**

Run: `./mvnw -o test -Dtest=CrearProductoEnNubeTest`
Expected: PASS (todos).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/CrearProductoEnNubeTest.java
git commit -m "feat(nube): published refleja el flag activo en el alta de producto"
```

---

### Task 2: Nube — `published` en el `PATCH` del update

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java:937` (dentro de `actualizarProductoEnNubeCore`)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/ActualizarProductoEnNubeTest.java`

**Interfaces:**
- Consumes: `TiendaNubeService.actualizarProductoEnNubeCore(Producto, BigDecimal pvp, BigDecimal pvpInflado, ObjectMapper, String storeId, List<Long> categoriaIds, Function<String,JsonNode> buscador, BiConsumer<String,String> patcher, ActualizadorPrecioVariante precioFn)` — **la firma NO cambia.**
- Produces: el body del `PATCH` incluye `"published"` = `Boolean.TRUE.equals(producto.getActivo())`, siempre.

- [ ] **Step 1: Escribir el test que falla**

Agregar a `ActualizarProductoEnNubeTest` (la clase ya tiene `om`, `producto()` con `activo=true` por default, y `existente(json)`):

```java
    @Test
    void actualiza_incluyePublishedSegunActivo() {
        JsonNode existente = existente("{\"id\":7,\"variants\":[{\"id\":8,\"sku\":\"1234567\"}]}");
        AtomicReference<String> patchBody = new AtomicReference<>();

        // producto() tiene activo=true por default
        TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), null, om, "9",
                java.util.List.of(),
                sku -> existente,
                (uri, body) -> patchBody.set(body),
                (productId, variantId, price, promo) -> true);
        assertThat(patchBody.get()).contains("\"published\":true");

        Producto inactivo = producto();
        inactivo.setActivo(false);
        AtomicReference<String> patchBody2 = new AtomicReference<>();
        TiendaNubeService.actualizarProductoEnNubeCore(
                inactivo, new BigDecimal("150"), null, om, "9",
                java.util.List.of(),
                sku -> existente,
                (uri, body) -> patchBody2.set(body),
                (productId, variantId, price, promo) -> true);
        assertThat(patchBody2.get()).contains("\"published\":false");
    }
```

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `./mvnw -o test -Dtest=ActualizarProductoEnNubeTest`
Expected: FAIL en `actualiza_incluyePublishedSegunActivo` — el body no contiene `published`.

- [ ] **Step 3: Implementar el cambio mínimo**

En `TiendaNubeService.java`, dentro de `actualizarProductoEnNubeCore`, después de la línea que pone `description` (línea 937) y antes del bloque de `categories`, agregar:

```java
            body.put("published", Boolean.TRUE.equals(producto.getActivo()));
```

El bloque queda así:

```java
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", Map.of("es", producto.getTituloNube() != null ? producto.getTituloNube() : ""));
            body.put("description", Map.of("es", NubeDescripcionBuilder.construir(producto)));
            body.put("published", Boolean.TRUE.equals(producto.getActivo()));
            if (categoriaIds != null && !categoriaIds.isEmpty()) {
                body.put("categories", new ArrayList<>(categoriaIds));
            }
```

- [ ] **Step 4: Correr el test para verificar que pasa**

Run: `./mvnw -o test -Dtest=ActualizarProductoEnNubeTest`
Expected: PASS (todos; los tests previos no se rompen porque solo se agrega una clave al body).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/ActualizarProductoEnNubeTest.java
git commit -m "feat(nube): published segun activo en el update (PATCH) del producto"
```

---

### Task 3: ML — cambio de `status` (active/paused) en el update

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (nuevo functional interface ~línea 1670; `actualizarItemEnMlCore` ~1681-1724; método real `updateItemStatus` nuevo; wiring en `actualizarItemEnMl` ~1730-1760)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/ActualizarItemEnMlTest.java`

**Interfaces:**
- Produces:
  - `interface ActualizadorEstadoItem { boolean actualizar(String mla, String status); }` (anidado en `MercadoLibreService`, junto a `ActualizadorPrecioItem`).
  - `actualizarItemEnMlCore(...)` gana un **9.º parámetro al final**: `ActualizadorEstadoItem putStatus`.
  - `public boolean updateItemStatus(String mla, String status)` — PUT real; `false` si falla.

- [ ] **Step 1: Actualizar los 7 tests existentes + agregar el test nuevo (todos fallan a compilar primero)**

En `ActualizarItemEnMlTest`, **agregar el argumento de status** (`(mla, status) -> true`) como último parámetro a CADA una de las 7 llamadas a `actualizarItemEnMlCore`. Ejemplo para `sinVentas_actualizaTituloDescripcionYPrecio`:

```java
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA111",
                mla -> 0,
                (mla, t) -> titulo.set(t),
                (mla, d) -> desc.set(d),
                (mla, p) -> { precio[0] = p; return true; },
                sku -> java.util.List.of(),
                (mla, pics) -> {},
                (mla, status) -> true);   // <-- nuevo: putStatus
```

Aplicar el mismo agregado (`, (mla, status) -> true)` antes del `)` final) en las 7 llamadas: `sinVentas_actualizaTituloDescripcionYPrecio`, `conVentas_salteaTituloYAvisa`, `faltaTitulo_error`, `conImagenes_reemplazaPictures`, `sinImagenes_noLlamaPutPictures`, `fallaImagenes_siguenActualizadoConAdvertencia`, `fallaPrecio_siguenActualizadoConAdvertencia`.

Luego agregar el `producto()` con helper de activo y dos tests nuevos:

```java
    private Producto productoActivo(boolean activo) {
        Producto p = producto();
        p.setActivo(activo);
        return p;
    }

    @Test
    void activo_poneStatusActive() {
        AtomicReference<String> statusPuesto = new AtomicReference<>();
        MercadoLibreService.actualizarItemEnMlCore(
                productoActivo(true), "MLA888",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> { statusPuesto.set(status); return true; });
        assertThat(statusPuesto.get()).isEqualTo("active");
    }

    @Test
    void inactivo_poneStatusPaused() {
        AtomicReference<String> statusPuesto = new AtomicReference<>();
        MercadoLibreService.actualizarItemEnMlCore(
                productoActivo(false), "MLA999",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> { statusPuesto.set(status); return true; });
        assertThat(statusPuesto.get()).isEqualTo("paused");
    }

    @Test
    void fallaStatus_sigueActualizadoConAdvertencia() {
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                productoActivo(true), "MLA1010",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> false);   // putStatus falla
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.advertencia()).contains("estado");
    }
```

- [ ] **Step 2: Correr los tests para verificar que fallan (compilación)**

Run: `./mvnw -o test -Dtest=ActualizarItemEnMlTest`
Expected: FAIL de compilación — `actualizarItemEnMlCore` aún tiene 8 parámetros, no 9.

- [ ] **Step 3: Agregar el functional interface, el parámetro y el paso en el core**

En `MercadoLibreService.java`, junto a `ActualizadorPrecioItem` (después de su `}`, ~línea 1670), agregar:

```java
    /** Cambia el estado de publicación de un item (mla, "active"|"paused") -> ok. */
    @FunctionalInterface
    public interface ActualizadorEstadoItem {
        boolean actualizar(String mla, String status);
    }
```

En la firma de `actualizarItemEnMlCore`, agregar el parámetro al final:

```java
    public static ResultadoAltaMl actualizarItemEnMlCore(
            ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto, String mla,
            Function<String, Integer> soldQtyFn,
            BiConsumer<String, String> putTitle,
            BiConsumer<String, String> putDesc,
            ActualizadorPrecioItem updatePrice,
            Function<String, List<String>> resolverPictureIds,
            BiConsumer<String, List<String>> putPictures,
            ActualizadorEstadoItem putStatus) {
```

Dentro del `try`, después del bloque de pictures (el `try/catch` que termina en `"imágenes no actualizadas"`, línea ~1717) y antes de `ResultadoAltaMl r = ResultadoAltaMl.actualizado(mla);`, agregar:

```java
            String estadoTarget = Boolean.TRUE.equals(producto.getActivo()) ? "active" : "paused";
            if (!putStatus.actualizar(mla, estadoTarget)) {
                advertencia = (advertencia == null ? "" : advertencia + "; ") + "estado no actualizado";
            }
```

- [ ] **Step 4: Implementar el método real `updateItemStatus` y conectarlo en `actualizarItemEnMl`**

Agregar el método (cerca de `updateItemPrice`, dentro de `MercadoLibreService`):

```java
    /**
     * Cambia el status de publicación de un item (active/paused). Devuelve false si no se pudo
     * (p. ej. el item está closed y no se puede reactivar) — el caller lo reporta como advertencia.
     */
    public boolean updateItemStatus(String mla, String status) {
        verificarTokens();
        try {
            retryHandler.putJson("/items/" + mla, () -> tokens.accessToken,
                    objectMapper.writeValueAsString(Map.of("status", status)));
            return true;
        } catch (Exception e) {
            log.warn("ML - Error actualizando status de item {} a {}: {}", mla, status, e.getMessage());
            return false;
        }
    }
```

En `actualizarItemEnMl`, agregar `this::updateItemStatus` como último argumento de la llamada a `actualizarItemEnMlCore` (después de la lambda de pictures, antes del `)` final de la línea ~1759):

```java
                (m, pictureIds) -> {
                    try {
                        List<Map<String, Object>> pics = new ArrayList<>();
                        for (String id : pictureIds) pics.add(Map.of("id", id));
                        retryHandler.putJson("/items/" + m, () -> tokens.accessToken,
                                objectMapper.writeValueAsString(Map.of("pictures", pics)));
                    } catch (Exception e) { throw new RuntimeException("imágenes: " + e.getMessage(), e); }
                },
                this::updateItemStatus);
```

- [ ] **Step 5: Correr los tests para verificar que pasan**

Run: `./mvnw -o test -Dtest=ActualizarItemEnMlTest`
Expected: PASS (10 tests: 7 originales + `activo_poneStatusActive`, `inactivo_poneStatusPaused`, `fallaStatus_sigueActualizadoConAdvertencia`).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/ActualizarItemEnMlTest.java
git commit -m "feat(ml): status active/paused segun activo al actualizar item (best-effort)"
```

---

### Task 4: ML — `paused` al crear un producto inactivo

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (`crearItemEnMl`, ~1869-1881)

**Interfaces:**
- Consumes: `crearItemEnMl(Producto) -> ResultadoAltaMl` (el resultado tiene `itemId()`), y `updateItemStatus(String, String)` de la Task 3.

**Nota:** este camino es I/O (red), sin núcleo testeable nuevo. Se valida en smoke. No agrega test unitario (igual que el resto del alta ML real). El paso es best-effort: si el `PUT` de status falla, NO debe romper el alta ya exitosa.

- [ ] **Step 1: Implementar el paso post-alta**

En `crearItemEnMl`, capturar el resultado y, si el producto está inactivo y el item se creó, pausarlo:

```java
    public ResultadoAltaMl crearItemEnMl(ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto) {
        if (!isConfigured()) return ResultadoAltaMl.error("Mercado Libre no configurado");
        verificarTokens();
        ResultadoAltaMl r = crearItemEnMlCore(
                producto, objectMapper,
                sku -> false,  // existencia ya verificada por el caller (upsert en MlExportService)
                sku -> imagenService.resolverArchivosPorSku(sku),
                filename -> subirImagenItem(filename),
                titulo -> predecirCategoria(titulo),
                json -> postearItem(json),
                (itemId, plainText) -> retryHandler.postJson("/items/" + itemId + "/description",
                        () -> tokens.accessToken, objectMapper.writeValueAsString(Map.of("plain_text", plainText))));

        // Producto inactivo: dejar la publicación recién creada en paused (best-effort).
        if (r.estado() == ResultadoAltaMl.Estado.CREADO
                && r.itemId() != null
                && !Boolean.TRUE.equals(producto.getActivo())
                && !updateItemStatus(r.itemId(), "paused")) {
            return r.conAdvertencia("estado no actualizado (no se pudo pausar)");
        }
        return r;
    }
```

- [ ] **Step 2: Compilar para verificar que no hay errores**

Run: `./mvnw -o test-compile`
Expected: BUILD SUCCESS (cambio aditivo; `ResultadoAltaMl.Estado.CREADO`, `.itemId()` y `.conAdvertencia(...)` ya existen — confirmar nombres en `ResultadoAltaMl`).

- [ ] **Step 3: Correr los tests de ML para confirmar que nada se rompió**

Run: `./mvnw -o test -Dtest=ActualizarItemEnMlTest,CrearItemEnMlTest,ResultadoAltaMlTest`
Expected: PASS (todos).

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java
git commit -m "feat(ml): pausar la publicacion recien creada si el producto esta inactivo"
```

---

## Verificación final (tras las 4 tareas)

- [ ] Correr la suite de los archivos tocados:

Run: `./mvnw -o test -Dtest=CrearProductoEnNubeTest,ActualizarProductoEnNubeTest,ActualizarItemEnMlTest,CrearItemEnMlTest,ResultadoAltaMlTest`
Expected: PASS en todos.

- [ ] **Smoke (usuario):** editar un producto publicado en Nube/ML, desmarcar "Activo", tildar los canales y guardar → en Nube `published:false` (oculto), en ML `paused`. Volver a marcar "Activo" y guardar → `published:true`, `status:active`. Verificar el caso ML `closed` (advertencia "estado no actualizado").

## Notas de diseño / decisiones (de la spec)

- **Dux:** sin cambios (`DuxItemBuilder` ya manda `habilitado=S/N` según `activo`).
- **Frontend:** sin cambios estructurales (el checkbox "Activo" y los de canal ya existen).
- **No se implementa** borrado/cierre definitivo (ML `closed`+`deleted`, Nube `DELETE`): la baja es siempre reversible.
- El cambio de Nube en el alta hace que un producto **activo** se publique (`published:true`), cambiando el comportamiento actual (hoy crea oculto). Decisión confirmada por el usuario.
- **Stock ML (decisión del usuario, 2026-06-21):** el stock se maneja **fuera de supermaster**; esta feature solo cambia `status` (no toca `available_quantity`). La visibilidad real en ML depende del stock: los items nacen `out_of_stock` (alta con `available_quantity=0`), y reactivar con `status=active` no hace visible un item sin stock. `status=paused` deja `paused_by_seller` (no se reactiva al reponer stock, solo manualmente con `active`) — correcto para baja deliberada. Tenerlo presente en el smoke: "activar" en ML saca de `paused_by_seller` pero la visibilidad la define el stock.
