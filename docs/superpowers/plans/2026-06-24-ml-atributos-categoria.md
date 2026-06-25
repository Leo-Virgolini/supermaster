# ML — Atributos de categoría en la publicación Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publicar en Mercado Libre el EAN/GTIN, el formato de venta y las características (principales/secundarias) de la categoría, y ofrecer retiro en persona.

**Architecture:** El código de barras va en una columna nueva `productos.ean`; el formato de venta y las características van en una tabla hija `producto_ml_atributo`. Un servicio backend trae/cachea/filtra los atributos de la categoría (`/categories/{id}/attributes`) y los expone por un endpoint que consume el form dinámico del front. El payload de alta/actualización suma esos atributos (EAN→`GTIN`) y `local_pick_up: true`, y bloquea el alta si falta algún `required`.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven / MapStruct / Spring Data JPA / MySQL (schema `supermaster`); Next.js / React / TypeScript.

## Global Constraints

- DB: `ddl-auto=validate` → cambios de schema requieren script SQL manual en `supermaster-backend/src/main/resources/db/`.
- Tests backend offline en el sandbox: usar `mvn -o test -Dtest=...` (no `mvnw`, falla por red).
- DTOs `record`: agregar un componente rompe `new XDTO(...)` posicional en tests; verificar con `mvn -o test` (no solo compile).
- OSIV off (`open-in-view=false`): tocar asociaciones LAZY (`producto.getMlAtributos()`) solo dentro de `@Transactional`.
- Jackson 3: `JsonNode.asString()` (no `.asText()`); import `tools.jackson.databind.JsonNode`.
- No cambiar el cálculo/persistencia de `mlas` ni el flujo de precios. Solo SUMAR atributos al payload.
- Atributos auto-gestionados (excluidos del motor dinámico, los arma el builder o la columna `ean`): `ITEM_CONDITION`, `BRAND`, `SELLER_SKU`, `SELLER_PACKAGE_HEIGHT`, `SELLER_PACKAGE_WIDTH`, `SELLER_PACKAGE_LENGTH`, `SELLER_PACKAGE_WEIGHT`, `VALUE_ADDED_TAX`, `IMPORT_DUTY`, `GTIN`, `EAN`.
- GTIN/EAN: opcional, **nunca** bloquea ni se valida como required.

---

## File Structure

**Backend (crear):**
- `db/2026-06-24-ml-atributos-categoria.sql` — DDL `productos.ean` + tabla `producto_ml_atributo`.
- `dominio/producto/entity/ProductoMlAtributo.java` — entidad hija.
- `dominio/producto/dto/ProductoMlAtributoDTO.java` — DTO del atributo guardado.
- `apis/ml/dto/MlAtributoDefDTO.java`, `apis/ml/dto/MlAtributoValorDTO.java` — metadata de categoría.
- `apis/ml/service/MlCategoriaAtributoService.java` — fetch/cache/filtrado/agrupado de `/categories/{id}/attributes`.

**Backend (modificar):**
- `dominio/producto/entity/Producto.java` — campo `ean` + `@OneToMany mlAtributos`.
- `dominio/producto/dto/ProductoDTO.java`, `ProductoCreateDTO.java`, `ProductoPatchDTO.java` — `ean` + `mlAtributos`.
- `dominio/producto/mapper/ProductoMapper.java` — mapear `ean` + `mlAtributos`.
- `dominio/producto/service/ProductoServiceImpl.java` — persistir `mlAtributos` (diff en update).
- `apis/ml/service/MlItemPayloadBuilder.java` — `local_pick_up: true`, EAN→GTIN, atributos guardados.
- `apis/ml/service/MercadoLibreService.java` — metadata para validación + gating; firma de core.
- `apis/ml/controller/MercadoLibreController.java` — endpoint `categorias/{id}/atributos`.

**Frontend (modificar):**
- `productos/productosService.ts` — tipos + `getMlCategoriaAtributosAPI`; `ean`/`mlAtributos` en payloads.
- `productos/ProductoFormModal.tsx` — input EAN + form dinámico de atributos.

---

## Task 1: Columna `productos.ean` (persistencia end-to-end)

