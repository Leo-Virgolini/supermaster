# Variantes de producto en ML — Fase 1 (backend habilitador) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar el backend listo para publicar variantes en el modelo nuevo de ML (User Products): capturar/persistir el nivel de familia y exponer qué atributos pueden ser eje de variación.

**Architecture:** Cada variante = un Producto propio; ML las agrupa solo por `family_name`. Se reusa la tabla `mlas` (una fila por publicación = por variante), agregándole `family_id` y `family_name`. No hay tabla nueva. El valor del eje por variante viaja como atributo ML (passthrough), sin columna. Esta fase agrega solo las piezas habilitadoras; la UI y el flujo de alta múltiple son Fase 2.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven / JPA / MapStruct / Jackson 3 / JUnit5 + AssertJ.

## Global Constraints

- ddl-auto=validate: todo cambio de columna requiere script SQL manual en `supermaster-backend/src/main/resources/db/` (formato `YYYY-MM-DD-desc.sql`). No se aplica solo.
- **No ejecutar llamadas reales a la API de ML** (ni POST ni PUT reales). Todo se valida con tests unitarios sobre funciones puras / lambdas mockeadas.
- Tests offline: `mvn -o test -Dtest=...` (el wrapper `mvnw` falla por red en este entorno; usar el `mvn` instalado con `-o`).
- Compilar offline: `mvn -o -q compile`.
- `family_id` de ML puede exceder BIGINT con signo (≈ 2⁶⁴) → se persiste como texto (VARCHAR).
- Records DTO: agregar un componente rompe los `new XDTO(...)` posicionales existentes; hay que actualizar todos los call sites (y `mvn -o test`, no solo compile, lo detecta).
- Base de paquetes: `ar.com.leo.super_master_backend`.

---

### Task 1: Migración SQL + columnas `family_id`/`family_name` en `mlas`

**Files:**
- Create: `supermaster-backend/src/main/resources/db/2026-07-01-mlas-family.sql`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mla/entity/Mla.java`

**Interfaces:**
- Produces: `Mla.getFamilyId()/setFamilyId(String)`, `Mla.getFamilyName()/setFamilyName(String)` (columnas `family_id`, `family_name`).

- [ ] **Step 1: Escribir el script SQL de migración**

Crear `2026-07-01-mlas-family.sql`:

```sql
-- Familia de User Products (modelo nuevo de variantes ML) a nivel publicacion.
-- ddl-auto=validate: aplicar a mano en la BD antes de arrancar el backend.
ALTER TABLE supermaster.mlas
  ADD COLUMN family_id   VARCHAR(30)  NULL AFTER mlau,
  ADD COLUMN family_name VARCHAR(255) NULL AFTER family_id;

CREATE INDEX idx_mlas_family_id ON supermaster.mlas (family_id);
```

- [ ] **Step 2: Agregar los campos a la entidad `Mla`**

En `Mla.java`, después del campo `mlau` (línea ~35), agregar:

```java
    @Size(max = 30)
    @Column(name = "family_id", length = 30)
    private String familyId;

    @Size(max = 255)
    @Column(name = "family_name", length = 255)
    private String familyName;
```

(Usa el mismo estilo Lombok/anotaciones que los campos vecinos; `import jakarta.validation.constraints.Size;` ya está presente por `mlau`.)

- [ ] **Step 3: Compilar**

Run: `mvn -o -q compile`
Expected: BUILD SUCCESS (exit 0).

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/resources/db/2026-07-01-mlas-family.sql \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mla/entity/Mla.java
git commit -m "feat(variantes-ml): columnas family_id/family_name en mlas (Fase 1)"
```

---

### Task 2: Extraer tags `allow_variations` / `variation_attribute` de la categoría

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlAtributoDefDTO.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlCategoriaAtributoService.java` (`parsear`, ~139-216)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlCategoriaAtributoServiceTest.java`

