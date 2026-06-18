# Alta de producto en Tienda Nube — Fase B1 — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar de alta un producto en Tienda Nube (oculto) vía `POST /products` cuando se marca KT HOGAR y/o KT GASTRO al guardar el producto, con nombre (Título Nube), descripción HTML, y variante con precio (PVP/PVP inflado de la cuota elegida) y peso/dimensiones fijos.

**Architecture:** Se agrega `postJson` al `NubeRetryHandler`; dos builders puros (descripción HTML y payload Map) testeables por unidad; `TiendaNubeService.crearProductoEnNube` orquesta (chequea duplicado, valida, postea); un `NubeExportService` + endpoint `POST /api/nube/exportar-productos` replican el patrón DUX (carga productos por SKU, resuelve canal+precio por tienda, arma resumen). En el frontend, los checkboxes KT HOGAR/GASTRO de "Canales de venta" se vuelven funcionales con un selector de cuota, y disparan la exportación al guardar.

**Tech Stack:** Spring Boot 4 / Java 25 / Jackson (ObjectMapper) / JUnit5 + Mockito + AssertJ (backend); Next.js 16 / React 19 / TypeScript (frontend).

**Referencia de diseño:** `docs/superpowers/specs/2026-06-17-alta-producto-tienda-nube-b1-design.md`

## Global Constraints
- TN `POST /products`: `name` multilingüe (usar `{ "es": tituloNube }`); `published=false`; `free_shipping=false`; variante con `stock:""` (sin gestión), `weight:"0.050"`, `depth:"8.00"`, `width:"5.00"`, `height:"5.00"`.
- Precio: fila `producto_canal_precios(productoId, canalId, cuotas)`. Si `pvpInflado != null && pvpInflado > pvp` → `price`=pvpInflado, `promotional_price`=pvp; si no → `price`=pvp. Precios como String.
- Tiendas: `STORE_HOGAR="KT HOGAR"`→canal "KT HOGAR"; `STORE_GASTRO="KT GASTRO"`→canal "KT GASTRO" (resolver canal por nombre con `CanalRepository.findByNombreIgnoreCase`).
- Si el SKU ya existe en la tienda (`buscarProductoPorSku != null`) → **no crear**, reportar "ya existía". Producto sin `tituloNube` o sin precio para canal/cuota → error reportado por SKU/tienda.
- Permiso de los endpoints/checkboxes: `INTEGRACIONES_EDITAR`.
- Commits en `main`, terminando con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Backend: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"` / `test`. Frontend: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`.
- **Fuera de B1:** categorías, imágenes, SEO; actualizar existentes; productos relacionados.

## File Structure
- Modify: `apis/nube/NubeRetryHandler.java` (+`postJson`).
- Create: `apis/nube/service/NubeDescripcionBuilder.java` (HTML), `apis/nube/service/NubeProductoPayloadBuilder.java` (Map payload).
- Modify: `apis/nube/service/TiendaNubeService.java` (+`crearProductoEnNube`).
- Create: `apis/nube/service/NubeExportService.java`, `apis/nube/controller/NubeProductoController.java`, DTOs `dto/ExportNubeRequestDTO`, `dto/ExportNubeResultDTO`, `dto/ResultadoAltaNube`.
- Tests: `NubeDescripcionBuilderTest`, `NubeProductoPayloadBuilderTest`, `CrearProductoEnNubeTest`.
- Frontend: `productos/productosService.ts` (+`exportarProductosANubeAPI`), `productos/page.tsx` (checkboxes + cuota + disparo).

---

## FASE 1 — Infraestructura POST

### Task 1: `postJson` en NubeRetryHandler

**Files:** Modify `apis/nube/NubeRetryHandler.java`

**Interfaces — Produces:** `String postJson(String uri, String accessToken, String jsonBody)`.