**Files:**
- Create: `supermaster-backend/src/main/resources/db/2026-06-24-ml-atributos-categoria.sql`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java`
- Modify: `dominio/producto/dto/ProductoDTO.java`, `ProductoCreateDTO.java`, `ProductoPatchDTO.java`
- Modify: `dominio/producto/mapper/ProductoMapper.java`
- Test: `src/test/java/ar/com/leo/super_master_backend/dominio/producto/mapper/ProductoMapperEanTest.java`

**Interfaces:**
- Produces: `Producto.getEan()/setEan(String)`; `ean` en `ProductoDTO`/`ProductoCreateDTO`/`ProductoPatchDTO`; el mapper copia `ean`.

- [ ] **Step 1: SQL script** (incluye también la tabla hija de Task 2 para no fragmentar el DDL)

`db/2026-06-24-ml-atributos-categoria.sql`:
```sql
ALTER TABLE supermaster.productos ADD COLUMN ean VARCHAR(20) NULL AFTER ml_category_nombre;

CREATE TABLE supermaster.producto_ml_atributo (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  id_producto  INT          NOT NULL,
  attribute_id VARCHAR(60)  NOT NULL,
  value_id     VARCHAR(60)  NULL,
  value_name   VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_producto_attribute (id_producto, attribute_id),
  CONSTRAINT fk_pma_producto FOREIGN KEY (id_producto)
    REFERENCES supermaster.productos (id_producto) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
Aplicar el script a mano en la BD local antes de correr el backend (ddl-auto=validate).

- [ ] **Step 2: Failing test** — el mapper copia `ean`.

`ProductoMapperEanTest.java`:
```java
package ar.com.leo.super_master_backend.dominio.producto.mapper;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCreateDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.assertj.core.api.Assertions.assertThat;

class ProductoMapperEanTest {
    private final ProductoMapper mapper = Mappers.getMapper(ProductoMapper.class);

    @Test
    void create_copiaEan() {
        ProductoCreateDTO dto = ProductoCreateDTOFixture.conEan("7791234567890"); // helper local o builder
        Producto p = mapper.toEntity(dto);
        assertThat(p.getEan()).isEqualTo("7791234567890");
    }
}
```
Nota: si no hay fixture, construir el `ProductoCreateDTO` con sus argumentos posicionales reales (ver el record actual) agregando `ean` como nuevo componente. Ajustar TODAS las construcciones posicionales existentes en tests al agregar el componente.

- [ ] **Step 3: Run test → FAIL** (`getEan` no existe / componente `ean` no existe)

Run: `mvn -o test -Dtest=ProductoMapperEanTest`
Expected: FAIL de compilación o aserción.

- [ ] **Step 4: Implementar**

En `Producto.java`, después de `ml_category_nombre`:
```java
@Column(name = "ean", length = 20)
private String ean;
```
Agregar `String ean` a `ProductoDTO`, `ProductoCreateDTO`, `ProductoPatchDTO` (como componente del record, al final para minimizar el ruido posicional). MapStruct copia `ean` por nombre automáticamente (no requiere `@Mapping`). Ajustar las construcciones posicionales rotas en tests existentes.

- [ ] **Step 5: Run test → PASS**

Run: `mvn -o test -Dtest=ProductoMapperEanTest,ProductoMapper*Test`
Expected: PASS.

- [ ] **Step 6: Commit**
```bash
git add supermaster-backend/src/main/resources/db/2026-06-24-ml-atributos-categoria.sql \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/Producto*DTO.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mapper/ProductoMapper.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/mapper/ProductoMapperEanTest.java
git commit -m "feat(ml): columna productos.ean para el codigo universal de producto"
```

---

## Task 2: Tabla hija `producto_ml_atributo` (entidad + DTO + persistencia)

**Files:**
- Create: `dominio/producto/entity/ProductoMlAtributo.java`
- Create: `dominio/producto/dto/ProductoMlAtributoDTO.java`
- Modify: `dominio/producto/entity/Producto.java` (relación)
- Modify: `dominio/producto/dto/ProductoDTO.java`, `ProductoCreateDTO.java`, `ProductoPatchDTO.java` (`List<ProductoMlAtributoDTO> mlAtributos`)
- Modify: `dominio/producto/mapper/ProductoMapper.java`
- Modify: `dominio/producto/service/ProductoServiceImpl.java`
- Test: `src/test/java/.../dominio/producto/service/ProductoMlAtributosPersistTest.java`

**Interfaces:**
- Consumes: `Producto.ean` (Task 1).
- Produces: `ProductoMlAtributo(producto, attributeId, valueId, valueName)`; `ProductoMlAtributoDTO(String attributeId, String valueId, String valueName)`; `Producto.getMlAtributos(): Set<ProductoMlAtributo>`; persistencia con reemplazo total del set al guardar.

- [ ] **Step 1: Entidad** `ProductoMlAtributo.java`
```java
package ar.com.leo.super_master_backend.dominio.producto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "producto_ml_atributo", schema = "supermaster")
public class ProductoMlAtributo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "attribute_id", nullable = false, length = 60)
    private String attributeId;

    @Column(name = "value_id", length = 60)
    private String valueId;

    @Column(name = "value_name", nullable = false, length = 255)
    private String valueName;
}
```

- [ ] **Step 2: DTO** `ProductoMlAtributoDTO.java`
```java
package ar.com.leo.super_master_backend.dominio.producto.dto;

public record ProductoMlAtributoDTO(String attributeId, String valueId, String valueName) {}
```

- [ ] **Step 3: Relación en `Producto.java`** (junto a las otras `@OneToMany`)
```java
@OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
@BatchSize(size = 50)
private Set<ProductoMlAtributo> mlAtributos = new LinkedHashSet<>();
```

- [ ] **Step 4: Failing test** — guardar un producto con atributos los persiste y reemplaza.

`ProductoMlAtributosPersistTest.java` (test de servicio con repos reales / @DataJpaTest o el patrón de tests de persistencia ya usado en el repo; si no hay, usar el del diff de relaciones N-a-N). Asserts:
```java
// crear producto con 2 atributos -> se persisten 2 filas
// actualizar con 1 atributo distinto -> queda 1 fila (reemplazo total)
assertThat(guardado.getMlAtributos()).extracting(ProductoMlAtributo::getAttributeId)
        .containsExactlyInAnyOrder("SALE_FORMAT");
```

- [ ] **Step 5: Run → FAIL**

Run: `mvn -o test -Dtest=ProductoMlAtributosPersistTest`
Expected: FAIL (mapeo/persistencia ausente).

- [ ] **Step 6: Mapper + persistencia**

En `ProductoMapper`: mapear `mlAtributos` ↔ `List<ProductoMlAtributoDTO>` (método para entity→dto). Para dto→entity en el service, reconstruir las entidades seteando `producto` (back-reference). En `ProductoServiceImpl` (create y update), tras setear los campos simples, **reemplazar el set completo**:
```java
producto.getMlAtributos().clear();
if (dto.mlAtributos() != null) {
    for (ProductoMlAtributoDTO a : dto.mlAtributos()) {
        ProductoMlAtributo e = new ProductoMlAtributo();
        e.setProducto(producto);
        e.setAttributeId(a.attributeId());
        e.setValueId((a.valueId() == null || a.valueId().isBlank()) ? null : a.valueId());
        e.setValueName(a.valueName());
        producto.getMlAtributos().add(e);
    }
}
```
(orphanRemoval=true + cascade ALL hace el INSERT/DELETE). En PATCH parcial: solo reemplazar si `dto.mlAtributos() != null` (null = no tocar).

- [ ] **Step 7: Run → PASS**

Run: `mvn -o test -Dtest=ProductoMlAtributosPersistTest`
Expected: PASS.

- [ ] **Step 8: Commit**
```bash
git add -A && git commit -m "feat(ml): tabla producto_ml_atributo (formato de venta + caracteristicas)"
```

---

## Task 3: `MlCategoriaAtributoService` (metadata por categoría)

**Files:**
- Create: `apis/ml/dto/MlAtributoValorDTO.java`, `apis/ml/dto/MlAtributoDefDTO.java`
- Create: `apis/ml/service/MlCategoriaAtributoService.java`
- Test: `src/test/java/.../apis/ml/service/MlCategoriaAtributoServiceTest.java`

**Interfaces:**
- Produces: `MlAtributoDefDTO(String id, String name, String valueType, List<MlAtributoValorDTO> values, List<String> allowedUnits, String defaultUnit, boolean required, boolean conditional, boolean multivalued, String grupo)`; `MlAtributoValorDTO(String id, String name)`; `MlCategoriaAtributoService.obtenerAtributos(String categoryId): List<MlAtributoDefDTO>` (filtrado, agrupado, cacheado) y `idsValidos(String categoryId): Set<String>` (todos los ids declarados por la categoría, sin filtrar — para gating de GTIN/EAN).
- Internal: `static List<MlAtributoDefDTO> parsear(JsonNode arr)` (testeable sin red).

- [ ] **Step 1: DTOs** (records arriba).

- [ ] **Step 2: Failing test** — parseo/filtrado/agrupado.

`MlCategoriaAtributoServiceTest.java`:
```java
@Test
void parsear_excluyeAutogestionadosYReadOnlyYFixed_yAgrupa() throws Exception {
    String json = """
      [
        {"id":"BRAND","name":"Marca","value_type":"string","attribute_group_id":"MAIN"},
        {"id":"SALE_FORMAT","name":"Formato de venta","value_type":"list",
         "attribute_group_id":"OTHERS","values":[{"id":"1359391","name":"Unidad"}]},
        {"id":"BICYCLE_TYPE","name":"Tipo","value_type":"string","attribute_group_id":"MAIN",
         "tags":{"required":true}},
        {"id":"PACKAGE_WIDTH","name":"Ancho","value_type":"number_unit",
         "tags":{"read_only":true}},
        {"id":"HEADPHONE_FORMAT","name":"Formato","value_type":"list","tags":{"fixed":true}}
      ]""";
    JsonNode arr = new ObjectMapper().readTree(json);
    List<MlAtributoDefDTO> defs = MlCategoriaAtributoService.parsear(arr);
    // BRAND excluido (auto-gestionado), PACKAGE_WIDTH (read_only) y HEADPHONE_FORMAT (fixed) excluidos
    assertThat(defs).extracting(MlAtributoDefDTO::id)
        .containsExactlyInAnyOrder("SALE_FORMAT", "BICYCLE_TYPE");
    MlAtributoDefDTO bt = defs.stream().filter(d -> d.id().equals("BICYCLE_TYPE")).findFirst().orElseThrow();
    assertThat(bt.required()).isTrue();
    assertThat(bt.grupo()).isEqualTo("PRINCIPALES");
    MlAtributoDefDTO sf = defs.stream().filter(d -> d.id().equals("SALE_FORMAT")).findFirst().orElseThrow();
    assertThat(sf.grupo()).isEqualTo("SECUNDARIAS");
    assertThat(sf.values()).extracting(MlAtributoValorDTO::name).containsExactly("Unidad");
}
```

- [ ] **Step 3: Run → FAIL**

Run: `mvn -o test -Dtest=MlCategoriaAtributoServiceTest`
Expected: FAIL (`parsear` no existe).

- [ ] **Step 4: Implementar `parsear` + servicio**

`parsear(JsonNode arr)`:
- Constante `Set<String> AUTOGESTIONADOS = Set.of("ITEM_CONDITION","BRAND","SELLER_SKU","SELLER_PACKAGE_HEIGHT","SELLER_PACKAGE_WIDTH","SELLER_PACKAGE_LENGTH","SELLER_PACKAGE_WEIGHT","VALUE_ADDED_TAX","IMPORT_DUTY","GTIN","EAN")`.
- Por cada nodo: leer `id`, `name`, `value_type`; `tags` (objeto) → `required = tags.has("required")||tags.has("new_required")`, `conditional = tags.has("conditional_required")`, `multivalued = tags.has("multivalued")`, `fixed`, `read_only`.
- Saltar si `id ∈ AUTOGESTIONADOS` o `fixed` o `read_only`.
- `values[]` (si hay), `allowed_units[]`→`List<String>` de sus `id`, `default_unit`.
- `grupo = "MAIN".equals(attribute_group_id) ? "PRINCIPALES" : "SECUNDARIAS"`.
- Usar Jackson 3 (`JsonNode.asString()`).

Servicio: inyecta `MlRetryHandler` + tokens; `obtenerAtributos(categoryId)`:
```java
JsonNode arr = objectMapper.readTree(retryHandler.get("/categories/" + categoryId + "/attributes", () -> tokens.accessToken));
```
Cache: `ConcurrentHashMap<String, CacheEntry>` con TTL (p. ej. 6 h); `idsValidos(categoryId)` parsea el mismo JSON crudo y devuelve TODOS los ids (sin exclusiones). Cachear el JSON crudo y derivar ambas vistas.

- [ ] **Step 5: Run → PASS**

Run: `mvn -o test -Dtest=MlCategoriaAtributoServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**
```bash
git add -A && git commit -m "feat(ml): servicio de metadata de atributos por categoria (cache + filtrado + agrupado)"
```

---

## Task 4: Endpoint `GET /api/ml/categorias/{id}/atributos`

**Files:**
- Modify: `apis/ml/controller/MercadoLibreController.java`
- Test: `src/test/java/.../apis/ml/MlCategoriaAtributosEndpointTest.java` (MockMvc, mockeando `MlCategoriaAtributoService`)

**Interfaces:**
- Consumes: `MlCategoriaAtributoService.obtenerAtributos` (Task 3).
- Produces: `GET /api/ml/categorias/{categoryId}/atributos` → `200 List<MlAtributoDefDTO>`.

- [ ] **Step 1: Failing test** — el endpoint devuelve la lista del servicio (status 200, json array).

- [ ] **Step 2: Run → FAIL** (404).
Run: `mvn -o test -Dtest=MlCategoriaAtributosEndpointTest`

- [ ] **Step 3: Implementar** en `MercadoLibreController` (respetar el `@PreAuthorize` del resto del controller):
```java
@GetMapping("/categorias/{categoryId}/atributos")
@PreAuthorize(Permisos.INTEGRACIONES_VER) // usar el mismo permiso que los otros GET ML del controller
public ResponseEntity<List<MlAtributoDefDTO>> atributosCategoria(@PathVariable String categoryId) {
    return ResponseEntity.ok(mlCategoriaAtributoService.obtenerAtributos(categoryId));
}
```
(Verificar el path base del controller — `/api/ml` — y el permiso exacto que usan `domain_discovery`/predictor.)

- [ ] **Step 4: Run → PASS**.
Run: `mvn -o test -Dtest=MlCategoriaAtributosEndpointTest`

- [ ] **Step 5: Commit**
```bash
git add -A && git commit -m "feat(ml): endpoint GET /api/ml/categorias/{id}/atributos"
```

---

## Task 5: `MlItemPayloadBuilder` — retiro, EAN→GTIN, atributos guardados

**Files:**
- Modify: `apis/ml/service/MlItemPayloadBuilder.java`
- Test: `src/test/java/.../apis/ml/service/MlItemPayloadBuilderTest.java`

**Interfaces:**
- Consumes: `Producto.ean`, `Producto.getMlAtributos()` (Tasks 1-2); `Set<String> categoriaAttrIds` (Task 3, ids válidos de la categoría).
- Produces: `construirAtributos(Producto p, Set<String> categoriaAttrIds): List<Map<String,Object>>` (firma nueva); `construir(...)` pasa `categoriaAttrIds` a `construirAtributos`. `shipping.local_pick_up = true`.

- [ ] **Step 1: Failing tests**
```java
@Test
void shipping_ofreceRetiroEnPersona() {
    Map<String,Object> payload = MlItemPayloadBuilder.construir(productoBase(), "MLA1", BigDecimal.TEN, 0, List.of(), "fam");
    @SuppressWarnings("unchecked") Map<String,Object> shipping = (Map<String,Object>) payload.get("shipping");
    assertThat(shipping.get("local_pick_up")).isEqualTo(true);
}

@Test
void atributos_eanComoGtin_siCategoriaDeclaraGtin() {
    Producto p = productoBase(); p.setEan("7791234567890");
    var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of("GTIN"));
    assertThat(attrs).anySatisfy(a -> { assertThat(a.get("id")).isEqualTo("GTIN");
        assertThat(a.get("value_name")).isEqualTo("7791234567890"); });
}

