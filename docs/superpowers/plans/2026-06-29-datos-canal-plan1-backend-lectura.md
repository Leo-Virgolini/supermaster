# Datos de canal en el modal — Plan 1: Backend lectura (estado + Dux + campos editables)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que `GET /api/productos/{id}/estado-publicacion` devuelva, además del estado de ML/Hogar/Gastro, el **estado de Dux** y los **campos editables leídos del canal** (categoría ML, atributos ML, y descripción por canal), todo en un solo viaje reutilizando las lecturas que ya se hacen.

**Architecture:** Cambio **aditivo** y sin romper el contrato actual: se agregan campos al `EstadoPublicacionDTO` (un `dux` y un bloque `datos`) y parsers puros nuevos. La lectura de ML reutiliza el `JsonNode` del ítem que ya se obtiene para el estado; la de Nube reutiliza el `JsonNode` del producto; Dux usa `DuxService.obtenerProductoPorCodigo(sku)`. Una llamada nueva a ML (`GET /items/{id}/description`) para la descripción.

**Tech Stack:** Spring Boot 4, Java 25, Maven, JPA, Jackson 3 (`tools.jackson`), JUnit 5 + AssertJ.

## Global Constraints

- Backend: Spring Boot 4, Java 25, Maven, JPA con `ddl-auto=validate` (dev/prod), tests con `ddl-auto=none`. Correr tests con `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`).
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos, nunca `-A` ni `.superpowers/`.
- Paquete base backend: `ar.com.leo.super_master_backend`.
- Formato de error: `{ "message": "...", "path": "/api/..." }`. Idiom Jackson 3: `node.path("x").asString(null)`.
- Este plan NO borra nada de la BD ni cambia la escritura (eso es el Plan 2). Es puramente lectura/aditivo.

## File Structure

- Crear `apis/ml/dto/MlAtributoDTO.java` — DTO de transporte de un atributo ML (compartido con el Plan 2). Responsabilidad: representar un atributo leído/enviado.
- Crear `dominio/producto/estado/dto/DatosCanalDTO.java` — bloque de campos editables leídos del canal.
- Crear `dominio/producto/estado/DuxEstadoParser.java` — parsea un `Item` de Dux a `EstadoCanalDTO`.
- Crear `dominio/producto/estado/MlDatosParser.java` — extrae categoría + atributos del `JsonNode` del ítem ML.
- Modificar `dominio/producto/estado/dto/EstadoPublicacionDTO.java` — agregar `dux` y `datos`.
- Modificar `apis/ml/service/MercadoLibreService.java` — agregar `leerDescripcionMl(String)`.
- Modificar `dominio/producto/estado/EstadoPublicacionService.java` — ensamblar todo en `leer(...)`.
- Tests: `MlDatosParserTest`, `DuxEstadoParserTest` en `src/test/java/.../dominio/producto/estado/`.

---

### Task 1: DTO de atributo ML (`MlAtributoDTO`)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlAtributoDTO.java`

**Interfaces:**
- Produces: `record MlAtributoDTO(String attributeId, String valueId, String valueName, boolean noAplica)` — usado por `MlDatosParser` (Task 4) y por el request de export del Plan 2.

- [ ] **Step 1: Crear el record**

```java
package ar.com.leo.super_master_backend.apis.ml.dto;

/** Un atributo de ficha técnica de ML (leído del ítem o enviado al publicar). */
public record MlAtributoDTO(String attributeId, String valueId, String valueName, boolean noAplica) {}
```

- [ ] **Step 2: Compilar**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlAtributoDTO.java
git commit -m "feat(canal): DTO de transporte de atributo ML"
```

---

### Task 2: DTO de campos editables del canal (`DatosCanalDTO`)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/DatosCanalDTO.java`

**Interfaces:**
- Consumes: `MlAtributoDTO` (Task 1).
- Produces: `record DatosCanalDTO(String mlCategoryId, String mlCategoryNombre, java.util.List<MlAtributoDTO> mlAtributos, String descripcionMl, String descripcionHogar, String descripcionGastro)`.

- [ ] **Step 1: Crear el record**

```java
package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;

import java.util.List;

/** Campos editables leídos del canal para pre-llenar el modal (no persistidos). */
public record DatosCanalDTO(
        String mlCategoryId,
        String mlCategoryNombre,
        List<MlAtributoDTO> mlAtributos,
        String descripcionMl,
        String descripcionHogar,
        String descripcionGastro
) {
    public static DatosCanalDTO vacio() {
        return new DatosCanalDTO(null, null, List.of(), null, null, null);
    }
}
```

