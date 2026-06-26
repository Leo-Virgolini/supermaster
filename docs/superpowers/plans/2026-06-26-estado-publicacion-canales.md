# Estado de publicación ML/Nube — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Desde el modal de edición, leer al abrir el estado real y un snapshot read-only (precio/stock/dimensiones) de la publicación de ML y de cada tienda Nube, y aplicar al guardar los cambios de estado (ML activa/pausada, Nube visible/oculta por tienda).

**Architecture:** Los servicios de canal (`MercadoLibreService`, `TiendaNubeService`) exponen métodos HTTP crudos (leer item / actualizar status / actualizar published). Un paquete nuevo `dominio/producto/estado` orquesta: parsers PUROS convierten el JSON de cada canal en un DTO por canal, un `EstadoPublicacionService` cruza ML + las dos tiendas Nube, y un controller expone `GET`/`PUT /api/productos/{id}/estado-publicacion`. El frontend lo consume en el modal.

**Tech Stack:** Java 25 / Spring Boot 4 / Maven / JUnit 5 / AssertJ / Mockito / Jackson 3 (`tools.jackson`); Next.js / React / TypeScript (frontend).

## Global Constraints

- Backend: tests con `mvn -o test` (offline) desde `supermaster-backend`. El wrapper `mvnw` falla por red; usar el `mvn` del PATH.
- Fetch on demand: NO se persiste estado ni ids de ML/Nube. Solo se lee al abrir y se aplica al guardar.
- ML: solo `active` ↔ `paused` (nunca `closed`). Nube: `published` true (visible) / false (oculta), por tienda (`STORE_HOGAR = "KT HOGAR"`, `STORE_GASTRO = "KT GASTRO"`).
- Solo opera sobre publicaciones existentes; no cambia el alta.
- Jackson 3: el package es `tools.jackson.databind`. `JsonNode`/`ObjectMapper` vienen de ahí (ya se usa así en el proyecto).
- Commits en español, estilo `tipo(scope): ...`, cerrando con:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- Frontend: verificación con `npm run lint` + `npx tsc --noEmit -p tsconfig.json` desde `supermaster-frontend`; sin tests automáticos (manual + lint + tsc). El archivo `ProductoFormModal.tsx` ya tiene ~7 errores de lint pre-existentes (`set-state-in-effect`): no agregar nuevos de otra clase.

## Estructura de archivos

- Crear `dominio/producto/estado/dto/EstadoCanalDTO.java` — record por canal (estado + snapshot).
- Crear `dominio/producto/estado/dto/EstadoPublicacionDTO.java` — record con los 3 canales (respuesta del GET).
- Crear `dominio/producto/estado/dto/EstadoPublicacionUpdateDTO.java` — record con el estado deseado por canal (body del PUT).
- Crear `dominio/producto/estado/MlEstadoParser.java` — puro: `JsonNode item → EstadoCanalDTO`.
- Crear `dominio/producto/estado/NubeEstadoParser.java` — puro: `JsonNode product → EstadoCanalDTO`.
- Crear `dominio/producto/estado/EstadoPublicacionService.java` — orquesta lectura/escritura.
- Crear `dominio/producto/estado/EstadoPublicacionController.java` — endpoints GET/PUT.
- Modificar `apis/ml/service/MercadoLibreService.java` — `leerItemRaw`, `actualizarStatusItem`.
- Modificar `apis/nube/service/TiendaNubeService.java` — `actualizarPublished`.
- Modificar frontend `productos/productosService.ts` — llamadas al GET/PUT.
- Modificar frontend `productos/ProductoFormModal.tsx` — sección de estado, fetch al abrir, apply al guardar, fix tooltip ML.

---

### Task 1: DTOs + parsers puros (ML y Nube)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/EstadoCanalDTO.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/EstadoPublicacionDTO.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/EstadoPublicacionUpdateDTO.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlEstadoParser.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/NubeEstadoParser.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlEstadoParserTest.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/NubeEstadoParserTest.java`

**Interfaces:**
- Produces: `EstadoCanalDTO(boolean publicado, String estado, java.math.BigDecimal precio, Integer stock, String peso, String dimensiones, boolean error)`.
- Produces: `MlEstadoParser.parse(JsonNode item) → EstadoCanalDTO` (estado = "active"/"paused"/otro).
- Produces: `NubeEstadoParser.parse(JsonNode product) → EstadoCanalDTO` (estado = "visible"/"oculta").
- Produces helpers de "no publicado"/"error": `EstadoCanalDTO.noPublicado()`, `EstadoCanalDTO.error()`.

