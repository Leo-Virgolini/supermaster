# Dux: payload enriquecido — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enriquecer el payload de exportación a Dux (`/item/nuevoItem`, upsert por `cod_item`) con rubro, subrubro, marca, proveedor, unidad de medida y flags, al crear y editar productos; más los datos de mapeo a Dux que faltan y un ajuste visual de los checkboxes de canal.

**Architecture:** Se agrega un catálogo `UnidadMedida` (IDs iguales a Dux) y campos de mapeo en `Marca` (`codigoDux`), `ClasifGral`/`ClasifGastro` (`idDux`) y `Producto` (relación a `UnidadMedida`). El armado del payload se extrae a un builder testeable y a un resolver de jerarquía rubro/subrubro; corre dentro de una transacción readOnly (por LAZY + OSIV off) y el POST a Dux queda fuera de la transacción.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven / Spring Data JPA / MapStruct / JUnit5 + AssertJ (backend). Next.js / React / TypeScript / Tailwind (frontend). MySQL schema `supermaster`, `ddl-auto=validate`.

## Global Constraints

- Schema BD `supermaster`; `ddl-auto=validate` → cada cambio de columna/tabla requiere su script SQL manual en `supermaster-backend/src/main/resources/db/`. Ningún `id` de catálogo de Dux es autogenerado.
- Convención SQL: tablas plural snake_case; PK `id_<entidad>`; FK `fk_<tabla>_<ref>`; índices `idx_<tabla>_<campos>`.
- OSIV apagado (`open-in-view=false`): tocar asociaciones LAZY (incluido `getPadre()` recursivo de las clasificaciones) debe ocurrir dentro de `@Transactional`. El POST HTTP a Dux NO debe ejecutarse dentro de la transacción.
- API REST: base `/api`, listados con `page`/`size`/`search`, errores `{ "message", "path" }`, status estándar (200/201/204/400/404/409).
- MapStruct: `GlobalMapperConfig` no se modifica. Para updates parciales usar `updateEntityFromDTO(dto, @MappingTarget entity)`.
- Mensajes de commit en español, terminando con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Mapeo Dux (verbatim del spec): `cod_item`←sku, `item`←tituloDux, `tipo_producto`←esCombo?COMBO:SIMPLE, `id_moneda`=1, `porc_iva`←iva, `costo`←costo, `id_proveedor`←proveedor.id, `id_rubro`←idDux clasif nivel1, `id_sub_rubro`←idDux clasif nivel2, `codigo_marca`←marca.codigoDux, `id_unidad_medida`←unidadMedida.id, `stockeable`="S", `acepta_stock_negativo`="S", `habilitado`←activo?S:N, `trazable`="S", `disponible_para`="todos", `indica_ctd_bultos`="S", `descripcion`←tituloNube. Quitar `codigo_externo` y `ctd_unidades_por_bulto`. Todo campo con origen `null`/blank se OMITE del JSON.
- Regla rubro/subrubro: si el producto tiene `clasifGral` y `clasifGastro` → usar `clasifGral`; si tiene solo una → esa; si ninguna → omitir rubro y subrubro. Desde el nodo del producto, subir por `getPadre()` hasta la raíz: rubro = raíz (nivel 1), subrubro = nodo de nivel 2 en la cadena (omitir si el nodo es la raíz). Si un `idDux` involucrado es null → omitir ese campo.
- Fuera de alcance: `stock_matricial`, `cod_barra`, `fecha_vencimiento`, `precios`, y la actualización en Nube/ML (Spec A).

---

## File Structure

**Backend (nuevos):**
- `dominio/unidad_medida/entity/UnidadMedida.java` — entidad catálogo (id = id Dux, codigo).
- `dominio/unidad_medida/repository/UnidadMedidaRepository.java`
- `dominio/unidad_medida/dto/UnidadMedidaDTO.java`
- `dominio/unidad_medida/controller/UnidadMedidaController.java` — solo GET listado (catálogo fijo).
- `apis/dux/service/DuxClasifResolver.java` — resuelve (idRubro, idSubRubro) de un Producto.
- `apis/dux/service/DuxItemBuilder.java` — arma el `Map<String,Object>` de un Producto.
- `src/main/resources/db/unidad-medida.sql` — crea `unidades_medida` + seed.
- `src/main/resources/db/dux-mapping-fields.sql` — `productos.id_unidad_medida`, `marcas.codigo_dux`, `clasif_gral.id_dux`, `clasif_gastro.id_dux`.

**Backend (modificados):**
- `dominio/producto/entity/Producto.java` — relación `unidadMedida`.
- `dominio/marca/entity/Marca.java` — `codigoDux`.
- `dominio/clasif_gral/entity/ClasifGral.java` — `idDux`.
- `dominio/clasif_gastro/entity/ClasifGastro.java` — `idDux`.
- DTOs y mappers de producto/marca/clasif (exponer los campos nuevos).
- `apis/dux/service/DuxService.java` — usar builder + resolver con separación tx/HTTP.

**Frontend (modificados):**
- `src/app/productos/page.tsx` — select unidad de medida + payload + iconos/colores de canal.
- `src/app/productos/productosService.ts` — `searchUnidadesMedida`.
- `src/app/productos/types.ts` — `unidadMedidaId`.
- `src/app/marcas/` — input `codigoDux` (page + service + types).
- `src/app/clasificaciones/` y `src/app/clasif-gastro/` — input `idDux` (page + service + types).

**Tests (nuevos):**
- `src/test/java/.../apis/dux/DuxClasifResolverTest.java`
- `src/test/java/.../apis/dux/DuxItemBuilderTest.java`

---

