# Nube/ML: actualizar al editar (Spec A / D1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Al editar un producto y marcar Tienda Nube / Mercado Libre, **actualizar** la publicación existente (título + precio + descripción) en vez de intentar darla de alta; con patrón upsert (crear si no existe, actualizar si existe), texto genérico "Sincronizar con …" en los checkboxes de canal, y reporte de creados/actualizados/errores.

**Architecture:** Cada export service decide por SKU si la publicación ya existe y llama a un nuevo método de actualización o al alta actual. La actualización se extrae a un "core" testeable con lambdas (sin red), igual que el alta (`crearProductoEnNubeCore`/`crearItemEnMlCore`). Los records de resultado (`ResultadoAltaNube`/`ResultadoAltaMl`) suman un estado `ACTUALIZADO`; los DTOs de respuesta suman `actualizados`. El frontend solo cambia labels y el toast.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven / JUnit5 + AssertJ (backend). Next.js / React / TypeScript (frontend).

## Global Constraints

- **Upsert por SKU**: si la publicación existe en el canal → actualizar; si no → crear (el alta actual, sin tocar). El frontend NO cambia su llamada; la decisión vive en el backend.
- **Nube actualiza**: `name` (= `{es: tituloNube}`), `description` (= `{es: NubeDescripcionBuilder.construir(p)}`) vía `PATCH /products/{id}`, y el precio de la variante vía `actualizarPrecioVariante`. Imágenes y categorías NO se tocan (eso es D2).
- **ML actualiza**: `title` SOLO si `sold_quantity == 0` (si tuvo ventas, se saltea y se agrega aviso "título no actualizado por tener ventas"); `description` vía `PUT /items/{mla}/description`; `price` vía `updateItemPrice`. Imágenes y categoría NO se tocan (D2).
- **Riesgo ML precio (validar en smoke)**: ML (marzo 2026) puede rechazar un `PUT /items/{id}` que envía SOLO `price`. `updateItemPrice` hace exactamente eso. Implementarlo con `updateItemPrice` (existente) y dejar el riesgo documentado; si en la prueba real falla, el follow-up es usar la API de precios `/items/{id}/prices`. NO implementar esa rama alternativa salvo que la prueba lo exija (YAGNI).
- **Labels**: los checkboxes de los **4** canales (Dux, KT HOGAR, KT GASTRO, Mercado Libre) usan texto genérico **fijo** "Sincronizar con …" — NO depende de editar vs crear, porque el backend hace upsert (sube si no está, actualiza si está). Reemplaza el condicional actual de Dux (`editandoProductoId ? "Actualizar en Dux" : "Subir a Dux"`).
- **Reporte**: `ExportNubeResultDTO` y `MlExportResultDTO` suman `List<String> actualizados`; el toast del front muestra "N creado(s) · M actualizado(s) · K con error: …" + avisos.
- **Transaccionalidad**: `NubeExportService.exportar` sigue `@Transactional(readOnly = true)` (el I/O de red ocurre dentro, patrón ya aceptado para este export de bajo volumen). `MlExportService.exportar` sigue SIN `@Transactional`; la carga del producto (LAZY) ocurre en un método `self.*` `@Transactional(readOnly = true)`. El post-alta best-effort SOLO corre en el camino de creación.
- Commits en español, terminando con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Fuera de alcance (D2): actualización de imágenes y categorías; variaciones/atributos.

---

## File Structure

**Backend Nube (modificados):**
- `apis/nube/dto/ResultadoAltaNube.java` — estado `ACTUALIZADO` + factory `actualizado(...)`.
- `apis/nube/dto/ExportNubeResultDTO.java` — campo `actualizados`.
- `apis/nube/service/TiendaNubeService.java` — `actualizarProductoEnNubeCore` (testeable) + `actualizarProductoEnNube` (con red).
- `apis/nube/service/NubeExportService.java` — ramo upsert + contador `actualizados`.

**Backend ML (modificados):**
- `apis/ml/dto/ResultadoAltaMl.java` — estado `ACTUALIZADO` + factory `actualizado(...)`.
- `apis/ml/dto/MlExportResultDTO.java` — campo `actualizados`.
- `apis/ml/service/MercadoLibreService.java` — `actualizarItemEnMlCore` (testeable) + `actualizarItemEnMl` (con red).
- `apis/ml/service/MlExportService.java` — ramo upsert.

**Frontend (modificados):**
- `src/app/productos/page.tsx` — labels condicionales Nube/ML + `reportarExportToast` con `actualizados`.
- `src/app/productos/productosService.ts` — `actualizados` en `ExportNubeResultDTO`/`ExportMlResultDTO`.