- [ ] **Step 1: Crear los DTOs**

`EstadoCanalDTO.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

import java.math.BigDecimal;

/** Estado + snapshot read-only de la publicación de un canal. */
public record EstadoCanalDTO(
        boolean publicado,
        String estado,        // ML: "active"/"paused"; Nube: "visible"/"oculta"; null si no aplica
        BigDecimal precio,
        Integer stock,
        String peso,          // ej "1.5 kg" / "214 g"
        String dimensiones,   // ej "10 × 20 × 30 cm"
        boolean error
) {
    public static EstadoCanalDTO noPublicado() {
        return new EstadoCanalDTO(false, null, null, null, null, null, false);
    }
    public static EstadoCanalDTO error() {
        return new EstadoCanalDTO(false, null, null, null, null, null, true);
    }
}
```

`EstadoPublicacionDTO.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

/** Respuesta del GET: estado + snapshot de cada canal. */
public record EstadoPublicacionDTO(
        EstadoCanalDTO ml,
        EstadoCanalDTO hogar,
        EstadoCanalDTO gastro
) {}
```

`EstadoPublicacionUpdateDTO.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

/** Body del PUT: estado deseado por canal; null = no tocar ese canal. */
public record EstadoPublicacionUpdateDTO(
        String ml,        // "active" | "paused" | null
        Boolean hogar,    // true=visible / false=oculta / null
        Boolean gastro
) {}
```

- [ ] **Step 2: Escribir los tests de los parsers (fallan)**

`MlEstadoParserTest.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MlEstadoParserTest {

    private static JsonNode json(String s) {
        return new ObjectMapper().readTree(s);
    }

    @Test
    void parse_extraeEstadoPrecioStockYDimensiones() {
        JsonNode item = json("""
            {
              "status": "active",
              "price": 12345.0,
              "available_quantity": 7,
              "attributes": [
                {"id": "SELLER_PACKAGE_HEIGHT", "value_name": "10 cm"},
                {"id": "SELLER_PACKAGE_WIDTH",  "value_name": "20 cm"},
                {"id": "SELLER_PACKAGE_LENGTH", "value_name": "30 cm"},
                {"id": "SELLER_PACKAGE_WEIGHT", "value_name": "214 g"}
              ]
            }
            """);

        EstadoCanalDTO dto = MlEstadoParser.parse(item);

        assertThat(dto.publicado()).isTrue();
        assertThat(dto.estado()).isEqualTo("active");
        assertThat(dto.precio()).isEqualByComparingTo(new BigDecimal("12345.0"));
        assertThat(dto.stock()).isEqualTo(7);
        assertThat(dto.peso()).isEqualTo("214 g");
        assertThat(dto.dimensiones()).isEqualTo("10 cm × 20 cm × 30 cm");
        assertThat(dto.error()).isFalse();
    }

    @Test
    void parse_sinAtributosDeDimensiones_dejaPesoYDimsNull() {
        JsonNode item = json("""
            {"status": "paused", "price": 100, "available_quantity": 0, "attributes": []}
            """);

        EstadoCanalDTO dto = MlEstadoParser.parse(item);

        assertThat(dto.estado()).isEqualTo("paused");
        assertThat(dto.peso()).isNull();
        assertThat(dto.dimensiones()).isNull();
        assertThat(dto.publicado()).isTrue();
    }
}
```

`NubeEstadoParserTest.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NubeEstadoParserTest {

    private static JsonNode json(String s) {
        return new ObjectMapper().readTree(s);
    }

    @Test
    void parse_visibleConVariant() {
        JsonNode product = json("""
            {
              "id": 555,
              "published": true,
              "variants": [
                {"id": 999, "price": "12345.00", "stock": 4, "weight": "1.50",
                 "height": "10.00", "width": "20.00", "depth": "30.00"}
              ]
            }
            """);

        EstadoCanalDTO dto = NubeEstadoParser.parse(product);

        assertThat(dto.publicado()).isTrue();
        assertThat(dto.estado()).isEqualTo("visible");
        assertThat(dto.precio()).isEqualByComparingTo(new BigDecimal("12345.00"));
        assertThat(dto.stock()).isEqualTo(4);
        assertThat(dto.peso()).isEqualTo("1.50 kg");
        assertThat(dto.dimensiones()).isEqualTo("10.00 × 20.00 × 30.00 cm");
    }

    @Test
    void parse_ocultaYStockNulo() {
        JsonNode product = json("""
            {"id": 1, "published": false, "variants": [{"id": 2, "price": "10", "stock": null,
              "weight": "0.050", "height": "5.00", "width": "5.00", "depth": "8.00"}]}
            """);

        EstadoCanalDTO dto = NubeEstadoParser.parse(product);

        assertThat(dto.estado()).isEqualTo("oculta");
        assertThat(dto.stock()).isNull();
        assertThat(dto.publicado()).isTrue();
    }
}
```

