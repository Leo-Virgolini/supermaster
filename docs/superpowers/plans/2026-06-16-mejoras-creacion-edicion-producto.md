# Mejoras en creación/edición de producto — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corregir y mejorar el formulario de alta/edición de producto y su backend (9 ítems): imagen por SKU, KT HOGAR por defecto en precio inflado, quitar margen fijo, validaciones obligatorias, INSUMO en tag, mover aptos, bug del 0 en costo, recálculo al crear y nueva sección "Canales de venta".

**Architecture:** Cambios en el dominio `producto` del backend (entidad/DTOs/servicios/cálculo + script SQL) y en la sección `productos` del frontend (modal en `page.tsx`, `PreciosInfladosSection.tsx`, `columns.tsx`, servicios y tipos). Se respeta el patrón existente (validaciones de negocio en el servicio, `RecalculoPendienteService` para recálculo, flujo de dos llamadas crear-producto + upsert-margen).

**Tech Stack:** Spring Boot 4 / Java 25 / JPA / JUnit5 + Mockito + AssertJ (backend); Next.js 16 / React 19 / TypeScript / Tailwind (frontend). `ddl-auto=validate` → DDL manual.

**Referencia de diseño:** `docs/superpowers/specs/2026-06-16-mejoras-creacion-edicion-producto-design.md`

---

## Convenciones
- Trabajar directo sobre `main` (convención del repo). Terminar cada commit con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Backend compila offline: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"` (o `& .\mvnw.cmd -q -o compile`).
- Tests backend: `cmd /c "mvnw.cmd -q -o test -Dtest=NombreTest"`.
- Frontend typecheck: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`.
- `BadRequestException` está en `ar.com.leo.super_master_backend.dominio.common.exception`.

---

## File Structure

**Backend modificado:**
- `entity/ProductoMargen.java` — quitar 2 campos margen fijo.
- `entity/Tag.java` — agregar `INSUMO`.
- `dto/ProductoMargenDTO.java`, `dto/ProductoMargenPatchDTO.java` — quitar margen fijo.
- `calculo/dto/SimulacionPrecioInputDTO.java` — quitar margen fijo.
- `service/ProductoMargenServiceImpl.java` — quitar margen fijo + validación "al menos un margen > 0".
- `service/ProductoServiceImpl.java` — recálculo al crear + validación producto simple.
- `calculo/service/CalculoPrecioServiceImpl.java` — quitar los 6 usos de margen fijo.
- `src/main/resources/db/quitar-margen-fijo.sql` — DROP columns (nuevo).
- Tests: `ProductoMargenValidacionTest.java`, `ProductoSimpleValidacionTest.java` (nuevos).

**Frontend modificado (`src/app/productos/`):**
- `page.tsx` — costo, validaciones, tag, aptos→dimensiones, imagen por SKU, sección Canales de venta, quitar inputs margen fijo.
- `PreciosInfladosSection.tsx` — KT HOGAR por defecto.
- `columns.tsx` — quitar columnas margen fijo + `MARGEN_FIELDS`.
- `types.ts` — `Tag` (+INSUMO), `ProductoDTO` (quitar margen fijo).
- `productoMargenService.ts` — quitar margen fijo del tipo.

---

## FASE 1 — Backend: quitar margen fijo

### Task 1: Quitar margen fijo de la fórmula de cálculo

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/calculo/service/CalculoPrecioServiceImpl.java`

- [ ] **Step 1: Quitar la obtención del margen fijo (PASO 6)**

Reemplazar (líneas ~565-573):
```java
        // ============================================
        // PASO 6: Margen fijo (de producto_canal)
        // ============================================

        // ============================================
        // PASO 6: Margen fijo (de producto_canal)
        // ============================================
        BigDecimal margenFijo = obtenerMargenFijo(productoMargen, conceptos);
        // Nota: El margen fijo se aplicará al final sobre el PVP, no aquí
```
por:
```java
```
(eliminar el bloque completo).

- [ ] **Step 2: Quitar el PASO 12 (suma al PVP en indicadores)**

Reemplazar (líneas ~725-730):
```java
        // ============================================
        // PASO 12: Margen fijo
        // ============================================
        if (margenFijo.compareTo(BigDecimal.ZERO) > 0) {
            pvp = pvp.add(margenFijo);
        }

```
por:
```java
```

- [ ] **Step 3: Quitar el paso 12 de la fórmula paso a paso**

Reemplazar (líneas ~1336-1347):
```java
        // Paso 12: Margen fijo
        BigDecimal margenFijo = obtenerMargenFijo(productoMargen, conceptos);
        if (margenFijo.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pvpAntesMargenFijo = pvp;
            pvp = pvp.add(margenFijo);
            pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                    "Aplicar margen fijo",
                    "PVP = PVP + MARGEN_FIJO",
                    rd(pvp),
                    String.format("%s + %s = %s", fmt(pvpAntesMargenFijo), fmt(margenFijo), fmt(pvp)),
                    FormulaCalculoDTO.UNIDAD_MONEDA));
        }

```
por:
```java
```

- [ ] **Step 4: Quitar el `+ MARGEN_FIJO` del string de fórmula general**