**Tests (nuevos):**
- `src/test/java/.../apis/nube/ActualizarProductoEnNubeTest.java`
- `src/test/java/.../apis/ml/ActualizarItemEnMlTest.java`

---

### Task 1: Tienda Nube — actualizar al editar (upsert)

**Files:**
- Modify: `apis/nube/dto/ResultadoAltaNube.java`
- Modify: `apis/nube/dto/ExportNubeResultDTO.java`
- Modify: `apis/nube/service/TiendaNubeService.java`
- Modify: `apis/nube/service/NubeExportService.java`
- Test: `src/test/java/ar/com/leo/super_master_backend/apis/nube/ActualizarProductoEnNubeTest.java`

**Interfaces:**
- Consumes: `buscarProductoPorSku(sku, storeName) → JsonNode` (id, variants[].id/sku); `actualizarPrecioVariante(storeName, productId, variantId, price, promotionalPrice) → boolean`; `NubeDescripcionBuilder.construir(Producto) → String`; `retryHandler.patchJson(uri, accessToken, jsonBody)`.
- Produces: `ResultadoAltaNube.Estado.ACTUALIZADO` + `ResultadoAltaNube.actualizado(Long productoNubeId)`; `TiendaNubeService.actualizarProductoEnNube(String storeName, Producto producto, BigDecimal pvp, BigDecimal pvpInflado) → ResultadoAltaNube`; `ExportNubeResultDTO` con `List<String> actualizados`.

- [ ] **Step 1: Estado ACTUALIZADO en el record**

En `ResultadoAltaNube.java`, agregar el estado y la factory:

```java
package ar.com.leo.super_master_backend.apis.nube.dto;

public record ResultadoAltaNube(Estado estado, String motivo, Long productoNubeId, String advertencia) {
    public enum Estado { CREADO, ACTUALIZADO, YA_EXISTIA, ERROR }
    public static ResultadoAltaNube creado(Long productoNubeId) { return new ResultadoAltaNube(Estado.CREADO, null, productoNubeId, null); }
    public static ResultadoAltaNube actualizado(Long productoNubeId) { return new ResultadoAltaNube(Estado.ACTUALIZADO, null, productoNubeId, null); }
    public static ResultadoAltaNube yaExistia() { return new ResultadoAltaNube(Estado.YA_EXISTIA, null, null, null); }
    public static ResultadoAltaNube error(String motivo) { return new ResultadoAltaNube(Estado.ERROR, motivo, null, null); }

    public ResultadoAltaNube conAdvertencia(String advertencia) {
        return new ResultadoAltaNube(estado, motivo, productoNubeId, advertencia);
    }
}
```

- [ ] **Step 2: Campo `actualizados` en el DTO de resultado**

En `ExportNubeResultDTO.java`:

```java
public record ExportNubeResultDTO(int creados, List<String> actualizados, List<String> yaExistian, List<String> errores, List<String> advertencias) {}
```

(Nota: esto cambia la aridad del record — el Step 7 actualiza el único `new ExportNubeResultDTO(...)` en `NubeExportService`.)

- [ ] **Step 3: Test del core de actualización (falla)**

Crear `ActualizarProductoEnNubeTest.java`. El core `actualizarProductoEnNubeCore` arma el body PATCH (name+description), elige el `variantId` por SKU y dispara precio mediante lambdas (sin red):