- [ ] **Step 3: Correr los tests y verlos fallar**

Run: `cd supermaster-backend && mvn -o test -Dtest=MlEstadoParserTest,NubeEstadoParserTest`
Expected: FAIL — `MlEstadoParser`/`NubeEstadoParser` no existen.

- [ ] **Step 4: Implementar los parsers**

`MlEstadoParser.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** Parsea el JSON de GET /items/{id} de ML a un EstadoCanalDTO (puro). */
public final class MlEstadoParser {

    private MlEstadoParser() {}

    public static EstadoCanalDTO parse(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) return EstadoCanalDTO.error();
        String status = item.path("status").asString(null);
        JsonNode precioN = item.path("price");
        BigDecimal precio = precioN.isNumber() ? precioN.decimalValue() : null;
        JsonNode stockN = item.path("available_quantity");
        Integer stock = stockN.isNumber() ? stockN.asInt() : null;

        String alto = atributo(item, "SELLER_PACKAGE_HEIGHT");
        String ancho = atributo(item, "SELLER_PACKAGE_WIDTH");
        String largo = atributo(item, "SELLER_PACKAGE_LENGTH");
        String peso = atributo(item, "SELLER_PACKAGE_WEIGHT");
        String dims = (alto != null && ancho != null && largo != null)
                ? alto + " × " + ancho + " × " + largo : null;

        return new EstadoCanalDTO(true, status, precio, stock, peso, dims, false);
    }

    /** value_name del atributo con ese id, o null. Usa asString(null) (idiom Jackson 3 del proyecto). */
    private static String atributo(JsonNode item, String id) {
        for (JsonNode a : item.path("attributes")) {
            if (id.equals(a.path("id").asString(null))) return a.path("value_name").asString(null);
        }
        return null;
    }
}
```

`NubeEstadoParser.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** Parsea el JSON de GET /products/sku/{sku} de Nube a un EstadoCanalDTO (puro). */
public final class NubeEstadoParser {

    private NubeEstadoParser() {}

    public static EstadoCanalDTO parse(JsonNode product) {
        if (product == null || product.isMissingNode() || product.isNull()) return EstadoCanalDTO.error();
        boolean published = product.path("published").asBoolean(false);
        JsonNode variant = product.path("variants").path(0);

        String precioStr = variant.path("price").asString(null);
        BigDecimal precio = precioStr != null ? new BigDecimal(precioStr) : null;
        JsonNode stockN = variant.path("stock");
        Integer stock = stockN.isNumber() ? stockN.asInt() : null;
        String pesoStr = variant.path("weight").asString(null);
        String peso = pesoStr != null ? pesoStr + " kg" : null;
        String alto = variant.path("height").asString(null);
        String ancho = variant.path("width").asString(null);
        String largo = variant.path("depth").asString(null);
        String dims = (alto != null && ancho != null && largo != null)
                ? alto + " × " + ancho + " × " + largo + " cm" : null;

        return new EstadoCanalDTO(true, published ? "visible" : "oculta", precio, stock, peso, dims, false);
    }
}
```

- [ ] **Step 5: Correr los tests y verlos pasar**

Run: `cd supermaster-backend && mvn -o test -Dtest=MlEstadoParserTest,NubeEstadoParserTest`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/
git add supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/
git commit -m "feat(estado): DTOs y parsers de estado de publicación ML/Nube"
```

---

### Task 2: MercadoLibreService — leer item raw + actualizar status

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (agregar dos métodos junto a `updateItemPrice`, ~línea 1366)

**Interfaces:**
- Consumes: `retryHandler.get(uri, Supplier<String>)` → String (raw); `retryHandler.putJson(uri, Supplier<String>, body)`; `objectMapper.readTree(String)`; `tokens.accessToken`; `verificarTokens()`.
- Produces: `JsonNode leerItemRaw(String mlaCode)` (null si falla); `boolean actualizarStatusItem(String mlaCode, String status)`.

- [ ] **Step 1: Implementar `leerItemRaw`**

En `MercadoLibreService`, junto a `updateItemPrice`, agregar:
```java
    /** GET /items/{id} crudo (JsonNode) para leer estado/precio/stock/dimensiones. Null si falla. */
    public JsonNode leerItemRaw(String mlaCode) {
        if (mlaCode == null || mlaCode.isBlank()) return null;
        verificarTokens();
        try {
            String response = retryHandler.get("/items/" + mlaCode, () -> tokens.accessToken);
            return response == null ? null : objectMapper.readTree(response);
        } catch (Exception e) {
            log.warn("ML - No se pudo leer item {}: {}", mlaCode, e.getMessage());
            return null;
        }
    }