**Interfaces:**
- Produces: `MlAtributoDefDTO` con 2 componentes nuevos `boolean allowVariations, boolean variationAttribute` (al final del record). `parsear()` los completa desde `tags`.

- [ ] **Step 1: Escribir el test que falla**

Agregar a `MlCategoriaAtributoServiceTest` (usa el estilo existente: JSON crudo → `parsear`):

```java
@Test
void parsear_marcaEjesDeVariacion() throws Exception {
    String json = """
        [
          {"id":"COLOR","name":"Color","value_type":"list",
           "tags":{"allow_variations":true,"hidden":true},
           "values":[{"id":"52049","name":"Negro"}]},
          {"id":"MODEL","name":"Modelo","value_type":"string","tags":{}}
        ]
        """;
    JsonNode arr = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
    List<MlAtributoDefDTO> defs = MlCategoriaAtributoService.parsear(arr);

    MlAtributoDefDTO color = defs.stream().filter(d -> d.id().equals("COLOR")).findFirst().orElseThrow();
    MlAtributoDefDTO model = defs.stream().filter(d -> d.id().equals("MODEL")).findFirst().orElseThrow();
    assertThat(color.allowVariations()).isTrue();
    assertThat(color.variationAttribute()).isFalse();
    assertThat(model.allowVariations()).isFalse();
}
```

> Nota: verificá el import de `ObjectMapper`/`JsonNode` que ya usa el archivo de test (Jackson 3 en este proyecto: `tools.jackson.databind`); usá el mismo que el resto del test.

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `mvn -o test -Dtest=MlCategoriaAtributoServiceTest`
Expected: FAIL de compilación (`allowVariations()` no existe).

- [ ] **Step 3: Agregar los componentes al record `MlAtributoDefDTO`**

Agregar al final de los componentes del record (después de `hint`):

```java
        String hint,
        boolean allowVariations,
        boolean variationAttribute
```

- [ ] **Step 4: Completar los tags en `parsear()`**

En `MlCategoriaAtributoService.parsear()`, junto a la lectura de tags existente, agregar:

```java
        boolean allowVariations    = !tags.isMissingNode() && tags.has("allow_variations");
        boolean variationAttribute = !tags.isMissingNode() && tags.has("variation_attribute");
```

Y en el `new MlAtributoDefDTO(...)`, agregar los 2 valores al final:

```java
        result.add(new MlAtributoDefDTO(
                id, name, valueType, values, allowedUnits, defaultUnit,
                required, conditional, multivalued, grupo,
                relevance, valueMaxLength, example, hint,
                allowVariations, variationAttribute
        ));
```

- [ ] **Step 5: Correr el test para verificar que pasa**

Run: `mvn -o test -Dtest=MlCategoriaAtributoServiceTest`
Expected: PASS. Si otro test o call site rompe por el constructor del record, actualizarlo con los 2 valores nuevos (`false, false` cuando no aplique).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlAtributoDefDTO.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlCategoriaAtributoService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlCategoriaAtributoServiceTest.java
git commit -m "feat(variantes-ml): exponer allow_variations/variation_attribute por atributo (Fase 1)"
```

---

### Task 3: Capturar `family_id`/`family_name` de la respuesta de alta ML

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/ResultadoAltaMl.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (`crearItemEnMlCore`, ~2215-2277)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreServiceCrearCoreTest.java` (crear si no existe)

**Interfaces:**
- Consumes: `MlItemPayloadBuilder.construir(...)`, `Producto` (entity ML del dominio).
- Produces: `ResultadoAltaMl` con `String familyId, String familyName` nuevos; factory `ResultadoAltaMl.creado(String itemId, String mlau, String familyId, String familyName)`. `crearItemEnMlCore` devuelve `familyId`/`familyName` extraídos de la respuesta.

- [ ] **Step 1: Escribir el test que falla (core puro, sin red)**