@Test
void atributos_eanComoEan_siNoHayGtin() {
    Producto p = productoBase(); p.setEan("7791234567890");
    var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of("EAN"));
    assertThat(attrs).anySatisfy(a -> assertThat(a.get("id")).isEqualTo("EAN"));
}

@Test
void atributos_sinEan_noAgregaIdentificador() {
    var attrs = MlItemPayloadBuilder.construirAtributos(productoBase(), Set.of("GTIN"));
    assertThat(attrs).noneSatisfy(a -> assertThat(a.get("id")).isIn("GTIN","EAN"));
}

@Test
void atributos_guardadosSeInyectan_conYsinValueId() {
    Producto p = productoBase();
    ProductoMlAtributo a1 = new ProductoMlAtributo(); a1.setAttributeId("SALE_FORMAT"); a1.setValueId("1359391"); a1.setValueName("Unidad");
    ProductoMlAtributo a2 = new ProductoMlAtributo(); a2.setAttributeId("MODEL"); a2.setValueName("X100");
    p.getMlAtributos().addAll(List.of(a1, a2));
    var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of());
    assertThat(attrs).anySatisfy(a -> { assertThat(a.get("id")).isEqualTo("SALE_FORMAT");
        assertThat(a.get("value_id")).isEqualTo("1359391"); assertThat(a.get("value_name")).isEqualTo("Unidad"); });
    assertThat(attrs).anySatisfy(a -> { assertThat(a.get("id")).isEqualTo("MODEL");
        assertThat(a).doesNotContainKey("value_id"); assertThat(a.get("value_name")).isEqualTo("X100"); });
}
```
(Los tests existentes que llaman `construirAtributos(p)` deben pasar a `construirAtributos(p, Set.of())`.)

- [ ] **Step 2: Run → FAIL**
Run: `mvn -o test -Dtest=MlItemPayloadBuilderTest`

- [ ] **Step 3: Implementar**
- `construir`: `shipping.put("local_pick_up", true);` y `payload.put("attributes", construirAtributos(p, categoriaAttrIds));` (agregar `Set<String> categoriaAttrIds` como parámetro de `construir`, propagado desde `crearItemEnMlCore`).
- `construirAtributos(Producto p, Set<String> categoriaAttrIds)`: mantiene los auto-gestionados actuales; al final:
```java
// Código universal: GTIN si la categoría lo declara, si no EAN. Solo uno; opcional.
if (p.getEan() != null && !p.getEan().isBlank()) {
    String idIdentificador = categoriaAttrIds.contains("GTIN") ? "GTIN"
                           : categoriaAttrIds.contains("EAN") ? "EAN" : null;
    if (idIdentificador != null) {
        attributes.add(Map.of("id", idIdentificador, "value_name", p.getEan().trim()));
    }
}
// Atributos guardados (formato de venta + características)
for (ProductoMlAtributo a : p.getMlAtributos()) {
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("id", a.getAttributeId());
    if (a.getValueId() != null && !a.getValueId().isBlank()) m.put("value_id", a.getValueId());
    m.put("value_name", a.getValueName());
    attributes.add(m);
}
```

- [ ] **Step 4: Run → PASS**
Run: `mvn -o test -Dtest=MlItemPayloadBuilderTest`

- [ ] **Step 5: Commit**
```bash
git add -A && git commit -m "feat(ml): payload con retiro en persona, EAN->GTIN y atributos guardados"
```

---

## Task 6: Validación de obligatorios + cableado en el alta/actualización

**Files:**
- Modify: `apis/ml/service/MercadoLibreService.java`
- Test: `src/test/java/.../apis/ml/service/MlValidacionRequeridosTest.java`

**Interfaces:**
- Consumes: `MlCategoriaAtributoService.obtenerAtributos` + `idsValidos` (Task 3); `MlItemPayloadBuilder.construirAtributos(p, ids)` (Task 5).
- Produces: `static List<String> faltantesRequeridos(Producto p, List<MlAtributoDefDTO> defs)` (testeable sin red) — ids `required` ausentes (excluye GTIN/EAN); el `crearItemEnMl` (no-core, con red) trae la metadata, llama a `faltantesRequeridos`, y si hay faltantes devuelve `ResultadoAltaMl.error(...)` antes del POST; pasa `idsValidos` al core/builder.

- [ ] **Step 1: Failing test** (núcleo puro)
```java
@Test
void faltantesRequeridos_detectaRequiredAusente_excluyeGtin() {
    Producto p = productoBase(); // sin atributos guardados
    ProductoMlAtributo sf = new ProductoMlAtributo(); sf.setAttributeId("SALE_FORMAT"); sf.setValueName("Unidad");
    p.getMlAtributos().add(sf);
    List<MlAtributoDefDTO> defs = List.of(
        def("BICYCLE_TYPE", true), def("SALE_FORMAT", true), def("GTIN", true));
    // BICYCLE_TYPE required y ausente -> falta; SALE_FORMAT presente; GTIN excluido aunque required
    assertThat(MercadoLibreService.faltantesRequeridos(p, defs)).containsExactly("BICYCLE_TYPE");
}
```
(`def(id, required)` helper que arma un `MlAtributoDefDTO`.)

- [ ] **Step 2: Run → FAIL**
Run: `mvn -o test -Dtest=MlValidacionRequeridosTest`

- [ ] **Step 3: Implementar**
```java
static final Set<String> IDENTIFICADORES_OPCIONALES = Set.of("GTIN", "EAN");