```

- [ ] **Step 2: Implementar `actualizarStatusItem`**

```java
    /** Activa o pausa la publicación. status debe ser "active" o "paused". */
    public boolean actualizarStatusItem(String mlaCode, String status) {
        if (mlaCode == null || mlaCode.isBlank()) return false;
        if (!"active".equals(status) && !"paused".equals(status)) {
            log.warn("ML - status inválido para item {}: {}", mlaCode, status);
            return false;
        }
        verificarTokens();
        try {
            String body = "{\"status\":\"" + status + "\"}";
            retryHandler.putJson("/items/" + mlaCode, () -> tokens.accessToken, body);
            return true;
        } catch (Exception e) {
            log.warn("ML - Error actualizando status de item {}: {}", mlaCode, e.getMessage());
            return false;
        }
    }
```

- [ ] **Step 3: Verificar imports y compilar**

Confirmar que `JsonNode` (`tools.jackson.databind.JsonNode`) está importado en el archivo (ya se usa `objectMapper.readTree` con JsonNode en `obtenerStatusItems`, así que el import existe). Compilar:

Run: `cd supermaster-backend && mvn -o test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java
git commit -m "feat(ml): leerItemRaw y actualizarStatusItem (active/paused)"
```

---

### Task 3: TiendaNubeService — actualizar published

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java` (agregar método junto a `actualizarPrecioVariante`, ~línea 418)

**Interfaces:**
- Consumes: `verificarCredenciales()`, `getStore(storeName)` → `StoreCredentials` con `getStoreId()`/`getAccessToken()`, `retryHandler.putJson(uri, accessToken, body)`. Constantes `STORE_HOGAR`/`STORE_GASTRO`.
- Produces: `boolean actualizarPublished(String storeName, long productId, boolean published)`.

- [ ] **Step 1: Implementar `actualizarPublished`**

En `TiendaNubeService`, junto a `actualizarPrecioVariante`:
```java
    /** Cambia la visibilidad (published) de un producto en una tienda Nube. */
    public boolean actualizarPublished(String storeName, long productId, boolean published) {
        verificarCredenciales();
        StoreCredentials store = getStore(storeName);
        if (store == null) {
            log.warn("NUBE - Store '{}' no encontrada en credenciales", storeName);
            return false;
        }
        String uri = String.format("/%s/products/%d", store.getStoreId(), productId);
        String body = "{\"published\":" + published + "}";
        try {
            retryHandler.putJson(uri, store.getAccessToken(), body);
            return true;
        } catch (Exception e) {
            log.warn("NUBE - Error actualizando published de producto {} en {}: {}", productId, storeName, e.getMessage());
            return false;
        }
    }
```

- [ ] **Step 2: Compilar**

Run: `cd supermaster-backend && mvn -o test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java
git commit -m "feat(nube): actualizarPublished (visible/oculta) por tienda"
```

---

### Task 4: EstadoPublicacionService (orquestación)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionServiceTest.java`

**Interfaces:**
- Consumes: `ProductoRepository.findById(Integer)` → `Optional<Producto>`; `Producto.getMla()` → `Mla` (`getMla()` String code); `Producto.getSku()`; `MercadoLibreService.leerItemRaw/actualizarStatusItem`; `TiendaNubeService.buscarProductoPorSku(sku, store)/actualizarPublished(store, id, bool)`; `MlEstadoParser.parse`, `NubeEstadoParser.parse`; constantes `TiendaNubeService.STORE_HOGAR/STORE_GASTRO`.
- Produces: `EstadoPublicacionDTO leer(Integer productoId)`; `void aplicar(Integer productoId, EstadoPublicacionUpdateDTO cambios)`.

- [ ] **Step 1: Escribir el test (falla)**