### Task 1: Catálogo `UnidadMedida` + endpoint de listado + migración

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/unidad_medida/entity/UnidadMedida.java`
- Create: `.../dominio/unidad_medida/repository/UnidadMedidaRepository.java`
- Create: `.../dominio/unidad_medida/dto/UnidadMedidaDTO.java`
- Create: `.../dominio/unidad_medida/controller/UnidadMedidaController.java`
- Create: `supermaster-backend/src/main/resources/db/unidad-medida.sql`

**Interfaces:**
- Produces: entidad `UnidadMedida` con `Integer getId()` / `String getCodigo()`; `GET /api/unidades-medida?page&size&search` → `Page<UnidadMedidaDTO>` con `UnidadMedidaDTO(Integer id, String codigo)`.

**Contexto:** patrón de catálogo = `Material` (`dominio/material/...`), pero con dos diferencias: el `id` NO es autogenerado (coincide con Dux) y el campo de texto se llama `codigo` (no `nombre`). El catálogo es fijo (poblado por seed); solo se necesita el GET de listado para el `<select>` del front.

- [ ] **Step 1: Script SQL — crear tabla + seed**

Crear `supermaster-backend/src/main/resources/db/unidad-medida.sql`:

```sql
-- Catálogo de unidades de medida de Dux. El id DEBE coincidir con el id real
-- de Dux (no autogenerado). Los ids 1..22 de abajo son PROVISORIOS: reemplazar
-- por los ids reales de Dux antes de exportar a producción.
CREATE TABLE IF NOT EXISTS supermaster.unidades_medida (
    id_unidad_medida INT          NOT NULL PRIMARY KEY,
    codigo           VARCHAR(20)  NOT NULL,
    CONSTRAINT uk_unidades_medida_codigo UNIQUE (codigo)
);

INSERT INTO supermaster.unidades_medida (id_unidad_medida, codigo) VALUES
    (1,'T1'),(2,'T3'),(3,'J2-C'),(4,'J2-F'),(5,'J1-D'),(6,'J3-D'),(7,'J3-B'),
    (8,'J2-H'),(9,'J2-G'),(10,'T2'),(11,'J1-E'),(12,'J2-D'),(13,'J2-B'),
    (14,'J1-B'),(15,'J2-I'),(16,'J3-C'),(17,'J3-A'),(18,'COMBOS'),(19,'J2-A'),
    (20,'J1-C'),(21,'J1-A'),(22,'J2-E');
```

> NOTA AL IMPLEMENTADOR: dejar el comentario sobre los ids provisorios. El usuario reemplazará los ids por los reales de Dux.

- [ ] **Step 2: Entidad**

Crear `UnidadMedida.java` (copiar estilo de `Material.java`, SIN `@GeneratedValue`):

```java
package ar.com.leo.super_master_backend.dominio.unidad_medida.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "unidades_medida", schema = "supermaster")
public class UnidadMedida {

    @Id
    @Column(name = "id_unidad_medida", nullable = false)
    private Integer id;

    @Size(max = 20)
    @NotNull
    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    public UnidadMedida(Integer id) {
        this.id = id;
    }
}
```

- [ ] **Step 3: Repository**

```java
package ar.com.leo.super_master_backend.dominio.unidad_medida.repository;

import ar.com.leo.super_master_backend.dominio.unidad_medida.entity.UnidadMedida;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Integer> {
    Page<UnidadMedida> findByCodigoContainingIgnoreCase(String texto, Pageable pageable);
}
```

- [ ] **Step 4: DTO**

```java
package ar.com.leo.super_master_backend.dominio.unidad_medida.dto;

public record UnidadMedidaDTO(Integer id, String codigo) {}
```

- [ ] **Step 5: Controller (solo GET listado)**

```java
package ar.com.leo.super_master_backend.dominio.unidad_medida.controller;

import ar.com.leo.super_master_backend.dominio.unidad_medida.dto.UnidadMedidaDTO;
import ar.com.leo.super_master_backend.dominio.unidad_medida.repository.UnidadMedidaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/unidades-medida")
public class UnidadMedidaController {

    private final UnidadMedidaRepository repository;

    public UnidadMedidaController(UnidadMedidaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<UnidadMedidaDTO> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        var pageable = PageRequest.of(page, size, Sort.by("codigo").ascending());
        var resultado = search.isBlank()
                ? repository.findAll(pageable)
                : repository.findByCodigoContainingIgnoreCase(search, pageable);
        return resultado.map(u -> new UnidadMedidaDTO(u.getId(), u.getCodigo()));
    }
}
```

- [ ] **Step 6: Aplicar el SQL y compilar**

Aplicar `unidad-medida.sql` en la BD local (ver memoria "Acceso BD MySQL local"). Luego:

Run: `cd supermaster-backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS (la entidad valida contra la tabla nueva).

- [ ] **Step 7: Verificar el endpoint (smoke)**

Con el backend levantado (`./mvnw spring-boot:run`, perfil dev), verificar:

Run: `curl "http://localhost:8080/api/unidades-medida?size=5"`
Expected: JSON `Page` con `content` de unidades (ej. `{"id":18,"codigo":"COMBOS"}` entre ellas).

- [ ] **Step 8: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/unidad_medida supermaster-backend/src/main/resources/db/unidad-medida.sql
git commit -m "feat(dux): catalogo UnidadMedida (ids de Dux) + endpoint de listado"
```

---

### Task 2: Relación `Producto.unidadMedida` + DTOs + mapper

**Files:**
- Modify: `.../dominio/producto/entity/Producto.java` (zona de relaciones ManyToOne, ~líneas 85-111)
- Modify: `.../dominio/producto/dto/ProductoDTO.java`
- Modify: `.../dominio/producto/dto/ProductoConPreciosDTO.java` (si el front de edición lo consume; agregar `unidadMedidaId`)
- Modify: el `ProductoCreateDTO` y `ProductoUpdateDTO`/`ProductoPatchDTO` del producto
- Modify: `.../dominio/producto/mapper/ProductoMapper.java`
- Modify: `supermaster-backend/src/main/resources/db/dux-mapping-fields.sql` (se crea acá, se completa en Tasks 3 y 4)

**Interfaces:**
- Consumes: `UnidadMedida` (Task 1).
- Produces: `Producto.getUnidadMedida()`/`setUnidadMedida(UnidadMedida)`; campo `Integer unidadMedidaId` en los DTOs de producto (lectura y create/update).

**Contexto:** las relaciones del producto siguen el patrón LAZY + `@JoinColumn`. En `ProductoMapper`, `marca` es opcional vía `@Mapping(target="marca", expression="java(dto.marcaId() != null ? new Marca(dto.marcaId()) : null)")` y en `toDTO` se extrae `marcaId` de la entidad. Replicar EXACTAMENTE ese patrón para `unidadMedida` (es opcional).

- [ ] **Step 1: Script SQL — columna en productos**

Crear `supermaster-backend/src/main/resources/db/dux-mapping-fields.sql` con (de momento) la columna del producto:

```sql
-- Campos de mapeo a Dux.
ALTER TABLE supermaster.productos
    ADD COLUMN id_unidad_medida INT NULL,
    ADD CONSTRAINT fk_productos_unidad_medida
        FOREIGN KEY (id_unidad_medida) REFERENCES supermaster.unidades_medida (id_unidad_medida);