static List<String> faltantesRequeridos(Producto p, List<MlAtributoDefDTO> defs) {
    Set<String> presentes = p.getMlAtributos().stream()
        .map(ProductoMlAtributo::getAttributeId).collect(Collectors.toSet());
    // Los auto-gestionados (BRAND, etc.) se consideran provistos por el builder.
    return defs.stream()
        .filter(MlAtributoDefDTO::required)
        .map(MlAtributoDefDTO::id)
        .filter(id -> !IDENTIFICADORES_OPCIONALES.contains(id))
        .filter(id -> !presentes.contains(id))
        .toList();
}
```
En `crearItemEnMl` (método con red que llama a `crearItemEnMlCore`): antes de publicar, `defs = mlCategoriaAtributoService.obtenerAtributos(categoryId)`; `faltan = faltantesRequeridos(producto, defs)`; si `!faltan.isEmpty()` → `return ResultadoAltaMl.error("faltan atributos obligatorios de la categoría: " + faltan)`. Pasar `idsValidos = mlCategoriaAtributoService.idsValidos(categoryId)` al builder (vía `crearItemEnMlCore`/`construir`). Aplicar la misma extensión de atributos en `actualizarItemEnMl` cuando haya `categoryId`.

Nota: `faltantesRequeridos` no cuenta los auto-gestionados como faltantes porque el builder siempre los arma (BRAND si hay marca, etc.). Si un `required` es uno auto-gestionado, ya va en el payload.

- [ ] **Step 4: Run → PASS**
Run: `mvn -o test -Dtest=MlValidacionRequeridosTest`

- [ ] **Step 5: Regresión ML completa**
Run: `mvn -o test -Dtest='CrearItemEnMlTest,ActualizarItemEnMlTest,MlItemPayloadBuilderTest,MlValidacionRequeridosTest,MlCategoriaAtributoServiceTest'`
Expected: PASS.

- [ ] **Step 6: Commit**
```bash
git add -A && git commit -m "feat(ml): bloquear alta si faltan atributos required (GTIN/EAN opcional)"
```

---

## Task 7: Frontend — input EAN + servicio

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Produces: tipo `MlAtributoDef`, `getMlCategoriaAtributosAPI(categoryId): Promise<MlAtributoDef[]>`; `ean` y `mlAtributos` en el payload de alta/patch del producto; estado `ean` en el form.

- [ ] **Step 1: Servicio** en `productosService.ts`
```ts
export type MlAtributoValor = { id: string; name: string };
export type MlAtributoDef = {
  id: string; name: string; valueType: "string"|"number"|"number_unit"|"boolean"|"list";
  values: MlAtributoValor[]; allowedUnits: string[]; defaultUnit: string | null;
  required: boolean; conditional: boolean; multivalued: boolean; grupo: "PRINCIPALES"|"SECUNDARIAS";
};
export type ProductoMlAtributo = { attributeId: string; valueId: string | null; valueName: string };