```java
package ar.com.leo.super_master_backend.apis.nube;

import ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ActualizarProductoEnNubeTest {

    private final ObjectMapper om = new ObjectMapper();

    private Producto producto() {
        Producto p = new Producto();
        p.setSku("1234567");
        p.setTituloNube("Olla acero 24cm");
        return p;
    }

    private JsonNode existente(String json) {
        try { return om.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void actualiza_armaPatchYPrecio() {
        // producto existente en Nube con id 500 y una variante (id 900) cuyo sku coincide
        JsonNode existente = existente("{\"id\":500,\"variants\":[{\"id\":900,\"sku\":\"1234567\",\"price\":\"100\"}]}");
        AtomicReference<String> patchUri = new AtomicReference<>();
        AtomicReference<String> patchBody = new AtomicReference<>();
        AtomicReference<String> precioPrice = new AtomicReference<>();
        AtomicReference<String> precioPromo = new AtomicReference<>();
        long[] precioIds = new long[2];

        ResultadoAltaNube r = TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), new BigDecimal("180"), om, "9",
                sku -> existente,
                (uri, body) -> { patchUri.set(uri); patchBody.set(body); },
                (productId, variantId, price, promo) -> {
                    precioIds[0] = productId; precioIds[1] = variantId;
                    precioPrice.set(price); precioPromo.set(promo); return true;
                });

        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ACTUALIZADO);
        assertThat(r.productoNubeId()).isEqualTo(500L);
        assertThat(patchUri.get()).isEqualTo("/9/products/500");
        assertThat(patchBody.get()).contains("\"name\"").contains("Olla acero 24cm").contains("\"description\"");
        assertThat(precioIds[0]).isEqualTo(500L);
        assertThat(precioIds[1]).isEqualTo(900L);
        // pvpInflado(180) > pvp(150): price=180 (tachado), promotional=150
        assertThat(precioPrice.get()).isEqualTo("180");
        assertThat(precioPromo.get()).isEqualTo("150");
    }

    @Test
    void actualiza_sinInflado_soloPrice() {
        JsonNode existente = existente("{\"id\":1,\"variants\":[{\"id\":2,\"sku\":\"1234567\"}]}");
        AtomicReference<String> precioPrice = new AtomicReference<>();
        AtomicReference<String> precioPromo = new AtomicReference<>();

        TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), null, om, "9",
                sku -> existente, (uri, body) -> {},
                (productId, variantId, price, promo) -> { precioPrice.set(price); precioPromo.set(promo); return true; });

        assertThat(precioPrice.get()).isEqualTo("150");
        assertThat(precioPromo.get()).isNull();
    }

    @Test
    void actualiza_noExiste_error() {
        ResultadoAltaNube r = TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), null, om, "9",
                sku -> null, (uri, body) -> {}, (a, b, c, d) -> true);
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
    }
}
```

- [ ] **Step 4: Correr el test (falla)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarProductoEnNubeTest test`
Expected: FAIL (no compila: `actualizarProductoEnNubeCore` no existe).

- [ ] **Step 5: Implementar el core + el método con red**

En `TiendaNubeService.java`, agregar una interfaz funcional para el precio y los dos métodos. Importar lo necesario (`java.util.function.BiConsumer`, `JsonNode`, `BigDecimal`, `LinkedHashMap`, `Map`).

```java
/** Actualiza price/promotional_price de una variante. */
@FunctionalInterface
public interface ActualizadorPrecioVariante {
    boolean actualizar(long productId, long variantId, String price, String promotionalPrice);
}

/** Núcleo testeable de la actualización (sin red). buscador(sku)->JSON existente; patcher(uri,body)->PATCH; precioFn aplica el precio. */
static ResultadoAltaNube actualizarProductoEnNubeCore(
        Producto producto, BigDecimal pvp, BigDecimal pvpInflado, ObjectMapper om, String storeId,
        java.util.function.Function<String, JsonNode> buscador,
        java.util.function.BiConsumer<String, String> patcher,
        ActualizadorPrecioVariante precioFn) {
    try {
        JsonNode existente = buscador.apply(producto.getSku());
        if (existente == null) return ResultadoAltaNube.error("no encontrado en Nube al actualizar");
        long productId = existente.path("id").asLong(0);
        if (productId <= 0) return ResultadoAltaNube.error("id de producto Nube inválido");

        // variantId: la variante cuyo sku coincide; si no, la primera.
        long variantId = 0;
        JsonNode variants = existente.path("variants");
        if (variants.isArray()) {
            for (JsonNode v : variants) {
                if (producto.getSku().equals(v.path("sku").asText(null))) { variantId = v.path("id").asLong(0); break; }
            }
            if (variantId == 0 && variants.size() > 0) variantId = variants.get(0).path("id").asLong(0);
        }
        if (variantId <= 0) return ResultadoAltaNube.error("variante Nube no encontrada");

        // PATCH name + description
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", Map.of("es", producto.getTituloNube() != null ? producto.getTituloNube() : ""));
        body.put("description", Map.of("es", NubeDescripcionBuilder.construir(producto)));
        patcher.accept("/" + storeId + "/products/" + productId, om.writeValueAsString(body));

        // Precio (misma lógica que el alta: inflado => price tachado + promotional)
        String price = null, promo = null;
        if (pvpInflado != null && pvp != null && pvpInflado.compareTo(pvp) > 0) {
            price = pvpInflado.toPlainString(); promo = pvp.toPlainString();
        } else if (pvp != null) {
            price = pvp.toPlainString();
        }
        if (price != null) precioFn.actualizar(productId, variantId, price, promo);

        return ResultadoAltaNube.actualizado(productId);
    } catch (Exception e) {
        return ResultadoAltaNube.error(e.getMessage());
    }
}