```

Aplicar el SQL en la BD local.

- [ ] **Step 2: Relación en la entidad Producto**

En `Producto.java`, junto a las otras relaciones (`marca`, `clasifGral`, …), agregar:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "id_unidad_medida")
private UnidadMedida unidadMedida;
```

Agregar el import `import ar.com.leo.super_master_backend.dominio.unidad_medida.entity.UnidadMedida;`.

- [ ] **Step 3: DTOs de producto**

En `ProductoDTO` agregar el campo `Integer unidadMedidaId` (junto a `marcaId`, `tipoId`, etc.). En el `ProductoConPreciosDTO` agregar lo mismo si el front de edición consume ese DTO. En `ProductoCreateDTO` y `ProductoUpdateDTO`/`ProductoPatchDTO` agregar `Integer unidadMedidaId` como **opcional** (nullable, sin `@NotNull`).

- [ ] **Step 4: Mapper de producto**

En `ProductoMapper`:
- Import `UnidadMedida`.
- En `toDTO(...)`: mapear `unidadMedidaId` desde `producto.getUnidadMedida() != null ? producto.getUnidadMedida().getId() : null` (seguir el mismo mecanismo que `marcaId`).
- En `toEntity(ProductoCreateDTO dto)`: agregar `@Mapping(target = "unidadMedida", expression = "java(dto.unidadMedidaId() != null ? new UnidadMedida(dto.unidadMedidaId()) : null)")`.
- En `updateEntityFromDTO(...)`: mismo `@Mapping` para `unidadMedida` que en `toEntity`, para que el update lo refleje (seguir cómo está resuelto `marca` en el update).

- [ ] **Step 5: Compilar**

Run: `cd supermaster-backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS (MapStruct regenera los mappers sin error; la entidad valida contra la columna nueva).

- [ ] **Step 6: Verificar persistencia (smoke)**

Con el backend levantado, hacer un PATCH a un producto seteando `unidadMedidaId` y un GET para confirmar que vuelve:

Run: `curl -X PATCH "http://localhost:8080/api/productos/<ID>" -H "Content-Type: application/json" -H "X-Audit-Origin: API" -d '{"unidadMedidaId":18}'`
Expected: 200; un GET del producto devuelve `"unidadMedidaId":18`.

- [ ] **Step 7: Commit**

```bash
git add supermaster-backend/src/main/java/.../dominio/producto supermaster-backend/src/main/resources/db/dux-mapping-fields.sql
git commit -m "feat(dux): relacion Producto.unidadMedida + exposicion en DTOs"
```

---

### Task 3: `Marca.codigoDux`

**Files:**
- Modify: `.../dominio/marca/entity/Marca.java`
- Modify: DTOs de marca (`MarcaDTO`, create/update/patch correspondientes)
- Modify: mapper de marca
- Modify: `supermaster-backend/src/main/resources/db/dux-mapping-fields.sql` (añadir el ALTER de marcas)

**Interfaces:**
- Produces: `Marca.getCodigoDux()`/`setCodigoDux(String)`; `codigoDux` en `MarcaDTO` y en los DTOs de create/update/patch de marca.

**Contexto:** `Marca` ya tiene `nombre` y jerarquía `padre`. `codigoDux` es un String opcional. Seguir el patrón de campos opcionales de marca (mirar cómo se exponen `nombre`/`padreId` en su DTO/mapper y replicar para `codigoDux`).

- [ ] **Step 1: Script SQL**

Añadir a `dux-mapping-fields.sql`:

```sql
ALTER TABLE supermaster.marcas
    ADD COLUMN codigo_dux VARCHAR(45) NULL;
```

Aplicar en la BD local.

- [ ] **Step 2: Campo en la entidad**

En `Marca.java` agregar:

```java
@Size(max = 45)
@Column(name = "codigo_dux", length = 45)
private String codigoDux;
```

- [ ] **Step 3: DTOs + mapper de marca**

Agregar `String codigoDux` (opcional) al `MarcaDTO` (lectura) y a los DTOs de create/update/patch de marca. En el mapper de marca, mapear `codigoDux` en `toDTO` y en `toEntity`/`updateEntityFromDTO` (seguir cómo se trata `nombre`). Para el PATCH (si usa `JsonNullable`), seguir el patrón de los demás campos.

- [ ] **Step 4: Compilar**

Run: `cd supermaster-backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Verificar (smoke)**

Run: `curl -X PATCH "http://localhost:8080/api/marcas/<ID>" -H "Content-Type: application/json" -H "X-Audit-Origin: API" -d '{"codigoDux":"ABC123"}'`
Expected: 200; GET de la marca devuelve `"codigoDux":"ABC123"`.

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/.../dominio/marca supermaster-backend/src/main/resources/db/dux-mapping-fields.sql
git commit -m "feat(dux): Marca.codigoDux para mapear codigo_marca de Dux"
```

---

### Task 4: `ClasifGral.idDux` + `ClasifGastro.idDux`

**Files:**
- Modify: `.../dominio/clasif_gral/entity/ClasifGral.java`
- Modify: `.../dominio/clasif_gastro/entity/ClasifGastro.java`
- Modify: DTOs y mappers de ambas clasificaciones
- Modify: `dux-mapping-fields.sql` (ALTER de ambas tablas)

**Interfaces:**
- Produces: `ClasifGral.getIdDux()`/`setIdDux(Integer)` y `ClasifGastro.getIdDux()`/`setIdDux(Integer)`; `idDux` en sus DTOs (lectura + create/update/patch).

**Contexto:** ambas entidades tienen `id`, `nombre`, jerarquía `padre` (LAZY). `idDux` es un `Integer` opcional. Replicar para las dos.

- [ ] **Step 1: Script SQL**

Añadir a `dux-mapping-fields.sql`:

```sql
ALTER TABLE supermaster.clasif_gral
    ADD COLUMN id_dux INT NULL;

ALTER TABLE supermaster.clasif_gastro
    ADD COLUMN id_dux INT NULL;