export const getMlCategoriaAtributosAPI = async (categoryId: string): Promise<MlAtributoDef[]> => {
  const res = await fetchAPI(`${API_BASE_URL}/api/ml/categorias/${encodeURIComponent(categoryId)}/atributos`);
  if (!res.ok) throw new Error("No se pudieron obtener los atributos de la categoría");
  return await res.json();
};
```
Agregar `ean?: string | null` y `mlAtributos?: ProductoMlAtributo[]` a los tipos de create/patch del producto.

- [ ] **Step 2: Estado + input EAN** en `ProductoFormModal.tsx` (sección MercadoLibre): `const [ean, setEan] = useState("")`; precargar `setEan(producto.ean ?? "")`; incluir `ean: ean.trim() || null` en los payloads de alta y edición. Input con aviso suave:
```tsx
<label className="block">
  <span className={fieldLabelClassName}>EAN / Código universal</span>
  <input type="text" inputMode="numeric" className={inputBaseClassName} value={ean}
         onChange={e => setEan(e.target.value)} placeholder="Código de barras (8–14 dígitos)" />
  {ean.trim() && !/^\d{8,14}$/.test(ean.trim()) && (
    <span className="mt-0.5 block text-[11px] text-amber-600">Debería tener 8–14 dígitos</span>
  )}