Crear `MercadoLibreServiceCrearCoreTest.java`. Usa un `Producto` mínimo y un `poster` lambda que devuelve JSON canned con `family_id`/`family_name`:

```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoLibreServiceCrearCoreTest {

    private Producto productoBase() {
        Producto p = new Producto();
        p.setSku("SKU-VAR-1");
        p.setTituloMl("Vaso Cristal Negro");
        p.setCosto(new BigDecimal("1000"));
        p.setIva(new BigDecimal("21"));
        return p;
    }

    @Test
    void crearItemEnMlCore_capturaFamilyIdYFamilyName() {
        ObjectMapper om = new ObjectMapper();
        String respuestaMl = """
            {"id":"MLA123","user_product_id":"MLAU999",
             "family_id":18446744000000000615,"family_name":"Vaso Cristal"}
            """;

        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                productoBase(), om,
                sku -> false,                       // no existe
                sku -> List.of("foto1.jpg"),        // archivos
                filename -> "PIC1",                 // subir imagen -> picId
                "MLA1055",                          // categoryId
                new BigDecimal("2500"),             // precioFinal
                Set.of(),                           // categoriaAttrIds
                cat -> 60,                           // maxTitleLength
                json -> respuestaMl,                // poster (POST /items)
                (itemId, desc) -> "ok");            // posterDescripcion

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(r.itemId()).isEqualTo("MLA123");
        assertThat(r.mlau()).isEqualTo("MLAU999");
        assertThat(r.familyId()).isEqualTo("18446744000000000615");
        assertThat(r.familyName()).isEqualTo("Vaso Cristal");
    }
}
```

> Ajustá la firma exacta de `crearItemEnMlCore` a la real (mismo orden de lambdas). Si el `Producto` mínimo no basta para `MlItemPayloadBuilder.construir` (ej. requiere marca), setear lo mínimo necesario para que no lance.

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `mvn -o test -Dtest=MercadoLibreServiceCrearCoreTest`
Expected: FAIL de compilación (`r.familyId()` no existe).

- [ ] **Step 3: Agregar campos y factory a `ResultadoAltaMl`**

Agregar `familyId` y `familyName` al record (después de `advertencia`), actualizar TODAS las factories para setearlos en `null` por defecto, y agregar una nueva factory:

```java
public record ResultadoAltaMl(Estado estado, String motivo, String itemId, String mlau,
                              String advertencia, String familyId, String familyName) {

    public enum Estado { CREADO, ACTUALIZADO, YA_EXISTIA, ERROR }

    public static ResultadoAltaMl creado(String itemId, String mlau) {
        return new ResultadoAltaMl(Estado.CREADO, null, itemId, mlau, null, null, null);
    }
    public static ResultadoAltaMl creado(String itemId, String mlau, String familyId, String familyName) {
        return new ResultadoAltaMl(Estado.CREADO, null, itemId, mlau, null, familyId, familyName);
    }
    public static ResultadoAltaMl actualizado(String itemId) {
        return new ResultadoAltaMl(Estado.ACTUALIZADO, null, itemId, null, null, null, null);
    }
    public static ResultadoAltaMl actualizado(String itemId, String mlau) {
        return new ResultadoAltaMl(Estado.ACTUALIZADO, null, itemId, mlau, null, null, null);
    }
    public static ResultadoAltaMl yaExistia() {
        return new ResultadoAltaMl(Estado.YA_EXISTIA, null, null, null, null, null, null);
    }
    public static ResultadoAltaMl error(String motivo) {
        return new ResultadoAltaMl(Estado.ERROR, motivo, null, null, null, null, null);
    }
    public ResultadoAltaMl conAdvertencia(String advertencia) {
        return new ResultadoAltaMl(estado, motivo, itemId, mlau, advertencia, familyId, familyName);
    }
}
```

- [ ] **Step 4: Extraer family en `crearItemEnMlCore`**