```

Aplicar en la BD local.

- [ ] **Step 2: Campo en ambas entidades**

En `ClasifGral.java` y `ClasifGastro.java` agregar:

```java
@Column(name = "id_dux")
private Integer idDux;
```

- [ ] **Step 3: DTOs + mappers**

Agregar `Integer idDux` (opcional) a los DTO de lectura y create/update/patch de clasif general y gastro. Mapear en `toDTO`/`toEntity`/`updateEntityFromDTO` siguiendo el tratamiento de `nombre`.

- [ ] **Step 4: Compilar**

Run: `cd supermaster-backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Verificar (smoke)**

Run: `curl -X PATCH "http://localhost:8080/api/clasif-gral/<ID>" -H "Content-Type: application/json" -H "X-Audit-Origin: API" -d '{"idDux":501}'`
Expected: 200; GET devuelve `"idDux":501`. Repetir contra `/api/clasif-gastro/<ID>`.

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/.../dominio/clasif_gral supermaster-backend/src/main/java/.../dominio/clasif_gastro supermaster-backend/src/main/resources/db/dux-mapping-fields.sql
git commit -m "feat(dux): idDux en ClasifGral y ClasifGastro para mapear id_rubro/id_sub_rubro"
```

---

### Task 5: Resolver de rubro/subrubro (`DuxClasifResolver`) — TDD

**Files:**
- Create: `.../apis/dux/service/DuxClasifResolver.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/dux/DuxClasifResolverTest.java`

**Interfaces:**
- Consumes: `Producto.getClasifGral()`, `getClasifGastro()`; `ClasifGral`/`ClasifGastro` con `getPadre()` y `getIdDux()` (Task 4).
- Produces: `DuxClasifResolver.resolver(Producto) → DuxRubro` donde `record DuxRubro(Integer idRubro, Integer idSubRubro)` (cualquiera de los dos puede ser `null`).

**Contexto:** `ClasifGral` y `ClasifGastro` son dos tipos distintos (no comparten interfaz). Para no duplicar la lógica de ascsenso, el resolver primero convierte el nodo elegido a una cadena raíz→…→nodo de `Integer idDux` usando funciones de acceso genéricas. Implementación concreta abajo. El `getPadre()` es LAZY; los tests construyen las entidades en memoria (sin proxy), y en producción el resolver se invoca dentro de la transacción readOnly (Task 6).

- [ ] **Step 1: Test que falla**

Crear `DuxClasifResolverTest.java`:

```java
package ar.com.leo.super_master_backend.apis.dux;

import ar.com.leo.super_master_backend.apis.dux.service.DuxClasifResolver;
import ar.com.leo.super_master_backend.apis.dux.service.DuxClasifResolver.DuxRubro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuxClasifResolverTest {

    private ClasifGral gral(Integer idDux, ClasifGral padre) {
        ClasifGral c = new ClasifGral();
        c.setIdDux(idDux);
        c.setPadre(padre);
        return c;
    }

    private ClasifGastro gastro(Integer idDux, ClasifGastro padre) {
        ClasifGastro c = new ClasifGastro();
        c.setIdDux(idDux);
        c.setPadre(padre);
        return c;
    }

    @Test
    void nodoNivel3_rubroEsRaiz_subrubroEsNivel2() {
        ClasifGral raiz = gral(10, null);
        ClasifGral nivel2 = gral(20, raiz);
        ClasifGral nivel3 = gral(30, nivel2);
        Producto p = new Producto();
        p.setClasifGral(nivel3);

        DuxRubro r = new DuxClasifResolver().resolver(p);

        assertThat(r.idRubro()).isEqualTo(10);
        assertThat(r.idSubRubro()).isEqualTo(20);
    }

    @Test
    void nodoNivel1_sinSubrubro() {
        Producto p = new Producto();
        p.setClasifGral(gral(10, null));

        DuxRubro r = new DuxClasifResolver().resolver(p);

        assertThat(r.idRubro()).isEqualTo(10);
        assertThat(r.idSubRubro()).isNull();
    }

    @Test
    void ambasClasif_usaGeneral() {
        Producto p = new Producto();
        p.setClasifGral(gral(10, null));
        p.setClasifGastro(gastro(99, null));

        DuxRubro r = new DuxClasifResolver().resolver(p);

        assertThat(r.idRubro()).isEqualTo(10);
    }

    @Test
    void soloGastro_usaGastro() {
        ClasifGastro raiz = gastro(99, null);
        ClasifGastro nivel2 = gastro(98, raiz);
        Producto p = new Producto();
        p.setClasifGastro(nivel2);

        DuxRubro r = new DuxClasifResolver().resolver(p);

        assertThat(r.idRubro()).isEqualTo(99);
        assertThat(r.idSubRubro()).isEqualTo(98);
    }

    @Test
    void sinClasif_ambosNull() {
        DuxRubro r = new DuxClasifResolver().resolver(new Producto());
        assertThat(r.idRubro()).isNull();
        assertThat(r.idSubRubro()).isNull();
    }
}
```

- [ ] **Step 2: Correr el test (falla)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=DuxClasifResolverTest test`
Expected: FAIL (no compila: `DuxClasifResolver` no existe).

- [ ] **Step 3: Implementar el resolver**

Crear `DuxClasifResolver.java`:

```java
package ar.com.leo.super_master_backend.apis.dux.service;

import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Resuelve id_rubro / id_sub_rubro de Dux a partir de la clasificación del producto. */
@Component
public class DuxClasifResolver {

    public record DuxRubro(Integer idRubro, Integer idSubRubro) {}

    public DuxRubro resolver(Producto producto) {
        // Selección de la clasificación fuente: ambas -> general; solo una -> esa.
        if (producto.getClasifGral() != null) {
            return desdeCadena(cadena(producto.getClasifGral(), ClasifGral::getPadre, ClasifGral::getIdDux));
        }
        if (producto.getClasifGastro() != null) {
            return desdeCadena(cadena(producto.getClasifGastro(), ClasifGastro::getPadre, ClasifGastro::getIdDux));
        }
        return new DuxRubro(null, null);
    }

    /** Construye la cadena raíz -> ... -> nodo, como lista de idDux. */
    private <T> List<Integer> cadena(T nodo, Function<T, T> getPadre, Function<T, Integer> getIdDux) {
        List<Integer> desdeNodo = new ArrayList<>();
        T actual = nodo;
        while (actual != null) {
            desdeNodo.add(getIdDux.apply(actual));
            actual = getPadre.apply(actual);
        }
        // desdeNodo = [nodo, ..., raiz]; invertir para [raiz, ..., nodo].
        List<Integer> raizANodo = new ArrayList<>(desdeNodo);
        java.util.Collections.reverse(raizANodo);
        return raizANodo;
    }

    private DuxRubro desdeCadena(List<Integer> raizANodo) {
        Integer idRubro = raizANodo.isEmpty() ? null : raizANodo.get(0);
        Integer idSubRubro = raizANodo.size() >= 2 ? raizANodo.get(1) : null;
        return new DuxRubro(idRubro, idSubRubro);
    }
}
```