- [ ] **Step 2: Compilar**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/DatosCanalDTO.java
git commit -m "feat(canal): DTO de campos editables leídos del canal"
```

---

### Task 3: Parser de estado de Dux (`DuxEstadoParser`)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/DuxEstadoParser.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/DuxEstadoParserTest.java`

**Interfaces:**
- Consumes: `ar.com.leo.super_master_backend.apis.dux.model.Item` (campo `habilitado` String "S"/"N"), `EstadoCanalDTO`.
- Produces: `static EstadoCanalDTO parse(Item item)` — `noPublicado()` si `item==null`; si no, `publicado=true`, `estado="habilitado"` cuando `habilitado=="S"` (case-insensitive), si no `"deshabilitado"`; precio/stock/peso/dim null.

- [ ] **Step 1: Escribir el test que falla**

```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuxEstadoParserTest {

    @Test
    void itemNuloEsNoPublicado() {
        EstadoCanalDTO dto = DuxEstadoParser.parse(null);
        assertThat(dto.publicado()).isFalse();
        assertThat(dto.estado()).isNull();
        assertThat(dto.error()).isFalse();
    }

    @Test
    void habilitadoS() {
        Item item = new Item();
        item.setHabilitado("S");
        EstadoCanalDTO dto = DuxEstadoParser.parse(item);
        assertThat(dto.publicado()).isTrue();
        assertThat(dto.estado()).isEqualTo("habilitado");
    }

    @Test
    void habilitadoN() {
        Item item = new Item();
        item.setHabilitado("n");
        EstadoCanalDTO dto = DuxEstadoParser.parse(item);
        assertThat(dto.publicado()).isTrue();
        assertThat(dto.estado()).isEqualTo("deshabilitado");
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd supermaster-backend && mvn -o -q -Dtest=DuxEstadoParserTest test`
Expected: FAIL (no compila: `DuxEstadoParser` no existe).

- [ ] **Step 3: Implementar el parser**

```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;

/** Parsea un Item de Dux a EstadoCanalDTO (solo estado: Dux no aporta precio/stock comparables). */
public final class DuxEstadoParser {

    private DuxEstadoParser() {}

    public static EstadoCanalDTO parse(Item item) {
        if (item == null) return EstadoCanalDTO.noPublicado();
        boolean habilitado = "S".equalsIgnoreCase(item.getHabilitado());
        return new EstadoCanalDTO(true, habilitado ? "habilitado" : "deshabilitado",
                null, null, null, null, false);
    }
}
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd supermaster-backend && mvn -o -q -Dtest=DuxEstadoParserTest test`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/DuxEstadoParser.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/DuxEstadoParserTest.java
git commit -m "feat(canal): parser de estado de Dux para el panel"
```

---

### Task 4: Parser de categoría + atributos del ítem ML (`MlDatosParser`)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlDatosParser.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlDatosParserTest.java`

**Interfaces:**
- Consumes: `JsonNode` del ítem ML (de `MercadoLibreService.leerItemRaw`), `MlAtributoDTO` (Task 1).
- Produces:
  - `static String categoryId(JsonNode item)` — `category_id` o null.
  - `static List<MlAtributoDTO> atributos(JsonNode item)` — mapea `attributes[]`: `attributeId`=`id`, `valueId`=`value_id`, `valueName`=`value_name`; si `value_id=="-1"` → `noAplica=true` y `valueName=null`. Se omiten los atributos de sistema que no son ficha técnica editable (`SELLER_SKU`, `SELLER_PACKAGE_*`, `IMPORT_DUTY`, `ITEM_CONDITION`).

- [ ] **Step 1: Escribir el test que falla**

```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlDatosParserTest {

    private static final ObjectMapper M = new ObjectMapper();

    private JsonNode parse(String json) {
        return M.readTree(json);
    }

    @Test
    void categoryIdYAtributosFichaTecnica() {
        JsonNode item = parse("""
            {
              "category_id": "MLA1234",
              "attributes": [
                {"id":"ITEM_CONDITION","value_id":"2230284","value_name":"Nuevo"},
                {"id":"SELLER_SKU","value_name":"ABC"},
                {"id":"BRAND","value_id":"111","value_name":"Acme"},
                {"id":"COLOR","value_id":"-1","value_name":null}
              ]
            }
            """);
        assertThat(MlDatosParser.categoryId(item)).isEqualTo("MLA1234");
        List<MlAtributoDTO> attrs = MlDatosParser.atributos(item);
        assertThat(attrs).extracting(MlAtributoDTO::attributeId)
                .containsExactly("BRAND", "COLOR"); // ITEM_CONDITION y SELLER_SKU se omiten
        assertThat(attrs.get(0).valueId()).isEqualTo("111");
        assertThat(attrs.get(0).valueName()).isEqualTo("Acme");
        assertThat(attrs.get(0).noAplica()).isFalse();
        assertThat(attrs.get(1).noAplica()).isTrue();
        assertThat(attrs.get(1).valueName()).isNull();
    }

    @Test
    void itemSinAtributos() {
        JsonNode item = parse("{\"category_id\":\"MLA1\"}");
        assertThat(MlDatosParser.atributos(item)).isEmpty();
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd supermaster-backend && mvn -o -q -Dtest=MlDatosParserTest test`
Expected: FAIL (no compila: `MlDatosParser` no existe).

