# Campañas desde categorías de Tienda Nube — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Importar las categorías de Tienda Nube (tienda KT HOGAR) y su tageo de productos a la base de datos, en una nueva sección, para asignar un precio fijo manual por producto+campaña.

**Architecture:** Dos tablas nuevas (`campania`, `campania_producto`) en un dominio backend nuevo `dominio/campania/` que sigue el patrón existente (entity → repository → mapper → service → controller). Un `CampaniaSyncService` orquesta la sincronización leyendo de TN vía métodos nuevos de `TiendaNubeService` y reconciliando contra la BD. El frontend agrega una sección `campanias` (lista + detalle) siguiendo el patrón de `marcas`.

**Tech Stack:** Spring Boot 4 / Java 25 / Spring Data JPA / MapStruct / JUnit5 + Mockito + AssertJ (backend); Next.js 16 / React 19 / TanStack Table / Tailwind (frontend). Schema con `ddl-auto=validate` → DDL manual en `src/main/resources/db/`.

**Referencia de diseño:** `docs/superpowers/specs/2026-06-13-campanias-tienda-nube-design.md`

---

## Convenciones a respetar (del codebase)

- Paquete base backend: `ar.com.leo.super_master_backend` (underscore).
- DTOs son `record` con Bean Validation. Mappers MapStruct con `@Mapper(config = GlobalMapperConfig.class)`.
- Controllers solo delegan; `@PreAuthorize(Permisos.XXX)`; errores vía `NotFoundException`/`ConflictException`/`BadRequestException`.
- Listados paginados con `page`, `size`, `sort`, búsqueda `search`. Respuesta Spring `Page` (`content` + `page.totalElements`).
- Frontend: por sección `types.ts` / `<seccion>Service.ts` / `use<Seccion>.ts` / `columns.tsx` / `page.tsx`; `fetchAPI`, `withAuditOrigin`, `buildListParams`; toasts con `notificar`.
- Permisos a usar: `INTEGRACIONES_VER` (lectura) e `INTEGRACIONES_EDITAR` (escritura), igual que DUX.
- Canal de precios: se obtiene por nombre con `CanalRepository.findByNombreIgnoreCase("NUBE")`.

---

## File Structure

**Backend (nuevo dominio `dominio/campania/`):**
- `entity/Campania.java` — entidad de la categoría/campaña importada.
- `entity/CampaniaProducto.java` — join producto↔campaña con `precioManual`.
- `repository/CampaniaRepository.java`
- `repository/CampaniaProductoRepository.java`
- `dto/CampaniaDTO.java`, `dto/CampaniaUpdateDTO.java`, `dto/CampaniaProductoDTO.java`, `dto/CampaniaProductoPrecioDTO.java`, `dto/SincronizacionResultadoDTO.java`
- `mapper/CampaniaMapper.java`
- `service/CampaniaService.java` (+ `CampaniaServiceImpl.java`) — CRUD + edición de precio.
- `service/CampaniaSyncService.java` — orquestación de la sincronización (separada para ser testeable).
- `controller/CampaniaController.java`

**Backend (modificado):**
- `apis/nube/service/TiendaNubeService.java` — agregar `listarCategorias()` y `mapearCategoriasASkus()`.
- `src/main/resources/db/crear-tablas-campania.sql` — DDL manual (nuevo).

**Backend (tests):**
- `dominio/campania/service/CampaniaSyncServiceTest.java`

**Frontend (nueva sección `src/app/campanias/`):**
- `types.ts`, `campaniasService.ts`, `useCampanias.ts`, `columns.tsx`, `page.tsx` (lista)
- `[id]/page.tsx`, `[id]/useCampaniaDetalle.ts`, `[id]/columns.tsx` (detalle de productos)

**Frontend (modificado):**
- `src/app/components/navigation/navigationConfig.tsx` — item nuevo en sección Integraciones.

---

## FASE 1 — Backend: schema y entidades

### Task 1: Script DDL de las tablas

**Files:**
- Create: `supermaster-backend/src/main/resources/db/crear-tablas-campania.sql`

- [ ] **Step 1: Escribir el script DDL**

```sql
-- Tablas para campañas a partir de categorías de Tienda Nube.
-- Schema supermaster. Ejecutar manualmente antes de levantar (ddl-auto=validate).

CREATE TABLE supermaster.campania (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    tn_categoria_id    BIGINT       NOT NULL,
    nombre             VARCHAR(150) NOT NULL,
    id_canal           INT          NOT NULL,
    fecha_desde        DATE         NULL,
    fecha_hasta        DATE         NULL,
    activa             TINYINT(1)   NOT NULL DEFAULT 0,
    fecha_ultima_sync  DATETIME     NULL,
    observaciones      VARCHAR(255) NULL,
    CONSTRAINT uq_campania_tn_categoria UNIQUE (tn_categoria_id),
    CONSTRAINT fk_campania_canal FOREIGN KEY (id_canal)
        REFERENCES supermaster.canales (id)
);

CREATE TABLE supermaster.campania_producto (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    id_campania    INT            NOT NULL,
    id_producto    INT            NOT NULL,
    precio_manual  DECIMAL(15,2)  NULL,
    fecha_sync     DATETIME       NULL,
    observaciones  VARCHAR(255)   NULL,
    CONSTRAINT uq_campania_producto UNIQUE (id_campania, id_producto),
    CONSTRAINT fk_campprod_campania FOREIGN KEY (id_campania)
        REFERENCES supermaster.campania (id) ON DELETE CASCADE,
    CONSTRAINT fk_campprod_producto FOREIGN KEY (id_producto)
        REFERENCES supermaster.productos (id) ON DELETE CASCADE
);

CREATE INDEX idx_campania_producto_campania ON supermaster.campania_producto (id_campania);
```

- [ ] **Step 2: Ejecutar el script en la BD local** (manual, fuera del flujo de build)

Run (PowerShell, ajustá credenciales): `mysql -u root supermaster < supermaster-backend/src/main/resources/db/crear-tablas-campania.sql`
Expected: las tablas `campania` y `campania_producto` existen en el schema `supermaster`.
Nota: este paso lo corre Leo; el resto del plan no compila contra la BD hasta que existan (ddl-auto=validate).

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/resources/db/crear-tablas-campania.sql
git commit -m "feat(campania): script DDL de tablas campania y campania_producto"
```

---

### Task 2: Entidad `Campania`

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/entity/Campania.java`

- [ ] **Step 1: Escribir la entidad**

```java
package ar.com.leo.super_master_backend.dominio.campania.entity;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "campania", schema = "supermaster",
        uniqueConstraints = @UniqueConstraint(name = "uq_campania_tn_categoria", columnNames = {"tn_categoria_id"}))
public class Campania {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @NotNull
    @Column(name = "tn_categoria_id", nullable = false)
    private Long tnCategoriaId;

    @Size(max = 150)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_canal", nullable = false)
    private Canal canal;

    @Column(name = "fecha_desde")
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @ColumnDefault("0")
    @Column(name = "activa", nullable = false)
    private Boolean activa = false;

    @Column(name = "fecha_ultima_sync")
    private LocalDateTime fechaUltimaSync;

    @Size(max = 255)
    @Column(name = "observaciones", length = 255)
    private String observaciones;

    public Campania(Integer id) {
        this.id = id;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/entity/Campania.java
git commit -m "feat(campania): entidad Campania"
```

---

### Task 3: Entidad `CampaniaProducto`

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/entity/CampaniaProducto.java`

- [ ] **Step 1: Escribir la entidad**

```java
package ar.com.leo.super_master_backend.dominio.campania.entity;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "campania_producto", schema = "supermaster",
        uniqueConstraints = @UniqueConstraint(name = "uq_campania_producto", columnNames = {"id_campania", "id_producto"}))