- [ ] **Step 4: Correr el test (pasa)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=DuxClasifResolverTest test`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/.../apis/dux/service/DuxClasifResolver.java supermaster-backend/src/test/java/.../apis/dux/DuxClasifResolverTest.java
git commit -m "feat(dux): resolver de rubro/subrubro desde la jerarquia de clasificacion"
```

---

### Task 6: `DuxItemBuilder` + integración en `DuxService` (tx/HTTP separadas) — TDD

**Files:**
- Create: `.../apis/dux/service/DuxItemBuilder.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/dux/DuxItemBuilderTest.java`
- Modify: `.../apis/dux/service/DuxService.java` (`exportarProductosADux` + nuevo método tx de carga/armado)

**Interfaces:**
- Consumes: `DuxClasifResolver.resolver(Producto) → DuxRubro` (Task 5); `Producto` getters; `Marca.getCodigoDux()`, `Proveedor.getId()`, `UnidadMedida.getId()`.
- Produces: `DuxItemBuilder.construir(Producto) → Map<String,Object>` (el objeto JSON de un producto para Dux).

**Contexto:** hoy el armado del `itemDux` está inline en `DuxService.exportarProductosADux` (líneas ~1288-1315). Se extrae a `DuxItemBuilder` (testeable en memoria). El builder usa el resolver para rubro/subrubro y aplica la regla de omisión de nulls. Luego `DuxService` arma la lista DENTRO de un método `@Transactional(readOnly = true)` (resuelve LAZY: marca, proveedor, clasif + padres, unidadMedida) y el POST a Dux queda FUERA de la transacción.

- [ ] **Step 1: Test que falla**

Crear `DuxItemBuilderTest.java`:

```java
package ar.com.leo.super_master_backend.apis.dux;

import ar.com.leo.super_master_backend.apis.dux.service.DuxClasifResolver;
import ar.com.leo.super_master_backend.apis.dux.service.DuxItemBuilder;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.unidad_medida.entity.UnidadMedida;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DuxItemBuilderTest {

    private DuxItemBuilder builder() {
        return new DuxItemBuilder(new DuxClasifResolver());
    }

    private Producto productoCompleto() {
        Producto p = new Producto();
        p.setSku("1234567");
        p.setTituloDux("OLLA ACERO 24CM");
        p.setEsCombo(false);
        p.setActivo(true);
        p.setCosto(new BigDecimal("1000.50"));
        p.setIva(new BigDecimal("21.000"));
        p.setTituloNube("Olla de acero inoxidable 24cm");

        Marca marca = new Marca();
        marca.setCodigoDux("MARCA-1");
        p.setMarca(marca);

        Proveedor prov = new Proveedor();
        prov.setId(77);
        p.setProveedor(prov);

        ClasifGral raiz = new ClasifGral(); raiz.setIdDux(10);
        ClasifGral nivel2 = new ClasifGral(); nivel2.setIdDux(20); nivel2.setPadre(raiz);
        p.setClasifGral(nivel2);

        UnidadMedida um = new UnidadMedida(18);
        p.setUnidadMedida(um);
        return p;
    }

    @Test
    void construir_mapeaTodosLosCampos() {
        Map<String, Object> m = builder().construir(productoCompleto());

        assertThat(m).containsEntry("cod_item", "1234567");
        assertThat(m).containsEntry("item", "OLLA ACERO 24CM");
        assertThat(m).containsEntry("tipo_producto", "SIMPLE");
        assertThat(m).containsEntry("id_moneda", 1);
        assertThat(m).containsEntry("porc_iva", 21.0);
        assertThat(m).containsEntry("costo", 1000.5);
        assertThat(m).containsEntry("id_proveedor", 77);
        assertThat(m).containsEntry("id_rubro", 10);
        assertThat(m).containsEntry("id_sub_rubro", 20);
        assertThat(m).containsEntry("codigo_marca", "MARCA-1");
        assertThat(m).containsEntry("id_unidad_medida", 18);
        assertThat(m).containsEntry("stockeable", "S");
        assertThat(m).containsEntry("acepta_stock_negativo", "S");
        assertThat(m).containsEntry("habilitado", "S");
        assertThat(m).containsEntry("trazable", "S");
        assertThat(m).containsEntry("disponible_para", "todos");
        assertThat(m).containsEntry("indica_ctd_bultos", "S");
        assertThat(m).containsEntry("descripcion", "Olla de acero inoxidable 24cm");
    }

    @Test
    void construir_noIncluyeCamposViejosNiNulos() {
        Producto p = new Producto();
        p.setSku("9999999");
        p.setTituloDux("X");
        p.setEsCombo(true);
        p.setActivo(false);
        // sin costo, iva, marca, proveedor, clasif, unidad, tituloNube

        Map<String, Object> m = builder().construir(p);

        assertThat(m).containsEntry("cod_item", "9999999");
        assertThat(m).containsEntry("tipo_producto", "COMBO");
        assertThat(m).containsEntry("habilitado", "N");
        // campos eliminados del payload viejo:
        assertThat(m).doesNotContainKey("codigo_externo");
        assertThat(m).doesNotContainKey("ctd_unidades_por_bulto");
        // omitidos por null:
        assertThat(m).doesNotContainKey("costo");
        assertThat(m).doesNotContainKey("porc_iva");
        assertThat(m).doesNotContainKey("id_proveedor");
        assertThat(m).doesNotContainKey("id_rubro");
        assertThat(m).doesNotContainKey("id_sub_rubro");
        assertThat(m).doesNotContainKey("codigo_marca");
        assertThat(m).doesNotContainKey("id_unidad_medida");
        assertThat(m).doesNotContainKey("descripcion");
    }
}
```