- [ ] **Step 3: Implementar el parser**

```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Extrae categoría y atributos de ficha técnica editable del JSON de GET /items/{id} de ML. */
public final class MlDatosParser {

    private MlDatosParser() {}

    /** Atributos de sistema que arma el payload builder; no son ficha técnica editable por el usuario. */
    private static final Set<String> OMITIR = Set.of(
            "ITEM_CONDITION", "SELLER_SKU",
            "SELLER_PACKAGE_HEIGHT", "SELLER_PACKAGE_WIDTH", "SELLER_PACKAGE_LENGTH", "SELLER_PACKAGE_WEIGHT",
            "IMPORT_DUTY");

    public static String categoryId(JsonNode item) {
        if (item == null) return null;
        return item.path("category_id").asString(null);
    }

    public static List<MlAtributoDTO> atributos(JsonNode item) {
        List<MlAtributoDTO> out = new ArrayList<>();
        if (item == null) return out;
        for (JsonNode a : item.path("attributes")) {
            String id = a.path("id").asString(null);
            if (id == null || OMITIR.contains(id)) continue;
            String valueId = a.path("value_id").asString(null);
            boolean noAplica = "-1".equals(valueId);
            String valueName = noAplica ? null : a.path("value_name").asString(null);
            out.add(new MlAtributoDTO(id, noAplica ? null : valueId, valueName, noAplica));
        }
        return out;
    }
}
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd supermaster-backend && mvn -o -q -Dtest=MlDatosParserTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlDatosParser.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlDatosParserTest.java
git commit -m "feat(canal): parser de categoría y atributos del ítem ML"
```

---

### Task 5: Leer la descripción de ML (`MercadoLibreService.leerDescripcionMl`)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (agregar método justo después de `leerItemRaw`, ~línea 1541)

**Interfaces:**
- Produces: `public String leerDescripcionMl(String mlaCode)` — `plain_text` de `GET /items/{id}/description`; null si falta o falla.

- [ ] **Step 1: Implementar el método (sin test unitario: I/O sobre RestClient, igual que `leerItemRaw`)**

Insertar después del cierre de `leerItemRaw` (línea 1541):

```java
    /** GET /items/{id}/description → texto plano de la descripción publicada en ML. Null si falla/ausente. */
    public String leerDescripcionMl(String mlaCode) {
        if (mlaCode == null || mlaCode.isBlank()) return null;
        verificarTokens();
        try {
            String response = retryHandler.get("/items/" + mlaCode + "/description", () -> tokens.accessToken);
            if (response == null) return null;
            JsonNode node = objectMapper.readTree(response);
            return node.path("plain_text").asString(null);
        } catch (Exception e) {
            log.warn("ML - No se pudo leer la descripción del item {}: {}", mlaCode, e.getMessage());
            return null;
        }
    }
```

- [ ] **Step 2: Compilar**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java
git commit -m "feat(ml): leer descripción publicada (GET /items/{id}/description)"
```

---

### Task 6: Ampliar `EstadoPublicacionDTO` con `dux` y `datos`

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/EstadoPublicacionDTO.java`

**Interfaces:**
- Consumes: `EstadoCanalDTO`, `DatosCanalDTO` (Task 2).
- Produces: `record EstadoPublicacionDTO(EstadoCanalDTO ml, EstadoCanalDTO hogar, EstadoCanalDTO gastro, EstadoCanalDTO dux, DatosCanalDTO datos)`.

- [ ] **Step 1: Editar el record**

```java
package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

/** Respuesta del GET: estado + snapshot de cada canal (ML/Hogar/Gastro/Dux) + campos editables del canal. */
public record EstadoPublicacionDTO(
        EstadoCanalDTO ml,
        EstadoCanalDTO hogar,
        EstadoCanalDTO gastro,
        EstadoCanalDTO dux,
        DatosCanalDTO datos
) {}
```