Reemplazar (líneas ~1444-1446):
```java
        if (margenFijo.compareTo(BigDecimal.ZERO) > 0) {
            formulaGeneral.append(" + MARGEN_FIJO");
        }
```
por:
```java
```

- [ ] **Step 5: Quitar el método `obtenerMargenFijo`**

Reemplazar (líneas ~2006-2021, el javadoc + método completo):
```java
    /**
     * Obtiene el margen fijo según los conceptos del canal.
     * - Si tiene MARGEN_MAYORISTA → usa margenFijoMayorista
     * - Si tiene MARGEN_MINORISTA → usa margenFijoMinorista
     */
    private BigDecimal obtenerMargenFijo(ProductoMargen productoMargen, List<CanalConcepto> conceptos) {
        TipoMargenCanal tipo = detectarTipoMargenRequerido(conceptos);
        if (tipo == TipoMargenCanal.MAYORISTA) {
            return productoMargen.getMargenFijoMayorista() != null ? productoMargen.getMargenFijoMayorista() : BigDecimal.ZERO;
        }
        if (tipo == TipoMargenCanal.MINORISTA) {
            return productoMargen.getMargenFijoMinorista() != null ? productoMargen.getMargenFijoMinorista() : BigDecimal.ZERO;
        }
        // Si el canal no exige ningún margen específico, retorna ZERO
        return BigDecimal.ZERO;
    }
```
por:
```java
```

- [ ] **Step 6: Quitar el set de margen fijo en la simulación**

Reemplazar (líneas ~2625-2632):
```java
    private ProductoMargen construirMargenHipotetico(SimulacionPrecioInputDTO input) {
        ProductoMargen m = new ProductoMargen();
        m.setMargenMinorista(input.margenMinorista());
        m.setMargenMayorista(input.margenMayorista());
        m.setMargenFijoMinorista(input.margenFijoMinorista());
        m.setMargenFijoMayorista(input.margenFijoMayorista());
        return m;
    }
```
por:
```java
    private ProductoMargen construirMargenHipotetico(SimulacionPrecioInputDTO input) {
        ProductoMargen m = new ProductoMargen();
        m.setMargenMinorista(input.margenMinorista());
        m.setMargenMayorista(input.margenMayorista());
        return m;
    }
```

- [ ] **Step 7: Buscar usos residuales**

Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"`
Si falla por referencias a `margenFijo`/`getMargenFijoMinorista`/`getMargenFijoMayorista`/`obtenerMargenFijo` en este archivo, resolverlas (eliminarlas). Repetir hasta compilar (puede fallar todavía por los DTOs/entity que se tocan en tasks siguientes — en ese caso continuar y compilar al final de la Fase 1).

- [ ] **Step 8: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/calculo/service/CalculoPrecioServiceImpl.java
git commit -m "refactor(calculo): quitar el componente de margen fijo de la formula"
```

---

### Task 2: Quitar margen fijo de entidad y DTOs

**Files:**
- Modify: `entity/ProductoMargen.java`, `dto/ProductoMargenDTO.java`, `dto/ProductoMargenPatchDTO.java`, `calculo/dto/SimulacionPrecioInputDTO.java`

- [ ] **Step 1: `ProductoMargen.java` — quitar los 2 campos**

Eliminar el bloque:
```java
    // ---------------------------
    // MÁRGENES FIJOS (en pesos)
    // ---------------------------
    @Column(name = "margen_fijo_minorista", precision = 10, scale = 2)
    private BigDecimal margenFijoMinorista;

    @Column(name = "margen_fijo_mayorista", precision = 10, scale = 2)
    private BigDecimal margenFijoMayorista;
```

- [ ] **Step 2: `ProductoMargenDTO.java` — quitar los 2 campos**

Eliminar del record:
```java
        @PositiveOrZero(message = "El margen fijo minorista debe ser mayor o igual a 0")
        BigDecimal margenFijoMinorista,
        @PositiveOrZero(message = "El margen fijo mayorista debe ser mayor o igual a 0")
        BigDecimal margenFijoMayorista,
```
(la coma del campo anterior `margenMayorista` queda; el siguiente campo es `observaciones`).

- [ ] **Step 3: `ProductoMargenPatchDTO.java` — quitar los 2 campos**

Eliminar:
```java
    private JsonNullable<BigDecimal> margenFijoMinorista = JsonNullable.undefined();
    private JsonNullable<BigDecimal> margenFijoMayorista = JsonNullable.undefined();
```

- [ ] **Step 4: `SimulacionPrecioInputDTO.java` — quitar los 2 campos**

Abrir el archivo, localizar `margenFijoMinorista` / `margenFijoMayorista` (≈ líneas 60-61) y eliminarlos del record, ajustando comas. (Las llamadas a `input.margenFijoMinorista()` ya se quitaron en Task 1 Step 6.)

- [ ] **Step 5: Commit** (compila al final de Fase 1)
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/ProductoMargen.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ProductoMargenDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ProductoMargenPatchDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/calculo/dto/SimulacionPrecioInputDTO.java
git commit -m "refactor(producto): quitar margen fijo de entidad y DTOs"
```