- [ ] **Step 1: Agregar el método** (copia de `putJson` usando `restClient.post()`), después de `patchJson`:
```java
    public String postJson(String uri, String accessToken, String jsonBody) {
        int normalRetries = 0;
        int rateLimitRetries = 0;

        while (true) {
            try {
                rateLimiter.acquire();

                return restClient.post()
                        .uri(uri)
                        .header("Authentication", "bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .body(jsonBody)
                        .retrieve()
                        .body(String.class);

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 401) {
                    log.error("NUBE - 401 Unauthorized (POST {}) - Token inválido", uri);
                    throw e;
                }
                if (status == 404) throw e;
                if (status == 429) {
                    if (rateLimitRetries >= MAX_RETRIES_RATE_LIMIT) throw e;
                    rateLimitRetries++;
                    long waitMs = Math.min(parseRetryAfter(e.getResponseHeaders(), baseWaitMs * 2), MAX_WAIT_MS);
                    log.warn("NUBE - 429 (POST {}). Retry en {}s... ({}/{})", uri, waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                    notifyRetryListener(String.format("Nube rate limit - reintentando en %ds... (%d/%d)",
                            waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT));
                    sleep(waitMs);
                    continue;
                }
                throw e;
            } catch (HttpServerErrorException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("NUBE - 5xx {} (POST {}). Retry en {}ms... ({}/{})", e.getStatusCode().value(), uri, waitMs, normalRetries, MAX_RETRIES);
                sleep(waitMs);
            } catch (ResourceAccessException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("NUBE - Error conexión (POST {}). Retry en {}ms... ({}/{}): {}", uri, waitMs, normalRetries, MAX_RETRIES, e.getMessage());
                sleep(waitMs);
            }
        }
    }
```

- [ ] **Step 2: Compilar** `cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"` → BUILD SUCCESS.

- [ ] **Step 3: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/NubeRetryHandler.java
git commit -m "feat(nube): postJson en NubeRetryHandler"
```

---

## FASE 2 — Builders (TDD)

### Task 2: NubeDescripcionBuilder

**Files:**
- Create: `apis/nube/service/NubeDescripcionBuilder.java`
- Test: `src/test/java/.../apis/nube/service/NubeDescripcionBuilderTest.java`

**Interfaces — Produces:** `static String construir(Producto producto)` → HTML.

- [ ] **Step 1: Test**
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.apto.entity.Apto;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoApto;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NubeDescripcionBuilderTest {

    @Test
    void incluyeBulletsDeLosDatosPresentes() {
        Producto p = new Producto();
        p.setLargo("30"); p.setAncho("5");
        Material m = new Material(); m.setNombre("Plástico"); p.setMaterial(m);
        Marca ma = new Marca(); ma.setNombre("Tramontina"); p.setMarca(ma);

        String html = NubeDescripcionBuilder.construir(p);

        assertThat(html).contains("<b>CARACTERÍSTICAS</b>");
        assertThat(html).contains("Largo: 30");
        assertThat(html).contains("Ancho: 5");
        assertThat(html).contains("Material: Plástico");
        assertThat(html).contains("Marca: Tramontina");
    }

    @Test
    void omiteBulletsDeDatosVacios() {
        Producto p = new Producto();
        p.setLargo("30"); // sin material, sin marca, sin aptos

        String html = NubeDescripcionBuilder.construir(p);

        assertThat(html).contains("Largo: 30");
        assertThat(html).doesNotContain("Material:");
        assertThat(html).doesNotContain("Marca:");
        assertThat(html).doesNotContain("Aptos:");
    }
}
```

- [ ] **Step 2: Correr (falla)** `cd supermaster-backend && cmd /c "mvnw.cmd -q -o test -Dtest=NubeDescripcionBuilderTest"` → falla (clase no existe).

- [ ] **Step 3: Implementar**
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Construye la descripción HTML del producto para Tienda Nube. */
public final class NubeDescripcionBuilder {

    private NubeDescripcionBuilder() {}