- [ ] **Step 2: Compilar (fallará: el service construye el record con 3 args)**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: FAIL (constructor de `EstadoPublicacionDTO` no coincide en `EstadoPublicacionService`). Se arregla en la Task 7. (No commitear este paso suelto: se commitea junto con la Task 7.)

---

### Task 7: Ensamblar todo en `EstadoPublicacionService.leer`

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionService.java`

**Interfaces:**
- Consumes: `DuxService.obtenerProductoPorCodigo(String)` → `Item`; `MercadoLibreService.leerItemRaw(String)`, `leerDescripcionMl(String)`; `TiendaNubeService.buscarProductoPorSku(String, String)`; `MlDatosParser`, `DuxEstadoParser`, `DatosCanalDTO`, `MlAtributoDTO`.
- Produces: `leer(Integer)` devuelve el `EstadoPublicacionDTO` ampliado.

Notas de implementación:
- Inyectar `DuxService` en el constructor.
- Refactorizar para **reutilizar** el `JsonNode` del ítem ML (una sola GET) tanto para el estado como para `datos` (categoría + atributos), y el `JsonNode` del producto Nube de cada tienda para estado **y** `description.es`.
- `mlCategoryNombre`: no viene en `/items/{id}`; se deja `null` (el frontend muestra el id; resolver el nombre exigiría otra GET y queda fuera de alcance).

- [ ] **Step 1: Reescribir el service**

Reemplazar el cuerpo de la clase `EstadoPublicacionService` (campos, constructor y método `leer`; los métodos `aplicar`/`aplicarMl`/`aplicarNube` quedan igual). Mostrar el archivo completo:

```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.DatosCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoAplicarDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoAplicarDTO.CanalAplicado;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
public class EstadoPublicacionService {

    private final ProductoRepository productoRepository;
    private final MercadoLibreService mercadoLibreService;
    private final TiendaNubeService tiendaNubeService;
    private final DuxService duxService;

    public EstadoPublicacionService(ProductoRepository productoRepository,
                                    MercadoLibreService mercadoLibreService,
                                    TiendaNubeService tiendaNubeService,
                                    DuxService duxService) {
        this.productoRepository = productoRepository;
        this.mercadoLibreService = mercadoLibreService;
        this.tiendaNubeService = tiendaNubeService;
        this.duxService = duxService;
    }

    @Transactional(readOnly = true)
    public EstadoPublicacionDTO leer(Integer productoId) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        // --- ML: una sola GET del ítem, reutilizada para estado y datos ---
        String mlaCode = p.getMla() != null ? p.getMla().getMla() : null;
        JsonNode mlItem = (mlaCode != null && !mlaCode.isBlank()) ? mercadoLibreService.leerItemRaw(mlaCode) : null;
        EstadoCanalDTO ml = estadoMl(mlaCode, mlItem);
        String descMl = (mlaCode != null && !mlaCode.isBlank()) ? mercadoLibreService.leerDescripcionMl(mlaCode) : null;

        // --- Nube: una sola GET por tienda, reutilizada para estado y descripción ---
        JsonNode hogarProd = leerNubeProducto(p.getSku(), TiendaNubeService.STORE_HOGAR);
        JsonNode gastroProd = leerNubeProducto(p.getSku(), TiendaNubeService.STORE_GASTRO);
        EstadoCanalDTO hogar = estadoNube(hogarProd);
        EstadoCanalDTO gastro = estadoNube(gastroProd);

        // --- Dux ---
        EstadoCanalDTO dux = estadoDux(p.getSku());

        DatosCanalDTO datos = new DatosCanalDTO(
                MlDatosParser.categoryId(mlItem),
                null, // nombre de categoría no viene en /items/{id}
                MlDatosParser.atributos(mlItem),
                descMl,
                descripcionNube(hogarProd),
                descripcionNube(gastroProd));