---

### Task 3: Quitar margen fijo de `ProductoMargenServiceImpl`

**Files:**
- Modify: `service/ProductoMargenServiceImpl.java`

- [ ] **Step 1: Quitar variables de "anterior" y set en `guardar`**

En `guardar(...)`: eliminar las declaraciones `BigDecimal margenFijoMinoristaAnterior = null;` y `BigDecimal margenFijoMayoristaAnterior = null;`, las asignaciones `margenFijoMinoristaAnterior = pm.getMargenFijoMinorista();` / `margenFijoMayoristaAnterior = pm.getMargenFijoMayorista();`, y los `pm.setMargenFijoMinorista(...)` / `pm.setMargenFijoMayorista(...)` del bloque `else` (creación).

- [ ] **Step 2: Quitar detección de cambios de margen fijo (en `guardar` y `patch`)**

Reemplazar en ambos métodos el bloque de detección:
```java
        boolean cambioMargenMinorista = !Objects.equals(margenMinoristaAnterior, pm.getMargenMinorista());
        boolean cambioMargenMayorista = !Objects.equals(margenMayoristaAnterior, pm.getMargenMayorista());
        boolean cambioMargenFijoMinorista = !Objects.equals(margenFijoMinoristaAnterior, pm.getMargenFijoMinorista());
        boolean cambioMargenFijoMayorista = !Objects.equals(margenFijoMayoristaAnterior, pm.getMargenFijoMayorista());

        if (cambioMargenMinorista || cambioMargenMayorista || cambioMargenFijoMinorista || cambioMargenFijoMayorista) {
```
por:
```java
        boolean cambioMargenMinorista = !Objects.equals(margenMinoristaAnterior, pm.getMargenMinorista());
        boolean cambioMargenMayorista = !Objects.equals(margenMayoristaAnterior, pm.getMargenMayorista());

        if (cambioMargenMinorista || cambioMargenMayorista) {
```
(y quitar las variables `margenFijoMinoristaAnterior`/`margenFijoMayoristaAnterior` del `patch`).

- [ ] **Step 3: Quitar set de margen fijo en `patch`**

Eliminar el bloque:
```java
        if (presente(patchDto.getMargenFijoMinorista())) {
            pm.setMargenFijoMinorista(leerDecimalNoNegativoOpcional(patchDto.getMargenFijoMinorista(), "margenFijoMinorista"));
        }
        if (presente(patchDto.getMargenFijoMayorista())) {
            pm.setMargenFijoMayorista(leerDecimalNoNegativoOpcional(patchDto.getMargenFijoMayorista(), "margenFijoMayorista"));
        }
```
y quitar del check de "patch vacío" las condiciones `&& !presente(patchDto.getMargenFijoMinorista()) && !presente(patchDto.getMargenFijoMayorista())`.

- [ ] **Step 4: Quitar margen fijo del snapshot de auditoría**

En `capturarSnapshot(...)`, eliminar:
```java
        snap.put("margenFijoMinorista", pm.getMargenFijoMinorista() != null ? pm.getMargenFijoMinorista().toPlainString() : "");
        snap.put("margenFijoMayorista", pm.getMargenFijoMayorista() != null ? pm.getMargenFijoMayorista().toPlainString() : "");
```

- [ ] **Step 5: Verificar `ProductoMargenController` y el mapper**

Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"`
Si `ProductoMargenController` reconstruye el DTO con margen fijo, o el mapper MapStruct referencia esos campos, ajustarlos (quitar). Repetir hasta compilar el módulo de margen.

- [ ] **Step 6: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/
git commit -m "refactor(producto): quitar margen fijo del servicio de margen y auditoria"
```

---

### Task 4: Quitar margen fijo de `ProductoDTO` (backend) y script SQL

**Files:**
- Modify: `dto/ProductoDTO.java`
- Create: `src/main/resources/db/quitar-margen-fijo.sql`

- [ ] **Step 1: Quitar margen fijo de `ProductoDTO.java`**

Eliminar del record `ProductoDTO` los campos `margenFijoMinorista` y `margenFijoMayorista` (≈ líneas 67-68) y ajustar comas. Ajustar el mapper/servicio que construye ese DTO (grep `margenFijoMinorista` en backend y eliminar usos restantes).

- [ ] **Step 2: Compilar TODO el backend**

Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"`
Expected: BUILD SUCCESS, sin referencias a margen fijo. Resolver cualquier residual.

- [ ] **Step 3: Script SQL**
```sql
-- Quita las columnas de margen fijo (ya no se usan). Ejecutar manualmente (ddl-auto=validate).
ALTER TABLE supermaster.producto_margen
    DROP COLUMN margen_fijo_minorista,
    DROP COLUMN margen_fijo_mayorista;
```

- [ ] **Step 4: Ejecutar el script en la BD local**

Run (PowerShell): correr el `.sql` con `mysql.exe` (host=localhost, user=root, pass=admin, db=supermaster). Verificar con `DESCRIBE supermaster.producto_margen;` que las columnas ya no están.