    public static String construir(Producto p) {
        List<String> bullets = new ArrayList<>();

        String dimensiones = dimensiones(p);
        if (!dimensiones.isBlank()) bullets.add("Dimensiones: " + dimensiones);
        if (p.getMaterial() != null && p.getMaterial().getNombre() != null)
            bullets.add("Material: " + p.getMaterial().getNombre());
        String aptos = aptos(p);
        if (!aptos.isBlank()) bullets.add("Aptos: " + aptos);
        if (p.getMarca() != null && p.getMarca().getNombre() != null)
            bullets.add("Marca: " + p.getMarca().getNombre());

        StringBuilder sb = new StringBuilder("<p><b>CARACTERÍSTICAS</b></p><ul>");
        for (String b : bullets) sb.append("<li>").append(escape(b)).append("</li>");
        sb.append("</ul>");
        return sb.toString();
    }

    private static String dimensiones(Producto p) {
        List<String> partes = new ArrayList<>();
        agregar(partes, "Capacidad", p.getCapacidad());
        agregar(partes, "Largo", p.getLargo());
        agregar(partes, "Ancho", p.getAncho());
        agregar(partes, "Alto", p.getAlto());
        agregar(partes, "Diám. boca", p.getDiamboca());
        agregar(partes, "Diám. base", p.getDiambase());
        agregar(partes, "Espesor", p.getEspesor());
        return String.join(", ", partes);
    }

    private static void agregar(List<String> partes, String label, String valor) {
        if (valor != null && !valor.isBlank()) partes.add(label + ": " + valor.trim());
    }