        return new EstadoPublicacionDTO(ml, hogar, gastro, dux, datos);
    }

    private EstadoCanalDTO estadoMl(String mlaCode, JsonNode item) {
        if (mlaCode == null || mlaCode.isBlank()) return EstadoCanalDTO.noPublicado();
        if (item == null) return EstadoCanalDTO.ofError();
        return MlEstadoParser.parse(item);
    }

    private JsonNode leerNubeProducto(String sku, String store) {
        try {
            return tiendaNubeService.buscarProductoPorSku(sku, store);
        } catch (Exception e) {
            return null;
        }
    }

    private EstadoCanalDTO estadoNube(JsonNode product) {
        if (product == null) return EstadoCanalDTO.noPublicado();
        return NubeEstadoParser.parse(product);
    }

    private String descripcionNube(JsonNode product) {
        if (product == null) return null;
        return product.path("description").path("es").asString(null);
    }

    private EstadoCanalDTO estadoDux(String sku) {
        try {
            Item item = duxService.obtenerProductoPorCodigo(sku);
            return DuxEstadoParser.parse(item);
        } catch (Exception e) {
            return EstadoCanalDTO.ofError();
        }
    }

    @Transactional(readOnly = true)
    public EstadoAplicarDTO aplicar(Integer productoId, EstadoPublicacionUpdateDTO cambios) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        CanalAplicado ml = cambios.ml() != null ? aplicarMl(p, cambios.ml()) : null;
        CanalAplicado hogar = cambios.hogar() != null ? aplicarNube(p.getSku(), TiendaNubeService.STORE_HOGAR, cambios.hogar()) : null;
        CanalAplicado gastro = cambios.gastro() != null ? aplicarNube(p.getSku(), TiendaNubeService.STORE_GASTRO, cambios.gastro()) : null;
        return new EstadoAplicarDTO(ml, hogar, gastro);
    }

    private CanalAplicado aplicarMl(Producto p, String estado) {
        if (!"active".equals(estado) && !"paused".equals(estado))
            return new CanalAplicado(false, "Estado inválido");
        String mla = p.getMla() != null ? p.getMla().getMla() : null;
        if (mla == null || mla.isBlank())
            return new CanalAplicado(false, "Sin publicación en Mercado Libre");
        boolean ok = mercadoLibreService.updateItemStatus(mla, estado);
        if (!ok) return new CanalAplicado(false, "Mercado Libre rechazó el cambio");
        return new CanalAplicado(true, "active".equals(estado) ? "Activada" : "Pausada");
    }

    private CanalAplicado aplicarNube(String sku, String store, boolean visible) {
        JsonNode product;
        try { product = tiendaNubeService.buscarProductoPorSku(sku, store); }
        catch (Exception e) { return new CanalAplicado(false, "Error consultando Nube"); }
        if (product == null) return new CanalAplicado(false, "No publicado en " + store);
        long productId = product.path("id").asLong();
        boolean ok = tiendaNubeService.actualizarPublished(store, productId, visible);
        if (!ok) return new CanalAplicado(false, "Nube rechazó el cambio");
        return new CanalAplicado(true, visible ? "Visible" : "Oculta");
    }
}
```

> Nota: el import `List` queda usado indirectamente vía `DatosCanalDTO`/`MlAtributoDTO`; si el compilador marca import sin usar, quitarlo. (`unmappedTargetPolicy` no aplica aquí.)

- [ ] **Step 2: Compilar**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: BUILD SUCCESS (se resuelve el constructor de 5 args de la Task 6).

- [ ] **Step 3: Correr la suite de estado + parsers**

Run: `cd supermaster-backend && mvn -o -q -Dtest=DuxEstadoParserTest,MlDatosParserTest test`
Expected: PASS.

- [ ] **Step 4: Correr la suite completa (verificar que nada se rompió)**

Run: `cd supermaster-backend && mvn -o test`
Expected: BUILD SUCCESS, 0 fallos. (Si algún test construye `EstadoPublicacionDTO` por constructor posicional, actualizarlo a 5 args — ver memoria de records.)

- [ ] **Step 5: Commit (incluye la Task 6)**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/EstadoPublicacionDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionService.java
git commit -m "feat(canal): estado-publicacion devuelve Dux + campos editables del canal"
```

---

## Self-Review (rellenado por el autor del plan)

1. **Cobertura de la spec (sección "Lectura al abrir"):** estado ML/Hogar/Gastro+Dux → Tasks 3,6,7; categoría+atributos ML → Tasks 4,7; descripción ML → Tasks 5,7; descripción Nube por tienda → Task 7 (reusa el producto). ✅
2. **Placeholders:** ninguno; todo el código está completo.
3. **Consistencia de tipos:** `MlAtributoDTO(attributeId, valueId, valueName, noAplica)` usado igual en Tasks 1, 4, 7. `DatosCanalDTO` con 6 componentes usado igual en Tasks 2 y 7. `EstadoPublicacionDTO` de 5 componentes en Tasks 6 y 7.
4. **No rompe contrato:** cambio aditivo; el frontend (Plan 3) ignora `dux`/`datos` hasta que los use.
