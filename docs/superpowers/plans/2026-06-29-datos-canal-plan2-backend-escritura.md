# Datos de canal en el modal — Plan 2: Backend escritura + migración

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar de persistir `descripcion`, `ml_category_id`/`ml_category_nombre` y `producto_ml_atributo`; los builders de publicación leen esos datos de campos **`@Transient`** que el export setea desde el request. Las descripciones pasan a **passthrough** (se envían tal cual) y se agrega un endpoint que **compone una descripción sugerida**.

**Architecture:** Estrategia `@Transient` de bajo riesgo: la entidad `Producto` conserva los campos (`descripcionMl`, `descripcionNube`, `mlCategoryId`, `mlAtributos`) pero como no persistidos. Los export services (single SKU desde el modal) los setean en la entidad cargada antes de publicar; en lote van null → el publish **omite** esos campos (no pisa con vacío). La composición vieja (descripción + características) se mueve a builders "sugerida" detrás de un endpoint nuevo.

**Tech Stack:** Spring Boot 4, Java 25, Maven, JPA (`ddl-auto=validate` dev/prod; tests `ddl-auto=none`), MapStruct, Lombok, JUnit 5 + AssertJ + Mockito.

## Global Constraints

- Tests con `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`). Compilar con `mvn -o -q compile`.
- `ddl-auto=validate`: el schema se cambia con script SQL manual en `supermaster-backend/src/main/resources/db/`. Quitar columnas del entity con la columna aún presente en la BD **no** rompe `validate` (Hibernate ignora columnas extra). Los tests usan `ddl-auto=none`.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos, nunca `-A` ni `.superpowers/`.
- Paquete base: `ar.com.leo.super_master_backend`. Formato de error `{ "message": "...", "path": "..." }`.
- **Requiere el Plan 1** (existe `apis/ml/dto/MlAtributoDTO.java`: `record MlAtributoDTO(String attributeId, String valueId, String valueName, boolean noAplica)`).
- Records: agregar/quitar componentes rompe `new XDTO(...)` posicional en tests; `mvn -o compile` no siempre lo detecta, correr `mvn -o test`.

## File Structure

- Create: `supermaster-backend/src/main/resources/db/2026-06-29-quitar-datos-canal.sql` — DROP de columnas + tabla.
- Create: `apis/ml/service/MlDescripcionSugeridaBuilder.java` — composición ML (características) reusable.
- Create: `apis/nube/service/NubeDescripcionSugeridaBuilder.java` — composición Nube (HTML) reusable.
- Create: `dominio/producto/descripcion/DescripcionSugeridaController.java` — `GET /api/productos/{id}/descripcion-sugerida`.
- Create: `dominio/producto/descripcion/DescripcionSugeridaService.java` — carga producto + compone.
- Create: `dominio/producto/descripcion/dto/DescripcionSugeridaDTO.java` — `record DescripcionSugeridaDTO(String texto)`.
- Modify: `dominio/producto/entity/Producto.java` — quitar columnas, agregar `@Transient`.
- Delete: `dominio/producto/entity/ProductoMlAtributo.java`, `dominio/producto/dto/ProductoMlAtributoDTO.java`.
- Modify: builders `MlDescripcionBuilder`, `NubeDescripcionBuilder` (passthrough); `MlItemPayloadBuilder` (usa `MlAtributoDTO`); `NubeProductoPayloadBuilder` (guard descripción).
- Modify: `MercadoLibreService` (`faltantesRequeridos`, guards de descripción en los cores), `TiendaNubeService` (guard descripción en PATCH).
- Modify: `ProductoMapper`, `ProductoDTO`, `ProductoCreateDTO`, `ProductoUpdateDTO`, `ProductoPatchDTO`, `ProductoServiceImpl` (quitar campos/lógica).
- Modify: `apis/ml/dto/MlExportRequestDTO.java`, `apis/nube/dto/ExportNubeRequestDTO.java`, `MlExportService`, `NubeExportService` (cablear transitorios).