    private static String aptos(Producto p) {
        if (p.getProductosApto() == null) return "";
        return p.getProductosApto().stream()
                .filter(pa -> pa.getApto() != null && pa.getApto().getNombre() != null)
                .map(pa -> pa.getApto().getNombre())
                .collect(Collectors.joining(", "));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 4: Correr (pasa)** mismo comando → 2 tests PASS.

- [ ] **Step 5: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeDescripcionBuilder.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeDescripcionBuilderTest.java
git commit -m "feat(nube): NubeDescripcionBuilder (HTML con bullets)"
```

---

### Task 3: NubeProductoPayloadBuilder

**Files:**
- Create: `apis/nube/service/NubeProductoPayloadBuilder.java`
- Test: `src/test/java/.../apis/nube/service/NubeProductoPayloadBuilderTest.java`

**Interfaces — Consumes:** `NubeDescripcionBuilder.construir`. **Produces:** `static Map<String,Object> construir(Producto producto, BigDecimal pvp, BigDecimal pvpInflado)` → el body del POST /products (sin serializar).

- [ ] **Step 1: Test**
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NubeProductoPayloadBuilderTest {

    private Producto base() {
        Producto p = new Producto();
        p.setSku("SKU1"); p.setTituloNube("Producto de prueba");
        p.setCosto(new BigDecimal("100"));
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinInflado_priceEsPvp_sinPromotional() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), null);

        assertThat(((Map<String, Object>) payload.get("name")).get("es")).isEqualTo("Producto de prueba");
        assertThat(payload.get("published")).isEqualTo(false);
        assertThat(payload.get("free_shipping")).isEqualTo(false);

        List<Map<String, Object>> variants = (List<Map<String, Object>>) payload.get("variants");
        Map<String, Object> v = variants.get(0);
        assertThat(v.get("sku")).isEqualTo("SKU1");
        assertThat(v.get("price")).isEqualTo("1500.00");
        assertThat(v).doesNotContainKey("promotional_price");
        assertThat(v.get("stock")).isEqualTo("");
        assertThat(v.get("weight")).isEqualTo("0.050");
        assertThat(v.get("depth")).isEqualTo("8.00");
        assertThat(v.get("width")).isEqualTo("5.00");
        assertThat(v.get("height")).isEqualTo("5.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void conInfladoMayor_priceEsInflado_promotionalEsPvp() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), new BigDecimal("2000.00"));
        Map<String, Object> v = ((List<Map<String, Object>>) payload.get("variants")).get(0);
        assertThat(v.get("price")).isEqualTo("2000.00");
        assertThat(v.get("promotional_price")).isEqualTo("1500.00");
    }
}
```

- [ ] **Step 2: Correr (falla)** `cmd /c "mvnw.cmd -q -o test -Dtest=NubeProductoPayloadBuilderTest"`.

- [ ] **Step 3: Implementar**
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Construye el body de POST /products de Tienda Nube para un producto. */
public final class NubeProductoPayloadBuilder {

    private NubeProductoPayloadBuilder() {}

    public static Map<String, Object> construir(Producto p, BigDecimal pvp, BigDecimal pvpInflado) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", Map.of("es", p.getTituloNube() != null ? p.getTituloNube() : ""));
        payload.put("description", Map.of("es", NubeDescripcionBuilder.construir(p)));
        payload.put("published", false);
        payload.put("free_shipping", false);

        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("sku", p.getSku());
        // Precio de lista (tachado) y promocional según haya inflado.
        if (pvpInflado != null && pvp != null && pvpInflado.compareTo(pvp) > 0) {
            variant.put("price", pvpInflado.toPlainString());
            variant.put("promotional_price", pvp.toPlainString());
        } else if (pvp != null) {
            variant.put("price", pvp.toPlainString());
        }
        if (p.getCosto() != null) variant.put("cost", p.getCosto().toPlainString());
        variant.put("weight", "0.050");
        variant.put("depth", "8.00");
        variant.put("width", "5.00");
        variant.put("height", "5.00");
        variant.put("stock", "");

        List<Map<String, Object>> variants = new ArrayList<>();
        variants.add(variant);
        payload.put("variants", variants);
        return payload;
    }
}
```

- [ ] **Step 4: Correr (pasa)** → 2 tests PASS.

- [ ] **Step 5: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilderTest.java
git commit -m "feat(nube): NubeProductoPayloadBuilder (variante con precio/inflado)"
```

---

## FASE 3 — TiendaNubeService.crearProductoEnNube (TDD)

### Task 4: crearProductoEnNube + ResultadoAltaNube

**Files:**
- Create: `apis/nube/dto/ResultadoAltaNube.java`
- Modify: `apis/nube/service/TiendaNubeService.java`
- Test: `src/test/java/.../apis/nube/service/CrearProductoEnNubeTest.java`

**Interfaces — Produces:** `ResultadoAltaNube crearProductoEnNube(String storeName, Producto producto, BigDecimal pvp, BigDecimal pvpInflado)`.

- [ ] **Step 1: DTO de resultado**
```java
package ar.com.leo.super_master_backend.apis.nube.dto;

public record ResultadoAltaNube(Estado estado, String motivo) {
    public enum Estado { CREADO, YA_EXISTIA, ERROR }
    public static ResultadoAltaNube creado() { return new ResultadoAltaNube(Estado.CREADO, null); }
    public static ResultadoAltaNube yaExistia() { return new ResultadoAltaNube(Estado.YA_EXISTIA, null); }
    public static ResultadoAltaNube error(String motivo) { return new ResultadoAltaNube(Estado.ERROR, motivo); }
}
```

- [ ] **Step 2: Test** (mockea las dependencias internas vía un constructor de test o spy). El método usa `buscarProductoPorSku`, `getStore` y `retryHandler.postJson`. Para testear sin red, el test usa un `TiendaNubeService` con un `NubeRetryHandler` mockeado y credenciales seteadas por reflexión, o (preferido) extraé la lógica a un método package-private `crearProductoEnNube(StoreCredentials store, Producto, pvp, pvpInflado, java.util.function.BiFunction<String,String,String> poster, java.util.function.Function<String,com.fasterxml.jackson.databind.JsonNode> buscador)` y testealo directo. Usá este enfoque testeable:
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube;
import ar.com.leo.super_master_backend.apis.nube.model.NubeCredentials.StoreCredentials;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CrearProductoEnNubeTest {

    private final ObjectMapper om = new ObjectMapper();

    private Producto producto() {
        Producto p = new Producto();
        p.setSku("SKU1"); p.setTituloNube("Prod"); p.setCosto(new BigDecimal("100"));
        return p;
    }
    private StoreCredentials store() {
        StoreCredentials s = new StoreCredentials(); s.setStoreId("999"); s.setAccessToken("tok");
        return s;
    }

    @Test
    void yaExiste_noPostea() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                (sku, token) -> om.createObjectNode().put("id", 1), // buscador: existe
                (uri, body) -> { posted.set(body); return "{}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.YA_EXISTIA);
        assertThat(posted.get()).isNull();
    }

    @Test
    void sinTitulo_error() {
        Producto p = producto(); p.setTituloNube("  ");
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), p, new BigDecimal("1500"), null, om,
                (sku, token) -> null, (uri, body) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("título");
    }