`EstadoPublicacionServiceTest.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EstadoPublicacionServiceTest {

    private final ProductoRepository repo = mock(ProductoRepository.class);
    private final MercadoLibreService ml = mock(MercadoLibreService.class);
    private final TiendaNubeService nube = mock(TiendaNubeService.class);
    private final EstadoPublicacionService service = new EstadoPublicacionService(repo, ml, nube);

    private static tools.jackson.databind.JsonNode json(String s) {
        return new ObjectMapper().readTree(s);
    }

    private Producto producto(String mlaCode, String sku) {
        Producto p = new Producto();
        p.setSku(sku);
        if (mlaCode != null) {
            Mla mla = new Mla();
            mla.setMla(mlaCode);
            p.setMla(mla);
        }
        return p;
    }

    @Test
    void leer_cruzaMlYLasDosTiendas() {
        Producto p = producto("MLA123", "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(ml.leerItemRaw("MLA123")).thenReturn(json("""
            {"status":"active","price":100,"available_quantity":3,"attributes":[]}"""));
        when(nube.buscarProductoPorSku("ABC", TiendaNubeService.STORE_HOGAR)).thenReturn(json("""
            {"id":5,"published":true,"variants":[{"id":9,"price":"100","stock":2,"weight":"1.0","height":"1","width":"1","depth":"1"}]}"""));
        when(nube.buscarProductoPorSku("ABC", TiendaNubeService.STORE_GASTRO)).thenReturn(null);

        EstadoPublicacionDTO dto = service.leer(1);

        assertThat(dto.ml().publicado()).isTrue();
        assertThat(dto.ml().estado()).isEqualTo("active");
        assertThat(dto.hogar().publicado()).isTrue();
        assertThat(dto.hogar().estado()).isEqualTo("visible");
        assertThat(dto.gastro().publicado()).isFalse(); // no encontrado
    }

    @Test
    void leer_sinMla_mlNoPublicado() {
        Producto p = producto(null, "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(nube.buscarProductoPorSku(anyString(), anyString())).thenReturn(null);

        EstadoPublicacionDTO dto = service.leer(1);

        assertThat(dto.ml().publicado()).isFalse();
        verify(ml, never()).leerItemRaw(any());
    }

    @Test
    void aplicar_soloLosCanalesPresentes() {
        Producto p = producto("MLA123", "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(nube.buscarProductoPorSku("ABC", TiendaNubeService.STORE_HOGAR)).thenReturn(json("""
            {"id":5,"published":false,"variants":[{"id":9}]}"""));

        service.aplicar(1, new EstadoPublicacionUpdateDTO("paused", Boolean.TRUE, null));

        verify(ml).actualizarStatusItem("MLA123", "paused");
        verify(nube).actualizarPublished(TiendaNubeService.STORE_HOGAR, 5L, true);
        verify(nube, never()).actualizarPublished(eq(TiendaNubeService.STORE_GASTRO), anyLong(), anyBoolean());
    }
}
```

- [ ] **Step 2: Correr el test y verlo fallar**

Run: `cd supermaster-backend && mvn -o test -Dtest=EstadoPublicacionServiceTest`
Expected: FAIL — `EstadoPublicacionService` no existe.

- [ ] **Step 3: Implementar el service**

`EstadoPublicacionService.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class EstadoPublicacionService {

    private final ProductoRepository productoRepository;
    private final MercadoLibreService mercadoLibreService;
    private final TiendaNubeService tiendaNubeService;

    public EstadoPublicacionService(ProductoRepository productoRepository,
                                    MercadoLibreService mercadoLibreService,
                                    TiendaNubeService tiendaNubeService) {
        this.productoRepository = productoRepository;
        this.mercadoLibreService = mercadoLibreService;
        this.tiendaNubeService = tiendaNubeService;
    }

    @Transactional(readOnly = true)
    public EstadoPublicacionDTO leer(Integer productoId) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        return new EstadoPublicacionDTO(
                leerMl(p),
                leerNube(p.getSku(), TiendaNubeService.STORE_HOGAR),
                leerNube(p.getSku(), TiendaNubeService.STORE_GASTRO));
    }

    private EstadoCanalDTO leerMl(Producto p) {
        String mlaCode = p.getMla() != null ? p.getMla().getMla() : null;
        if (mlaCode == null || mlaCode.isBlank()) return EstadoCanalDTO.noPublicado();
        JsonNode item = mercadoLibreService.leerItemRaw(mlaCode);
        if (item == null) return EstadoCanalDTO.error();
        return MlEstadoParser.parse(item);
    }

    private EstadoCanalDTO leerNube(String sku, String store) {
        JsonNode product;
        try {
            product = tiendaNubeService.buscarProductoPorSku(sku, store);
        } catch (Exception e) {
            return EstadoCanalDTO.error();
        }
        if (product == null) return EstadoCanalDTO.noPublicado();
        return NubeEstadoParser.parse(product);
    }

    @Transactional(readOnly = true)
    public void aplicar(Integer productoId, EstadoPublicacionUpdateDTO cambios) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        if (cambios.ml() != null && p.getMla() != null && p.getMla().getMla() != null) {
            mercadoLibreService.actualizarStatusItem(p.getMla().getMla(), cambios.ml());
        }
        if (cambios.hogar() != null) aplicarNube(p.getSku(), TiendaNubeService.STORE_HOGAR, cambios.hogar());
        if (cambios.gastro() != null) aplicarNube(p.getSku(), TiendaNubeService.STORE_GASTRO, cambios.gastro());
    }

    private void aplicarNube(String sku, String store, boolean visible) {
        JsonNode product = tiendaNubeService.buscarProductoPorSku(sku, store);
        if (product == null) return; // no publicado en esa tienda: nada que cambiar
        long productId = product.path("id").asLong();
        tiendaNubeService.actualizarPublished(store, productId, visible);
    }
}
```