</label>
```

- [ ] **Step 3: Verificar typecheck**
Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 4: Commit**
```bash
git add -A && git commit -m "feat(ml): input EAN y servicio de atributos de categoria (front)"
```

---

## Task 8: Frontend — form dinámico de atributos por categoría

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `getMlCategoriaAtributosAPI`, `MlAtributoDef`, `ProductoMlAtributo` (Task 7); `mlCategoryId`/`mlAtributos`.

- [ ] **Step 1: Estado + fetch** — `const [mlAtributosDef, setMlAtributosDef] = useState<MlAtributoDef[]>([])` y `const [mlAtributosVal, setMlAtributosVal] = useState<Record<string, ProductoMlAtributo>>({})`. Precargar `mlAtributosVal` desde `producto.mlAtributos` al editar. `useEffect` sobre `mlCategoryId`:
```tsx
useEffect(() => {
  if (!mlCategoryId) { setMlAtributosDef([]); return; }
  let cancel = false;
  getMlCategoriaAtributosAPI(mlCategoryId)
    .then(defs => { if (!cancel) setMlAtributosDef(defs); })
    .catch(() => { if (!cancel) setMlAtributosDef([]); });
  return () => { cancel = true; };
}, [mlCategoryId]);
```

- [ ] **Step 2: Helpers de set/valor**
```tsx
const setAtributo = (id: string, valueName: string, valueId: string | null = null) =>
  setMlAtributosVal(prev => {
    const next = { ...prev };
    if (!valueName) delete next[id]; else next[id] = { attributeId: id, valueId, valueName };
    return next;
  });