    @Test
    void ok_posteaYDevuelveCreado() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                (sku, token) -> null, // no existe
                (uri, body) -> { posted.set(body); return "{\"id\": 5}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.CREADO);
        assertThat(posted.get()).contains("\"sku\":\"SKU1\"").contains("\"published\":false");
    }
}
```

- [ ] **Step 3: Implementar** en `TiendaNubeService`:
  - Método público que resuelve store/credenciales y delega al core testeable:
```java
    public ResultadoAltaNube crearProductoEnNube(String storeName, Producto producto, java.math.BigDecimal pvp, java.math.BigDecimal pvpInflado) {
        verificarCredenciales();
        StoreCredentials store = getStore(storeName);
        if (store == null) return ResultadoAltaNube.error("Tienda '" + storeName + "' no configurada");
        return crearProductoEnNubeCore(store, producto, pvp, pvpInflado, objectMapper,
                (sku, token) -> buscarProductoPorSku(sku, storeName),
                (uri, body) -> retryHandler.postJson(uri, store.getAccessToken(), body));
    }

    /** Lógica testeable sin red. `buscador` devuelve el JSON del producto si existe (o null); `poster` hace POST(uri, body)->respuesta. */
    static ResultadoAltaNube crearProductoEnNubeCore(
            StoreCredentials store, Producto producto, java.math.BigDecimal pvp, java.math.BigDecimal pvpInflado,
            com.fasterxml.jackson.databind.ObjectMapper om,
            java.util.function.BiFunction<String, String, com.fasterxml.jackson.databind.JsonNode> buscador,
            java.util.function.BiFunction<String, String, String> poster) {
        try {
            if (producto.getTituloNube() == null || producto.getTituloNube().isBlank())
                return ResultadoAltaNube.error("Falta Título Nube");
            if (buscador.apply(producto.getSku(), store.getAccessToken()) != null)
                return ResultadoAltaNube.yaExistia();

            java.util.Map<String, Object> payload = NubeProductoPayloadBuilder.construir(producto, pvp, pvpInflado);
            String body = om.writeValueAsString(payload);
            String uri = "/" + store.getStoreId() + "/products";
            poster.apply(uri, body);
            return ResultadoAltaNube.creado();
        } catch (Exception e) {
            return ResultadoAltaNube.error(e.getMessage());
        }
    }
```
  Nota: ajustá la firma del `poster` del core a `BiFunction<String uri, String body, String resp>` (en el test el poster ignora el token porque ya está capturado en la lambda pública). Si preferís pasar también el token, mantené la firma coherente entre test e impl.

- [ ] **Step 4: Correr (pasa)** `cmd /c "mvnw.cmd -q -o test -Dtest=CrearProductoEnNubeTest"` → 3 PASS. Ajustar la firma del `poster`/`buscador` para que test e impl coincidan exactamente (BiFunction iguales).

- [ ] **Step 5: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/
git commit -m "feat(nube): crearProductoEnNube (no duplica, valida, postea)"
```

---

## FASE 4 — Servicio de exportación + endpoint

### Task 5: NubeExportService + endpoint + DTOs

**Files:**
- Create: `apis/nube/dto/ExportNubeRequestDTO.java`, `apis/nube/dto/ExportNubeResultDTO.java`, `apis/nube/service/NubeExportService.java`, `apis/nube/controller/NubeProductoController.java`
- Modify: (usa `CanalRepository`, `ProductoCanalPrecioRepository`, `ProductoRepository`)

**Interfaces — Consumes:** `TiendaNubeService.crearProductoEnNube`, `ProductoCanalPrecioRepository.findByProductoIdAndCanalIdAndCuotas`, `CanalRepository.findByNombreIgnoreCase`. **Produces:** `POST /api/nube/exportar-productos`.