- [ ] **Step 5: Verificar arranque + suite de cálculo**

Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o test -Dtest=CalculoPrecioFormulaTest,RecalculoAutomaticoIntegrationTest"`
Expected: PASS (el cálculo sigue correcto sin el margen fijo). Si algún test fijaba un valor que dependía del margen fijo, ajustar el test (no debería: 0 productos lo usaban).

- [ ] **Step 6: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ProductoDTO.java supermaster-backend/src/main/resources/db/quitar-margen-fijo.sql
git commit -m "refactor(producto): quitar margen fijo de ProductoDTO + script DROP columns"
```

---

## FASE 2 — Backend: enum Tag, validaciones y recálculo al crear

### Task 5: Agregar `INSUMO` al enum Tag

**Files:**
- Modify: `entity/Tag.java`

- [ ] **Step 1: Editar el enum**
```java
package ar.com.leo.super_master_backend.dominio.producto.entity;

public enum Tag {
    MAQUINA,
    REPUESTO,
    MENAJE,
    INSUMO
}
```

- [ ] **Step 2: Verificar la columna**

La columna `tag` es `ENUM(...)` en MySQL o `VARCHAR`. Si es un `ENUM` de MySQL, agregar `INSUMO` requiere un `ALTER TABLE`. Verificar:
Run (PowerShell, mysql.exe): `SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='supermaster' AND TABLE_NAME='productos' AND COLUMN_NAME='tag';`
- Si es `varchar` → no hace falta DDL.
- Si es `enum('MAQUINA','REPUESTO','MENAJE')` → agregar al script `src/main/resources/db/quitar-margen-fijo.sql` (o uno nuevo `tag-insumo.sql`):
  `ALTER TABLE supermaster.productos MODIFY COLUMN tag ENUM('MAQUINA','REPUESTO','MENAJE','INSUMO');`
  y ejecutarlo.

- [ ] **Step 3: Compilar y commit**
```bash
cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Tag.java
git commit -m "feat(producto): agregar INSUMO al enum Tag"
```

---

### Task 6: Validación "al menos un margen > 0" (TDD)

**Files:**
- Modify: `service/ProductoMargenServiceImpl.java`
- Test: `src/test/java/ar/com/leo/super_master_backend/dominio/producto/service/ProductoMargenValidacionTest.java` (nuevo)

- [ ] **Step 1: Escribir el test**

```java
package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ProductoMargenValidacionTest {

    @Mock private ProductoMargenRepository repo;
    @Mock private ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMargenMapper mapper;
    @Mock private ProductoRepository productoRepository;
    @Mock private ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService recalculoPendienteService;
    @Mock private ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService auditoriaService;

    @InjectMocks private ProductoMargenServiceImpl service;

    @Test
    @DisplayName("guardar con ambos márgenes en 0 lanza BadRequestException")
    void ambosCeroRebota() {
        Producto p = new Producto(1);
        p.setSku("X");
        lenient().when(productoRepository.findById(1)).thenReturn(Optional.of(p));
        lenient().when(repo.findByProductoId(1)).thenReturn(Optional.empty());

        ProductoMargenDTO dto = new ProductoMargenDTO(
                null, 1, BigDecimal.ZERO, BigDecimal.ZERO, null);

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("margen");
    }
}
```
Nota: el constructor de `ProductoMargenDTO` tras la Task 2 es `(id, productoId, margenMinorista, margenMayorista, observaciones)`.

- [ ] **Step 2: Correr el test (debe fallar: hoy no valida)**

Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o test -Dtest=ProductoMargenValidacionTest"`
Expected: FAIL (no se lanza la excepción).

- [ ] **Step 3: Agregar la validación**

En `ProductoMargenServiceImpl`, agregar un helper privado:
```java
    private void validarAlMenosUnMargen(BigDecimal minorista, BigDecimal mayorista) {
        boolean minOk = minorista != null && minorista.compareTo(BigDecimal.ZERO) > 0;
        boolean mayOk = mayorista != null && mayorista.compareTo(BigDecimal.ZERO) > 0;
        if (!minOk && !mayOk) {
            throw new BadRequestException(
                    "Debe cargar al menos un margen (minorista o mayorista) mayor a 0.");
        }
    }
```
Llamarlo en `guardar(...)` justo antes de `pm = repo.save(pm);`, con los valores finales:
```java
        validarAlMenosUnMargen(pm.getMargenMinorista(), pm.getMargenMayorista());
        pm = repo.save(pm);
```
e igual en `patch(...)` antes de su `pm = repo.save(pm);`.