Donde hoy parsea la respuesta y hace `return ResultadoAltaMl.creado(itemId, mlau...)`, extraer family y usar la factory nueva:

```java
        String mlau = creado.path("user_product_id").asString("");
        String familyId = creado.path("family_id").isMissingNode() ? null
                : creado.path("family_id").asString(null);
        String familyName = blankToNull(creado.path("family_name").asString(null));
        // ... (descripción best-effort, igual que hoy) ...
        return ResultadoAltaMl.creado(itemId, mlau.isBlank() ? null : mlau, familyId, familyName);
```

(Si no hay helper `blankToNull` accesible, usar: `String familyName = creado.path("family_name").asString(""); if (familyName.isBlank()) familyName = null;`. Preservar la lógica de `advertencia` con `.conAdvertencia(...)`.)

- [ ] **Step 5: Correr el test para verificar que pasa**

Run: `mvn -o test -Dtest=MercadoLibreServiceCrearCoreTest`
Expected: PASS. Si algún call site de las factories rompe, ya quedó cubierto (las firmas de las factories no cambiaron).

- [ ] **Step 6: Correr toda la suite ML para no romper nada**

Run: `mvn -o test -Dtest='Ml*Test,MercadoLibre*Test'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/ResultadoAltaMl.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreServiceCrearCoreTest.java
git commit -m "feat(variantes-ml): capturar family_id/family_name de la respuesta de alta ML (Fase 1)"
```

---

### Task 4: Persistir `family_id`/`family_name` en `mlas` al asociar

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mla/service/MlaService.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mla/service/MlaServiceImpl.java` (`asegurarYAsociar`, `asegurarMla`)
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlExportService.java` (`postAlta`, ~117-138; call site en `exportar`, ~56)

**Interfaces:**
- Consumes: `ResultadoAltaMl.familyId()`, `ResultadoAltaMl.familyName()` (Task 3); `Mla.setFamilyId/setFamilyName` (Task 1).
- Produces: `MlaService.asegurarYAsociar(Integer productoId, String mlaCode, String mlau, String familyId, String familyName)` (sobrecarga); `asegurarMla(String mlaCode, String mlau, String familyId, String familyName)`.

- [ ] **Step 1: Agregar la sobrecarga a la interfaz `MlaService`**

En `MlaService.java`, junto a la firma existente, agregar:

```java
    void asegurarYAsociar(Integer productoId, String mlaCode, String mlau, String familyId, String familyName);
```

- [ ] **Step 2: Implementar en `MlaServiceImpl`**

Refactorizar para que la versión de 3 args delegue en la de 5 (family = null), y setear family en `asegurarMla`. Mantener el comportamiento actual:

```java
    @Override
    @Transactional
    public void asegurarYAsociar(Integer productoId, String mlaCode, String mlau) {
        asegurarYAsociar(productoId, mlaCode, mlau, null, null);
    }

    @Override
    @Transactional
    public void asegurarYAsociar(Integer productoId, String mlaCode, String mlau, String familyId, String familyName) {
        Integer mlaId = self.asegurarMla(mlaCode, mlau, familyId, familyName);
        Mla mla = repo.findById(mlaId)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado tras asegurarlo: " + mlaCode));
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado: " + productoId));
        producto.setMla(mla);
        productoRepository.save(producto);
    }

    @Transactional
    public Integer asegurarMla(String mlaCode, String mlau) {
        return asegurarMla(mlaCode, mlau, null, null);
    }

    @Transactional
    public Integer asegurarMla(String mlaCode, String mlau, String familyId, String familyName) {
        return repo.findFirstByMla(mlaCode).map(existente -> {
            // Completar family si vino y falta (no pisar con null)
            boolean cambio = false;
            if (familyId != null && existente.getFamilyId() == null) { existente.setFamilyId(familyId); cambio = true; }
            if (familyName != null && existente.getFamilyName() == null) { existente.setFamilyName(familyName); cambio = true; }
            if (cambio) repo.save(existente);
            return existente.getId();
        }).orElseGet(() -> {
            Mla nuevo = new Mla();
            nuevo.setMla(mlaCode);
            nuevo.setMlau(mlau);
            nuevo.setFamilyId(familyId);
            nuevo.setFamilyName(familyName);
            nuevo.setTopePromocion(0);
            Mla guardado = repo.save(nuevo);
            auditoriaService.registrarCambios(
                    AuditoriaEntidad.MLA, guardado.getId(), guardado.getMla(),
                    AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(guardado));
            return guardado.getId();
        });
    }
```

