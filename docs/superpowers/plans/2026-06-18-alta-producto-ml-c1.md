# Alta de producto en Mercado Libre — Fase C1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publicar un producto en Mercado Libre (cuenta del seller, sitio MLA) con título, categoría por predictor, precio costo×5, condición nuevo, envío me2, imágenes (multipart) y descripción (texto plano), disparado al guardar el producto con un checkbox.

**Architecture:** Builders puros (`MlDescripcionBuilder`, `MlItemPayloadBuilder`) + un núcleo testeable por lambdas (`crearItemEnMlCore`) dentro de `MercadoLibreService`, que orquesta: chequeo no-duplicado, subida de imágenes, predictor de categoría, `POST /items` y `POST /items/{id}/description`. `MlExportService` + endpoint replican el patrón de `NubeExportService`. Reutiliza el `MlRetryHandler` existente (OAuth refresh, retry, rate limit), al que se le suma un método multipart.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven; Jackson 3 (`tools.jackson.databind`); RestClient; JUnit 5 + AssertJ; frontend Next.js/TypeScript.

## Global Constraints

- Jackson 3: importar de `tools.jackson.databind` (NO `com.fasterxml.jackson`); excepciones unchecked. Parsear con `.path(...).asString("")` / `.asLong(0)`.
- Código backend nuevo de ML en `ar.com.leo.super_master_backend.apis.ml.*`; cambios de imágenes en `dominio.imagen`.
- Sitio **MLA**. Token de ML: `() -> tokens.accessToken` (campo de `MercadoLibreService`). Todas las llamadas pasan por `retryHandler`.
- **Ninguna llamada real a Mercado Libre** en los tests: builders puros y núcleo con lambdas. Los wrappers de red (multipart, predictor, `crearItemEnMl` público, `MlExportService`) no se testean unitariamente.
- Valores fijos: `currency_id="ARS"`, `buying_mode="buy_it_now"`, `listing_type_id="gold_special"`, `condition="new"` + attribute `ITEM_CONDITION` value_id `"2230284"`, `shipping={mode:"me2", local_pick_up:false, free_shipping:false, free_methods:[]}`. Precio = `costo × 5`. Cantidad: intento `0`, si ML lo rechaza por stock reintento `1` (aviso).
- Imágenes **obligatorias** (gold_special): sin imágenes → error. Convención de archivos del SKU (de B3): `resolverArchivosPorSku`.
- Descripción: **texto plano**, saltos con `\n`, sin HTML. Endpoint aparte `POST /items/{id}/description`.
- No duplicar: `buscarMlaPorSku(sku)` ≠ null → "ya existía".
- `MlExportService.exportar` es `@Transactional(readOnly=true)` (accede a asociaciones LAZY de Producto con `open-in-view=false`).
- Maven offline en Windows: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=...`. Frontend: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`.
- Commits terminan con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: `ImagenService.leerBytes` (bytes crudos para multipart)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceBytesTest.java`

**Interfaces:**
- Produces: `byte[] leerBytes(String filename)` — bytes de `{baseDir}/{filename}`; propaga `UncheckedIOException` si no se puede leer. (Complementa `leerBase64`/`resolverArchivosPorSku` de B3.)

- [ ] **Step 1: Write the failing test**

`ImagenServiceBytesTest.java`:
```java
package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagenServiceBytesTest {

    private ImagenService servicioSobre(Path dir) {
        return new ImagenService(dir.toString(), 0L);
    }

    @Test
    void leerBytes_devuelveContenido(@TempDir Path dir) throws Exception {
        byte[] contenido = {10, 20, 30, 40};
        Files.write(dir.resolve("ABC123.jpg"), contenido);
        assertThat(servicioSobre(dir).leerBytes("ABC123.jpg")).containsExactly(10, 20, 30, 40);
    }

    @Test
    void leerBytes_inexistente_lanza(@TempDir Path dir) {
        assertThatThrownBy(() -> servicioSobre(dir).leerBytes("NOEXISTE.jpg"))
                .isInstanceOf(UncheckedIOException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=ImagenServiceBytesTest`
Expected: FAIL de compilación ("cannot find symbol: method leerBytes").

- [ ] **Step 3: Write minimal implementation**

En `ImagenService.java`, agregar junto a `leerBase64`:
```java
    /** Lee {baseDir}/{filename} y devuelve sus bytes crudos. */
    public byte[] leerBytes(String filename) {
        try {
            return Files.readAllBytes(baseDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen " + filename, e);
        }
    }
```
(`Files`, `IOException`, `UncheckedIOException` ya están importados.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=ImagenServiceBytesTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceBytesTest.java
git commit -m "feat(imagen): leerBytes (contenido crudo para multipart)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `MlDescripcionBuilder` (descripción texto plano)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionBuilder.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionBuilderTest.java`

**Interfaces:**
- Produces: `static String construir(Producto p)` → texto plano `CARACTERÍSTICAS\n` + bullets `• …\n` (Dimensiones, Material, Aptos, Marca), omitiendo vacíos. Sin HTML.

- [ ] **Step 1: Write the failing test**

`MlDescripcionBuilderTest.java`:
```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MlDescripcionBuilderTest {

    @Test
    void construir_incluyeCabeceraYBullets() {
        Producto p = new Producto();
        Marca marca = new Marca(); marca.setNombre("Tramontina");
        Material material = new Material(); material.setNombre("Acero");
        p.setMarca(marca);
        p.setMaterial(material);
        p.setCapacidad("500ml");

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).startsWith("CARACTERÍSTICAS");
        assertThat(desc).contains("• Material: Acero");
        assertThat(desc).contains("• Marca: Tramontina");
        assertThat(desc).contains("500ml");
        assertThat(desc).doesNotContain("<"); // sin HTML
    }

    @Test
    void construir_omiteVacios() {
        Producto p = new Producto();
        Marca marca = new Marca(); marca.setNombre("Tramontina");
        p.setMarca(marca);

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).contains("• Marca: Tramontina");
        assertThat(desc).doesNotContain("Material:");
        assertThat(desc).doesNotContain("Dimensiones:");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=MlDescripcionBuilderTest`
Expected: FAIL de compilación ("cannot find symbol: class MlDescripcionBuilder").

- [ ] **Step 3: Write minimal implementation**

`MlDescripcionBuilder.java`:
```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoApto;

import java.util.ArrayList;
import java.util.List;

/** Construye la descripción en TEXTO PLANO para Mercado Libre (sin HTML; saltos con \n). */
public final class MlDescripcionBuilder {

    private MlDescripcionBuilder() {}

    public static String construir(Producto p) {
        StringBuilder sb = new StringBuilder("CARACTERÍSTICAS\n");
        String dimensiones = dimensiones(p);
        if (!dimensiones.isBlank()) sb.append("• Dimensiones: ").append(dimensiones).append("\n");
        if (p.getMaterial() != null && p.getMaterial().getNombre() != null)
            sb.append("• Material: ").append(p.getMaterial().getNombre()).append("\n");
        String aptos = aptos(p);
        if (!aptos.isBlank()) sb.append("• Aptos: ").append(aptos).append("\n");
        if (p.getMarca() != null && p.getMarca().getNombre() != null)
            sb.append("• Marca: ").append(p.getMarca().getNombre()).append("\n");
        return sb.toString();
    }

    private static String dimensiones(Producto p) {
        List<String> partes = new ArrayList<>();
        agregar(partes, "Capacidad", p.getCapacidad());
        agregar(partes, "Largo", p.getLargo());
        agregar(partes, "Ancho", p.getAncho());
        agregar(partes, "Alto", p.getAlto());
        agregar(partes, "Diámetro boca", p.getDiamboca());
        agregar(partes, "Diámetro base", p.getDiambase());
        agregar(partes, "Espesor", p.getEspesor());
        return String.join(", ", partes);
    }

    private static void agregar(List<String> partes, String etiqueta, String valor) {
        if (valor != null && !valor.isBlank()) partes.add(etiqueta + " " + valor.trim());
    }

    private static String aptos(Producto p) {
        if (p.getProductosApto() == null) return "";
        List<String> nombres = new ArrayList<>();
        for (ProductoApto pa : p.getProductosApto()) {
            if (pa.getApto() != null && pa.getApto().getNombre() != null) nombres.add(pa.getApto().getNombre());
        }
        return String.join(", ", nombres);
    }
}
```
**Nota para el implementer:** verificá los getters reales — `Producto.getProductosApto()` devuelve `Set<ProductoApto>` y `ProductoApto.getApto().getNombre()`. Si el acceso a aptos difiere, ajustalo siguiendo cómo lo hace `NubeDescripcionBuilder` (mismo dato). Si `ProductoApto` no se puede importar limpio, replicá el patrón exacto de `NubeDescripcionBuilder` para los aptos.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=MlDescripcionBuilderTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionBuilder.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionBuilderTest.java
git commit -m "feat(ml): MlDescripcionBuilder (texto plano, CARACTERISTICAS + bullets)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `MlItemPayloadBuilder` (payload del ítem)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilderTest.java`

**Interfaces:**
- Produces: `static Map<String,Object> construir(Producto p, String categoryId, BigDecimal price, int availableQuantity, List<String> pictureIds)` → JSON del ítem (title, category_id, price, currency_id ARS, available_quantity, buying_mode, listing_type_id, condition, attributes[ITEM_CONDITION, BRAND si hay marca, SELLER_SKU], shipping me2, pictures[{id}]).

- [ ] **Step 1: Write the failing test**

`MlItemPayloadBuilderTest.java`:
```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MlItemPayloadBuilderTest {

    private Producto base() {
        Producto p = new Producto();
        p.setSku("ABC123"); p.setTituloMl("Olla acero 5L");
        Marca m = new Marca(); m.setNombre("Tramontina"); p.setMarca(m);
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void construir_camposBasicosYAtributos() {
        Map<String, Object> payload = MlItemPayloadBuilder.construir(
                base(), "MLA1234", new BigDecimal("5000"), 0, List.of("PIC1", "PIC2"));

        assertThat(payload.get("title")).isEqualTo("Olla acero 5L");
        assertThat(payload.get("category_id")).isEqualTo("MLA1234");
        assertThat(payload.get("price")).isEqualTo(new BigDecimal("5000"));
        assertThat(payload.get("currency_id")).isEqualTo("ARS");
        assertThat(payload.get("available_quantity")).isEqualTo(0);
        assertThat(payload.get("buying_mode")).isEqualTo("buy_it_now");
        assertThat(payload.get("listing_type_id")).isEqualTo("gold_special");
        assertThat(payload.get("condition")).isEqualTo("new");

        Map<String, Object> shipping = (Map<String, Object>) payload.get("shipping");
        assertThat(shipping.get("mode")).isEqualTo("me2");

        List<Map<String, Object>> pics = (List<Map<String, Object>>) payload.get("pictures");
        assertThat(pics).extracting(m -> m.get("id")).containsExactly("PIC1", "PIC2");

        List<Map<String, Object>> attrs = (List<Map<String, Object>>) payload.get("attributes");
        assertThat(attrs).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo("ITEM_CONDITION");
            assertThat(a.get("value_id")).isEqualTo("2230284");
        });
        assertThat(attrs).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo("BRAND");
            assertThat(a.get("value_name")).isEqualTo("Tramontina");
        });
        assertThat(attrs).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo("SELLER_SKU");
            assertThat(a.get("value_name")).isEqualTo("ABC123");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void construir_sinMarca_noIncluyeBrand() {
        Producto p = base(); p.setMarca(null);
        Map<String, Object> payload = MlItemPayloadBuilder.construir(p, "MLA1", new BigDecimal("100"), 1, List.of("P1"));
        List<Map<String, Object>> attrs = (List<Map<String, Object>>) payload.get("attributes");
        assertThat(attrs).noneSatisfy(a -> assertThat(a.get("id")).isEqualTo("BRAND"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=MlItemPayloadBuilderTest`
Expected: FAIL de compilación ("cannot find symbol: class MlItemPayloadBuilder").

- [ ] **Step 3: Write minimal implementation**

`MlItemPayloadBuilder.java`:
```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Construye el body de POST /items de Mercado Libre (sitio MLA). */
public final class MlItemPayloadBuilder {

    private MlItemPayloadBuilder() {}

    public static Map<String, Object> construir(Producto p, String categoryId, BigDecimal price,
                                                int availableQuantity, List<String> pictureIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", p.getTituloMl());
        payload.put("category_id", categoryId);
        payload.put("price", price);
        payload.put("currency_id", "ARS");
        payload.put("available_quantity", availableQuantity);
        payload.put("buying_mode", "buy_it_now");
        payload.put("listing_type_id", "gold_special");
        payload.put("condition", "new");

        List<Map<String, Object>> attributes = new ArrayList<>();
        attributes.add(Map.of("id", "ITEM_CONDITION", "value_id", "2230284"));
        if (p.getMarca() != null && p.getMarca().getNombre() != null) {
            attributes.add(Map.of("id", "BRAND", "value_name", p.getMarca().getNombre()));
        }
        attributes.add(Map.of("id", "SELLER_SKU", "value_name", p.getSku()));
        payload.put("attributes", attributes);

        Map<String, Object> shipping = new LinkedHashMap<>();
        shipping.put("mode", "me2");
        shipping.put("local_pick_up", false);
        shipping.put("free_shipping", false);
        shipping.put("free_methods", new ArrayList<>());
        payload.put("shipping", shipping);

        List<Map<String, Object>> pictures = new ArrayList<>();
        for (String id : pictureIds) pictures.add(Map.of("id", id));
        payload.put("pictures", pictures);

        return payload;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=MlItemPayloadBuilderTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilderTest.java
git commit -m "feat(ml): MlItemPayloadBuilder (item MLA: precio, me2, attributes, pictures)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: `MlRetryHandler.postMultipart` (subida de imágenes)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/MlRetryHandler.java`

**Interfaces:**
- Produces: `String postMultipart(String uri, Supplier<String> tokenSupplier, String filename, byte[] contenido)` — POST `multipart/form-data` con la parte `file`. Mismo manejo de auth/retry/rate-limit. Devuelve el body (JSON con el `id` de la imagen).

> **Nota:** wrapper de red; no lleva test unitario (igual que `postForm`/`postJson`). Verificación: compila.

- [ ] **Step 1: Implementar `postMultipart`**

Agregar imports (si faltan):
```java
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
```
Agregar el método (después de `postJson`, ~línea 224):
```java
    /**
     * POST multipart/form-data con un único archivo en la parte "file". Mismo manejo de auth/retry.
     * Usado para subir imágenes a /pictures/items/upload de Mercado Libre.
     */
    public String postMultipart(String uri, Supplier<String> tokenSupplier, String filename, byte[] contenido) {
        int normalRetries = 0;
        int authRetries = 0;
        int rateLimitRetries = 0;

        while (true) {
            try {
                rateLimiter.acquire();

                MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
                ByteArrayResource resource = new ByteArrayResource(contenido) {
                    @Override
                    public String getFilename() { return filename; }
                };
                parts.add("file", resource);

                return restClient.post()
                        .uri(uri)
                        .header("Authorization", "Bearer " + tokenSupplier.get())
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(parts)
                        .retrieve()
                        .body(String.class);

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 401) {
                    if (authRetries >= MAX_RETRIES_AUTH) throw e;
                    authRetries++;
                    if (tokenRefresher != null) tokenRefresher.run();
                    continue;
                }
                if (status == 429) {
                    if (rateLimitRetries >= MAX_RETRIES_RATE_LIMIT) throw e;
                    rateLimitRetries++;
                    long waitMs = Math.min(parseRetryAfter(e.getResponseHeaders(), baseWaitMs), MAX_WAIT_MS);
                    log.warn("ML - 429 (multipart {}). Retry en {}s... ({}/{})", uri, waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                    sleep(waitMs);
                    continue;
                }
                throw e;
            } catch (HttpServerErrorException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("ML - 5xx (multipart {}). Retry en {}ms... ({}/{})", uri, waitMs, normalRetries, MAX_RETRIES);
                sleep(waitMs);
            } catch (ResourceAccessException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("ML - Error conexión (multipart {}). Retry en {}ms... ({}/{}): {}", uri, waitMs, normalRetries, MAX_RETRIES, e.getMessage());
                sleep(waitMs);
            }
        }
    }
```

- [ ] **Step 2: Compilar el módulo**

Run: `cd supermaster-backend && ./mvnw.cmd -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/MlRetryHandler.java
git commit -m "feat(ml): MlRetryHandler.postMultipart para subir imagenes

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: `ResultadoAltaMl` + núcleo `crearItemEnMlCore` + `crearItemEnMl` (en `MercadoLibreService`)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/ResultadoAltaMl.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/CrearItemEnMlTest.java`

**Interfaces:**
- Consumes: `MlItemPayloadBuilder.construir` (Task 3), `MlDescripcionBuilder.construir` (Task 2), `ImagenService.resolverArchivosPorSku`/`leerBytes` (B3/Task 1), `MlRetryHandler.postMultipart`/`postJson`/`get` (Task 4), `buscarMlaPorSku`.
- Produces:
  - `record ResultadoAltaMl(Estado{CREADO,YA_EXISTIA,ERROR}, String motivo, String itemId, String advertencia)` con factories `creado(itemId)`, `yaExistia()`, `error(motivo)`, método `conAdvertencia(adv)`.
  - `static ResultadoAltaMl crearItemEnMlCore(Producto producto, ObjectMapper om, Function<String,Boolean> yaExiste, Function<String,List<String>> archivosResolver, Function<String,String> subidorImagen, Function<String,String> predictor, Function<String,String> poster, BiFunction<String,String,String> posterDescripcion)`.
  - `ResultadoAltaMl crearItemEnMl(Producto)` (público; arma las lambdas reales).

- [ ] **Step 1: Write `ResultadoAltaMl` + the failing test**

`ResultadoAltaMl.java`:
```java
package ar.com.leo.super_master_backend.apis.ml.dto;

public record ResultadoAltaMl(Estado estado, String motivo, String itemId, String advertencia) {
    public enum Estado { CREADO, YA_EXISTIA, ERROR }
    public static ResultadoAltaMl creado(String itemId) { return new ResultadoAltaMl(Estado.CREADO, null, itemId, null); }
    public static ResultadoAltaMl yaExistia() { return new ResultadoAltaMl(Estado.YA_EXISTIA, null, null, null); }
    public static ResultadoAltaMl error(String motivo) { return new ResultadoAltaMl(Estado.ERROR, motivo, null, null); }
    public ResultadoAltaMl conAdvertencia(String advertencia) {
        return new ResultadoAltaMl(estado, motivo, itemId, advertencia);
    }
}
```

`CrearItemEnMlTest.java`:
```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CrearItemEnMlTest {

    private final ObjectMapper om = new ObjectMapper();

    private Producto producto() {
        Producto p = new Producto();
        p.setSku("ABC123"); p.setTituloMl("Olla acero 5L"); p.setCosto(new BigDecimal("1000"));
        Marca m = new Marca(); m.setNombre("Tramontina"); p.setMarca(m);
        return p;
    }

    // Lambdas por defecto (caso feliz).
    private final Function<String, Boolean> noExiste = sku -> false;
    private final Function<String, List<String>> conImagen = sku -> List.of("ABC123.jpg");
    private final Function<String, String> subeOk = filename -> "PIC_" + filename;
    private final Function<String, String> predice = titulo -> "MLA1234";

    @Test
    void sinTitulo_error() {
        Producto p = producto(); p.setTituloMl("  ");
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                p, om, noExiste, conImagen, subeOk, predice,
                json -> "{\"id\":\"MLA1\"}", (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("título");
    }

    @Test
    void yaExiste_noPostea() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, sku -> true, conImagen, subeOk, predice,
                json -> { posted.set(json); return "{\"id\":\"MLA1\"}"; }, (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.YA_EXISTIA);
        assertThat(posted.get()).isNull();
    }

    @Test
    void sinImagenes_error() {
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, sku -> List.of(), subeOk, predice,
                json -> "{\"id\":\"MLA1\"}", (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("imágenes");
    }

    @Test
    void ok_creadoConItemId() {
        AtomicReference<String> descripcion = new AtomicReference<>();
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, conImagen, subeOk, predice,
                json -> "{\"id\":\"MLA999\"}",
                (id, txt) -> { descripcion.set(id + "|" + txt); return "{}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(r.itemId()).isEqualTo("MLA999");
        assertThat(descripcion.get()).startsWith("MLA999|CARACTERÍSTICAS");
    }

    @Test
    void respuestaConError_devuelveError() {
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, conImagen, subeOk, predice,
                json -> "{\"message\":\"Validation error\",\"cause\":[{\"type\":\"error\",\"message\":\"Attribute [BRAND] is required\"}]}",
                (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
        assertThat(r.motivo()).contains("BRAND");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=CrearItemEnMlTest`
Expected: FAIL de compilación ("cannot find symbol: method crearItemEnMlCore").

- [ ] **Step 3: Implementar el núcleo y el método público en `MercadoLibreService`**

Agregar imports en `MercadoLibreService.java` (si faltan): `java.util.function.Function`, `java.util.function.BiFunction`, `ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl`, `ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService`, `java.math.BigDecimal` (ya está). Inyectar `ImagenService` (agregar al constructor un parámetro `ImagenService imagenService` y el campo `private final ImagenService imagenService;`, asignándolo).

Agregar el núcleo estático (testeable, sin red):
```java
    /**
     * Núcleo testeable del alta a ML. Las lambdas aíslan la red:
     *  - yaExiste(sku) → true si ya hay publicación (no duplicar).
     *  - archivosResolver(sku) → nombres de archivo de imagen del SKU.
     *  - subidorImagen(filename) → pictureId subido (o null si falló esa imagen).
     *  - predictor(titulo) → category_id (o null).
     *  - poster(json) → respuesta de POST /items (éxito con "id" o body de error con "cause").
     *  - posterDescripcion(itemId, plainText) → respuesta (se ignora salvo excepción).
     */
    static ResultadoAltaMl crearItemEnMlCore(
            Producto producto, ObjectMapper om,
            Function<String, Boolean> yaExiste,
            Function<String, List<String>> archivosResolver,
            Function<String, String> subidorImagen,
            Function<String, String> predictor,
            Function<String, String> poster,
            BiFunction<String, String, String> posterDescripcion) {
        try {
            String sku = producto.getSku();
            if (producto.getTituloMl() == null || producto.getTituloMl().isBlank())
                return ResultadoAltaMl.error("Falta Título ML");
            if (producto.getCosto() == null)
                return ResultadoAltaMl.error("Falta costo");
            if (Boolean.TRUE.equals(yaExiste.apply(sku)))
                return ResultadoAltaMl.yaExistia();

            List<String> archivos = archivosResolver.apply(sku);
            if (archivos == null || archivos.isEmpty())
                return ResultadoAltaMl.error("Sin imágenes (obligatorias para publicación clásica)");
            List<String> pictureIds = new ArrayList<>();
            for (String filename : archivos) {
                String picId = subidorImagen.apply(filename);
                if (picId != null && !picId.isBlank()) pictureIds.add(picId);
            }
            if (pictureIds.isEmpty())
                return ResultadoAltaMl.error("No se pudieron subir las imágenes");

            String categoryId = predictor.apply(producto.getTituloMl());
            if (categoryId == null || categoryId.isBlank())
                return ResultadoAltaMl.error("No se pudo predecir la categoría");

            BigDecimal price = producto.getCosto().multiply(BigDecimal.valueOf(5));

            // Intento con cantidad 0; si ML lo rechaza por stock, reintento con 1 (aviso).
            String advertencia = null;
            String respuesta = poster.apply(om.writeValueAsString(
                    MlItemPayloadBuilder.construir(producto, categoryId, price, 0, pictureIds)));
            String error = extraerErrorMl(om, respuesta);
            if (error != null && error.toLowerCase().contains("quantity")) {
                respuesta = poster.apply(om.writeValueAsString(
                        MlItemPayloadBuilder.construir(producto, categoryId, price, 1, pictureIds)));
                error = extraerErrorMl(om, respuesta);
                advertencia = "publicado con stock 1 (la categoría no admite 0)";
            }
            if (error != null) return ResultadoAltaMl.error(error);

            String itemId = om.readTree(respuesta).path("id").asString("");
            if (itemId.isBlank()) return ResultadoAltaMl.error("ML no devolvió id del ítem");

            try {
                posterDescripcion.apply(itemId, MlDescripcionBuilder.construir(producto));
            } catch (Exception e) {
                advertencia = (advertencia == null ? "" : advertencia + "; ") + "ítem creado pero falló la descripción";
            }

            ResultadoAltaMl r = ResultadoAltaMl.creado(itemId);
            return advertencia == null ? r : r.conAdvertencia(advertencia);
        } catch (Exception e) {
            return ResultadoAltaMl.error(e.getMessage());
        }
    }

    /** Si la respuesta de ML es un error (tiene "cause" con type:error o un "error"/"message" de validación), devuelve el texto; si no, null. */
    private static String extraerErrorMl(ObjectMapper om, String respuesta) {
        if (respuesta == null || respuesta.isBlank()) return null;
        JsonNode root = om.readTree(respuesta);
        JsonNode cause = root.path("cause");
        if (cause.isArray() && !cause.isEmpty()) {
            List<String> errores = new ArrayList<>();
            for (JsonNode c : cause) {
                if ("error".equals(c.path("type").asString(""))) errores.add(c.path("message").asString(""));
            }
            if (!errores.isEmpty()) return String.join("; ", errores);
        }
        // Sin "id" y con "error"/"message" → fallo.
        if (root.path("id").asString("").isBlank() && !root.path("error").asString("").isBlank())
            return root.path("message").asString(root.path("error").asString("Error de Mercado Libre"));
        return null;
    }
```

Agregar el método público que arma las lambdas reales:
```java
    /** Da de alta un producto en Mercado Libre (sitio MLA). Resuelve las dependencias de red y delega al núcleo. */
    public ResultadoAltaMl crearItemEnMl(Producto producto) {
        if (!isConfigured()) return ResultadoAltaMl.error("Mercado Libre no configurado");
        verificarTokens();
        return crearItemEnMlCore(
                producto, objectMapper,
                sku -> buscarMlaPorSku(sku) != null,
                sku -> imagenService.resolverArchivosPorSku(sku),
                filename -> subirImagenItem(filename),
                titulo -> predecirCategoria(titulo),
                json -> postearItem(json),
                (itemId, plainText) -> retryHandler.postJson("/items/" + itemId + "/description",
                        () -> tokens.accessToken, objectMapper.writeValueAsString(Map.of("plain_text", plainText))));
    }

    /** Sube una imagen a ML por multipart y devuelve su picture_id (o null si falla). */
    private String subirImagenItem(String filename) {
        try {
            byte[] bytes = imagenService.leerBytes(filename);
            String resp = retryHandler.postMultipart("/pictures/items/upload", () -> tokens.accessToken, filename, bytes);
            String id = objectMapper.readTree(resp).path("id").asString("");
            return id.isBlank() ? null : id;
        } catch (Exception e) {
            log.warn("ML - Falló subir imagen {}: {}", filename, e.getMessage());
            return null;
        }
    }

    /** Predictor de categoría a partir del título. Devuelve el category_id de mayor probabilidad (o null). */
    private String predecirCategoria(String titulo) {
        try {
            String uri = "/sites/MLA/domain_discovery/search?limit=1&q="
                    + URLEncoder.encode(titulo, StandardCharsets.UTF_8);
            String resp = retryHandler.get(uri, () -> tokens.accessToken);
            JsonNode arr = objectMapper.readTree(resp);
            if (arr.isArray() && !arr.isEmpty()) {
                String cat = arr.get(0).path("category_id").asString("");
                return cat.isBlank() ? null : cat;
            }
            return null;
        } catch (Exception e) {
            log.warn("ML - Falló predecir categoría para '{}': {}", titulo, e.getMessage());
            return null;
        }
    }

    /** POST /items devolviendo el body (éxito o, ante 4xx, el body de error de ML). */
    private String postearItem(String json) {
        try {
            return retryHandler.postJson("/items", () -> tokens.accessToken, json);
        } catch (HttpClientErrorException e) {
            return e.getResponseBodyAsString();
        }
    }
```
(`Map`, `URLEncoder`, `StandardCharsets`, `HttpClientErrorException`, `JsonNode` ya están importados en la clase.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=CrearItemEnMlTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/ResultadoAltaMl.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/CrearItemEnMlTest.java
git commit -m "feat(ml): alta de item (nucleo testeable: imagenes, predictor, item, descripcion)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: `MlExportService` + DTOs + endpoint `POST /api/ml/exportar-productos`

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlExportRequestDTO.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlExportResultDTO.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlExportService.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/controller/MlExportController.java`

**Interfaces:**
- Consumes: `MercadoLibreService.crearItemEnMl(Producto)` (Task 5), `ProductoRepository.findBySkuIn`.
- Produces: `MlExportResultDTO(int creados, List<String> yaExistian, List<String> errores, List<String> advertencias)`; endpoint `POST /api/ml/exportar-productos`.

> **Nota:** orquesta red; sin test unitario. Verificación: compila y la suite ML/imagen sigue verde.

- [ ] **Step 1: Crear DTOs**

`MlExportRequestDTO.java`:
```java
package ar.com.leo.super_master_backend.apis.ml.dto;

import java.util.List;

public record MlExportRequestDTO(List<String> skus) {}
```
`MlExportResultDTO.java`:
```java
package ar.com.leo.super_master_backend.apis.ml.dto;

import java.util.List;

public record MlExportResultDTO(int creados, List<String> yaExistian, List<String> errores, List<String> advertencias) {}
```

- [ ] **Step 2: Crear `MlExportService`**

`MlExportService.java`:
```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.MlExportRequestDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlExportResultDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MlExportService {

    private final ProductoRepository productoRepository;
    private final MercadoLibreService mercadoLibreService;

    /**
     * Necesita sesión JPA abierta durante el loop: el alta accede a asociaciones LAZY del Producto
     * (marca, material, aptos) con open-in-view=false. El I/O de red contra ML ocurre dentro de la
     * transacción; aceptable para este export manual de bajo volumen.
     */
    @Transactional(readOnly = true)
    public MlExportResultDTO exportar(MlExportRequestDTO request) {
        int creados = 0;
        List<String> yaExistian = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        if (request == null || request.skus() == null) {
            return new MlExportResultDTO(0, yaExistian, errores, advertencias);
        }

        List<Producto> productos = productoRepository.findBySkuIn(
                request.skus().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList());

        for (Producto producto : productos) {
            String etiqueta = producto.getSku();
            ResultadoAltaMl r = mercadoLibreService.crearItemEnMl(producto);
            switch (r.estado()) {
                case CREADO -> {
                    creados++;
                    if (r.advertencia() != null) advertencias.add(etiqueta + ": " + r.advertencia());
                }
                case YA_EXISTIA -> yaExistian.add(etiqueta);
                case ERROR -> errores.add(etiqueta + ": " + r.motivo());
            }
        }
        return new MlExportResultDTO(creados, yaExistian, errores, advertencias);
    }
}
```

- [ ] **Step 3: Crear `MlExportController`**

`MlExportController.java` (mirá `NubeProductoController` para el patrón exacto de `@PreAuthorize`/import de `Permisos`):
```java
package ar.com.leo.super_master_backend.apis.ml.controller;

import ar.com.leo.super_master_backend.apis.ml.dto.MlExportRequestDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlExportResultDTO;
import ar.com.leo.super_master_backend.apis.ml.service.MlExportService;
import ar.com.leo.super_master_backend.config.security.Permisos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
public class MlExportController {

    private final MlExportService mlExportService;

    @PostMapping("/exportar-productos")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<MlExportResultDTO> exportar(@RequestBody(required = false) MlExportRequestDTO request) {
        return ResponseEntity.ok(mlExportService.exportar(request));
    }
}
```
**Nota:** verificá la ruta/constante real de `Permisos.INTEGRACIONES_EDITAR` y el import exacto copiándolos de `NubeProductoController.java`.

- [ ] **Step 4: Compilar y correr la suite ML/imagen**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest="ar.com.leo.super_master_backend.apis.ml.**,ImagenService*Test"`
Expected: PASS — incluye `CrearItemEnMlTest` (5), `MlDescripcionBuilderTest` (2), `MlItemPayloadBuilderTest` (2), tests de `ImagenService`.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlExportRequestDTO.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlExportResultDTO.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlExportService.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/controller/MlExportController.java
git commit -m "feat(ml): MlExportService + endpoint POST /api/ml/exportar-productos

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Frontend — checkbox "Subir a Mercado Libre" + toast

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

**Interfaces:**
- Consumes: `POST /api/ml/exportar-productos` con `{ skus: [...] }` → `{ creados, yaExistian, errores, advertencias }`.

- [ ] **Step 1: Agregar tipo y función al service**

En `productosService.ts`, después del bloque de Nube (`exportarProductosANubeAPI`), agregar (respetando tabs):
```ts
export type ExportMlResultDTO = {
	creados: number;
	yaExistian: string[];
	errores: string[];
	advertencias: string[];
};

export const exportarProductosAMlAPI = async (skus: string[]): Promise<ExportMlResultDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/ml/exportar-productos`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ skus }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Mercado Libre"));
	return await res.json();
};
```

- [ ] **Step 2: Estado del checkbox**

En `page.tsx`, junto a `subirKtHogar`/`subirKtGastro`, agregar el estado:
```ts
    const [subirMl, setSubirMl] = useState(false);
```
y resetearlo donde se resetean los otros (en `resetForm`/`abrirEdicion`): `setSubirMl(false);`. Importar `exportarProductosAMlAPI` del service (sumarlo al import existente de `./productosService`).

- [ ] **Step 3: Checkbox en "Canales de venta"**

En la sección "Canales de venta" de `page.tsx`, junto al checkbox de Mercado Libre que hoy es placeholder (buscar el texto "Mercado Libre" / "ML" en esa sección), reemplazar el placeholder por un checkbox funcional (mismo estilo que los de KT HOGAR/GASTRO):
```tsx
                    <label className="flex items-center gap-2">
                        <input type="checkbox" checked={subirMl} onChange={(e) => setSubirMl(e.target.checked)} />
                        Subir a Mercado Libre
                    </label>
```
(Adaptá las clases/estructura al markup real de los checkboxes vecinos en esa sección.)

- [ ] **Step 4: Disparo + toast en handleCreate y handleGuardarEdicion**

En `page.tsx`, en AMBOS handlers (creación ~después del bloque de Nube en `handleCreate`, y edición en `handleGuardarEdicion`), agregar tras el bloque de Nube:
```ts
            if (subirMl && canExportarDux) {
                try {
                    const r = await exportarProductosAMlAPI([sku.trim()]);
                    const partes: string[] = [];
                    if (r.creados > 0) partes.push(`${r.creados} creado(s) en ML`);
                    if (r.yaExistian.length) partes.push(`${r.yaExistian.length} ya existía(n)`);
                    if (r.advertencias?.length) partes.push(`avisos: ${r.advertencias.join("; ")}`);
                    if (r.errores.length) partes.push(`${r.errores.length} con error: ${r.errores.join("; ")}`);
                    if (r.errores.length) notificar.error(`Mercado Libre: ${partes.join(" · ")}`);
                    else notificar.success(`Mercado Libre: ${partes.join(" · ") || "sin cambios"}`);
                } catch (e) {
                    notificar.error(`Mercado Libre: ${e instanceof Error ? e.message : "error al subir"}`);
                }
            }
```
**Nota:** usá la misma variable de SKU que el bloque de Nube vecino (en `handleCreate` es `sku`, en `handleGuardarEdicion` revisá cuál usa el bloque de Nube — `sku` o el del producto en edición — y reproducila). Mantené el patrón: el alta a ML no debe tumbar el guardado del producto (de ahí el try/catch).

- [ ] **Step 5: Typecheck y commit**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: exit 0.
```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): checkbox Subir a Mercado Libre + toast

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Verificación final (tras todas las tasks)

- [ ] **Suite completa backend:** `cd supermaster-backend && ./mvnw.cmd -o test` → 0 failures, 0 errors (C1 suma ~11 tests).
- [ ] **Typecheck frontend:** `cd supermaster-frontend && cmd /c "npx tsc --noEmit"` → 0 errores.
- [ ] **Ninguna llamada real a Mercado Libre** se ejecuta en los tests.

## Notas de cierre

- El alta a ML se dispara en backend al guardar el producto con el checkbox; el front muestra el resumen.
- En producción, la subida real (multipart, predictor, POST /items, descripción) la prueba el usuario al usar la cuenta ML configurada. Los errores de ML (atributos obligatorios por categoría) se reportan por SKU y guían el mapeo de ficha técnica de C2.
- Fuera de alcance: C2 (ficha técnica/atributos por categoría), EAN/GTIN, variaciones, garantía.