- [ ] **Step 4: Correr el test (debe pasar)**

Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o test -Dtest=ProductoMargenValidacionTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/service/ProductoMargenServiceImpl.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/service/ProductoMargenValidacionTest.java
git commit -m "feat(producto): exigir al menos un margen mayor a 0"
```

---

### Task 7: Validación "producto simple completo" + recálculo al crear (TDD)

**Files:**
- Modify: `service/ProductoServiceImpl.java`
- Test: `src/test/java/.../producto/service/ProductoSimpleValidacionTest.java` (nuevo)

- [ ] **Step 1: Implementar la validación y el recálculo al crear**

En `ProductoServiceImpl`, agregar el helper (junto a `validarAlMenosUnaClasificacion`):
```java
    /**
     * Regla de negocio: un producto SIMPLE (no combo) debe tener marca, origen,
     * proveedor, material y tag. En combos siguen siendo opcionales.
     */
    private void validarProductoSimpleCompleto(Producto entity) {
        if (Boolean.TRUE.equals(entity.getEsCombo())) {
            return; // los combos no exigen estos campos
        }
        if (entity.getMarca() == null) throw new BadRequestException("La marca es obligatoria para productos simples.");
        if (entity.getOrigen() == null) throw new BadRequestException("El origen es obligatorio para productos simples.");
        if (entity.getProveedor() == null) throw new BadRequestException("El proveedor es obligatorio para productos simples.");
        if (entity.getMaterial() == null) throw new BadRequestException("El material es obligatorio para productos simples.");
        if (entity.getTag() == null) throw new BadRequestException("El tag es obligatorio para productos simples.");
    }
```
Llamarlo en `crear`, `actualizar` y `patch` justo después de `validarAlMenosUnaClasificacion(entity);`.

En `crear(...)`, agregar el recálculo al final (antes del `return`):
```java
        productoRepository.save(entity);
        productoAuditoriaService.registrarCreacion(entity);
        programarRecalculoPostCommit("Producto creado", entity.getId());
        return productoMapper.toDTO(entity);
```
(`programarRecalculoPostCommit` ya existe en la clase y llama a `recalculoPendienteService.marcarProductoOCalcularInicial`.)

- [ ] **Step 2: Escribir el test (verifica la validación de producto simple)**

```java
package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica la regla "producto simple completo" invocando el método privado por reflexión
 * (no requiere levantar el contexto Spring).
 */
class ProductoSimpleValidacionTest {