---

### Task 1: Migración SQL (drop de columnas y tabla)

**Files:**
- Create: `supermaster-backend/src/main/resources/db/2026-06-29-quitar-datos-canal.sql`

- [ ] **Step 1: Crear el SQL**

```sql
-- 2026-06-29 — Externalizar datos de canal: dejan de persistirse (fuente de verdad = el canal).
-- DESTRUCTIVO de una sola vía: para ítems publicados el dato sigue en ML/Nube; para no publicados se pierde.
-- Cargar con: mysql --default-character-set=utf8mb4 ... (no aplica acá pero se mantiene la convención).
USE supermaster;

ALTER TABLE productos
    DROP COLUMN descripcion,
    DROP COLUMN ml_category_id,
    DROP COLUMN ml_category_nombre;

DROP TABLE IF EXISTS producto_ml_atributo;
```

- [ ] **Step 2: Commit (la aplicación a la BD es paso manual de Leo)**

```bash
git add supermaster-backend/src/main/resources/db/2026-06-29-quitar-datos-canal.sql
git commit -m "feat(db): drop de descripcion/ml_category/producto_ml_atributo (externalizar al canal)"
```

---

### Task 2: Builders de descripción sugerida + endpoint

Independiente de la remoción: crea las clases que preservan la composición vieja (características) antes de que los builders originales pasen a passthrough.

**Files:**
- Create: `apis/ml/service/MlDescripcionSugeridaBuilder.java`
- Create: `apis/nube/service/NubeDescripcionSugeridaBuilder.java`
- Create: `dominio/producto/descripcion/dto/DescripcionSugeridaDTO.java`
- Create: `dominio/producto/descripcion/DescripcionSugeridaService.java`
- Create: `dominio/producto/descripcion/DescripcionSugeridaController.java`
- Test: `src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionSugeridaBuilderTest.java`

**Interfaces:**
- Produces: `MlDescripcionSugeridaBuilder.construir(Producto)` → texto plano con características; `NubeDescripcionSugeridaBuilder.construir(Producto)` → HTML con características; `DescripcionSugeridaService.sugerir(Integer id, String canal)` → `DescripcionSugeridaDTO`; `GET /api/productos/{id}/descripcion-sugerida?canal=ml|nube`.

- [ ] **Step 1: Test de `MlDescripcionSugeridaBuilder` (falla)**

```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MlDescripcionSugeridaBuilderTest {

    @Test
    void componeCaracteristicasSinPrefijoManual() {
        Producto p = new Producto();
        p.setSku("ABC123");
        p.setCapacidad("500 ml");
        Material m = new Material();
        m.setNombre("Plástico");
        p.setMaterial(m);

        String txt = MlDescripcionSugeridaBuilder.construir(p);

        assertThat(txt).contains("CARACTERÍSTICAS");
        assertThat(txt).contains("Material: Plástico");
        assertThat(txt).contains("Capacidad: 500 ml");
        assertThat(txt).contains("SKU: ABC123");
    }
}
```

- [ ] **Step 2: Correr el test (falla: clase inexistente)**

Run: `cd supermaster-backend && mvn -o -q -Dtest=MlDescripcionSugeridaBuilderTest test`
Expected: FAIL (no compila).

- [ ] **Step 3: Implementar `MlDescripcionSugeridaBuilder`**

(Es la lógica de `MlDescripcionBuilder` ANTES de este plan, sin el prefijo manual `p.getDescripcion()` —que ya no existe.)