- [ ] **Step 1: DTOs**
```java
// ExportNubeRequestDTO.java
package ar.com.leo.super_master_backend.apis.nube.dto;
import java.util.List;
public record ExportNubeRequestDTO(List<String> skus, List<DestinoNube> tiendas) {
    public record DestinoNube(String tienda, Integer cuotas) {}
}
```
```java
// ExportNubeResultDTO.java
package ar.com.leo.super_master_backend.apis.nube.dto;
import java.util.List;
public record ExportNubeResultDTO(int creados, List<String> yaExistian, List<String> errores) {}
```

- [ ] **Step 2: NubeExportService**
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.nube.dto.ExportNubeRequestDTO;
import ar.com.leo.super_master_backend.apis.nube.dto.ExportNubeResultDTO;
import ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NubeExportService {

    private final ProductoRepository productoRepository;
    private final ProductoCanalPrecioRepository precioRepository;
    private final CanalRepository canalRepository;
    private final TiendaNubeService tiendaNubeService;

    public ExportNubeResultDTO exportar(ExportNubeRequestDTO request) {
        int creados = 0;
        List<String> yaExistian = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        if (request == null || request.skus() == null || request.tiendas() == null) {
            return new ExportNubeResultDTO(0, yaExistian, errores);
        }

        List<Producto> productos = productoRepository.findBySkuIn(
                request.skus().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList());

        for (Producto producto : productos) {
            for (ExportNubeRequestDTO.DestinoNube destino : request.tiendas()) {
                String tienda = destino.tienda();
                String etiqueta = producto.getSku() + " / " + tienda;
                Optional<Canal> canal = canalRepository.findByNombreIgnoreCase(tienda);
                if (canal.isEmpty()) { errores.add(etiqueta + ": canal '" + tienda + "' no existe"); continue; }
                Optional<ProductoCanalPrecio> precio = precioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.get().getId(), destino.cuotas());
                if (precio.isEmpty()) { errores.add(etiqueta + ": sin precio calculado para esa cuota"); continue; }

                ResultadoAltaNube r = tiendaNubeService.crearProductoEnNube(
                        tienda, producto, precio.get().getPvp(), precio.get().getPvpInflado());
                switch (r.estado()) {
                    case CREADO -> creados++;
                    case YA_EXISTIA -> yaExistian.add(etiqueta);
                    case ERROR -> errores.add(etiqueta + ": " + r.motivo());
                }
            }
        }
        return new ExportNubeResultDTO(creados, yaExistian, errores);
    }
}
```

- [ ] **Step 3: Controller**
```java
package ar.com.leo.super_master_backend.apis.nube.controller;

import ar.com.leo.super_master_backend.apis.nube.dto.ExportNubeRequestDTO;
import ar.com.leo.super_master_backend.apis.nube.dto.ExportNubeResultDTO;
import ar.com.leo.super_master_backend.apis.nube.service.NubeExportService;
import ar.com.leo.super_master_backend.config.Permisos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nube")
public class NubeProductoController {

    private final NubeExportService nubeExportService;

    @PostMapping("/exportar-productos")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<ExportNubeResultDTO> exportar(@RequestBody(required = false) ExportNubeRequestDTO request) {
        return ResponseEntity.ok(nubeExportService.exportar(request));
    }
}
```

- [ ] **Step 4: Compilar + suite**
Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o test"` → BUILD SUCCESS (incluye los tests de builders y crearProducto).

- [ ] **Step 5: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/
git commit -m "feat(nube): endpoint exportar-productos + NubeExportService"
```

---

## FASE 5 — Frontend

### Task 6: Service exportarProductosANubeAPI

**Files:** Modify `productos/productosService.ts`

**Interfaces — Produces:** `exportarProductosANubeAPI(skus, tiendas)` → `ExportNubeResultDTO`.

- [ ] **Step 1: Agregar tipos + función** (junto a `exportarProductosADuxAPI`):
```ts
export type DestinoNube = { tienda: "KT HOGAR" | "KT GASTRO"; cuotas: number };
export type ExportNubeResultDTO = {
    creados: number;
    yaExistian: string[];
    errores: string[];
};