public class CampaniaProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_campania", nullable = false)
    private Campania campania;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "precio_manual", precision = 15, scale = 2)
    private BigDecimal precioManual;

    @Column(name = "fecha_sync")
    private LocalDateTime fechaSync;

    @Size(max = 255)
    @Column(name = "observaciones", length = 255)
    private String observaciones;
}
```

- [ ] **Step 2: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/entity/CampaniaProducto.java
git commit -m "feat(campania): entidad CampaniaProducto"
```

---

### Task 4: Repositorios

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/repository/CampaniaRepository.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/repository/CampaniaProductoRepository.java`

- [ ] **Step 1: `CampaniaRepository`**

```java
package ar.com.leo.super_master_backend.dominio.campania.repository;

import ar.com.leo.super_master_backend.dominio.campania.entity.Campania;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaniaRepository extends JpaRepository<Campania, Integer> {

    Optional<Campania> findByTnCategoriaId(Long tnCategoriaId);

    Page<Campania> findByNombreContainingIgnoreCase(String texto, Pageable pageable);
}
```

- [ ] **Step 2: `CampaniaProductoRepository`**

```java
package ar.com.leo.super_master_backend.dominio.campania.repository;

import ar.com.leo.super_master_backend.dominio.campania.entity.CampaniaProducto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaniaProductoRepository extends JpaRepository<CampaniaProducto, Integer> {

    List<CampaniaProducto> findByCampaniaId(Integer campaniaId);

    Page<CampaniaProducto> findByCampaniaId(Integer campaniaId, Pageable pageable);

    long countByCampaniaId(Integer campaniaId);
}
```

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/repository/
git commit -m "feat(campania): repositorios CampaniaRepository y CampaniaProductoRepository"
```

---

## FASE 2 — Backend: DTOs y mapper

### Task 5: DTOs

**Files:**
- Create: `dto/CampaniaDTO.java`, `dto/CampaniaUpdateDTO.java`, `dto/CampaniaProductoDTO.java`, `dto/CampaniaProductoPrecioDTO.java`, `dto/SincronizacionResultadoDTO.java` (todos en `.../dominio/campania/dto/`)

- [ ] **Step 1: `CampaniaDTO`** (respuesta de la lista de campañas)

```java
package ar.com.leo.super_master_backend.dominio.campania.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CampaniaDTO(
        Integer id,
        Long tnCategoriaId,
        String nombre,
        Integer canalId,
        String canalNombre,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        Boolean activa,
        LocalDateTime fechaUltimaSync,
        String observaciones,
        long cantidadProductos
) {
}
```

- [ ] **Step 2: `CampaniaUpdateDTO`** (editar vigencia/estado/observaciones; el nombre y el canal vienen de TN, no se editan)

```java
package ar.com.leo.super_master_backend.dominio.campania.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CampaniaUpdateDTO(
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        Boolean activa,
        @Size(max = 255, message = "Las observaciones no pueden exceder 255 caracteres")
        String observaciones
) {
}
```

- [ ] **Step 3: `CampaniaProductoDTO`** (fila del detalle: datos del producto + precio manual)

```java
package ar.com.leo.super_master_backend.dominio.campania.dto;

import java.math.BigDecimal;

public record CampaniaProductoDTO(
        Integer id,
        Integer productoId,
        String sku,
        String descripcion,
        BigDecimal costo,
        BigDecimal precioManual
) {
}
```

- [ ] **Step 4: `CampaniaProductoPrecioDTO`** (body del PATCH de precio)

```java
package ar.com.leo.super_master_backend.dominio.campania.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CampaniaProductoPrecioDTO(
        @PositiveOrZero(message = "El precio debe ser mayor o igual a 0")
        BigDecimal precioManual
) {
}
```

- [ ] **Step 5: `SincronizacionResultadoDTO`** (resumen del sync)

```java
package ar.com.leo.super_master_backend.dominio.campania.dto;

import java.util.List;

public record SincronizacionResultadoDTO(
        int categoriasImportadas,
        int productosVinculados,
        List<String> skusSinMatch
) {
}
```

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/dto/
git commit -m "feat(campania): DTOs de campania, detalle, precio y resultado de sync"
```

---

### Task 6: Mapper

**Files:**
- Create: `mapper/CampaniaMapper.java`

- [ ] **Step 1: Escribir el mapper**

`cantidadProductos` no se mapea desde la entidad (se calcula en el service); se ignora gracias a `unmappedTargetPolicy = IGNORE` del `GlobalMapperConfig`. `canalId`/`canalNombre` se mapean desde la relación.

```java
package ar.com.leo.super_master_backend.dominio.campania.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaDTO;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaProductoDTO;
import ar.com.leo.super_master_backend.dominio.campania.entity.Campania;
import ar.com.leo.super_master_backend.dominio.campania.entity.CampaniaProducto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface CampaniaMapper {

    @Mapping(target = "canalId", source = "canal.id")
    @Mapping(target = "canalNombre", source = "canal.nombre")
    @Mapping(target = "cantidadProductos", ignore = true)
    CampaniaDTO toDTO(Campania entity);

    @Mapping(target = "productoId", source = "producto.id")
    @Mapping(target = "sku", source = "producto.sku")
    @Mapping(target = "descripcion", source = "producto.descripcion")
    @Mapping(target = "costo", source = "producto.costo")
    CampaniaProductoDTO toProductoDTO(CampaniaProducto entity);
}
```

- [ ] **Step 2: Compilar para verificar que MapStruct genera el impl**

Run: `cd supermaster-backend && ./mvnw -q -o compile`
Expected: BUILD SUCCESS; se genera `CampaniaMapperImpl`. (Si la BD no tiene las tablas aún, esto compila igual — la validación de schema ocurre al arrancar la app, no al compilar.)

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/mapper/CampaniaMapper.java
git commit -m "feat(campania): mapper MapStruct CampaniaMapper"
```

---

## FASE 3 — Backend: lectura de categorías desde Tienda Nube

### Task 7: Métodos `listarCategorias` y `mapearCategoriasASkus` en `TiendaNubeService`

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java`

Reutilizan el patrón existente: `verificarCredenciales()`, `getStore(storeName)`, loop `while (uri != null)` con `retryHandler.getWithHeaders(uri, store.getAccessToken())`, `objectMapper.readTree(...)`, `parseLinkNext(...)`, y el helper `extraerNombreProducto(...)` (sirve para el `name` multilingüe de la categoría, que tiene la misma forma `{"es": "..."}`).

- [ ] **Step 1: Agregar `listarCategorias`** (id de categoría TN → nombre `es`)

Insertar como método público nuevo (junto a los demás métodos de listado, ~después de `listarVariantesPorSku`):

```java
/**
 * Lista las categorías de la tienda. Devuelve id de categoría TN → nombre (idioma es).
 * Las categorías cuyo name no tenga texto en es se omiten.
 */