- [ ] **Step 2: Correr el test (falla)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=DuxItemBuilderTest test`
Expected: FAIL (no compila: `DuxItemBuilder` no existe).

- [ ] **Step 3: Implementar el builder**

Crear `DuxItemBuilder.java`:

```java
package ar.com.leo.super_master_backend.apis.dux.service;

import ar.com.leo.super_master_backend.apis.dux.service.DuxClasifResolver.DuxRubro;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Arma el objeto JSON de un producto para POST /item/nuevoItem de Dux. */
@Component
public class DuxItemBuilder {

    private final DuxClasifResolver clasifResolver;

    public DuxItemBuilder(DuxClasifResolver clasifResolver) {
        this.clasifResolver = clasifResolver;
    }

    public Map<String, Object> construir(Producto p) {
        Map<String, Object> item = new HashMap<>();

        // Requeridos / fijos
        item.put("cod_item", p.getSku());
        item.put("item", p.getTituloDux() != null ? p.getTituloDux() : "");
        item.put("tipo_producto", Boolean.TRUE.equals(p.getEsCombo()) ? "COMBO" : "SIMPLE");
        item.put("id_moneda", 1);
        item.put("habilitado", Boolean.TRUE.equals(p.getActivo()) ? "S" : "N");
        item.put("stockeable", "S");
        item.put("acepta_stock_negativo", "S");
        item.put("trazable", "S");
        item.put("disponible_para", "todos");
        item.put("indica_ctd_bultos", "S");

        // Opcionales (se omiten si null/blank)
        if (p.getIva() != null) item.put("porc_iva", p.getIva().doubleValue());
        if (p.getCosto() != null) item.put("costo", p.getCosto().doubleValue());
        if (p.getProveedor() != null && p.getProveedor().getId() != null) {
            item.put("id_proveedor", p.getProveedor().getId());
        }
        if (p.getMarca() != null && p.getMarca().getCodigoDux() != null && !p.getMarca().getCodigoDux().isBlank()) {
            item.put("codigo_marca", p.getMarca().getCodigoDux());
        }
        if (p.getUnidadMedida() != null && p.getUnidadMedida().getId() != null) {
            item.put("id_unidad_medida", p.getUnidadMedida().getId());
        }
        if (p.getTituloNube() != null && !p.getTituloNube().isBlank()) {
            item.put("descripcion", p.getTituloNube());
        }

        DuxRubro rubro = clasifResolver.resolver(p);
        if (rubro.idRubro() != null) item.put("id_rubro", rubro.idRubro());
        if (rubro.idSubRubro() != null) item.put("id_sub_rubro", rubro.idSubRubro());

        return item;
    }
}
```

- [ ] **Step 4: Correr el test (pasa)**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=DuxItemBuilderTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Integrar en `DuxService` con tx/HTTP separadas**

En `DuxService`:
- Inyectar `DuxItemBuilder` (constructor/field, como las demás dependencias).
- Crear un método nuevo que cargue y arme la lista DENTRO de transacción readOnly (resuelve LAZY). Llamarlo vía `self.` para que el proxy aplique la transacción:

```java
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public List<Map<String, Object>> cargarYArmarItemsDux(List<String> skus, List<String> errores) {
    List<Producto> productos = cargarProductosParaExportacion(skus, errores);
    List<Map<String, Object>> items = new ArrayList<>();
    for (Producto producto : productos) {
        try {
            items.add(duxItemBuilder.construir(producto));
        } catch (Exception e) {
            errores.add("SKU " + producto.getSku() + ": Error mapeando - " + e.getMessage());
            log.warn("Error mapeando producto {} para DUX: {}", producto.getSku(), e.getMessage());
        }
    }
    return items;
}
```

- Reescribir `exportarProductosADux` para: llamar `self.cargarYArmarItemsDux(skus, errores)`, y dejar la serialización + `retryHandler.postJson("/item/nuevoItem", ...)` + parseo del idProceso FUERA de la transacción (como ya está, pero usando la lista que devuelve el método nuevo). Eliminar el armado inline anterior (el bloque del `itemDux`).

```java
public ExportDuxResultDTO exportarProductosADux(List<String> skus) {
    log.info("Iniciando exportación de productos a DUX...");
    List<String> errores = new ArrayList<>();

    List<Map<String, Object>> productosJson = self.cargarYArmarItemsDux(skus, errores);

    if (productosJson.isEmpty()) {
        log.warn("DUX Export - No hay productos válidos para enviar");
        return new ExportDuxResultDTO(0, 0, errores);
    }

    verificarTokens();

    String jsonBody;
    try {
        jsonBody = objectMapper.writeValueAsString(Map.of("productos", productosJson));
    } catch (Exception e) {
        log.error("DUX Export - Error serializando productos", e);
        errores.add("Error preparando datos para DUX: " + e.getMessage());
        return new ExportDuxResultDTO(0, 0, errores);
    }

    String response = retryHandler.postJson("/item/nuevoItem", tokens.token, jsonBody);

    int idProceso = 0;
    if (response != null) {
        Pattern pattern = Pattern.compile("ID de proceso:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            idProceso = Integer.parseInt(matcher.group(1));
            log.info("DUX Export - Proceso iniciado con ID: {}", idProceso);
        } else {
            log.warn("DUX Export - No se encontró ID de proceso en respuesta: {}", response);
        }
    } else {
        errores.add("No se recibió respuesta de DUX");
    }

    log.info("DUX Export - {} productos enviados, proceso ID: {}, {} errores",
            productosJson.size(), idProceso, errores.size());
    return new ExportDuxResultDTO(productosJson.size(), idProceso, errores);
}
```

> NOTA: `DuxService` ya tiene self-proxy (`@Lazy @Autowired private DuxService self;`). Usar `self.cargarYArmarItemsDux(...)` es obligatorio para que `@Transactional` aplique (una llamada interna directa lo saltearía).

- [ ] **Step 6: Compilar + correr los tests de dux**

Run: `cd supermaster-backend && ./mvnw -q -Dtest=DuxItemBuilderTest,DuxClasifResolverTest test`
Expected: PASS. Luego `./mvnw -q -DskipTests compile` → BUILD SUCCESS.

- [ ] **Step 7: Verificar export (smoke, opcional con backend)**

Con un SKU que tenga marca.codigoDux, proveedor.id, clasif.idDux y unidadMedida cargados, disparar la exportación a Dux desde la UI (editar producto, marcar "Actualizar en Dux") y verificar en logs que el payload incluye `id_rubro`, `codigo_marca`, `id_unidad_medida`, etc., y que NO incluye `codigo_externo`/`ctd_unidades_por_bulto`.

- [ ] **Step 8: Commit**

```bash
git add supermaster-backend/src/main/java/.../apis/dux supermaster-backend/src/test/java/.../apis/dux/DuxItemBuilderTest.java
git commit -m "feat(dux): payload enriquecido via DuxItemBuilder con tx/HTTP separadas"
```

---

### Task 7: Frontend — select de unidad de medida en el form de producto

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` (agregar `searchUnidadesMedida`)
- Modify: `supermaster-frontend/src/app/productos/types.ts` (`unidadMedidaId` en `ProductoDTO`, `ProductoCreateDTO`)
- Modify: `supermaster-frontend/src/app/productos/page.tsx` (estado + `<AsyncSelect>` + payload create/patch)