export const exportarProductosANubeAPI = async (skus: string[], tiendas: DestinoNube[]): Promise<ExportNubeResultDTO> => {
    const res = await fetchAPI(`${API_BASE_URL}/api/nube/exportar-productos`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ skus, tiendas }),
    });
    if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Tienda Nube"));
    return await res.json();
};
```

- [ ] **Step 2: Typecheck + commit**
```bash
cd supermaster-frontend && cmd /c "npx tsc --noEmit"
git add supermaster-frontend/src/app/productos/productosService.ts
git commit -m "feat(front/nube): service exportarProductosANubeAPI"
```

### Task 7: Checkboxes KT HOGAR/GASTRO funcionales + cuota + disparo

**Files:** Modify `productos/page.tsx`

**Interfaces — Consumes:** `exportarProductosANubeAPI`, `getCuotasPorCanalAPI`.

- [ ] **Step 1: Estados + carga de cuotas** (junto a `subirADux`, ≈línea 237):
```tsx
    const [subirKtHogar, setSubirKtHogar] = useState(false);
    const [subirKtGastro, setSubirKtGastro] = useState(false);
    const [cuotaHogar, setCuotaHogar] = useState<number>(-1);   // Transferencia por defecto
    const [cuotaGastro, setCuotaGastro] = useState<number>(6);  // 6 cuotas por defecto
    const [cuotasHogar, setCuotasHogar] = useState<{ cuotas: number; descripcion: string }[]>([]);
    const [cuotasGastro, setCuotasGastro] = useState<{ cuotas: number; descripcion: string }[]>([]);
```
Importar `getCuotasPorCanalAPI` de `../canal-concepto-cuotas/canalConceptoCuotaService` y, en un `useEffect([])` (al montar), cargar las cuotas de los canales KT HOGAR y KT GASTRO. Como el canalId no es fijo, resolverlo: traer la lista de canales (ya hay un loader de canales en el form para otras cosas; si no, usar `getCuotasPorCanalAPI` por nombre no existe → usar el id). Para B1, si el front ya tiene los canales cargados con sus ids, buscar el id de "KT HOGAR"/"KT GASTRO"; si no, dejar los selects con las opciones por defecto (-1 y 6) sin cargar dinámicamente. **Implementá la carga dinámica si los canales/ids están disponibles en el componente; si no, dejá selects fijos con Transferencia/Contado/3/6/9/12 según corresponda y marcá el default.**

- [ ] **Step 2: Reemplazar los placeholders KT HOGAR/GASTRO** (líneas 1514-1521) por checkboxes funcionales con selector de cuota cuando están marcados:
```tsx
                            <div className={checkboxCardClassName}>
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirKtHogar} onChange={e => setSubirKtHogar(e.target.checked)} id="subirKtHogar" disabled={!canExportarDux} />
                                <label htmlFor="subirKtHogar" className="cursor-pointer">KT HOGAR (Nube)</label>
                                {subirKtHogar && (
                                    <select className={`${selectBaseClassName} ml-auto w-auto`} value={cuotaHogar} onChange={e => setCuotaHogar(Number(e.target.value))}>
                                        {(cuotasHogar.length ? cuotasHogar : [{cuotas:-1,descripcion:"Transferencia"},{cuotas:6,descripcion:"6 cuotas"}]).map(c => (
                                            <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                        ))}
                                    </select>
                                )}
                            </div>
                            <div className={checkboxCardClassName}>
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirKtGastro} onChange={e => setSubirKtGastro(e.target.checked)} id="subirKtGastro" disabled={!canExportarDux} />
                                <label htmlFor="subirKtGastro" className="cursor-pointer">KT GASTRO (Nube)</label>
                                {subirKtGastro && (
                                    <select className={`${selectBaseClassName} ml-auto w-auto`} value={cuotaGastro} onChange={e => setCuotaGastro(Number(e.target.value))}>
                                        {(cuotasGastro.length ? cuotasGastro : [{cuotas:-1,descripcion:"Transferencia"},{cuotas:6,descripcion:"6 cuotas"}]).map(c => (
                                            <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                        ))}
                                    </select>
                                )}
                            </div>