```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoApto;

import java.util.ArrayList;
import java.util.List;

/** Compone una descripción SUGERIDA en texto plano (características) para Mercado Libre. */
public final class MlDescripcionSugeridaBuilder {

    private MlDescripcionSugeridaBuilder() {}

    public static String construir(Producto p) {
        StringBuilder sb = new StringBuilder();
        sb.append("CARACTERÍSTICAS\n");
        String dimensiones = dimensiones(p);
        if (!dimensiones.isBlank()) sb.append("• Dimensiones: ").append(dimensiones).append("\n");
        if (p.getMaterial() != null && p.getMaterial().getNombre() != null)
            sb.append("• Material: ").append(p.getMaterial().getNombre()).append("\n");
        String aptos = aptos(p);
        if (!aptos.isBlank()) sb.append("• Aptos: ").append(aptos).append("\n");
        if (p.getMarca() != null && p.getMarca().getNombre() != null)
            sb.append("• Marca: ").append(p.getMarca().getNombre()).append("\n");
        if (p.getSku() != null && !p.getSku().isBlank())
            sb.append("SKU: ").append(p.getSku().trim()).append("\n");
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
        if (valor != null && !valor.isBlank()) partes.add(etiqueta + ": " + valor.trim());
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

- [ ] **Step 4: Correr el test (pasa)**

Run: `cd supermaster-backend && mvn -o -q -Dtest=MlDescripcionSugeridaBuilderTest test`
Expected: PASS.

- [ ] **Step 5: Implementar `NubeDescripcionSugeridaBuilder`** (lógica de `NubeDescripcionBuilder` ANTES de este plan, sin el prefijo manual)

```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Compone una descripción SUGERIDA en HTML (características) para Tienda Nube. */
public final class NubeDescripcionSugeridaBuilder {

    private NubeDescripcionSugeridaBuilder() {}

    private static final String LABEL_COLOR = "#1e40af";

    public static String construir(Producto p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p><b>CARACTERÍSTICAS</b></p><ul>");
        if (p.getMarca() != null && p.getMarca().getNombre() != null)
            sb.append("<li>").append(label("Marca")).append(" ").append(escape(p.getMarca().getNombre())).append("</li>");
        if (p.getMaterial() != null && p.getMaterial().getNombre() != null)
            sb.append("<li>").append(label("Material")).append(" ").append(escape(p.getMaterial().getNombre())).append("</li>");

        List<String> dims = dimensiones(p);
        if (!dims.isEmpty()) {
            sb.append("<li>").append(label("Dimensiones")).append("<ul>");
            for (String d : dims) sb.append("<li>").append(escape(d)).append("</li>");
            sb.append("</ul></li>");
        }

        List<String> aptos = aptos(p);
        if (!aptos.isEmpty()) {
            sb.append("<li>").append(label("Apto")).append("<ul>");
            for (String a : aptos) sb.append("<li>").append(escape(a)).append("</li>");
            sb.append("</ul></li>");
        }

        sb.append("</ul>");
        if (p.getSku() != null && !p.getSku().isBlank())
            sb.append("<p>").append(label("SKU")).append(" ").append(escape(p.getSku().trim())).append("</p>");
        return sb.toString();
    }

    private static String label(String texto) {
        return "<b><u><span style=\"color:" + LABEL_COLOR + "\">" + escape(texto) + ":</span></u></b>";
    }

    private static List<String> dimensiones(Producto p) {
        List<String> partes = new ArrayList<>();
        agregar(partes, "Largo", p.getLargo());
        agregar(partes, "Ancho", p.getAncho());
        agregar(partes, "Alto", p.getAlto());
        agregar(partes, "Diámetro boca", p.getDiamboca());
        agregar(partes, "Diámetro base", p.getDiambase());
        agregar(partes, "Espesor", p.getEspesor());
        agregar(partes, "Capacidad", p.getCapacidad());
        return partes;
    }

    private static void agregar(List<String> partes, String label, String valor) {
        if (valor != null && !valor.isBlank()) partes.add(label + ": " + valor.trim());
    }