/** Actualiza un producto existente en Nube (name/description/precio). Resuelve credenciales y delega al core. */
public ResultadoAltaNube actualizarProductoEnNube(String storeName, Producto producto, BigDecimal pvp, BigDecimal pvpInflado) {
    StoreCredentials store;
    try {
        verificarCredenciales();
        store = getStore(storeName);
    } catch (Exception e) {
        return ResultadoAltaNube.error("Tienda Nube no configurada: " + e.getMessage());
    }
    if (store == null) return ResultadoAltaNube.error("Tienda '" + storeName + "' no configurada");
    return actualizarProductoEnNubeCore(
            producto, pvp, pvpInflado, objectMapper, store.getStoreId(),
            sku -> buscarProductoPorSku(sku, storeName),
            (uri, body) -> retryHandler.patchJson(uri, store.getAccessToken(), body),
            (productId, variantId, price, promo) ->
                    actualizarPrecioVariante(storeName, productId, variantId, price, promo));
}
```

> NOTA: si `StoreCredentials.getStoreId()` devuelve un tipo no-String, convertir con `String.valueOf(...)` al pasar `storeId` al core (el core lo usa solo para construir la URI). Verificar el tipo real de `getStoreId()` y ajustar.

- [ ] **Step 6: Correr el test (pasa)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarProductoEnNubeTest test`
Expected: PASS (3 tests).

- [ ] **Step 7: Upsert en `NubeExportService.exportar`**

En el bucle interno (por producto × tienda), reemplazar la llamada directa a `crearProductoEnNube` por: buscar si existe → actualizar; si no → crear. Agregar `List<String> actualizados` y el caso `ACTUALIZADO` al switch; actualizar el `return` con la nueva aridad del DTO.

```java
@Transactional(readOnly = true)
public ExportNubeResultDTO exportar(ExportNubeRequestDTO request) {
    int creados = 0;
    List<String> actualizados = new ArrayList<>();
    List<String> yaExistian = new ArrayList<>();
    List<String> errores = new ArrayList<>();
    List<String> advertencias = new ArrayList<>();

    if (request == null || request.skus() == null || request.tiendas() == null) {
        return new ExportNubeResultDTO(0, actualizados, yaExistian, errores, advertencias);
    }

    List<Producto> productos = productoRepository.findBySkuIn(
            request.skus().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList());

    Map<String, NubeCategoriaArbol> arbolesPorTienda = new HashMap<>();

    for (Producto producto : productos) {
        for (ExportNubeRequestDTO.DestinoNube destino : request.tiendas()) {
            String tienda = destino.tienda();
            String etiqueta = producto.getSku() + " / " + tienda;
            Optional<Canal> canal = canalRepository.findByNombreIgnoreCase(tienda);
            if (canal.isEmpty()) { errores.add(etiqueta + ": canal '" + tienda + "' no existe"); continue; }
            Optional<ProductoCanalPrecio> precio = precioRepository
                    .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.get().getId(), destino.cuotas());
            if (precio.isEmpty()) { errores.add(etiqueta + ": sin precio calculado para esa cuota"); continue; }
            if (precio.get().isObsoleto()) { errores.add(etiqueta + ": precio desactualizado (recalcular antes de subir)"); continue; }

            // Upsert: si ya existe en la tienda, actualizar; si no, crear.
            ResultadoAltaNube r;
            if (tiendaNubeService.buscarProductoPorSku(producto.getSku(), tienda) != null) {
                r = tiendaNubeService.actualizarProductoEnNube(
                        tienda, producto, precio.get().getPvp(), precio.get().getPvpInflado());
            } else {
                NubeCategoriaArbol arbol = arbolesPorTienda.computeIfAbsent(
                        tienda, tiendaNubeService::cargarArbolCategorias);
                r = tiendaNubeService.crearProductoEnNube(
                        tienda, producto, precio.get().getPvp(), precio.get().getPvpInflado(), arbol);
            }
            switch (r.estado()) {
                case CREADO -> {
                    creados++;
                    if (r.advertencia() != null) advertencias.add(etiqueta + ": " + r.advertencia());
                }
                case ACTUALIZADO -> {
                    actualizados.add(etiqueta);
                    if (r.advertencia() != null) advertencias.add(etiqueta + ": " + r.advertencia());
                }
                case YA_EXISTIA -> yaExistian.add(etiqueta);
                case ERROR -> errores.add(etiqueta + ": " + r.motivo());
            }
        }
    }
    return new ExportNubeResultDTO(creados, actualizados, yaExistian, errores, advertencias);
}
```