public Map<Long, String> listarCategorias(String storeName) {
    verificarCredenciales();
    StoreCredentials store = getStore(storeName);
    if (store == null) {
        log.warn("NUBE - Store '{}' no encontrada en credenciales", storeName);
        return Map.of();
    }

    Map<Long, String> mapa = new LinkedHashMap<>();
    String uri = String.format("/%s/categories?per_page=200", store.getStoreId());
    int paginas = 0;

    while (uri != null) {
        NubeRetryHandler.HttpResponse httpResponse;
        try {
            httpResponse = retryHandler.getWithHeaders(uri, store.getAccessToken());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404 && e.getResponseBodyAsString().contains("Last page is 0")) {
                break;
            }
            log.warn("NUBE ({}) - Error listando categorías en página {}: {}. Abortando.",
                    storeName, paginas + 1, e.getMessage());
            return Map.of();
        }

        if (httpResponse.body() == null) break;

        try {
            JsonNode categorias = objectMapper.readTree(httpResponse.body());
            if (!categorias.isArray() || categorias.isEmpty()) break;

            for (JsonNode cat : categorias) {
                long id = cat.path("id").asLong(0);
                if (id == 0) continue;
                String nombre = extraerNombreProducto(cat.path("name"));
                if (nombre == null) continue;
                mapa.putIfAbsent(id, nombre);
            }
            paginas++;
        } catch (Exception e) {
            log.warn("NUBE ({}) - Error parseando categorías en página {}: {}. Abortando.",
                    storeName, paginas + 1, e.getMessage());
            return Map.of();
        }

        uri = parseLinkNext(httpResponse.headers());
    }

    log.info("NUBE ({}) - Categorías indexadas: {} ({} páginas)", storeName, mapa.size(), paginas);
    return mapa;
}
```

- [ ] **Step 2: Agregar `mapearCategoriasASkus`** (id de categoría TN → lista de SKUs)

```java
/**
 * Recorre los productos de la tienda y arma id de categoría TN → SKUs que la tienen.
 * El array `categories` está a nivel producto; el SKU está a nivel variante:
 * todas las variantes (SKUs) de un producto pertenecen a las categorías de ese producto.
 */
public Map<Long, List<String>> mapearCategoriasASkus(String storeName) {
    verificarCredenciales();
    StoreCredentials store = getStore(storeName);
    if (store == null) {
        log.warn("NUBE - Store '{}' no encontrada en credenciales", storeName);
        return Map.of();
    }

    Map<Long, List<String>> mapa = new LinkedHashMap<>();
    String uri = String.format("/%s/products?per_page=200", store.getStoreId());
    int paginas = 0;

    while (uri != null) {
        NubeRetryHandler.HttpResponse httpResponse;
        try {
            httpResponse = retryHandler.getWithHeaders(uri, store.getAccessToken());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404 && e.getResponseBodyAsString().contains("Last page is 0")) {
                break;
            }
            log.warn("NUBE ({}) - Error listando productos (categorías) en página {}: {}. Abortando.",
                    storeName, paginas + 1, e.getMessage());
            return Map.of();
        }

        if (httpResponse.body() == null) break;

        try {
            JsonNode products = objectMapper.readTree(httpResponse.body());
            if (!products.isArray() || products.isEmpty()) break;

            for (JsonNode product : products) {
                JsonNode categories = product.path("categories");
                if (!categories.isArray() || categories.isEmpty()) continue;

                JsonNode variants = product.path("variants");
                if (!variants.isArray()) continue;

                List<String> skus = new ArrayList<>();
                for (JsonNode variant : variants) {
                    String sku = variant.path("sku").asString("");
                    if (!sku.isBlank()) skus.add(sku);
                }
                if (skus.isEmpty()) continue;

                for (JsonNode cat : categories) {
                    long catId = cat.path("id").asLong(0);
                    if (catId == 0) continue;
                    mapa.computeIfAbsent(catId, k -> new ArrayList<>()).addAll(skus);
                }
            }
            paginas++;
        } catch (Exception e) {
            log.warn("NUBE ({}) - Error parseando productos (categorías) en página {}: {}. Abortando.",
                    storeName, paginas + 1, e.getMessage());
            return Map.of();
        }

        uri = parseLinkNext(httpResponse.headers());
    }

    log.info("NUBE ({}) - Categorías con productos: {} ({} páginas)", storeName, mapa.size(), paginas);
    return mapa;
}
```

- [ ] **Step 3: Asegurar imports** `java.util.ArrayList` y `java.util.List` están presentes en el archivo (agregar si falta).

- [ ] **Step 4: Compilar**

Run: `cd supermaster-backend && ./mvnw -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java
git commit -m "feat(nube): listarCategorias y mapearCategoriasASkus en TiendaNubeService"
```

---

## FASE 4 — Backend: servicio de sincronización (TDD)

### Task 8: `CampaniaSyncService` con tests

**Files:**
- Create: `service/CampaniaSyncService.java`
- Test: `src/test/java/ar/com/leo/super_master_backend/dominio/campania/service/CampaniaSyncServiceTest.java`

El servicio recibe el `storeName`, obtiene el canal NUBE, trae categorías y mapa categoría→SKUs de `TiendaNubeService`, hace upsert de campañas y reconcilia `campania_producto` (agrega nuevos, quita destageados, **preserva `precioManual`** de los que siguen), y devuelve `SincronizacionResultadoDTO`.

- [ ] **Step 1: Escribir el test (falla a compilar primero porque la clase no existe)**

```java
package ar.com.leo.super_master_backend.dominio.campania.service;