> Nota: `aplicar` es `@Transactional(readOnly = true)` solo para cargar el Producto; no escribe en la BD (las escrituras son a las APIs externas).

- [ ] **Step 4: Correr el test y verlo pasar**

Run: `cd supermaster-backend && mvn -o test -Dtest=EstadoPublicacionServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionService.java
git add supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionServiceTest.java
git commit -m "feat(estado): EstadoPublicacionService orquesta lectura/escritura ML+Nube"
```

---

### Task 5: Controller GET/PUT

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionController.java`

**Interfaces:**
- Consumes: `EstadoPublicacionService.leer/aplicar`; el patrón de `@PreAuthorize(Permisos.PRODUCTOS_VER)` / `Permisos.INTEGRACIONES_EDITAR` (ya usados en el proyecto).
- Produces: `GET /api/productos/{id}/estado-publicacion` → `EstadoPublicacionDTO`; `PUT /api/productos/{id}/estado-publicacion` (body `EstadoPublicacionUpdateDTO`) → 204.

- [ ] **Step 1: Implementar el controller**

`EstadoPublicacionController.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.config.seguridad.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionUpdateDTO;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/productos/{id}/estado-publicacion")
public class EstadoPublicacionController {

    private final EstadoPublicacionService estadoPublicacionService;

    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<EstadoPublicacionDTO> leer(@PathVariable @Positive Integer id) {
        return ResponseEntity.ok(estadoPublicacionService.leer(id));
    }

    @PutMapping
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Void> aplicar(@PathVariable @Positive Integer id,
                                        @RequestBody EstadoPublicacionUpdateDTO cambios) {
        estadoPublicacionService.aplicar(id, cambios);
        return ResponseEntity.noContent().build();
    }
}
```

> Nota: verificar el import exacto de `Permisos` (buscar `import ...Permisos` en `MercadoLibreController.java`) y las constantes disponibles (`PRODUCTOS_VER`, `INTEGRACIONES_EDITAR`). Si el nombre difiere, usar el real.

- [ ] **Step 2: Compilar y correr la suite del paquete estado**

Run: `cd supermaster-backend && mvn -o test -Dtest='*Estado*'`
Expected: BUILD SUCCESS; tests de parsers y service en verde.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionController.java
git commit -m "feat(estado): endpoints GET/PUT /api/productos/{id}/estado-publicacion"
```

---

### Task 6: Frontend — fetch al abrir + sección de estado y snapshot

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` (agregar tipos + 2 llamadas)
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (estado nuevo, fetch al abrir, render de la sección — solo en modo edición)

**Interfaces:**
- Consumes: `fetchAPI`/patrón de los demás `*API` en `productosService.ts`; `esSesionExpirada`.
- Produces: tipos `EstadoCanal` y `EstadoPublicacion`; `getEstadoPublicacionAPI(id)` y `putEstadoPublicacionAPI(id, body)`; estado React `estadoCanales` + `estadoOriginal`.

- [ ] **Step 1: Agregar tipos y llamadas en productosService.ts**

Agregar (seguir el estilo de las funciones `*API` existentes del archivo):
```ts
export type EstadoCanal = {
    publicado: boolean;
    estado: string | null;       // ML: "active"/"paused"; Nube: "visible"/"oculta"
    precio: number | null;
    stock: number | null;
    peso: string | null;
    dimensiones: string | null;
    error: boolean;
};
export type EstadoPublicacion = { ml: EstadoCanal; hogar: EstadoCanal; gastro: EstadoCanal };
export type EstadoPublicacionUpdate = { ml?: string | null; hogar?: boolean | null; gastro?: boolean | null };