- [ ] **Step 8: Compilar + correr el test suite tocado**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarProductoEnNubeTest,CrearProductoEnNubeTest test`
Expected: PASS. Luego `./mvnw -q -DskipTests compile` → BUILD SUCCESS.

> NOTA: `mvnw compile` solo compila `src/main`. Si algún test o código construía `ExportNubeResultDTO` posicionalmente, la nueva aridad lo rompe en `src/test` — correr `./mvnw -q test -Dtest=*Nube*` o el grupo afectado para detectarlo (ver memoria sobre records y constructores posicionales).

- [ ] **Step 9: Commit**

```bash
git add supermaster-backend/src/main/java/.../apis/nube supermaster-backend/src/test/java/.../apis/nube/ActualizarProductoEnNubeTest.java
git commit -m "feat(nube): actualizar publicacion existente al editar (upsert name/description/precio)"
```

---

### Task 2: Mercado Libre — actualizar al editar (upsert)

**Files:**
- Modify: `apis/ml/dto/ResultadoAltaMl.java`
- Modify: `apis/ml/dto/MlExportResultDTO.java`
- Modify: `apis/ml/service/MercadoLibreService.java`
- Modify: `apis/ml/service/MlExportService.java`
- Test: `src/test/java/ar/com/leo/super_master_backend/apis/ml/ActualizarItemEnMlTest.java`

**Interfaces:**
- Consumes: `buscarMlaPorSku(sku) → MlaPorSku(mla, mlau)`; `updateItemPrice(itemId, price) → boolean`; `MlDescripcionBuilder.construir(Producto) → String`; `retryHandler.putJson(uri, tokenSupplier, jsonBody)`; `retryHandler.get(uri, tokenSupplier) → String`; `producto.getMla() → Mla` (con `getMla()` = código MLA).
- Produces: `ResultadoAltaMl.Estado.ACTUALIZADO` + `ResultadoAltaMl.actualizado(String itemId)`; `MercadoLibreService.actualizarItemEnMl(Producto producto, String mla) → ResultadoAltaMl`; `MlExportResultDTO` con `List<String> actualizados`.

- [ ] **Step 1: Estado ACTUALIZADO en el record**

En `ResultadoAltaMl.java`:

```java
package ar.com.leo.super_master_backend.apis.ml.dto;

public record ResultadoAltaMl(Estado estado, String motivo, String itemId, String mlau, String advertencia) {
    public enum Estado { CREADO, ACTUALIZADO, YA_EXISTIA, ERROR }
    public static ResultadoAltaMl creado(String itemId, String mlau) { return new ResultadoAltaMl(Estado.CREADO, null, itemId, mlau, null); }
    public static ResultadoAltaMl actualizado(String itemId) { return new ResultadoAltaMl(Estado.ACTUALIZADO, null, itemId, null, null); }
    public static ResultadoAltaMl yaExistia() { return new ResultadoAltaMl(Estado.YA_EXISTIA, null, null, null, null); }
    public static ResultadoAltaMl error(String motivo) { return new ResultadoAltaMl(Estado.ERROR, motivo, null, null, null); }
    public ResultadoAltaMl conAdvertencia(String advertencia) {
        return new ResultadoAltaMl(estado, motivo, itemId, mlau, advertencia);
    }
}
```

- [ ] **Step 2: Campo `actualizados` en el DTO de resultado**

En `MlExportResultDTO.java`:

```java
public record MlExportResultDTO(int creados, List<String> actualizados, List<String> yaExistian, List<String> errores, List<String> advertencias) {}
```

- [ ] **Step 3: Test del core de actualización (falla)**

Crear `ActualizarItemEnMlTest.java`. El core `actualizarItemEnMlCore`: lee `sold_quantity` (lambda), actualiza title solo si 0 (lambda), actualiza description (lambda), actualiza price (lambda). El precio = `costo × 5` (misma regla que el alta C1).

```java
package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ActualizarItemEnMlTest {

    private Producto producto() {
        Producto p = new Producto();
        p.setSku("1234567");
        p.setTituloMl("Olla acero 24cm premium");
        p.setCosto(new BigDecimal("1000"));
        return p;
    }

    @Test
    void sinVentas_actualizaTituloDescripcionYPrecio() {
        AtomicReference<String> titulo = new AtomicReference<>();
        AtomicReference<String> desc = new AtomicReference<>();
        double[] precio = new double[1];

        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA111",
                mla -> 0,                          // sold_quantity = 0
                (mla, t) -> titulo.set(t),
                (mla, d) -> desc.set(d),
                (mla, p) -> { precio[0] = p; return true; });

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.itemId()).isEqualTo("MLA111");
        assertThat(r.advertencia()).isNull();
        assertThat(titulo.get()).isEqualTo("Olla acero 24cm premium");
        assertThat(desc.get()).contains("CARACTERÍSTICAS");
        assertThat(precio[0]).isEqualTo(5000.0); // 1000 x 5
    }

    @Test
    void conVentas_salteaTituloYAvisa() {
        AtomicReference<String> titulo = new AtomicReference<>();
        double[] precio = new double[1];

        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA222",
                mla -> 7,                          // sold_quantity > 0
                (mla, t) -> titulo.set(t),
                (mla, d) -> {},
                (mla, p) -> { precio[0] = p; return true; });

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(titulo.get()).isNull();          // título NO actualizado
        assertThat(r.advertencia()).contains("ventas");
        assertThat(precio[0]).isEqualTo(5000.0);    // precio sí
    }

    @Test
    void faltaTitulo_error() {
        Producto p = producto();
        p.setTituloMl(null);
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                p, "MLA333", mla -> 0, (a, b) -> {}, (a, b) -> {}, (a, b) -> true);
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
    }
}
```

- [ ] **Step 4: Correr el test (falla)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarItemEnMlTest test`
Expected: FAIL (no compila: `actualizarItemEnMlCore` no existe).