    private static List<String> aptos(Producto p) {
        if (p.getProductosApto() == null) return List.of();
        return p.getProductosApto().stream()
                .filter(pa -> pa.getApto() != null && pa.getApto().getNombre() != null)
                .map(pa -> pa.getApto().getNombre())
                .collect(Collectors.toList());
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 6: Crear el DTO de respuesta**

```java
package ar.com.leo.super_master_backend.dominio.producto.descripcion.dto;

/** Descripción compuesta sugerida para un canal. */
public record DescripcionSugeridaDTO(String texto) {}
```

- [ ] **Step 7: Crear el service** (carga el producto en tx readOnly y compone; toca asociaciones LAZY dentro de la tx — OSIV off)

```java
package ar.com.leo.super_master_backend.dominio.producto.descripcion;

import ar.com.leo.super_master_backend.apis.ml.service.MlDescripcionSugeridaBuilder;
import ar.com.leo.super_master_backend.apis.nube.service.NubeDescripcionSugeridaBuilder;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.descripcion.dto.DescripcionSugeridaDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DescripcionSugeridaService {

    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public DescripcionSugeridaDTO sugerir(Integer productoId, String canal) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        String c = canal == null ? "" : canal.toLowerCase();
        return switch (c) {
            case "ml" -> new DescripcionSugeridaDTO(MlDescripcionSugeridaBuilder.construir(p));
            case "nube" -> new DescripcionSugeridaDTO(NubeDescripcionSugeridaBuilder.construir(p));
            default -> throw new BadRequestException("canal inválido (usar 'ml' o 'nube')");
        };
    }
}
```

- [ ] **Step 8: Crear el controller**

```java
package ar.com.leo.super_master_backend.dominio.producto.descripcion;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.descripcion.dto.DescripcionSugeridaDTO;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/productos/{id}/descripcion-sugerida")
public class DescripcionSugeridaController {

    private final DescripcionSugeridaService descripcionSugeridaService;

    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<DescripcionSugeridaDTO> sugerir(@PathVariable @Positive Integer id,
                                                          @RequestParam String canal) {
        return ResponseEntity.ok(descripcionSugeridaService.sugerir(id, canal));
    }
}
```

- [ ] **Step 9: Compilar y commit**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: BUILD SUCCESS.

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionSugeridaBuilder.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeDescripcionSugeridaBuilder.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/descripcion supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionSugeridaBuilderTest.java
git commit -m "feat(descripcion): builders de descripción sugerida + endpoint GET /descripcion-sugerida"
```

---

### Task 3: Refactor atómico — quitar persistencia y pasar builders a passthrough

Cambio de tipo amplio (`Producto.mlAtributos` deja de ser `Set<ProductoMlAtributo>`): entidad, builders, cores, mapper, DTOs y service deben cambiar juntos para compilar. Se verifica con `mvn -o test` verde y se commitea una vez.

**Files:** (todos Modify salvo los Delete)
- `dominio/producto/entity/Producto.java`
- Delete `dominio/producto/entity/ProductoMlAtributo.java`, `dominio/producto/dto/ProductoMlAtributoDTO.java`
- `apis/ml/service/MlItemPayloadBuilder.java`, `apis/ml/service/MlDescripcionBuilder.java`
- `apis/nube/service/NubeDescripcionBuilder.java`, `apis/nube/service/NubeProductoPayloadBuilder.java`
- `apis/ml/service/MercadoLibreService.java`, `apis/nube/service/TiendaNubeService.java`
- `dominio/producto/mapper/ProductoMapper.java`
- `dominio/producto/dto/ProductoDTO.java`, `ProductoCreateDTO.java`, `ProductoUpdateDTO.java`, `ProductoPatchDTO.java`
- `dominio/producto/service/ProductoServiceImpl.java`

**Interfaces:**
- Produces: `Producto` con `@Transient` `String descripcionMl`, `String descripcionNube`, `String mlCategoryId`, `List<MlAtributoDTO> mlAtributos` (Lombok genera getters/setters). Se elimina `mlCategoryNombre`.

- [ ] **Step 1: Entidad — quitar columnas, agregar `@Transient`**

En `Producto.java`, reemplazar el bloque de líneas 61-72 (descripcion + mlCategoryId + mlCategoryNombre):

```java
    // Datos de canal NO persistidos (fuente de verdad: el canal). Los setea el export desde el request
    // antes de publicar; en lote van null y el publish los omite. Ver plan 2026-06-29-datos-canal.
    @Transient
    private String descripcionMl;
    @Transient
    private String descripcionNube;
    @Transient
    private String mlCategoryId;
    @Transient
    private java.util.List<ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO> mlAtributos = new java.util.ArrayList<>();
```

Y reemplazar la relación (líneas 207-209):

```java
    // (producto_ml_atributo eliminado; los atributos ML viven en el campo @Transient mlAtributos)
```

Asegurar el import de `jakarta.persistence.Transient` (ya cubierto si el archivo usa `jakarta.persistence.*`). Quitar el import de `ProductoMlAtributo` si quedó sin uso.

- [ ] **Step 2: Borrar entidad y DTO de atributo viejos**

```bash
git rm supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/ProductoMlAtributo.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ProductoMlAtributoDTO.java
```

- [ ] **Step 3: `MlItemPayloadBuilder` — iterar `MlAtributoDTO`**

En `MlItemPayloadBuilder.java`: cambiar el import `ProductoMlAtributo` por `import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;`. En `construirAtributos`, reemplazar los accesos a getters de entidad por accessors de record:
- `p.getMlAtributos().stream().anyMatch(a -> "BRAND".equals(a.getAttributeId()))` → `a.attributeId()`.
- `... "MATERIAL".equals(a.getAttributeId())` → `a.attributeId()`.
- El loop `for (ProductoMlAtributo a : p.getMlAtributos())` → `for (MlAtributoDTO a : p.getMlAtributos())`; dentro: `a.getAttributeId()`→`a.attributeId()`, `a.isNoAplica()`→`a.noAplica()`, `a.getValueId()`→`a.valueId()`, `a.getValueName()`→`a.valueName()`.

- [ ] **Step 4: `MercadoLibreService.faltantesRequeridos` — usar `MlAtributoDTO`**

En `MercadoLibreService.java`, método `faltantesRequeridos` (~línea 2128): reemplazar
```java
        Set<String> presentes = p.getMlAtributos().stream()
                .filter(a -> !a.isNoAplica())
                .map(ProductoMlAtributo::getAttributeId).collect(Collectors.toSet());
```
por
```java
        Set<String> presentes = p.getMlAtributos().stream()
                .filter(a -> !a.noAplica())
                .map(ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO::attributeId)
                .collect(Collectors.toSet());
```
Quitar el import de `ProductoMlAtributo` si queda sin uso en el archivo.

- [ ] **Step 5: `MlDescripcionBuilder` → passthrough**

Reemplazar todo `MlDescripcionBuilder.java` por:

```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

/** Descripción para ML: passthrough del campo transitorio (lo que ve el usuario es lo que se publica). */
public final class MlDescripcionBuilder {

    private MlDescripcionBuilder() {}

    public static String construir(Producto p) {
        return p.getDescripcionMl();
    }
}
```

- [ ] **Step 6: `NubeDescripcionBuilder` → passthrough**

Reemplazar todo `NubeDescripcionBuilder.java` por:

```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

/** Descripción para Nube: passthrough del campo transitorio (HTML tal cual lo edita el usuario). */
public final class NubeDescripcionBuilder {

    private NubeDescripcionBuilder() {}

    public static String construir(Producto p) {
        return p.getDescripcionNube();
    }
}
```

- [ ] **Step 7: Guards de descripción (null = no tocar)**

En `MercadoLibreService.crearItemEnMlCore` (~línea 2206) reemplazar:
```java
            try {
                posterDescripcion.apply(itemId, MlDescripcionBuilder.construir(producto));
            } catch (Exception e) {
                advertencia = conMotivo("ítem creado pero falló la descripción", e);
            }
```
por:
```java
            String descMl = MlDescripcionBuilder.construir(producto);
            if (descMl != null && !descMl.isBlank()) {
                try {
                    posterDescripcion.apply(itemId, descMl);
                } catch (Exception e) {
                    advertencia = conMotivo("ítem creado pero falló la descripción", e);
                }
            }
```

En `MercadoLibreService.actualizarItemEnMlCore` (~línea 1976) reemplazar:
```java
            try {
                putDesc.accept(mla, MlDescripcionBuilder.construir(producto));
            } catch (Exception e) {
                advertencia = concatAdv(advertencia, conMotivo("descripción no actualizada", e));
            }
```
por:
```java
            String descMl = MlDescripcionBuilder.construir(producto);
            if (descMl != null && !descMl.isBlank()) {
                try {
                    putDesc.accept(mla, descMl);
                } catch (Exception e) {
                    advertencia = concatAdv(advertencia, conMotivo("descripción no actualizada", e));
                }
            }
```

En `NubeProductoPayloadBuilder.construir` (línea 22) reemplazar:
```java
        payload.put("description", Map.of("es", NubeDescripcionBuilder.construir(p)));
```
por:
```java
        String descNube = NubeDescripcionBuilder.construir(p);
        if (descNube != null && !descNube.isBlank()) payload.put("description", Map.of("es", descNube));
```

En `TiendaNubeService` (línea 967, PATCH) reemplazar:
```java
            body.put("description", Map.of("es", NubeDescripcionBuilder.construir(producto)));
```
por:
```java
            String descNube = NubeDescripcionBuilder.construir(producto);
            if (descNube != null && !descNube.isBlank()) body.put("description", Map.of("es", descNube));
```

- [ ] **Step 8: `ProductoMapper` — quitar mapeos**

En `ProductoMapper.java`:
- Quitar el import `ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMlAtributo;`.
- En `toDTO(...)` (líneas 56-110) quitar los argumentos `entity.getDescripcion(),` (línea 63), `entity.getMlCategoryId(),` y `entity.getMlCategoryNombre(),` (102-103) y `toMlAtributosDTO(entity)` (109; quitar también la coma previa del `entity.getEan()`).
- Borrar el método privado `toMlAtributosDTO` (líneas 140-145).
- Quitar las dos anotaciones `@Mapping(target = "mlAtributos", ignore = true)` (líneas 190 y 207).

- [ ] **Step 9: DTOs de producto — quitar componentes**

- `ProductoDTO.java`: quitar `String descripcion,` (línea 17), `String mlCategoryId,` y `String mlCategoryNombre,` (71-72), y `List<ProductoMlAtributoDTO> mlAtributos` (84, junto con su coma previa y el comentario). Quitar import de `ProductoMlAtributoDTO` si lo hubiera (es del mismo paquete, no hay import explícito).
- `ProductoCreateDTO.java`: quitar el bloque `descripcion` (23-24), `mlCategoryId`/`mlCategoryNombre` (80-83) y `List<ProductoMlAtributoDTO> mlAtributos` (97).
- `ProductoUpdateDTO.java`: idem (21-22, 75-78, 92).
- `ProductoPatchDTO.java`: quitar `descripcion` (20), `mlCategoryId`/`mlCategoryNombre` (46-47) y `mlAtributos` (53).

- [ ] **Step 10: `ProductoServiceImpl` — quitar lógica de atributos/descripción/categoría**

En `ProductoServiceImpl.java`:
- Quitar imports de `ProductoMlAtributo` y `ProductoMlAtributoDTO` (y el de `entityManager`/flush sólo si queda sin uso; verificar).
- `crear` (línea 120): borrar `reemplazarMlAtributos(entity, dto.mlAtributos());`.
- `actualizar` (línea 165): borrar `reemplazarMlAtributos(entity, dto.mlAtributos());`.
- `patch` (líneas 213-216): borrar el bloque `if (patchDto.getMlAtributos() != null) { reemplazarMlAtributos(...); }`.
- `aplicarPatch` (1206-1217): borrar los bloques `if (presente(patchDto.getMlCategoryId())) {...}`, `if (presente(patchDto.getMlCategoryNombre())) {...}` y `if (presente(patchDto.getDescripcion())) {...}`.
- `isPatchVacio` (1161-1163): quitar `&& !presente(patchDto.getMlCategoryId())`, `&& !presente(patchDto.getMlCategoryNombre())` y `&& patchDto.getMlAtributos() == null` (dejar la condición previa terminando en `;`).
- Borrar el método `reemplazarMlAtributos` completo (1334-1348).

- [ ] **Step 11: Compilar y arreglar tests rotos**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: BUILD SUCCESS. Si falla, resolver referencias remanentes a `ProductoMlAtributo`/`getDescripcion()`/`getMlCategoryNombre()`.

Run: `cd supermaster-backend && mvn -o test`
Expected: tras ajustes, BUILD SUCCESS. Tests a tocar (records por constructor posicional — memoria DTOs):
- Cualquier test que cree `ProductoDTO`/`ProductoCreateDTO`/`ProductoUpdateDTO` con los componentes eliminados → quitar esos argumentos.
- `MlItemPayloadBuilderTest` (si arma `ProductoMlAtributo`): cambiar a setear `producto.setMlAtributos(List.of(new MlAtributoDTO(...)))`.
- Tests de `MlDescripcionBuilder`/`NubeDescripcionBuilder` que esperaban "CARACTERÍSTICAS": ahora passthrough → ajustar para esperar el valor de `descripcionMl`/`descripcionNube` (o mover esas aserciones a los `*SugeridaBuilderTest`).

- [ ] **Step 12: Commit**

```bash
git add -u supermaster-backend/src/main/java supermaster-backend/src/test/java
git commit -m "feat(canal): externaliza descripcion/categoria/atributos ML (campos @Transient + passthrough)"
```
> Nota: `git add -u` agrega solo cambios de archivos ya rastreados (incluye los `git rm`); no agrega archivos nuevos ni `.superpowers/`. Verificar con `git status` antes de commitear.

---

### Task 4: Cablear datos transitorios en el export

**Files:**
- Modify: `apis/ml/dto/MlExportRequestDTO.java`, `apis/ml/service/MlExportService.java`
- Modify: `apis/nube/dto/ExportNubeRequestDTO.java`, `apis/nube/service/NubeExportService.java`

**Interfaces:**
- Consumes: `MlAtributoDTO`, `Producto` setters transitorios (Task 3).
- Produces:
  - `MlExportRequestDTO(List<String> skus, Integer cuotas, String mlCategoryId, List<MlAtributoDTO> mlAtributos, String descripcionMl)`.
  - `ExportNubeRequestDTO.DestinoNube(String tienda, Integer cuotas, SeoGeneradoDTO seo, String descripcion)`.

- [ ] **Step 1: Ampliar `MlExportRequestDTO`**

```java
package ar.com.leo.super_master_backend.apis.ml.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * skus + cuota. Los campos transitorios (categoría/atributos/descripción) solo se mandan
 * en la publicación desde el modal (1 SKU); en lote van null y el publish los omite.
 */
public record MlExportRequestDTO(
        List<String> skus,
        @NotNull(message = "La cuota es obligatoria") Integer cuotas,
        String mlCategoryId,
        List<MlAtributoDTO> mlAtributos,
        String descripcionMl) {}
```

- [ ] **Step 2: `MlExportService` — setear transitorios en la entidad cargada**

En `MlExportService.exportar`, pasar los transitorios a `procesarConProductoCargado`. Cambiar la firma y la llamada:

Llamada (línea 48):
```java
            ResultadoAltaMl r = self.procesarConProductoCargado(
                    productoId, request.cuotas(), request.mlCategoryId(), request.mlAtributos(), request.descripcionMl());
```

Firma + seteo (línea 81+), reemplazar el encabezado del método y agregar el seteo tras cargar `p`:
```java
    @Transactional(readOnly = true)
    public ResultadoAltaMl procesarConProductoCargado(Integer productoId, int cuotas,
                                                      String mlCategoryId,
                                                      java.util.List<ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO> mlAtributos,
                                                      String descripcionMl) {
        Producto p = productoRepository.findById(productoId).orElse(null);
        if (p == null) return ResultadoAltaMl.error("producto no encontrado");

        // Datos de canal transitorios (no persistidos): los usa el publish. En lote llegan null.
        if (mlCategoryId != null && !mlCategoryId.isBlank()) p.setMlCategoryId(mlCategoryId);
        if (mlAtributos != null) p.setMlAtributos(mlAtributos);
        p.setDescripcionMl(descripcionMl);
        // ... resto del método sin cambios (mla, upsert, crear/actualizar) ...
```
(El resto del cuerpo —cálculo de `mla`, `actualizarItemEnMl`/`crearItemEnMl`— queda igual.)

- [ ] **Step 3: Ampliar `ExportNubeRequestDTO.DestinoNube`**

```java
package ar.com.leo.super_master_backend.apis.nube.dto;

import java.util.List;

public record ExportNubeRequestDTO(List<String> skus, List<DestinoNube> tiendas) {
    public record DestinoNube(String tienda, Integer cuotas,
                              ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO seo,
                              String descripcion) {}
}
```

- [ ] **Step 4: `NubeExportService` — setear descripción por tienda antes de publicar**

En `NubeExportService.exportar`, dentro del loop de tiendas, antes del bloque upsert (antes de la línea `ResultadoAltaNube r;`), agregar:
```java
                // Descripción transitoria de esta tienda (no persistida; en lote llega null y el publish la omite).
                producto.setDescripcionNube(destino.descripcion());
```

- [ ] **Step 5: Compilar + suite**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: BUILD SUCCESS. Ajustar tests que construyen `MlExportRequestDTO`/`DestinoNube` por constructor posicional (agregar los nuevos componentes; en tests existentes pasar `null`/`List.of()`).

Run: `cd supermaster-backend && mvn -o test`
Expected: BUILD SUCCESS, 0 fallos.

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlExportRequestDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlExportService.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/dto/ExportNubeRequestDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeExportService.java
git add -u supermaster-backend/src/test/java
git commit -m "feat(export): cablea categoría/atributos/descripción transitorios al publicar (1 SKU desde el modal)"
```

---

## Self-Review

1. **Cobertura de la spec (sección "Escritura al publicar" + "Modelo de datos"):** drop de columnas/tabla → Task 1; passthrough + composición sugerida → Task 2; `@Transient` + remoción de persistencia + builders → Task 3; transitorios en export (null=omitir en lote) → Task 4. ✅
2. **Placeholders:** los pasos muestran código real o ediciones exactas con número de línea de referencia. El Step 11/Step 5 de "arreglar tests" describe el patrón (records posicionales) con los archivos concretos a tocar; no es un placeholder de implementación de producción.
3. **Consistencia de tipos:** `MlAtributoDTO(attributeId, valueId, valueName, noAplica)` (Plan 1) usado en entidad transitoria, `MlItemPayloadBuilder`, `faltantesRequeridos`, `MlExportRequestDTO`. `Producto.getDescripcionMl()/getDescripcionNube()/getMlCategoryId()/getMlAtributos()` consistentes entre entidad (Task 3 Step 1) y consumidores (Steps 3-7, Task 4).
4. **Compilabilidad por task:** Task 3 es atómica (cambio de tipo amplio) y se verifica/commitea una sola vez; las demás compilan al cierre.
5. **Interfaz con Plan 3:** endpoints `GET /descripcion-sugerida?canal=ml|nube`; request de export ampliados (ML: +mlCategoryId/+mlAtributos/+descripcionMl; Nube DestinoNube: +descripcion).