export async function getEstadoPublicacionAPI(id: number): Promise<EstadoPublicacion> {
    const r = await fetchAPI(`/api/productos/${id}/estado-publicacion`);
    return r.json();
}
export async function putEstadoPublicacionAPI(id: number, body: EstadoPublicacionUpdate): Promise<void> {
    await fetchAPI(`/api/productos/${id}/estado-publicacion`, { method: "PUT", body: JSON.stringify(body) });
}
```

> Nota: usar el mismo helper/firma que el resto del archivo (si `fetchAPI` se importa distinto o las funciones usan otro wrapper, replicar ese patrón exacto). Verificar cómo se hace un PUT con body en una función vecina.

- [ ] **Step 2: Estado React + fetch al abrir (modo edición)**

En `ProductoFormModal.tsx`, agregar estados:
```tsx
    const [estadoCanales, setEstadoCanales] = useState<EstadoPublicacion | null>(null);
    const [estadoOriginal, setEstadoOriginal] = useState<EstadoPublicacion | null>(null);
    const [cargandoEstado, setCargandoEstado] = useState(false);
```

En el bloque que ya carga datos al abrir en modo edición (donde se hace el `Promise.all` de aptos/catálogos/clientes, con el `producto.id`), agregar una carga async independiente:
```tsx
            setCargandoEstado(true);
            getEstadoPublicacionAPI(producto.id)
                .then(e => { setEstadoCanales(e); setEstadoOriginal(e); })
                .catch(err => { if (!esSesionExpirada(err)) notificar.error("No se pudo leer el estado de publicación"); })
                .finally(() => setCargandoEstado(false));
```

(Importar `getEstadoPublicacionAPI`, `putEstadoPublicacionAPI`, tipos desde `./productosService`.)

- [ ] **Step 3: Render de la sección "Estado de publicación"**

En el JSX del modo edición (dentro del form, junto a las demás secciones; solo cuando `editandoProductoId`), agregar un `<fieldset>` con un bloque por canal. Helper inline para un canal:
```tsx
    const renderEstadoCanal = (
        label: string,
        canal: EstadoCanal | undefined,
        control: React.ReactNode,
    ) => (
        <div className="rounded-2xl border border-slate-200 bg-white/70 p-4 dark:border-slate-700 dark:bg-slate-800/60">
            <div className="mb-2 text-sm font-semibold text-slate-700 dark:text-slate-200">{label}</div>
            {cargandoEstado ? <span className="text-xs text-slate-400">Leyendo estado…</span>
              : !canal || canal.error ? <span className="text-xs text-amber-600">No se pudo leer el estado</span>
              : !canal.publicado ? <span className="text-xs text-slate-400">No publicado</span>
              : (<>
                  {control}
                  <div className="mt-2 space-y-0.5 text-xs text-slate-500 dark:text-slate-400">
                      {canal.precio != null && <div>Precio: {canal.precio}</div>}
                      {canal.stock != null && <div>Stock: {canal.stock}</div>}
                      {canal.peso && <div>Peso: {canal.peso}</div>}
                      {canal.dimensiones && <div>Dimensiones: {canal.dimensiones}</div>}
                  </div>
              </>)}
        </div>
    );
```

Y la sección que usa el helper, con los controles editables que escriben en `estadoCanales`:
```tsx
                    {editandoProductoId && (
                    <fieldset className={`${sectionClassName} ${SECTION_TINT.canales}`}>
                        <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> Estado de publicación</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Estado real de cada publicación (se aplica al guardar).</p>
                        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                            {renderEstadoCanal("Mercado Libre", estadoCanales?.ml,
                                <select className={`${selectBaseClassName} w-full`} value={estadoCanales?.ml.estado ?? "active"}
                                    onChange={e => setEstadoCanales(p => p && ({ ...p, ml: { ...p.ml, estado: e.target.value } }))}>
                                    <option value="active">Activa</option>
                                    <option value="paused">Pausada</option>
                                </select>)}
                            {renderEstadoCanal("Nube · KT HOGAR", estadoCanales?.hogar,
                                <select className={`${selectBaseClassName} w-full`} value={estadoCanales?.hogar.estado ?? "visible"}
                                    onChange={e => setEstadoCanales(p => p && ({ ...p, hogar: { ...p.hogar, estado: e.target.value } }))}>
                                    <option value="visible">Visible</option>
                                    <option value="oculta">Oculta</option>
                                </select>)}
                            {renderEstadoCanal("Nube · KT GASTRO", estadoCanales?.gastro,
                                <select className={`${selectBaseClassName} w-full`} value={estadoCanales?.gastro.estado ?? "visible"}
                                    onChange={e => setEstadoCanales(p => p && ({ ...p, gastro: { ...p.gastro, estado: e.target.value } }))}>
                                    <option value="visible">Visible</option>
                                    <option value="oculta">Oculta</option>
                                </select>)}
                        </div>
                    </fieldset>
                    )}