- [ ] **Step 5: Implementar el core + el método con red**

En `MercadoLibreService.java`, agregar funciones e import (`java.util.function.Function`, `java.util.function.BiConsumer`, `java.util.function.BiFunction`, `Map`).

```java
/** Actualiza el precio de un item (mla, price)->ok. */
@FunctionalInterface
public interface ActualizadorPrecioItem {
    boolean actualizar(String mla, double price);
}

/**
 * Núcleo testeable de la actualización de un item ML (sin red).
 *  - soldQtyFn(mla) → unidades vendidas (para decidir si se puede cambiar el título).
 *  - putTitle(mla, title) → PUT del título (solo se llama si sold_quantity == 0).
 *  - putDesc(mla, plainText) → PUT de la descripción.
 *  - updatePrice(mla, price) → actualiza el precio (price = costo × 5).
 */
static ResultadoAltaMl actualizarItemEnMlCore(
        Producto producto, String mla,
        Function<String, Integer> soldQtyFn,
        BiConsumer<String, String> putTitle,
        BiConsumer<String, String> putDesc,
        ActualizadorPrecioItem updatePrice) {
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

        ResultadoAltaMl r = ResultadoAltaMl.actualizado(mla);
        return advertencia == null ? r : r.conAdvertencia(advertencia);
    } catch (Exception e) {
        return ResultadoAltaMl.error(e.getMessage());
    }
}

/** Actualiza una publicación existente en ML (título si sin ventas, descripción, precio). Delega al core. */
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
            this::updateItemPrice);
}

/** Lee sold_quantity de un item (0 si no se puede determinar). */
private int leerSoldQuantity(String mla) {
    try {
        String body = retryHandler.get("/items/" + mla + "?attributes=sold_quantity", () -> tokens.accessToken);
        if (body == null) return 0;
        return objectMapper.readTree(body).path("sold_quantity").asInt(0);
    } catch (Exception e) {
        log.warn("ML - No se pudo leer sold_quantity de {}: {}", mla, e.getMessage());
        return 0;
    }
}
```

> NOTA RIESGO (validar en smoke): `updateItemPrice` hace `PUT /items/{id}` con SOLO `price`. ML (marzo 2026) puede rechazarlo. Si la prueba real falla con ese error, el follow-up es usar la API de precios `/items/{id}/prices`. No implementar esa rama ahora.