    private void invocar(Producto p) throws Exception {
        ProductoServiceImpl svc = new ProductoServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        Method m = ProductoServiceImpl.class.getDeclaredMethod("validarProductoSimpleCompleto", Producto.class);
        m.setAccessible(true);
        try {
            m.invoke(svc, p);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void simpleSinMarcaRebota() {
        Producto p = new Producto();
        p.setEsCombo(false);
        assertThatThrownBy(() -> invocar(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("marca");
    }

    @Test
    void comboNoExige() throws Exception {
        Producto p = new Producto();
        p.setEsCombo(true);
        invocar(p); // no lanza
    }
}
```
Nota: el constructor de `ProductoServiceImpl` tiene 13 dependencias `final` (ver campos 51-63). Ajustar la cantidad de `null` a la firma real del constructor generado por `@RequiredArgsConstructor` (contar los `private final`). Si el constructor real difiere, adaptar el número de `null`.

- [ ] **Step 3: Correr los tests**

Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o test -Dtest=ProductoSimpleValidacionTest"`
Expected: PASS (ajustar el número de `null` del constructor si hay error de compilación).

- [ ] **Step 4: Suite completa del backend**

Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o test"`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/service/ProductoServiceImpl.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/service/ProductoSimpleValidacionTest.java
git commit -m "feat(producto): exigir campos en simples y recalcular al crear"
```

---

## FASE 3 — Frontend: quitar margen fijo

### Task 8: Quitar margen fijo del frontend

**Files:**
- Modify: `productos/page.tsx`, `productos/columns.tsx`, `productos/types.ts`, `productos/productoMargenService.ts`

- [ ] **Step 1: `productoMargenService.ts`** — quitar `margenFijoMinorista`/`margenFijoMayorista` de la interfaz `ProductoMargenDTO`.

- [ ] **Step 2: `types.ts`** — quitar `margenFijoMinorista`/`margenFijoMayorista` de `ProductoDTO`.

- [ ] **Step 3: `page.tsx`** —
  - Quitar los estados `margenFijoMinorista`/`margenFijoMayorista` (líneas ~279-280).
  - Quitar los dos `<label>` de "Margen fijo minorista/mayorista" del fieldset Márgenes (líneas ~1299-1307).
  - En `asociarMargenYRelaciones`, quitar las 2 líneas que agregan `margenFijoMinorista`/`margenFijoMayorista` al `margenDto` y su tipo.
  - Donde se cargan los márgenes al editar (buscar `setMargenFijoMinorista`/`setMargenFijoMayorista`), quitar esos sets.

- [ ] **Step 4: `columns.tsx`** —
  - Quitar las 2 columnas `margenFijoMinorista` (header "Fijo Min") y `margenFijoMayorista` (header "Fijo May") (líneas ~396-403).
  - Quitar `"margenFijoMinorista"` y `"margenFijoMayorista"` de la constante `MARGEN_FIELDS`.

- [ ] **Step 5: Typecheck**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: 0 errores. Resolver residuales de margen fijo.

- [ ] **Step 6: Commit**
```bash
git add supermaster-frontend/src/app/productos/page.tsx supermaster-frontend/src/app/productos/columns.tsx supermaster-frontend/src/app/productos/types.ts supermaster-frontend/src/app/productos/productoMargenService.ts
git commit -m "refactor(front/productos): quitar margen fijo del form y la tabla"
```

---

## FASE 4 — Frontend: validaciones, tag, costo, aptos

### Task 9: Bug del "0" en Costo Base

**Files:** Modify: `productos/page.tsx`

- [ ] **Step 1: Estado del costo a `number | ""`**

Cambiar (línea ~247):
```tsx
const [costo, setCosto] = useState(0);
```
por:
```tsx
const [costo, setCosto] = useState<number | "">("");
```

- [ ] **Step 2: Input de costo**

Cambiar el `onChange` y `value` del input de costo (líneas ~1237):
```tsx
        <input type="number" min={0} className={`${inputBaseClassName} !pl-7 ${formErrors.costo ? inputErrorClassName : ""}`} value={costo} onChange={e => { setCosto(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.costo) setFormErrors(p => ({ ...p, costo: "" })); }} required />
```

- [ ] **Step 3: Ajustar usos de `costo`**
  - En edición, donde se hace `setCosto(producto.costo ?? 0)`, cambiar a `setCosto(producto.costo ?? "")`.
  - En `resetForm`, donde se resetee costo a 0, cambiar a `""`.
  - En el payload de `handleCreate` (`costo,`), cambiar a `costo: costo === "" ? 0 : costo` (o validar antes, ver Task 11).

- [ ] **Step 4: Commit**
```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "fix(front/productos): costo base arranca vacio (sin 0 molesto)"
```

---

### Task 10: Tag — quitar "Sin tag", agregar Insumo

**Files:** Modify: `productos/types.ts`, `productos/page.tsx`

- [ ] **Step 1: `types.ts`** — `export type Tag = 'MAQUINA' | 'REPUESTO' | 'MENAJE' | 'INSUMO';`

- [ ] **Step 2: `page.tsx` — estado y select de tag**

Estado (línea ~301): `const [tag, setTag] = useState<"" | "MAQUINA" | "REPUESTO" | "MENAJE" | "INSUMO">("");`

Select (líneas ~1329-1337):
```tsx
<label className="block">
    <span className={fieldLabelClassName}>Tag {!esCombo && <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span>}</span>
    <select className={`${selectBaseClassName} ${formErrors.tag ? inputErrorClassName : ""}`} value={tag} onChange={e => { setTag(e.target.value as "" | "MAQUINA" | "REPUESTO" | "MENAJE" | "INSUMO"); if (formErrors.tag) setFormErrors(p => ({ ...p, tag: "" })); }}>
        <option value="">-- Seleccionar --</option>
        <option value="MAQUINA">Máquina</option>
        <option value="REPUESTO">Repuesto</option>
        <option value="MENAJE">Menaje</option>
        <option value="INSUMO">Insumo</option>
    </select>
    {formErrors.tag && <p className="mt-1 text-xs text-red-500">{formErrors.tag}</p>}
</label>
```
(Se reemplaza "Sin tag" por el placeholder "-- Seleccionar --" no válido para simples; agrega Insumo y el asterisk condicional.)

- [ ] **Step 3: Commit**
```bash
git add supermaster-frontend/src/app/productos/page.tsx supermaster-frontend/src/app/productos/types.ts
git commit -m "feat(front/productos): tag con Insumo y sin opcion 'Sin tag'"
```

---

### Task 11: Validaciones obligatorias en el form

**Files:** Modify: `productos/page.tsx`

- [ ] **Step 1: Extender `validateForm`**

Reemplazar el cuerpo de `validateForm` para agregar: costo > 0, al menos un margen, y (si no es combo) marca/origen/proveedor/material/tag:
```tsx
    const validateForm = (): boolean => {
        const errors: Record<string, string> = {};
        if (!sku.trim()) errors.sku = "El SKU es obligatorio";
        else if (sku.trim().length > 45) errors.sku = "Máximo 45 caracteres";
        else if (!editandoProductoId && skuYaExiste) errors.sku = "Ya existe un producto con este SKU";
        if (!descripcion.trim()) errors.descripcion = "La descripción es obligatoria";
        else if (descripcion.trim().length > 100) errors.descripcion = "Máximo 100 caracteres";
        if (!tituloWeb.trim()) errors.tituloWeb = "El Título Web es obligatorio";
        else if (tituloWeb.trim().length > 100) errors.tituloWeb = "Máximo 100 caracteres";
        if (costo === "" || Number(costo) <= 0) errors.costo = "El costo debe ser mayor a 0";
        if (uxb < 1) errors.uxb = "UxB debe ser al menos 1";
        if (!clasifGralId && !clasifGastroId) errors.clasificacion = "Seleccioná al menos una clasificación (general o gastronómica)";
        if (!tipoId) errors.tipoId = "El tipo es obligatorio";
        const tieneMargen = (margenMinorista !== "" && Number(margenMinorista) > 0) || (margenMayorista !== "" && Number(margenMayorista) > 0);
        if (!tieneMargen) errors.margen = "Cargá al menos un margen (minorista o mayorista) mayor a 0";
        if (!esCombo) {
            if (!marcaId) errors.marcaId = "La marca es obligatoria";
            if (!origenId) errors.origenId = "El origen es obligatorio";
            if (!proveedorId) errors.proveedorId = "El proveedor es obligatorio";
            if (!materialId) errors.materialId = "El material es obligatorio";
            if (!tag) errors.tag = "El tag es obligatorio";
        }
        if (largo.length > 45) errors.largo = "Máximo 45 caracteres";
        if (ancho.length > 45) errors.ancho = "Máximo 45 caracteres";
        if (alto.length > 45) errors.alto = "Máximo 45 caracteres";
        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };
```

- [ ] **Step 2: Mostrar los errores nuevos**

Donde están los `AsyncSelect` de marca/origen/proveedor/material y el input de margen, agregar debajo el `{formErrors.X && <p className="mt-1 text-xs text-red-500">{formErrors.X}</p>}` correspondiente (`marcaId`, `origenId`, `proveedorId`, `materialId`, `margen`). Y marcar el label de cada uno con el asterisco condicional `{!esCombo && <span .../>*}`. El de `tag` ya se agregó en Task 10.

- [ ] **Step 3: Typecheck + commit**
```bash
cd supermaster-frontend && cmd /c "npx tsc --noEmit"
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): validaciones obligatorias (margen y simples)"
```

---

### Task 12: Mover "Aptos" a Dimensiones

**Files:** Modify: `productos/page.tsx`

- [ ] **Step 1: Quitar Aptos del fieldset "Catálogos, Aptos y Clientes"**

Eliminar de ese fieldset la línea del `MultiAsyncSelect` de Aptos y renombrar el `<legend>` a "Catálogos y Clientes". El grid pasa de `md:grid-cols-3` a `md:grid-cols-2`.

- [ ] **Step 2: Agregar Aptos al fieldset "Dimensiones Físicas"**

Dentro del grid de Dimensiones, agregar (al final):
```tsx
        <div className="md:col-span-2 xl:col-span-4">
            <MultiAsyncSelect label="Aptos" loadOptions={(q) => searchAptos(q)} value={aptosSel} onChange={setAptosSel} placeholder="Buscar apto" inputClassName={inputBaseClassName} />
        </div>
```

- [ ] **Step 3: Typecheck + commit**
```bash
cd supermaster-frontend && cmd /c "npx tsc --noEmit"
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): mover Aptos a la seccion Dimensiones"
```

---

## FASE 5 — Frontend: imagen por SKU y KT HOGAR por defecto

### Task 13: Autocompletar imagen por SKU

**Files:** Modify: `productos/page.tsx`

- [ ] **Step 1: Agregar un efecto que busque la imagen por SKU**

Solo en alta (no edición), cuando el usuario no eligió imagen a mano. Reutiliza el endpoint `/api/imagenes/listar?search=<sku>` (los nombres vienen con extensión; se compara el nombre sin extensión contra el SKU). Agregar cerca del `useEffect` de verificación de SKU (≈ líneas 812-826):
```tsx
    // Autocompleta la imagen cuando el nombre del archivo coincide con el SKU.
    // Solo en alta y mientras el usuario no haya elegido una imagen a mano.
    useEffect(() => {
        if (editandoProductoId) return;
        const skuTrim = sku.trim();
        if (!skuTrim || imagenUrl) return;
        const controller = new AbortController();
        const t = setTimeout(async () => {
            try {
                const res = await fetch(`${API_BASE_URL}/api/imagenes/listar?search=${encodeURIComponent(skuTrim)}`, { signal: controller.signal });
                if (!res.ok) return;
                const data = await res.json();
                const archivos: string[] = Array.isArray(data?.archivos) ? data.archivos : [];
                const match = archivos.find(a => a.replace(/\.[^.]+$/, "") === skuTrim);
                if (match) setImagenUrl(match);
            } catch { /* abort o sin match: ignorar */ }
        }, 400);
        return () => { controller.abort(); clearTimeout(t); };
    }, [sku, editandoProductoId, imagenUrl]);
```

- [ ] **Step 2: Typecheck + verificación manual**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Manual: en alta, tipear un SKU cuyo archivo de imagen existe → la preview aparece sola. Elegir otra a mano sigue funcionando (override).

- [ ] **Step 3: Commit**
```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): autocompletar imagen por SKU"
```

---

### Task 14: KT HOGAR por defecto en precio inflado

**Files:** Modify: `productos/PreciosInfladosSection.tsx`

- [ ] **Step 1: Preseleccionar KT HOGAR en `abrirNuevo`**

Cambiar `abrirNuevo` para que, si KT HOGAR está entre los canales libres, lo deje preseleccionado:
```tsx
    const abrirNuevo = () => {
        setError(null);
        const ktHogar = canales.find(c => c.nombre === "KT HOGAR");
        const ktHogarLibre = ktHogar && !rows.some(r => r.canalId === ktHogar.id) ? ktHogar.id : "";
        setForm({ canalId: ktHogarLibre, precioInfladoId: "", fechaDesde: "", fechaHasta: "", observaciones: "", activo: true, modo: "nuevo" });
    };
```
(`canales` y `rows` están en el scope del componente.)

- [ ] **Step 2: Typecheck + commit**
```bash
cd supermaster-frontend && cmd /c "npx tsc --noEmit"
git add supermaster-frontend/src/app/productos/PreciosInfladosSection.tsx
git commit -m "feat(front/productos): KT HOGAR por defecto al agregar precio inflado"
```

---

## FASE 6 — Frontend: sección "Canales de venta"

### Task 15: Nueva sección "Canales de venta" (mover DUX + placeholders)

**Files:** Modify: `productos/page.tsx`

- [ ] **Step 1: Quitar el checkbox de DUX de la sección Identificación**

Eliminar el bloque `{canExportarDux && ( ... subirADux ... )}` (líneas ~1154-1159) de la sección Identificación.

- [ ] **Step 2: Agregar la nueva sección antes de "Precios Inflados por Canal"**

Insertar un `<fieldset>` nuevo (usar `sectionClassName`/`sectionTitleClassName`/`sectionDescriptionClassName` y `checkboxCardClassName` ya definidos):
```tsx
<fieldset className={sectionClassName}>
    <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> Canales de venta</legend>
    <p className={`${sectionDescriptionClassName} mb-4`}>Dónde publicar/subir el producto. Las integraciones de cada canal se irán habilitando.</p>
    <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        {canExportarDux && (
            <div className={checkboxCardClassName} title={editandoProductoId ? "Al guardar, actualiza el producto en Dux" : "Al crear, sube el producto a Dux"}>
                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirADux} onChange={e => setSubirADux(e.target.checked)} id="subirADux" />
                <label htmlFor="subirADux" className="cursor-pointer">{editandoProductoId ? "Actualizar en Dux" : "Subir a Dux"}</label>
            </div>
        )}
        <div className={`${checkboxCardClassName} opacity-60`} title="Próximamente">
            <input className="h-4 w-4 rounded border-slate-300" type="checkbox" disabled id="canalKtHogar" />
            <label htmlFor="canalKtHogar" className="cursor-not-allowed">KT HOGAR <span className="text-[10px] text-slate-400">(próximamente)</span></label>
        </div>
        <div className={`${checkboxCardClassName} opacity-60`} title="Próximamente">
            <input className="h-4 w-4 rounded border-slate-300" type="checkbox" disabled id="canalKtGastro" />
            <label htmlFor="canalKtGastro" className="cursor-not-allowed">KT GASTRO <span className="text-[10px] text-slate-400">(próximamente)</span></label>
        </div>
        <div className={`${checkboxCardClassName} opacity-60`} title="Próximamente">
            <input className="h-4 w-4 rounded border-slate-300" type="checkbox" disabled id="canalMl" />
            <label htmlFor="canalMl" className="cursor-not-allowed">ML <span className="text-[10px] text-slate-400">(próximamente)</span></label>
        </div>
    </div>