```

- [ ] **Step 3: Render por tipo, agrupado** — bajo la categoría, dos bloques (Principales = `grupo==="PRINCIPALES"`, Secundarias = resto). Por atributo:
```tsx
function renderInput(d: MlAtributoDef) {
  const v = mlAtributosVal[d.id];
  if (d.valueType === "list" || d.valueType === "boolean") {
    return (<select className={selectBaseClassName} value={v?.valueId ?? ""}
      onChange={e => { const opt = d.values.find(o => o.id === e.target.value);
        setAtributo(d.id, opt?.name ?? "", opt?.id ?? null); }}>
      <option value="">—</option>
      {d.values.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
    </select>);
  }
  if (d.valueType === "number_unit") {
    const [num, unit] = (v?.valueName ?? "").split(" ");
    const setNU = (n: string, u: string) => setAtributo(d.id, n ? `${n} ${u}` : "");
    return (<div className="flex gap-2">
      <input type="number" className={inputBaseClassName} value={num ?? ""}
        onChange={e => setNU(e.target.value, unit ?? d.defaultUnit ?? d.allowedUnits[0] ?? "")} />
      <select className={selectBaseClassName} value={unit ?? d.defaultUnit ?? ""}
        onChange={e => setNU(num ?? "", e.target.value)}>
        {d.allowedUnits.map(u => <option key={u} value={u}>{u}</option>)}
      </select></div>);
  }
  // string / number: texto/numérico, con datalist de sugeridos si hay values
  return (<input type={d.valueType === "number" ? "number" : "text"} className={inputBaseClassName}
    list={d.values.length ? `dl-${d.id}` : undefined} value={v?.valueName ?? ""}
    onChange={e => setAtributo(d.id, e.target.value)} />);
  // + <datalist id={`dl-${d.id}`}>{d.values.map(o => <option key={o.id} value={o.name}/>)}</datalist>
}
```
Etiqueta con `{d.name}{d.required && <span className="text-red-500"> *</span>}`.

- [ ] **Step 4: Guardar** — incluir `mlAtributos: Object.values(mlAtributosVal)` en los payloads de alta y edición del producto.

- [ ] **Step 5: Typecheck + lint**
Run: `cd supermaster-frontend && npx tsc --noEmit && npx eslint src/app/productos/ProductoFormModal.tsx src/app/productos/productosService.ts`
Expected: tsc exit 0; eslint sin errores nuevos (los `set-state-in-effect` preexistentes no cuentan).

- [ ] **Step 6: Commit**
```bash
git add -A && git commit -m "feat(ml): form dinamico de atributos de categoria (principales/secundarias)"
```

---

## Notas de integración (leer antes de empezar)

- `crearItemEnMlCore`/`actualizarItemEnMlCore` son estáticos y testeables sin red: la metadata de categoría se obtiene en los métodos NO estáticos (`crearItemEnMl`/`actualizarItemEnMl`) y se pasa al core/builder (defs para validar, `idsValidos` para gating). No meter llamadas de red en los `*Core`.
- OSIV off: `producto.getMlAtributos()` se accede durante el armado del payload, que corre con el producto cargado en la tx readOnly del alta (igual que `producto.getMarca()`). Verificar que la colección se inicialice ahí (BatchSize ya está).
- Al agregar `ean`/`mlAtributos` a los `record` DTO, correr `mvn -o test` (no solo compile) para cazar construcciones posicionales rotas.