```

- [ ] **Step 3: Disparo en `handleCreate`** (después del bloque de DUX, ≈línea 645):
```tsx
            const tiendasNube: { tienda: "KT HOGAR" | "KT GASTRO"; cuotas: number }[] = [];
            if (subirKtHogar) tiendasNube.push({ tienda: "KT HOGAR", cuotas: cuotaHogar });
            if (subirKtGastro) tiendasNube.push({ tienda: "KT GASTRO", cuotas: cuotaGastro });
            if (tiendasNube.length && canExportarDux) {
                try {
                    const r = await exportarProductosANubeAPI([sku.trim()], tiendasNube);
                    const partes: string[] = [];
                    if (r.creados > 0) partes.push(`${r.creados} creado(s) en Nube`);
                    if (r.yaExistian.length) partes.push(`${r.yaExistian.length} ya existía(n)`);
                    if (r.errores.length) partes.push(`${r.errores.length} con error: ${r.errores.join("; ")}`);
                    if (r.errores.length) notificar.error(`Tienda Nube: ${partes.join(" · ")}`);
                    else notificar.success(`Tienda Nube: ${partes.join(" · ") || "sin cambios"}`);
                } catch (e) {
                    notificar.error(e instanceof Error ? `Falló subir a Nube: ${e.message}` : "Falló subir a Nube");
                }
            }
```
Importar `exportarProductosANubeAPI` (y el tipo) de `./productosService`.

- [ ] **Step 4: (Opcional) mismo disparo en `handleGuardarEdicion`** — B1 solo da de alta; en edición el backend reporta "ya existía". Replicar el mismo bloque tras el de DUX si se quiere permitir subir desde edición (el resultado dirá "ya existía" si corresponde).

- [ ] **Step 5: Reset** (`resetForm` ≈934 y `abrirEdicion` ≈664): agregar `setSubirKtHogar(false); setSubirKtGastro(false);` (en ambos; los defaults de cuota se mantienen).

- [ ] **Step 6: Typecheck + commit**
```bash
cd supermaster-frontend && cmd /c "npx tsc --noEmit"
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/nube): checkboxes KT HOGAR/GASTRO con cuota y disparo de alta"
```

---

## FASE 6 — Verificación

### Task 8: Verificación end-to-end

- [ ] **Step 1: Suite backend** `cd supermaster-backend && cmd /c "mvnw.cmd -q -o test"` → BUILD SUCCESS (incluye los 3 tests nuevos + los existentes).
- [ ] **Step 2: Build frontend** `cd supermaster-frontend && cmd /c "npm run build"` → compila.
- [ ] **Step 3: Verificación manual** (requiere credenciales de una tienda de prueba en `nube_tokens.json` y backend levantado):
  1. Crear un producto con Título Nube, marcar **KT HOGAR**, elegir cuota → al guardar, se crea en TN oculto con el precio de esa cuota; toast "1 creado en Nube".
  2. Volver a subirlo → "ya existía".
  3. Producto sin Título Nube o sin precio para esa cuota → aparece en errores.
  4. Verificar en el panel de TN: producto oculto, nombre=Título Nube, descripción con bullets, variante con sku/precio, sin stock.

## Notas / riesgos
- **`name`/`description` multilingüe:** TN acepta el objeto con clave `es`. Si la tienda exige otra clave, ajustar.
- **Precio sin fila:** si el producto no tiene `producto_canal_precios` para (canal, cuota) — p. ej. nunca se recalculó — se reporta error; no rompe el resto.
- **Firma del core testeable (Task 4):** mantené idénticas las `BiFunction` entre el test y la implementación (mismo orden de parámetros) para que compile; si el linter de tipos se queja, alineá las firmas antes de seguir.
- **Fuera de B1:** categorías (B2), imágenes (B3), SEO (B4), actualizar existentes, productos relacionados (sin API).