```

> Verificar nombres reales de constantes de estilo (`sectionClassName`, `sectionTitleClassName`, `sectionDescriptionClassName`, `SECTION_TINT`, `selectBaseClassName`) en el archivo y usar los que existan. Reusar un ícono ya importado.

- [ ] **Step 4: Lint + type-check + verificación manual**

Run: `cd supermaster-frontend && npm run lint && npx tsc --noEmit -p tsconfig.json`
Expected: tsc exit 0; sin errores de lint nuevos (salvo los `set-state-in-effect` pre-existentes).

Manual: abrir un producto publicado en ML/Nube → ver estados y snapshot reales; uno no publicado en un canal → "No publicado".

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(modal): lee y muestra estado + snapshot de publicación por canal"
```

---

### Task 7: Frontend — aplicar al guardar + reporte + fix tooltip ML

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (en `handleGuardarEdicion`: aplicar diffs de estado; corregir el tooltip de ML)

**Interfaces:**
- Consumes: `putEstadoPublicacionAPI`, `estadoCanales`, `estadoOriginal`, `editandoProductoId`, `esSesionExpirada`.

- [ ] **Step 1: Aplicar los diffs de estado en `handleGuardarEdicion`**

Dentro de `handleGuardarEdicion`, después del PATCH del producto y antes/junto al reporte de canales, agregar:
```tsx
            // Estado de publicación: aplicar solo lo que cambió respecto de lo leído al abrir.
            if (estadoCanales && estadoOriginal) {
                const upd: EstadoPublicacionUpdate = {};
                if (estadoCanales.ml.publicado && estadoCanales.ml.estado !== estadoOriginal.ml.estado) upd.ml = estadoCanales.ml.estado;
                if (estadoCanales.hogar.publicado && estadoCanales.hogar.estado !== estadoOriginal.hogar.estado) upd.hogar = estadoCanales.hogar.estado === "visible";
                if (estadoCanales.gastro.publicado && estadoCanales.gastro.estado !== estadoOriginal.gastro.estado) upd.gastro = estadoCanales.gastro.estado === "visible";
                if (upd.ml !== undefined || upd.hogar !== undefined || upd.gastro !== undefined) {
                    try { await putEstadoPublicacionAPI(editandoProductoId, upd); }
                    catch (e) { if (!esSesionExpirada(e)) notificar.error("No se pudo aplicar el cambio de estado de publicación"); }
                }
            }
```

(Ubicarlo donde el resto de la lógica de guardado ya hace `await` de operaciones; mantener el orden: PATCH → exports → estado.)

- [ ] **Step 2: Corregir el tooltip engañoso de ML**

En el tooltip del canal Mercado Libre (busca el texto "activa o pausa según el flag 'Activo'"), reemplazar esa frase por una que describa el comportamiento real, p.ej.:
```
El estado de la publicación (activa/pausada) se controla desde la sección "Estado de publicación" al editar.
```

- [ ] **Step 3: Lint + type-check + verificación manual**

Run: `cd supermaster-frontend && npm run lint && npx tsc --noEmit -p tsconfig.json`
Expected: tsc exit 0; sin errores de lint nuevos.

Manual: en un producto publicado, cambiar ML a Pausada y una tienda Nube a Oculta, Guardar → verificar en ML/Nube que cambió; sin cambios de estado → no se llama al PUT.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(modal): aplica el cambio de estado de publicación al guardar"
```

---

## Notas de cierre

- Tras todas las tareas: `cd supermaster-backend && mvn -o test` (suite completa verde) y `npm run lint && npx tsc --noEmit` en el frontend.
- Verificación manual end-to-end con un producto real publicado en ML y en al menos una tienda Nube.