- [ ] **Step 6: Correr el test (pasa)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarItemEnMlTest test`
Expected: PASS (3 tests).

- [ ] **Step 7: Upsert en `MlExportService`**

Agregar un método `@Transactional(readOnly = true)` que cargue el producto, decida actualizar vs crear, y devuelva el resultado; `exportar` lo invoca vía `self.` y solo hace post-alta si fue CREADO. Agregar `List<String> actualizados`.

```java
public MlExportResultDTO exportar(MlExportRequestDTO request) {
    int creados = 0;
    List<String> actualizados = new ArrayList<>();
    List<String> yaExistian = new ArrayList<>();
    List<String> errores = new ArrayList<>();
    List<String> advertencias = new ArrayList<>();

    if (request == null || request.skus() == null) {
        return new MlExportResultDTO(0, actualizados, yaExistian, errores, advertencias);
    }

    List<Producto> productos = productoRepository.findBySkuIn(
            request.skus().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList());

    for (Producto producto : productos) {
        Integer productoId = producto.getId();
        String etiqueta = producto.getSku();
        ResultadoAltaMl r = self.procesarConProductoCargado(productoId);
        switch (r.estado()) {
            case CREADO -> {
                creados++;
                List<String> avisos = new ArrayList<>();
                if (r.advertencia() != null) avisos.add(r.advertencia());
                avisos.addAll(postAlta(productoId, r.itemId(), r.mlau()));
                for (String a : avisos) advertencias.add(etiqueta + ": " + a);
            }
            case ACTUALIZADO -> {
                actualizados.add(etiqueta);
                if (r.advertencia() != null) advertencias.add(etiqueta + ": " + r.advertencia());
            }
            case YA_EXISTIA -> yaExistian.add(etiqueta);
            case ERROR -> errores.add(etiqueta + ": " + r.motivo());
        }
    }
    return new MlExportResultDTO(creados, actualizados, yaExistian, errores, advertencias);
}

/**
 * Carga el producto (managed, lazy abierto) y decide: si ya existe publicación en ML
 * (producto.getMla() o búsqueda por SKU) → actualizar; si no → alta.
 */
@Transactional(readOnly = true)
public ResultadoAltaMl procesarConProductoCargado(Integer productoId) {
    Producto p = productoRepository.findById(productoId).orElse(null);
    if (p == null) return ResultadoAltaMl.error("Producto no encontrado");

    String mla = (p.getMla() != null) ? p.getMla().getMla() : null;
    if (mla == null) {
        var encontrado = mercadoLibreService.buscarMlaPorSku(p.getSku());
        if (encontrado != null) mla = encontrado.mla();
    }
    if (mla != null && !mla.isBlank()) {
        return mercadoLibreService.actualizarItemEnMl(p, mla);
    }
    return mercadoLibreService.crearItemEnMl(p);
}
```

Mantener el método `altaConProductoCargado` existente si algún otro flujo lo usa; si solo lo usaba `exportar`, puede quedar (no es necesario borrarlo). El `postAlta(...)` privado no cambia.

- [ ] **Step 8: Compilar + correr tests ML**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=ActualizarItemEnMlTest,CrearItemEnMlTest test`
Expected: PASS. Luego `./mvnw -q -DskipTests compile` → BUILD SUCCESS.

> NOTA: la nueva aridad de `MlExportResultDTO` rompe cualquier `new MlExportResultDTO(...)` posicional en tests. Correr `./mvnw -q test -Dtest=*Ml*` (o el grupo afectado) para detectarlo (memoria de records posicionales).

- [ ] **Step 9: Commit**

```bash
git add supermaster-backend/src/main/java/.../apis/ml supermaster-backend/src/test/java/.../apis/ml/ActualizarItemEnMlTest.java
git commit -m "feat(ml): actualizar publicacion existente al editar (upsert titulo/descripcion/precio)"
```

---

### Task 3: Frontend — labels "Sincronizar con …" + reporte de actualizados

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

**Interfaces:**
- Consumes: `ExportNubeResultDTO`/`MlExportResultDTO` del backend ahora traen `actualizados: string[]` (Tasks 1, 2).

- [ ] **Step 1: Tipos TS con `actualizados`**

En `productosService.ts`, agregar `actualizados: string[]` a ambos tipos:

```typescript
export type ExportNubeResultDTO = {
    creados: number;
    actualizados: string[];
    yaExistian: string[];
    errores: string[];
    advertencias: string[];
};

export type ExportMlResultDTO = {
    creados: number;
    actualizados: string[];
    yaExistian: string[];
    errores: string[];
    advertencias: string[];
};
```

- [ ] **Step 2: `reportarExportToast` muestra actualizados**

En `page.tsx`, extender la firma y el armado del mensaje:

```typescript
function reportarExportToast(plataforma: string, r: { creados: number; actualizados?: string[]; yaExistian: string[]; errores: string[]; advertencias?: string[] }) {
    const partes: string[] = [];
    if (r.creados > 0) partes.push(`${r.creados} creado(s)`);
    if (r.actualizados?.length) partes.push(`${r.actualizados.length} actualizado(s)`);
    if (r.yaExistian.length) partes.push(`${r.yaExistian.length} ya existía(n)`);
    if (r.advertencias?.length) partes.push(`avisos: ${r.advertencias.join("; ")}`);
    if (r.errores.length) partes.push(`${r.errores.length} con error: ${r.errores.join("; ")}`);
    if (r.errores.length) notificar.error(`${plataforma}: ${partes.join(" · ")}`);
    else notificar.success(`${plataforma}: ${partes.join(" · ") || "sin cambios"}`);
}
```

- [ ] **Step 3: Labels genéricos "Sincronizar con …" (4 canales)**

En la sección "Canales de venta" de `page.tsx`, poner texto **fijo** "Sincronizar con …" en los 4 labels (incluido Dux, que deja de usar el condicional `editandoProductoId`):

- Dux: `<label htmlFor="subirADux" className="cursor-pointer">Sincronizar con Dux</label>`
- KT HOGAR: `<label htmlFor="subirKtHogar" className="cursor-pointer">Sincronizar con KT HOGAR (Nube)</label>`
- KT GASTRO: `<label htmlFor="subirKtGastro" className="cursor-pointer">Sincronizar con KT GASTRO (Nube)</label>`
- Mercado Libre: `<label htmlFor="subirMl" className="cursor-pointer">Sincronizar con Mercado Libre</label>`

Además, simplificar el `title` (tooltip) del card de Dux —hoy `editandoProductoId ? "Al guardar, actualiza el producto en Dux" : "Al crear, sube el producto a Dux"`— a un texto genérico fijo, p. ej. `title="Sube el producto si no está, o lo actualiza si ya existe"`. (El `editandoProductoId` sigue usándose para el título del modal "Editar/Nuevo Producto" y para precargar el form — eso NO cambia.)

- [ ] **Step 4: Typecheck**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: exit 0.

- [ ] **Step 5: Verificar (smoke, manual del usuario)**

Editar un producto que ya esté publicado en Nube/ML y marcar el canal: el toast debe decir "N actualizado(s)"; los labels en edición dicen "Actualizar en …".

- [ ] **Step 6: Commit**

```bash
git add supermaster-frontend/src/app/productos
git commit -m "feat(front/productos): labels 'Actualizar en' en edicion + reporte de actualizados"
```

---

## Self-Review

**Spec coverage:**
- Upsert Nube (existe→actualizar, no→crear) → Task 1 Step 7. ✅
- Nube actualiza name/description/precio → Task 1 Step 5 (core + test). ✅
- Upsert ML + actualizar título(solo sin ventas)/descripción/precio → Task 2 Steps 5, 7. ✅
- Estados `ACTUALIZADO` + `actualizados` en DTOs → Tasks 1, 2. ✅
- Labels genéricos "Sincronizar con …" (4 canales, incluido Dux) + reporte actualizados → Task 3. ✅
- Transaccionalidad (Nube tx readOnly con I/O dentro; ML `self.procesarConProductoCargado` readOnly, post-alta solo en CREADO) → Tasks 1, 2. ✅
- Riesgo ML precio documentado (sin implementar rama alternativa) → Task 2 Step 5 nota. ✅
- Fuera de alcance (imágenes/categorías) → respetado. ✅

**Placeholder scan:** sin TBD/TODO; todo el código de cada step está presente.

**Type consistency:** `ResultadoAltaNube.actualizado(Long)`, `ResultadoAltaMl.actualizado(String)`, `actualizarProductoEnNube(String,Producto,BigDecimal,BigDecimal)`, `actualizarItemEnMl(Producto,String)`, `procesarConProductoCargado(Integer)`, `ExportNubeResultDTO(int,List,List,List,List)`, `MlExportResultDTO(int,List,List,List,List)`, `actualizados: string[]` — usados consistentemente. Los records de resultado cambian aridad: cada `new ExportNubeResultDTO(...)`/`new MlExportResultDTO(...)` se actualiza en su service (Steps 7), y las notas de Steps 8 recuerdan correr `mvnw test` para detectar constructores posicionales rotos en tests.

**Riesgos marcados:** (1) `StoreCredentials.getStoreId()` — verificar tipo y `String.valueOf` si hace falta (Task 1 Step 5 nota). (2) ML precio `PUT` solo-`price` (Task 2 Step 5 nota). (3) aridad de records de resultado vs constructores posicionales en tests (Steps 8 notas).