import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.campania.dto.SincronizacionResultadoDTO;
import ar.com.leo.super_master_backend.dominio.campania.entity.Campania;
import ar.com.leo.super_master_backend.dominio.campania.entity.CampaniaProducto;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaProductoRepository;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaRepository;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaniaSyncServiceTest {

    @Mock private TiendaNubeService tiendaNubeService;
    @Mock private CanalRepository canalRepository;
    @Mock private CampaniaRepository campaniaRepository;
    @Mock private CampaniaProductoRepository campaniaProductoRepository;
    @Mock private ProductoRepository productoRepository;

    private CampaniaSyncService service;

    private Canal canalNube;

    @BeforeEach
    void setUp() {
        service = new CampaniaSyncService(
                tiendaNubeService, canalRepository, campaniaRepository,
                campaniaProductoRepository, productoRepository);
        canalNube = new Canal(7);
        canalNube.setNombre("NUBE");
        when(canalRepository.findByNombreIgnoreCase("NUBE")).thenReturn(Optional.of(canalNube));
    }

    private Producto producto(int id, String sku) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        return p;
    }

    @Test
    @DisplayName("categoría nueva → crea campania con activa=false y vincula sus productos")
    void categoriaNueva_creaCampaniaYVincula() {
        when(tiendaNubeService.listarCategorias("KT HOGAR")).thenReturn(Map.of(100L, "Día del Padre"));
        when(tiendaNubeService.mapearCategoriasASkus("KT HOGAR")).thenReturn(Map.of(100L, List.of("SKU1", "SKU2")));
        when(campaniaRepository.findByTnCategoriaId(100L)).thenReturn(Optional.empty());
        when(campaniaRepository.save(any(Campania.class))).thenAnswer(inv -> {
            Campania c = inv.getArgument(0);
            if (c.getId() == null) c.setId(1);
            return c;
        });
        when(campaniaProductoRepository.findByCampaniaId(1)).thenReturn(List.of());
        when(productoRepository.findBySkuIn(List.of("SKU1", "SKU2")))
                .thenReturn(List.of(producto(11, "SKU1"), producto(12, "SKU2")));

        SincronizacionResultadoDTO r = service.sincronizar("KT HOGAR");

        assertThat(r.categoriasImportadas()).isEqualTo(1);
        assertThat(r.productosVinculados()).isEqualTo(2);
        assertThat(r.skusSinMatch()).isEmpty();

        ArgumentCaptor<Campania> campCaptor = ArgumentCaptor.forClass(Campania.class);
        verify(campaniaRepository, atLeastOnce()).save(campCaptor.capture());
        assertThat(campCaptor.getAllValues().get(0).getActiva()).isFalse();
        assertThat(campCaptor.getAllValues().get(0).getNombre()).isEqualTo("Día del Padre");

        verify(campaniaProductoRepository, times(2)).save(any(CampaniaProducto.class));
    }

    @Test
    @DisplayName("campaña existente → actualiza nombre y preserva precioManual de productos que siguen")
    void campaniaExistente_preservaPrecio() {
        Campania existente = new Campania(1);
        existente.setTnCategoriaId(100L);
        existente.setNombre("Viejo nombre");
        existente.setCanal(canalNube);

        Producto p1 = producto(11, "SKU1");
        CampaniaProducto cpExistente = new CampaniaProducto();
        cpExistente.setId(50);
        cpExistente.setCampania(existente);
        cpExistente.setProducto(p1);
        cpExistente.setPrecioManual(new BigDecimal("999.00"));

        when(tiendaNubeService.listarCategorias("KT HOGAR")).thenReturn(Map.of(100L, "Día del Padre"));
        when(tiendaNubeService.mapearCategoriasASkus("KT HOGAR")).thenReturn(Map.of(100L, List.of("SKU1")));
        when(campaniaRepository.findByTnCategoriaId(100L)).thenReturn(Optional.of(existente));
        when(campaniaRepository.save(any(Campania.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaniaProductoRepository.findByCampaniaId(1)).thenReturn(List.of(cpExistente));
        when(productoRepository.findBySkuIn(List.of("SKU1"))).thenReturn(List.of(p1));

        SincronizacionResultadoDTO r = service.sincronizar("KT HOGAR");

        assertThat(r.productosVinculados()).isEqualTo(1);
        assertThat(existente.getNombre()).isEqualTo("Día del Padre");
        // No se borra ni recrea la fila existente → precio intacto
        verify(campaniaProductoRepository, never()).delete(any(CampaniaProducto.class));
        verify(campaniaProductoRepository, never()).save(any(CampaniaProducto.class));
        assertThat(cpExistente.getPrecioManual()).isEqualByComparingTo("999.00");
    }

    @Test
    @DisplayName("producto destageado en TN → se quita de la campaña")
    void productoDestageado_seQuita() {
        Campania existente = new Campania(1);
        existente.setTnCategoriaId(100L);
        existente.setNombre("Día del Padre");
        existente.setCanal(canalNube);

        Producto p1 = producto(11, "SKU1");
        CampaniaProducto cpViejo = new CampaniaProducto();
        cpViejo.setId(50);
        cpViejo.setCampania(existente);
        cpViejo.setProducto(p1);

        when(tiendaNubeService.listarCategorias("KT HOGAR")).thenReturn(Map.of(100L, "Día del Padre"));
        when(tiendaNubeService.mapearCategoriasASkus("KT HOGAR")).thenReturn(Map.of(100L, List.of()));
        when(campaniaRepository.findByTnCategoriaId(100L)).thenReturn(Optional.of(existente));
        when(campaniaRepository.save(any(Campania.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaniaProductoRepository.findByCampaniaId(1)).thenReturn(List.of(cpViejo));

        SincronizacionResultadoDTO r = service.sincronizar("KT HOGAR");

        assertThat(r.productosVinculados()).isZero();
        verify(campaniaProductoRepository).delete(cpViejo);
    }

    @Test
    @DisplayName("SKU tageado en TN sin match en la BD → se reporta y se omite")
    void skuSinMatch_seReporta() {
        when(tiendaNubeService.listarCategorias("KT HOGAR")).thenReturn(Map.of(100L, "Día del Padre"));
        when(tiendaNubeService.mapearCategoriasASkus("KT HOGAR")).thenReturn(Map.of(100L, List.of("SKU1", "FANTASMA")));
        when(campaniaRepository.findByTnCategoriaId(100L)).thenReturn(Optional.empty());
        when(campaniaRepository.save(any(Campania.class))).thenAnswer(inv -> {
            Campania c = inv.getArgument(0);
            if (c.getId() == null) c.setId(1);
            return c;
        });
        when(campaniaProductoRepository.findByCampaniaId(1)).thenReturn(List.of());
        when(productoRepository.findBySkuIn(List.of("SKU1", "FANTASMA")))
                .thenReturn(List.of(producto(11, "SKU1")));

        SincronizacionResultadoDTO r = service.sincronizar("KT HOGAR");

        assertThat(r.productosVinculados()).isEqualTo(1);
        assertThat(r.skusSinMatch()).containsExactly("FANTASMA");
    }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que falla** (no compila: `CampaniaSyncService` no existe)

Run: `cd supermaster-backend && ./mvnw -q -o test -Dtest=CampaniaSyncServiceTest`
Expected: fallo de compilación / clase no encontrada.

- [ ] **Step 3: Implementar `CampaniaSyncService`**

```java
package ar.com.leo.super_master_backend.dominio.campania.service;

import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.campania.dto.SincronizacionResultadoDTO;
import ar.com.leo.super_master_backend.dominio.campania.entity.Campania;
import ar.com.leo.super_master_backend.dominio.campania.entity.CampaniaProducto;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaProductoRepository;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaRepository;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CampaniaSyncService {

    /** Nombre del canal de precios asociado a la tienda KT HOGAR. */
    public static final String CANAL_NUBE = "NUBE";

    private final TiendaNubeService tiendaNubeService;
    private final CanalRepository canalRepository;
    private final CampaniaRepository campaniaRepository;
    private final CampaniaProductoRepository campaniaProductoRepository;
    private final ProductoRepository productoRepository;

    @Transactional
    public SincronizacionResultadoDTO sincronizar(String storeName) {
        Canal canal = canalRepository.findByNombreIgnoreCase(CANAL_NUBE)
                .orElseThrow(() -> new NotFoundException("No existe el canal '" + CANAL_NUBE + "'"));

        Map<Long, String> categorias = tiendaNubeService.listarCategorias(storeName);
        Map<Long, List<String>> categoriaSkus = tiendaNubeService.mapearCategoriasASkus(storeName);

        LocalDateTime ahora = LocalDateTime.now();
        int productosVinculados = 0;
        LinkedHashSet<String> skusSinMatch = new LinkedHashSet<>();

        for (Map.Entry<Long, String> entry : categorias.entrySet()) {
            Long tnCategoriaId = entry.getKey();
            String nombre = entry.getValue();

            Campania campania = campaniaRepository.findByTnCategoriaId(tnCategoriaId)
                    .orElseGet(() -> {
                        Campania nueva = new Campania();
                        nueva.setTnCategoriaId(tnCategoriaId);
                        nueva.setCanal(canal);
                        nueva.setActiva(false);
                        return nueva;
                    });
            campania.setNombre(nombre);
            campania.setFechaUltimaSync(ahora);
            campania = campaniaRepository.save(campania);

            List<String> skus = categoriaSkus.getOrDefault(tnCategoriaId, List.of());
            productosVinculados += reconciliarProductos(campania, skus, ahora, skusSinMatch);
        }

        return new SincronizacionResultadoDTO(
                categorias.size(), productosVinculados, new ArrayList<>(skusSinMatch));
    }

    /**
     * Ajusta los productos de la campaña para que reflejen los SKUs tageados en TN.
     * Agrega los nuevos (precio en null), quita los que ya no están, y preserva los
     * que siguen (sin tocar su precioManual). Devuelve cuántos quedan vinculados.
     */
    private int reconciliarProductos(Campania campania, List<String> skus,
                                     LocalDateTime ahora, Set<String> skusSinMatch) {
        // SKU → Producto existente en la BD
        Map<String, Producto> porSku = new HashMap<>();
        if (!skus.isEmpty()) {
            List<String> distintos = skus.stream().distinct().toList();
            for (Producto p : productoRepository.findBySkuIn(distintos)) {
                porSku.put(p.getSku(), p);
            }
            for (String sku : distintos) {
                if (!porSku.containsKey(sku)) skusSinMatch.add(sku);
            }
        }

        // Ids de productos que deben estar en la campaña según TN
        Set<Integer> productoIdsDeseados = new LinkedHashSet<>();
        for (Producto p : porSku.values()) productoIdsDeseados.add(p.getId());

        // Estado actual en la BD
        List<CampaniaProducto> actuales = campaniaProductoRepository.findByCampaniaId(campania.getId());
        Set<Integer> productoIdsActuales = new HashSet<>();
        for (CampaniaProducto cp : actuales) {
            Integer pid = cp.getProducto().getId();
            if (productoIdsDeseados.contains(pid)) {
                productoIdsActuales.add(pid); // sigue → preservar (no tocar)
            } else {
                campaniaProductoRepository.delete(cp); // destageado → quitar
            }
        }

        // Agregar los nuevos
        for (Producto p : porSku.values()) {
            if (productoIdsActuales.contains(p.getId())) continue;
            CampaniaProducto cp = new CampaniaProducto();
            cp.setCampania(campania);
            cp.setProducto(p);
            cp.setFechaSync(ahora);
            campaniaProductoRepository.save(cp);
        }

        return productoIdsDeseados.size();
    }
}
```

- [ ] **Step 4: Ejecutar los tests y verificar que pasan**

Run: `cd supermaster-backend && ./mvnw -q -o test -Dtest=CampaniaSyncServiceTest`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/service/CampaniaSyncService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/campania/service/CampaniaSyncServiceTest.java
git commit -m "feat(campania): CampaniaSyncService con reconciliación de productos (TDD)"
```

---

## FASE 5 — Backend: servicio CRUD + controller

### Task 9: `CampaniaService` (interfaz + impl)

**Files:**
- Create: `service/CampaniaService.java`
- Create: `service/CampaniaServiceImpl.java`

- [ ] **Step 1: Interfaz `CampaniaService`**

```java
package ar.com.leo.super_master_backend.dominio.campania.service;

import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaDTO;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaProductoDTO;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface CampaniaService {

    Page<CampaniaDTO> listar(String search, Pageable pageable);

    CampaniaDTO obtenerPorId(Integer id);

    CampaniaDTO actualizar(Integer id, CampaniaUpdateDTO dto);

    Page<CampaniaProductoDTO> listarProductos(Integer campaniaId, Pageable pageable);

    CampaniaProductoDTO actualizarPrecio(Integer campaniaProductoId, BigDecimal precioManual);
}
```

- [ ] **Step 2: Implementación `CampaniaServiceImpl`**

```java
package ar.com.leo.super_master_backend.dominio.campania.service;

import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaDTO;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaProductoDTO;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.campania.entity.Campania;
import ar.com.leo.super_master_backend.dominio.campania.entity.CampaniaProducto;
import ar.com.leo.super_master_backend.dominio.campania.mapper.CampaniaMapper;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaProductoRepository;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CampaniaServiceImpl implements CampaniaService {

    private final CampaniaRepository repository;
    private final CampaniaProductoRepository productoRepository;
    private final CampaniaMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Page<CampaniaDTO> listar(String search, Pageable pageable) {
        Page<Campania> page = (search != null && !search.isBlank())
                ? repository.findByNombreContainingIgnoreCase(search, pageable)
                : repository.findAll(pageable);
        return page.map(this::toDTOConConteo);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaniaDTO obtenerPorId(Integer id) {
        Campania campania = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Campaña no encontrada con ID: " + id));
        return toDTOConConteo(campania);
    }

    @Override
    @Transactional
    public CampaniaDTO actualizar(Integer id, CampaniaUpdateDTO dto) {
        Campania campania = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Campaña no encontrada con ID: " + id));
        // Solo se editan vigencia, estado y observaciones; nombre y canal vienen de TN.
        // Null = no se modifica (PATCH parcial).
        if (dto.fechaDesde() != null) campania.setFechaDesde(dto.fechaDesde());
        if (dto.fechaHasta() != null) campania.setFechaHasta(dto.fechaHasta());
        if (dto.activa() != null) campania.setActiva(dto.activa());
        if (dto.observaciones() != null) campania.setObservaciones(dto.observaciones());
        campania = repository.save(campania);
        return toDTOConConteo(campania);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CampaniaProductoDTO> listarProductos(Integer campaniaId, Pageable pageable) {
        if (!repository.existsById(campaniaId)) {
            throw new NotFoundException("Campaña no encontrada con ID: " + campaniaId);
        }
        return productoRepository.findByCampaniaId(campaniaId, pageable).map(mapper::toProductoDTO);
    }

    @Override
    @Transactional
    public CampaniaProductoDTO actualizarPrecio(Integer campaniaProductoId, BigDecimal precioManual) {
        CampaniaProducto cp = productoRepository.findById(campaniaProductoId)
                .orElseThrow(() -> new NotFoundException(
                        "Producto de campaña no encontrado con ID: " + campaniaProductoId));
        cp.setPrecioManual(precioManual);
        cp = productoRepository.save(cp);
        return mapper.toProductoDTO(cp);
    }

    private CampaniaDTO toDTOConConteo(Campania campania) {
        CampaniaDTO base = mapper.toDTO(campania);
        long cantidad = productoRepository.countByCampaniaId(campania.getId());
        return new CampaniaDTO(
                base.id(), base.tnCategoriaId(), base.nombre(), base.canalId(), base.canalNombre(),
                base.fechaDesde(), base.fechaHasta(), base.activa(), base.fechaUltimaSync(),
                base.observaciones(), cantidad);
    }
}
```

- [ ] **Step 3: Compilar**

Run: `cd supermaster-backend && ./mvnw -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/service/CampaniaService.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/service/CampaniaServiceImpl.java
git commit -m "feat(campania): CampaniaService con CRUD y edición de precio"
```

---

### Task 10: `CampaniaController`

**Files:**
- Create: `controller/CampaniaController.java`

- [ ] **Step 1: Escribir el controller**

```java
package ar.com.leo.super_master_backend.dominio.campania.controller;

import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.campania.dto.*;
import ar.com.leo.super_master_backend.dominio.campania.service.CampaniaService;
import ar.com.leo.super_master_backend.dominio.campania.service.CampaniaSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/campanias")
public class CampaniaController {

    private final CampaniaService service;
    private final CampaniaSyncService syncService;

    @GetMapping
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Page<CampaniaDTO>> listar(@RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<CampaniaDTO> obtenerPorId(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<CampaniaDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody CampaniaUpdateDTO dto) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @GetMapping("/{id}/productos")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Page<CampaniaProductoDTO>> listarProductos(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id, Pageable pageable) {
        return ResponseEntity.ok(service.listarProductos(id, pageable));
    }

    @PatchMapping("/productos/{campaniaProductoId}/precio")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<CampaniaProductoDTO> actualizarPrecio(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer campaniaProductoId,
            @Valid @RequestBody CampaniaProductoPrecioDTO dto) {
        return ResponseEntity.ok(service.actualizarPrecio(campaniaProductoId, dto.precioManual()));
    }

    @PostMapping("/sincronizar")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<SincronizacionResultadoDTO> sincronizar() {
        return ResponseEntity.ok(syncService.sincronizar(TiendaNubeService.STORE_HOGAR));
    }
}
```

- [ ] **Step 2: Compilar y correr la suite del dominio**

Run: `cd supermaster-backend && ./mvnw -q -o compile && ./mvnw -q -o test -Dtest=CampaniaSyncServiceTest`
Expected: BUILD SUCCESS; tests PASS.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/campania/controller/CampaniaController.java
git commit -m "feat(campania): CampaniaController con CRUD, detalle, precio y sincronizar"
```

- [ ] **Step 4: Verificación manual de arranque** (requiere las tablas creadas en Task 1)

Run: `cd supermaster-backend && ./mvnw -q -o spring-boot:run` (Ctrl+C tras el arranque)
Expected: la app levanta sin error de validación de schema (Hibernate valida `campania` y `campania_producto`). Probar `GET http://localhost:8080/api/campanias` con token → `200` y página vacía.

---

## FASE 6 — Frontend: sección lista de campañas

> Patrón base: `src/app/marcas/`. Reutilizar `fetchAPI`, `buildListParams`, `withAuditOrigin`, `notificar`, `Table`, `EditableCell`.

### Task 11: Tipos y servicio

**Files:**
- Create: `supermaster-frontend/src/app/campanias/types.ts`
- Create: `supermaster-frontend/src/app/campanias/campaniasService.ts`

- [ ] **Step 1: `types.ts`**

```typescript
export type CampaniaDTO = {
	id: number;
	tnCategoriaId: number;
	nombre: string;
	canalId: number;
	canalNombre: string;
	fechaDesde: string | null;
	fechaHasta: string | null;
	activa: boolean;
	fechaUltimaSync: string | null;
	observaciones: string | null;
	cantidadProductos: number;
};

export type CampaniaProductoDTO = {
	id: number;
	productoId: number;
	sku: string;
	descripcion: string | null;
	costo: number | null;
	precioManual: number | null;
};

export type SincronizacionResultado = {
	categoriasImportadas: number;
	productosVinculados: number;
	skusSinMatch: string[];
};
```

- [ ] **Step 2: `campaniasService.ts`**

```typescript
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { AuditOrigin, FilterValue, buildListParams, withAuditOrigin } from "../utils/apiHelpers";

const API_URL = `${API_BASE_URL}/api/campanias`;

// READ: lista paginada de campañas
export const getCampaniasAPI = async (
	page: number,
	size: number,
	filters: Record<string, FilterValue> = {},
	sort = "id,desc",
) => {
	const response = await fetchAPI(`${API_URL}?${buildListParams(page, size, sort, filters)}`);
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

// READ: una campaña por id
export const getCampaniaAPI = async (id: number) => {
	const response = await fetchAPI(`${API_URL}/${id}`);
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

// READ: productos de una campaña (paginado)
export const getCampaniaProductosAPI = async (
	id: number,
	page: number,
	size: number,
	sort = "id,asc",
) => {
	const response = await fetchAPI(`${API_URL}/${id}/productos?${buildListParams(page, size, sort, {})}`);
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

// UPDATE: vigencia / estado / observaciones de una campaña
export const updateCampaniaAPI = async (
	id: number,
	data: { fechaDesde?: string | null; fechaHasta?: string | null; activa?: boolean; observaciones?: string | null },
	origin: AuditOrigin = "API",
) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar la campaña");
	return await response.json();
};

// UPDATE: precio manual de un producto dentro de la campaña
export const updateCampaniaPrecioAPI = async (
	campaniaProductoId: number,
	precioManual: number | null,
	origin: AuditOrigin = "INLINE",
) => {
	const response = await fetchAPI(`${API_URL}/productos/${campaniaProductoId}/precio`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify({ precioManual }),
	});
	if (!response.ok) throw new Error("Error al actualizar el precio");
	return await response.json();
};

// ACTION: disparar la sincronización con Tienda Nube
export const sincronizarCampaniasAPI = async (origin: AuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/sincronizar`, {
		method: "POST",
		headers: withAuditOrigin(origin),
	});
	if (!response.ok) throw new Error("Error al sincronizar con Tienda Nube");
	return await response.json();
};
```

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/campanias/types.ts supermaster-frontend/src/app/campanias/campaniasService.ts
git commit -m "feat(front/campanias): tipos y servicio API"
```

---

### Task 12: Hook `useCampanias`

**Files:**
- Create: `supermaster-frontend/src/app/campanias/useCampanias.ts`

- [ ] **Step 1: Escribir el hook** (calcado de `useMarcas`, con `sincronizar` y `updateCampania`)

```typescript
"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import { CampaniaDTO, SincronizacionResultado } from "./types";
import { getCampaniasAPI, updateCampaniaAPI, sincronizarCampaniasAPI } from "./campaniasService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useCampanias(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,desc";
	const [campanias, setCampanias] = useState<CampaniaDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const [isSyncing, setIsSyncing] = useState(false);
	const latestRequestIdRef = useRef(0);

	const getCampanias = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<CampaniaDTO> = await getCampaniasAPI(pageIndex, pageSize, filters, sortParam);
			if (latestRequestIdRef.current !== requestId) return;
			setCampanias(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			if (latestRequestIdRef.current !== requestId) return;
			setError(getErrorMessage(err, "Error desconocido"));
			setCampanias([]);
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getCampanias();
	}, [getCampanias]);

	const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;

	const updateCampania = async (
		id: number,
		data: Partial<Pick<CampaniaDTO, "fechaDesde" | "fechaHasta" | "activa" | "observaciones">>,
	) => {
		try {
			const actualizado: CampaniaDTO = await updateCampaniaAPI(id, data, "INLINE");
			setCampanias((prev) => prev.map((c) => (c.id === id ? { ...c, ...actualizado } : c)));
			notificar.success(`[Campañas] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const sincronizar = async () => {
		setIsSyncing(true);
		try {
			const r: SincronizacionResultado = await sincronizarCampaniasAPI("API");
			await getCampanias();
			const sinMatch = r.skusSinMatch.length;
			notificar.success(
				`[Campañas] Sincronizado: ${r.categoriasImportadas} categorías, ${r.productosVinculados} productos` +
				(sinMatch > 0 ? `. ${sinMatch} SKU(s) sin match en la BD.` : "."),
			);
			return r;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al sincronizar"));
			throw e;
		} finally {
			setIsSyncing(false);
		}
	};

	return {
		campanias,
		totalRecords,
		isLoading,
		error,
		pageCount,
		isSyncing,
		updateCampania,
		sincronizar,
		refresh: getCampanias,
	};
}
```

- [ ] **Step 2: Commit**

```bash
git add supermaster-frontend/src/app/campanias/useCampanias.ts
git commit -m "feat(front/campanias): hook useCampanias con sincronizar"
```

---

### Task 13: Columnas y página de lista

**Files:**
- Create: `supermaster-frontend/src/app/campanias/columns.tsx`
- Create: `supermaster-frontend/src/app/campanias/page.tsx`

- [ ] **Step 1: `columns.tsx`** (nombre read-only; vigencia y `activa` editables; link a detalle)

```typescript
"use client";

import Link from "next/link";
import { EyeIcon } from "@heroicons/react/24/outline";
import { ColumnDef } from "@tanstack/react-table";
import { CampaniaDTO } from "./types";
import EditableCell from "../components/Table/core/EditableCell";
import { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";

export function getColumns(canEdit = true): ColumnDef<CampaniaDTO>[] {
	return [
		{ accessorKey: "id", header: "ID", size: 50, enableColumnFilter: false },
		{ accessorKey: "nombre", header: "Campaña", enableColumnFilter: false },
		{
			accessorKey: "cantidadProductos",
			header: "Productos",
			size: 90,
			enableSorting: false,
			enableColumnFilter: false,
		},
		{
			accessorKey: "fechaDesde",
			header: "Desde",
			size: 130,
			meta: { editable: true },
			cell: ({ getValue, row, column, table }) => (
				<EditableCell
					initialValue={(getValue() as string) ?? ""}
					type="date"
					disabled={!canEdit}
					onSave={(v) => (table.options.meta as any)?.updateData?.(row.index, column.id, v || null)}
				/>
			),
			enableColumnFilter: false,
		},
		{
			accessorKey: "fechaHasta",
			header: "Hasta",
			size: 130,
			meta: { editable: true },
			cell: ({ getValue, row, column, table }) => (
				<EditableCell
					initialValue={(getValue() as string) ?? ""}
					type="date"
					disabled={!canEdit}
					onSave={(v) => (table.options.meta as any)?.updateData?.(row.index, column.id, v || null)}
				/>
			),
			enableColumnFilter: false,
		},
		{
			accessorKey: "activa",
			header: "Activa",
			size: 80,
			enableSorting: false,
			cell: ({ getValue, row, table }) => (
				<input
					type="checkbox"
					checked={!!getValue()}
					disabled={!canEdit}
					onChange={(e) => (table.options.meta as any)?.updateData?.(row.index, "activa", e.target.checked)}
					className="w-4 h-4 cursor-pointer align-middle"
				/>
			),
			enableColumnFilter: false,
		},
		{
			accessorKey: "fechaUltimaSync",
			header: "Última sync",
			size: 160,
			enableColumnFilter: false,
			cell: ({ getValue }) => {
				const v = getValue() as string | null;
				return v ? new Date(v).toLocaleString("es-AR") : "—";
			},
		},
		{
			id: "detalle",
			header: "Productos",
			size: 110,
			enableSorting: false,
			cell: ({ row }) => (
				<Link
					href={`/campanias/${row.original.id}`}
					title="Ver productos de la campaña"
					className={getTableActionButtonClasses("primary")}
				>
					<EyeIcon className="w-3.5 h-3.5" />
					Ver productos
				</Link>
			),
		},
	];
}
```

Nota de verificación: confirmar que `EditableCell` acepta una prop `type` (`"date"`). Si no la soporta, usar `<input type="date">` inline como en la columna `activa`. Revisar `src/app/components/Table/core/EditableCell.tsx` antes de implementar.

- [ ] **Step 2: `page.tsx`** (lista + botón Sincronizar)

```typescript
"use client";
import { useState, useMemo } from "react";
import { MegaphoneIcon, ArrowPathIcon } from "@heroicons/react/24/outline";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import Button from "../components/Button/Button";
import SearchInput from "../components/SearchInput/SearchInput";
import { type SortingState } from "@tanstack/react-table";
import { useAuth } from "../context/AuthContext";
import { useCampanias } from "./useCampanias";
import { getColumns } from "./columns";

export default function CampaniasPage() {
	const { hasPermiso } = useAuth();
	const canEdit = hasPermiso("INTEGRACIONES_EDITAR");

	const [pageIndex, setPageIndex] = useState(0);
	const [pageSize, setPageSize] = useState(() => getInitialPageSize("campanias"));
	const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
	const [filters, setFilters] = useState<any>({ search: "" });

	const { campanias, totalRecords, isLoading, error, pageCount, isSyncing, updateCampania, sincronizar } =
		useCampanias(pageIndex, pageSize, filters, sorting);

	const columns = useMemo(() => getColumns(canEdit), [canEdit]);

	const handleUpdate = (rowIndex: number, columnId: string, value: unknown) => {
		const c = campanias[rowIndex];
		updateCampania(c.id, { [columnId]: value } as any);
	};

	const handleGlobalSearch = (valor: string) => {
		setFilters((prev: any) => ({ ...prev, search: valor }));
		setPageIndex(0);
	};

	return (
		<main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
			<div className="flex justify-between items-center mb-3">
				<h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
					<MegaphoneIcon className="w-8 h-8 text-gray-600" />
					Campañas Tienda Nube
				</h1>
				<Button variant="dark" onClick={() => sincronizar()} disabled={!canEdit || isSyncing}>
					<ArrowPathIcon className={`w-4 h-4 ${isSyncing ? "animate-spin" : ""}`} />
					{isSyncing ? "Sincronizando..." : "Sincronizar"}
				</Button>
			</div>

			{error ? <ErrorBanner message={error} /> : (
				<div className="flex-1 min-h-0 overflow-hidden flex flex-col">
					<Table
						searchSlot={<SearchInput placeholder="Buscar campaña..." onSearch={(v) => { if (v !== filters.search) handleGlobalSearch(v); }} initialValue={filters.search} className="w-64" />}
						tableId="campanias"
						data={campanias}
						isLoading={isLoading}
						columns={columns}
						globalFilter={filters.search}
						setGlobalFilter={handleGlobalSearch}
						sorting={sorting}
						setSorting={setSorting}
						pageIndex={pageIndex}
						pageSize={pageSize}
						pageCount={pageCount}
						onPageChange={setPageIndex}
						onPageSizeChange={setPageSize}
						totalRecords={totalRecords}
						updateData={handleUpdate}
					/>
				</div>
			)}
		</main>
	);
}
```

- [ ] **Step 3: Verificar compilación del front**

Run: `cd supermaster-frontend && npm run build` (o `npx tsc --noEmit` si está disponible y es más rápido)
Expected: sin errores de tipos. Ajustar props de `Table`/`SearchInput`/`Button` si el typecheck marca diferencias con la firma real.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/campanias/columns.tsx supermaster-frontend/src/app/campanias/page.tsx
git commit -m "feat(front/campanias): columnas y página de lista con botón Sincronizar"
```

---

## FASE 7 — Frontend: detalle de campaña (productos + precio)

### Task 14: Hook y columnas del detalle

**Files:**
- Create: `supermaster-frontend/src/app/campanias/[id]/useCampaniaDetalle.ts`
- Create: `supermaster-frontend/src/app/campanias/[id]/columns.tsx`

- [ ] **Step 1: `useCampaniaDetalle.ts`**

```typescript
"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../../utils/notificar";
import { CampaniaDTO, CampaniaProductoDTO } from "../types";
import { getCampaniaAPI, getCampaniaProductosAPI, updateCampaniaPrecioAPI } from "../campaniasService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useCampaniaDetalle(
	campaniaId: number,
	pageIndex: number,
	pageSize: number,
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [campania, setCampania] = useState<CampaniaDTO | null>(null);
	const [productos, setProductos] = useState<CampaniaProductoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const latestRequestIdRef = useRef(0);

	const cargar = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const [cab, page]: [CampaniaDTO, PageResponse<CampaniaProductoDTO>] = await Promise.all([
				getCampaniaAPI(campaniaId),
				getCampaniaProductosAPI(campaniaId, pageIndex, pageSize, sortParam),
			]);
			if (latestRequestIdRef.current !== requestId) return;
			setCampania(cab);
			setProductos(page.content || []);
			setTotalRecords(page.page?.totalElements || 0);
		} catch (err: unknown) {
			if (latestRequestIdRef.current !== requestId) return;
			setError(getErrorMessage(err, "Error desconocido"));
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [campaniaId, pageIndex, pageSize, sortParam]);

	useEffect(() => {
		cargar();
	}, [cargar]);

	const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;

	const updatePrecio = async (campaniaProductoId: number, precio: number | null) => {
		try {
			const actualizado: CampaniaProductoDTO = await updateCampaniaPrecioAPI(campaniaProductoId, precio, "INLINE");
			setProductos((prev) => prev.map((p) => (p.id === campaniaProductoId ? { ...p, ...actualizado } : p)));
			notificar.success(`[Campañas] Precio actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar el precio"));
			throw e;
		}
	};

	return { campania, productos, totalRecords, isLoading, error, pageCount, updatePrecio, refresh: cargar };
}
```

- [ ] **Step 2: `columns.tsx` del detalle** (SKU/descr/costo read-only; `precioManual` editable)

```typescript
"use client";

import { ColumnDef } from "@tanstack/react-table";
import { CampaniaProductoDTO } from "../types";
import EditableCell from "../../components/Table/core/EditableCell";

export function getColumns(canEdit = true): ColumnDef<CampaniaProductoDTO>[] {
	return [
		{ accessorKey: "sku", header: "SKU", size: 120, enableColumnFilter: false },
		{ accessorKey: "descripcion", header: "Descripción", enableColumnFilter: false },
		{
			accessorKey: "costo",
			header: "Costo",
			size: 110,
			enableColumnFilter: false,
			cell: ({ getValue }) => {
				const v = getValue() as number | null;
				return v != null ? v.toLocaleString("es-AR", { style: "currency", currency: "ARS" }) : "—";
			},
		},
		{
			accessorKey: "precioManual",
			header: "Precio campaña",
			size: 140,
			meta: { editable: true },
			cell: ({ getValue, row, table }) => (
				<EditableCell
					initialValue={getValue() != null ? String(getValue()) : ""}
					type="number"
					disabled={!canEdit}
					onSave={(v) => {
						const parsed = v === "" || v == null ? null : Number(v);
						(table.options.meta as any)?.updateData?.(row.index, "precioManual", parsed);
					}}
				/>
			),
			enableColumnFilter: false,
		},
	];
}
```

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/campanias/\[id\]/useCampaniaDetalle.ts supermaster-frontend/src/app/campanias/\[id\]/columns.tsx
git commit -m "feat(front/campanias): hook y columnas del detalle de campaña"
```

---

### Task 15: Página de detalle

**Files:**
- Create: `supermaster-frontend/src/app/campanias/[id]/page.tsx`

- [ ] **Step 1: `page.tsx` del detalle**

```typescript
"use client";
import { useState, useMemo } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeftIcon, MegaphoneIcon } from "@heroicons/react/24/outline";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import Table, { getInitialPageSize } from "../../components/Table/core/Table";
import Button from "../../components/Button/Button";
import { type SortingState } from "@tanstack/react-table";
import { useAuth } from "../../context/AuthContext";
import { useCampaniaDetalle } from "./useCampaniaDetalle";
import { getColumns } from "./columns";

export default function CampaniaDetallePage() {
	const params = useParams();
	const router = useRouter();
	const campaniaId = Number(params.id);
	const { hasPermiso } = useAuth();
	const canEdit = hasPermiso("INTEGRACIONES_EDITAR");

	const [pageIndex, setPageIndex] = useState(0);
	const [pageSize, setPageSize] = useState(() => getInitialPageSize("campania-detalle"));
	const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: false }]);

	const { campania, productos, totalRecords, isLoading, error, pageCount, updatePrecio } =
		useCampaniaDetalle(campaniaId, pageIndex, pageSize, sorting);

	const columns = useMemo(() => getColumns(canEdit), [canEdit]);

	const handleUpdate = (rowIndex: number, _columnId: string, value: unknown) => {
		const p = productos[rowIndex];
		updatePrecio(p.id, value as number | null);
	};

	return (
		<main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
			<div className="flex items-center gap-3 mb-3">
				<Button variant="light" onClick={() => router.push("/campanias")}>
					<ArrowLeftIcon className="w-4 h-4" /> Volver
				</Button>
				<h1 className="text-2xl font-bold text-gray-800 flex items-center gap-2">
					<MegaphoneIcon className="w-7 h-7 text-gray-600" />
					{campania ? campania.nombre : "Campaña"}
				</h1>
			</div>

			{error ? <ErrorBanner message={error} /> : (
				<div className="flex-1 min-h-0 overflow-hidden flex flex-col">
					<Table
						tableId="campania-detalle"
						data={productos}
						isLoading={isLoading}
						columns={columns}
						sorting={sorting}
						setSorting={setSorting}
						pageIndex={pageIndex}
						pageSize={pageSize}
						pageCount={pageCount}
						onPageChange={setPageIndex}
						onPageSizeChange={setPageSize}
						totalRecords={totalRecords}
						updateData={handleUpdate}
					/>
				</div>
			)}
		</main>
	);
}
```

- [ ] **Step 2: Verificar build del front**

Run: `cd supermaster-frontend && npm run build`
Expected: compila sin errores de tipo.

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/campanias/\[id\]/page.tsx
git commit -m "feat(front/campanias): página de detalle con precios editables"
```

---

## FASE 8 — Navegación y verificación final

### Task 16: Registrar en el menú

**Files:**
- Modify: `supermaster-frontend/src/app/components/navigation/navigationConfig.tsx`

- [ ] **Step 1: Agregar el item** dentro del array `items` de la sección **Integraciones** (junto a "DUX ERP"). Usar un ícono ya importado (`MegaphoneIcon` no está en los imports actuales → agregarlo al bloque de import de heroicons, o reutilizar `BuildingStorefrontIcon` que ya está importado).

```typescript
{
    href: "/campanias",
    label: "Campañas Tienda Nube",
    description: "Categorías de TN y precios de campaña por producto",
    icon: BuildingStorefrontIcon,
    color: "fuchsia",
    requiredPermission: "INTEGRACIONES_VER",
    requiredRoles: ["ADMIN"],
},
```

- [ ] **Step 2: Verificar build**

Run: `cd supermaster-frontend && npm run build`
Expected: compila; el item aparece en el menú Integraciones.

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/components/navigation/navigationConfig.tsx
git commit -m "feat(front/campanias): item de navegación en Integraciones"
```

---

### Task 17: Verificación end-to-end manual

- [ ] **Step 1: Levantar backend y frontend** (tablas ya creadas en Task 1)

Run backend: `cd supermaster-backend && ./mvnw -o spring-boot:run`
Run frontend: `cd supermaster-frontend && npm run dev`

- [ ] **Step 2: Probar el flujo completo**

1. Loguearse con un usuario ADMIN con `INTEGRACIONES_VER`/`INTEGRACIONES_EDITAR`.
2. Ir a Integraciones → Campañas Tienda Nube.
3. Apretar **Sincronizar** → verificar el toast con el resumen (categorías / productos / SKUs sin match).
4. Verificar que aparecen las campañas (categorías de KT HOGAR) con su conteo de productos.
5. Editar `fechaDesde`/`fechaHasta`/`activa` de una campaña → persiste y muestra toast.
6. Entrar a "Ver productos" de una campaña → editar el **precio campaña** de un producto → persiste (recargar y verificar).
7. Volver a Sincronizar → confirmar que el precio cargado **se preservó** en los productos que siguen tageados.

Expected: todo el flujo funciona sin errores en consola ni en el log del backend.

- [ ] **Step 3: Correr la suite de tests backend completa**

Run: `cd supermaster-backend && ./mvnw -q -o test`
Expected: BUILD SUCCESS (incluye `CampaniaSyncServiceTest` y no rompe los tests existentes).

---

## Notas de implementación / riesgos a vigilar

- **Firmas exactas de componentes front:** `Table`, `EditableCell`, `Button`, `SearchInput` se usaron según el patrón de `marcas`. Antes de cada página, abrir el componente real y confirmar nombres de props (`updateData`, `onPageChange`, `searchSlot`, y si `EditableCell` soporta `type="date"`/`type="number"`). Ajustar si difiere; no inventar props.
- **`getInitialPageSize`** se importa desde `components/Table/core/Table` (igual que en `marcas`).
- **`@/lib/errors` (`getErrorMessage`)** y `notificar` ya existen y se usan en todos los hooks; importarlos igual que en `useMarcas`.
- **Schema primero:** nada del backend arranca (validate) hasta correr `crear-tablas-campania.sql`. La compilación de Java/MapStruct sí funciona sin la BD.
- **Canal NUBE:** el sync falla con 404 claro si no existe el canal `NUBE`. Verificar que esté en `canales` (lo está según `nube-conceptos.sql`).
- **Fuera de alcance (no implementar):** publicar precios a TN, KT GASTRO, % descuento, sync automático, escritura de categorías/tageo hacia TN.
```