</fieldset>
```

- [ ] **Step 3: Asegurar el import del ícono**

`BuildingStorefrontIcon` debe estar importado de `@heroicons/react/24/outline` (agregarlo al import existente de heroicons en `page.tsx` si falta).

- [ ] **Step 4: Typecheck + build + commit**
```bash
cd supermaster-frontend && cmd /c "npx tsc --noEmit"
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): seccion Canales de venta (DUX movido + placeholders)"
```

---

## FASE 7 — Verificación final

### Task 16: Verificación end-to-end

- [ ] **Step 1: Build frontend completo**

Run: `cd supermaster-frontend && cmd /c "npm run build"`
Expected: compila sin errores.

- [ ] **Step 2: Suite completa backend**

Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o test"`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Verificación manual** (requiere SQL de Tasks 4 y 5 corridos)

1. Crear un producto simple sin marca/origen/proveedor/material/tag → rebota con mensajes claros (front + back).
2. Crear sin margen → rebota. Con un margen > 0 → OK.
3. Costo arranca vacío; ingresar valor sin tener que borrar el 0.
4. Tag muestra Insumo y no "Sin tag".
5. Aptos aparece en Dimensiones.
6. Tipear un SKU con imagen existente → preview automática.
7. Agregar precio inflado → KT HOGAR preseleccionado.
8. Sección "Canales de venta" con DUX funcional + 3 placeholders deshabilitados.
9. Tras crear, el producto aparece como pendiente de recálculo / con precios calculados.
10. Editar un producto: no hay campos de margen fijo en form ni tabla; la fórmula del Monitor/Calculadora ya no tiene paso de margen fijo.

---

## Notas / riesgos

- **Margen fijo en el cálculo:** quitar el paso no debería cambiar ningún precio real (0 productos lo usaban). Verificar `CalculoPrecioFormulaTest` + `RecalculoAutomaticoIntegrationTest` verdes.
- **Constructor de `ProductoServiceImpl` en el test (Task 7):** ajustar el número de `null` a la cantidad real de dependencias `final`.
- **Tag ENUM en MySQL:** si la columna es `ENUM`, correr el `ALTER ... MODIFY COLUMN` para agregar INSUMO (Task 5 Step 2).
- **Obligatoriedad al editar viejos:** productos simples viejos incompletos rebotarán al editarse hasta completar marca/origen/proveedor/material/tag (decisión aprobada).
- **Fuera de alcance:** persistencia de los checkboxes KT GASTRO/KT HOGAR/ML; migración de datos viejos.
```