**Interfaces:**
- Consumes: `GET /api/unidades-medida` (Task 1); `unidadMedidaId` en el DTO de producto (Task 2).

**Contexto:** los selects de relación del form usan `<AsyncSelect loadOptions={searchX} value=... onChange=...>`. Los `search*` usan el helper `fetchOptions(endpoint, query, labelKey, size)`. El endpoint de unidades devuelve `{id, codigo}` (no `nombre`), así que se pasa `"codigo"` como `labelKey`.

- [ ] **Step 1: `searchUnidadesMedida` en el service**

En `productosService.ts`, junto a los otros `search*`:

```typescript
export const searchUnidadesMedida = (q: string, size?: number) => fetchOptions("unidades-medida", q, "codigo", size);
```

- [ ] **Step 2: Tipos**

En `types.ts`, agregar `unidadMedidaId: number | null;` a `ProductoDTO` (junto a `materialId`) y a `ProductoCreateDTO`.

- [ ] **Step 3: Estado + carga en edición (page.tsx)**

Agregar estado `const [unidadMedidaId, setUnidadMedidaId] = useState<number | null>(null);` y un `const [unidadMedidaDisplay, setUnidadMedidaDisplay] = useState("");`. En la función que precarga el form al editar un producto (donde se setean `marcaId`, etc.), setear `setUnidadMedidaId(producto.unidadMedidaId ?? null)` y el display con el código (si el DTO lo trae; si no, dejar vacío y que AsyncSelect lo resuelva). Al limpiar/cerrar el form, resetear ambos.

- [ ] **Step 4: `<AsyncSelect>` en el form**

En la sección de atributos del form (cerca del select de Material/Marca), agregar:

```jsx
<div>
    <AsyncSelect
        label="Unidad de medida (Dux)"
        loadOptions={searchUnidadesMedida}
        onChange={(v, label) => { setUnidadMedidaId(v ? Number(v) : null); setUnidadMedidaDisplay(v ? (label ?? "") : ""); }}
        value={unidadMedidaId}
        displayValue={unidadMedidaDisplay}
        placeholder="Buscar unidad (T1, COMBOS, ...)"
        inputClassName={inputBaseClassName}
    />
</div>
```

Importar `searchUnidadesMedida` en page.tsx.

- [ ] **Step 5: Incluir en el payload create y patch**

En el payload de creación (`ProductoCreateDTO`, ~líneas 521-531) y en el patch de edición (~líneas 651-658), agregar `unidadMedidaId`.

- [ ] **Step 6: Typecheck**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: exit 0.

- [ ] **Step 7: Verificar (smoke)**

Abrir el form de un producto, elegir una unidad de medida, guardar; reabrir y confirmar que quedó seleccionada.

- [ ] **Step 8: Commit**

```bash
git add supermaster-frontend/src/app/productos
git commit -m "feat(front/productos): select de unidad de medida (Dux) en alta/edicion"
```

---

### Task 8: Frontend — `codigoDux` en el ABM de marcas

**Files:**
- Modify: `supermaster-frontend/src/app/marcas/types.ts` (`codigoDux`)
- Modify: `supermaster-frontend/src/app/marcas/marcasService.ts` (enviar `codigoDux` en create/update)
- Modify: `supermaster-frontend/src/app/marcas/page.tsx` (input + estado)

**Interfaces:**
- Consumes: `codigoDux` en el DTO de marca (Task 3).

**Contexto:** el form de marca tiene `nombre` + `AsyncSelect` de padre. `createMarcaAPI(nombre, padreId, origin)` y `updateMarcaAPI(id, {nombre?, padreId?}, origin)` mandan JSON. Se agrega `codigoDux` (opcional).

- [ ] **Step 1: Tipo**

En `marcas/types.ts`, agregar `codigoDux?: string | null;` a `MarcaDTO`.

- [ ] **Step 2: Service**

En `marcasService.ts`, extender las firmas para incluir `codigoDux`:
- `createMarcaAPI(nombre, padreId, codigoDux, origin)` → body `{ nombre, padreId, codigoDux }`.
- `updateMarcaAPI(id, data, origin)` ya recibe un objeto `data`; permitir `codigoDux` en `data` (`{ nombre?, padreId?, codigoDux? }`).

- [ ] **Step 3: Form (page.tsx)**

Agregar estado `const [nuevoCodigoDux, setNuevoCodigoDux] = useState("");` y un input de texto debajo del nombre:

```jsx
<label className="block">
    <span className="text-gray-700 text-sm font-bold">Código Dux (opcional)</span>
    <input
        type="text"
        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
        placeholder="Ej: MARCA-001"
        value={nuevoCodigoDux}
        onChange={(e) => setNuevoCodigoDux(e.target.value)}
    />
</label>
```

Pasar `nuevoCodigoDux.trim() || null` al crear/actualizar. En el modo edición, precargar el input con `marca.codigoDux ?? ""`. Resetear al cerrar.

- [ ] **Step 4: Typecheck**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: exit 0.

- [ ] **Step 5: Verificar (smoke)**

Crear/editar una marca con un código Dux; reabrir y confirmar que persiste.

- [ ] **Step 6: Commit**

```bash
git add supermaster-frontend/src/app/marcas
git commit -m "feat(front/marcas): campo codigo Dux en alta/edicion de marca"
```

---

### Task 9: Frontend — `idDux` en los ABM de clasificación (general y gastro)