> Ajustar imports/tipos exactos (`Producto` es `ar.com.leo...producto.entity.Producto`; ya está referenciado en el archivo). `self` es el proxy transaccional existente.

- [ ] **Step 3: Pasar family desde `MlExportService.postAlta`**

Cambiar la firma de `postAlta` para recibir family y usarlo en la asociación, y actualizar el call site en `exportar` (rama `CREADO`):

```java
    // en exportar(), rama CREADO:
    List<String> avisos = postAlta(productoId, r.itemId(), r.mlau(), r.familyId(), r.familyName());

    // firma nueva:
    private List<String> postAlta(Integer productoId, String itemId, String mlau, String familyId, String familyName) {
        List<String> avisos = new ArrayList<>();
        try {
            mlaService.asegurarYAsociar(productoId, itemId, mlau, familyId, familyName);
        } catch (Exception e) {
            log.warn("ML - No se pudo asociar el MLA {} al producto {}: {}", itemId, productoId, e.getMessage());
            avisos.add("no se pudo asociar el MLA");
        }
        // ... resto igual (comisión, envío) ...
        return avisos;
    }
```

- [ ] **Step 4: Compilar y correr la suite**

Run: `mvn -o -q compile && mvn -o test -Dtest='Ml*Test,MercadoLibre*Test,Mla*Test'`
Expected: BUILD SUCCESS / tests PASS. (No hay test unitario nuevo: es capa de servicio con repos/DB; se verifica por compilación + suite existente. La lógica pura de family ya quedó cubierta en Task 3.)

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mla/service/MlaService.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mla/service/MlaServiceImpl.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlExportService.java
git commit -m "feat(variantes-ml): persistir family_id/family_name en mlas al asociar (Fase 1)"
```

---

## Verificación final de la Fase 1

- [ ] `mvn -o -q compile` → exit 0.
- [ ] `mvn -o test -Dtest='Ml*Test,MercadoLibre*Test,Mla*Test'` → PASS.
- [ ] Recordatorio manual (fuera del código): aplicar `2026-07-01-mlas-family.sql` en la BD antes de arrancar el backend (ddl-auto=validate).

## Qué queda para fases siguientes (no en este plan)

- **Fase 2 (UI):** en el modal, elegir el eje entre atributos con `allowVariations`, cargar N variantes (SKU/stock/precio/valor de eje/atributos por variante/imágenes) y, al guardar, crear N productos y publicarlos con el mismo `family_name` (mismo `tituloMl`) + su atributo de eje. El backend de alta+export ya lo soporta; el orquestador "un solo guardado crea N" se define ahí.
- **Fase 3:** editar detectando modelo legacy (`variations`) vs familia nueva.
- **Fase 4:** migración UPtin.

## Nota de refinamiento sobre la spec

Durante la planificación se confirmó que **no hace falta** un endpoint/DTO dedicado `ProductoConVariantesCreateDTO` en la Fase 1 (como sugería la spec): crear y publicar ya están separados y el flujo actual publica variantes correctamente con `family_name` compartido + atributo de eje por variante. El wrapper de "alta múltiple en un guardado" se trata en la Fase 2 (UI), donde tiene su lugar natural. La Fase 1 queda acotada a las piezas habilitadoras del backend.