**Files:**
- Modify: `supermaster-frontend/src/app/clasificaciones/types.ts`, `clasificacionesService.ts`, `page.tsx`
- Modify: `supermaster-frontend/src/app/clasif-gastro/types.ts`, `clasifGastroService.ts`, `page.tsx`

**Interfaces:**
- Consumes: `idDux` en los DTOs de clasif general y gastro (Task 4).

**Contexto:** ambos ABM tienen un form con `nombre` + `AsyncSelect` de padre (gastro además `esMaquina`). Se agrega un input numérico `idDux` (opcional) en cada uno.

- [ ] **Step 1: Tipos**

En `clasificaciones/types.ts` y `clasif-gastro/types.ts`, agregar `idDux?: number | null;` al DTO correspondiente.

- [ ] **Step 2: Services**

En `clasificacionesService.ts` y `clasifGastroService.ts`, incluir `idDux` en el body de create y update (igual que `padreId`: incluir solo si no es null/undefined, o mandarlo directo según el patrón existente del service).

- [ ] **Step 3: Forms (ambas page.tsx)**

Agregar estado `const [nuevoIdDux, setNuevoIdDux] = useState<string>("");` y un input numérico:

```jsx
<label className="block">
    <span className="text-gray-700 text-sm font-bold">ID Dux (opcional)</span>
    <input
        type="number"
        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
        placeholder="Ej: 501"
        value={nuevoIdDux}
        onChange={(e) => setNuevoIdDux(e.target.value)}
    />
</label>
```

Al crear/actualizar, mandar `nuevoIdDux !== "" ? Number(nuevoIdDux) : null`. En edición, precargar con `String(item.idDux ?? "")`. Resetear al cerrar.

- [ ] **Step 4: Typecheck**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: exit 0.

- [ ] **Step 5: Verificar (smoke)**

Cargar un `idDux` en una clasif general y otra gastro; confirmar persistencia al reabrir.

- [ ] **Step 6: Commit**

```bash
git add supermaster-frontend/src/app/clasificaciones supermaster-frontend/src/app/clasif-gastro
git commit -m "feat(front/clasif): campo id Dux en alta/edicion de clasificaciones"
```

---

### Task 10: Frontend — iconos y colores por canal en los checkboxes

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx` (sección "Canales de venta", ~líneas 1113-1150)

**Interfaces:** ninguna (cambio puramente visual).

**Contexto:** los cuatro checkboxes (`subirADux`, `subirKtHogar`, `subirKtGastro`, `subirMl`) usan `checkboxCardClassName` común. Se agrega a cada uno un icono de heroicons y un acento de color distintivo, sin cambiar la lógica ni los labels condicionales ("Subir a …" / "Actualizar en …"). Iconos disponibles en `@heroicons/react/24/outline`.

- [ ] **Step 1: Importar iconos**

En el import de heroicons de page.tsx, asegurar que estén: `CubeIcon` (Dux), `HomeIcon` (KT HOGAR), `FireIcon` (KT GASTRO), `ShoppingBagIcon` (Mercado Libre). Agregar los que falten.

- [ ] **Step 2: Icono + color por checkbox**

Para cada uno de los cuatro `<div className={checkboxCardClassName}>`, anteponer un icono con su color al lado del checkbox. Ejemplos (mantener el resto del contenido igual):

```jsx
{/* Dux */}
<CubeIcon className="h-5 w-5 shrink-0 text-indigo-500" />
{/* KT HOGAR (Nube) */}
<HomeIcon className="h-5 w-5 shrink-0 text-sky-500" />
{/* KT GASTRO (Nube) */}
<FireIcon className="h-5 w-5 shrink-0 text-emerald-500" />
{/* Mercado Libre */}
<ShoppingBagIcon className="h-5 w-5 shrink-0 text-yellow-500" />
```

Colocar cada icono dentro de su `<div>` de tarjeta, antes del `<input>`. Mantener `editandoProductoId ? "Actualizar en …" : "Subir a …"` donde ya existe (Dux) y los labels actuales de los demás (la edición de labels de Nube/ML pertenece al Spec A, no a este plan).

- [ ] **Step 3: Typecheck**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: exit 0.

- [ ] **Step 4: Verificar (smoke)**

Abrir el form de producto y confirmar que cada canal muestra su icono y color (Dux índigo/cubo, KT HOGAR celeste/casa, KT GASTRO verde/fuego, ML amarillo/bolsa).

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): icono y color distintivo por canal de venta"
```

---

## Self-Review

**Spec coverage:**
- Payload enriquecido (mapeo final) → Task 6 (builder) + Task 5 (rubro/subrubro). ✅
- Quitar `codigo_externo`/`ctd_unidades_por_bulto` → Task 6 (test lo verifica). ✅
- Omisión de nulls → Task 6 (test lo verifica). ✅
- Tabla `unidad_medida` + IDs Dux + relación producto → Tasks 1, 2. ✅
- `Marca.codigoDux` → Task 3. ✅
- `idDux` en clasif → Task 4. ✅
- Manejo LAZY/tx (POST fuera de tx) → Task 6 (método `@Transactional` + self-proxy). ✅
- Form select unidad → Task 7. ABMs marca/clasif → Tasks 8, 9. ✅
- Iconos/colores por canal → Task 10. ✅
- Fuera de alcance (stock_matricial/cod_barra/fecha_vencimiento/precios; labels Nube/ML del Spec A) → respetado. ✅
- Dependencias del usuario (IDs Dux de unidades, alinear proveedor.id, cargar codigoDux/idDux) → reflejadas en Task 1 (seed provisorio) y en los ABMs (Tasks 8, 9). ✅

**Type consistency:** `DuxRubro(idRubro, idSubRubro)`, `DuxClasifResolver.resolver(Producto)`, `DuxItemBuilder.construir(Producto)`, `UnidadMedida(Integer)`, `unidadMedidaId`, `codigoDux`, `idDux` — usados consistentemente entre tasks. ✅

**Notas de riesgo:** los IDs del seed de `unidades_medida` son provisorios (1..22); el usuario debe reemplazarlos por los reales de Dux antes de exportar a producción (marcado en Task 1). Igualmente, `id_proveedor`, `id_rubro`, `id_sub_rubro` y `codigo_marca` dependen de que el usuario cargue/alinee esos valores con Dux.
