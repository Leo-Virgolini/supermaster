# Verificación de SKU en Dux al crear — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bloquear la creación de un producto en supermaster si su SKU ya existe en Dux (o si no se puede verificar), para no pisar un ítem existente con el upsert ciego de Dux.

**Architecture:** Un gate en `ProductoServiceImpl.crear`, después de la validación de SKU duplicado en supermaster: consulta `DuxService.obtenerProductoPorCodigo(sku)`; si devuelve un ítem → 409; si la consulta falla → 409 (fail-closed). Se inyecta `DuxService` con `@Lazy @Autowired` para evitar un ciclo de dependencias.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven; JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Trabajar directo en `main` (sin ramas).
- Maven OFFLINE desde `supermaster-backend/`: `./mvnw -o ...`.
- **NO ejecutar nada que llame a las APIs reales de Dux/Nube/ML** — solo el test unitario offline (Mockito, sin red). No crear productos reales.
- **Fail-closed:** si la consulta a Dux falla (excepción), también se bloquea (con mensaje distinto). NO fail-open.
- Solo en `crear` (alta). NO en `actualizar` (edición).
- Mensajes EXACTOS:
  - Existe en Dux: `"El SKU ya existe en Dux: " + sku`
  - No se pudo verificar: `"No se pudo verificar el SKU en Dux (¿Dux no disponible?). Intentá de nuevo en un momento."`
- Inyectar `DuxService` con `@Lazy @Autowired` (field injection) — mismo patrón que `DuxService.self` — para romper el posible ciclo `ProductoServiceImpl → DuxService → RecalculoPrecioFacade → …`.
- `ConflictException` ya existe (`dominio/common/exception`, mapeada a 409). El frontend ya muestra el `message` del 409 y NO cierra el modal ante error (verificado) — **sin cambios de frontend**.
- Commits terminan EXACTAMENTE con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: Gate de verificación de SKU en Dux al crear

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/service/ProductoServiceImpl.java` (anotación `@Slf4j`; campo `duxService`; método `verificarSkuLibreEnDux`; llamada en `crear`)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/service/ProductoServiceCrearDuxTest.java` (nuevo)

**Interfaces:**
- Consumes: `DuxService.obtenerProductoPorCodigo(String codItem)` → `Item` (o `null`); puede lanzar si Dux no está configurado.
- Produces: `void ProductoServiceImpl.verificarSkuLibreEnDux(String sku)` (package-private) — lanza `ConflictException` si el SKU existe en Dux o si no se pudo verificar.

- [ ] **Step 1: Escribir el test (falla a compilar)**

Crear `ProductoServiceCrearDuxTest.java`:

```java
package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceCrearDuxTest {

    @Mock
    DuxService duxService;

    @InjectMocks
    ProductoServiceImpl service;

    @Test
    void skuExisteEnDux_lanzaConflict() {
        when(duxService.obtenerProductoPorCodigo("1234567")).thenReturn(mock(Item.class));
        assertThatThrownBy(() -> service.verificarSkuLibreEnDux("1234567"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya existe en Dux");
    }

    @Test
    void noSePudoVerificar_lanzaConflict() {
        when(duxService.obtenerProductoPorCodigo("1234567")).thenThrow(new RuntimeException("Dux caído"));
        assertThatThrownBy(() -> service.verificarSkuLibreEnDux("1234567"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No se pudo verificar");
    }

    @Test
    void skuLibreEnDux_noLanza() {
        when(duxService.obtenerProductoPorCodigo("1234567")).thenReturn(null);
        assertThatCode(() -> service.verificarSkuLibreEnDux("1234567")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `./mvnw -o test -Dtest=ProductoServiceCrearDuxTest`
Expected: FAIL de compilación — `verificarSkuLibreEnDux` no existe (y `duxService` no es un campo).

- [ ] **Step 3: Agregar `@Slf4j`, el campo `duxService` y el método; llamarlo en `crear`**

En `ProductoServiceImpl.java`:

1. Agregar imports:
```java
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
```

2. Agregar `@Slf4j` a la clase (junto a `@Service @RequiredArgsConstructor`):
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {
```

3. Agregar el campo `duxService` (NO final, `@Lazy @Autowired` — rompe el ciclo; no lo toma `@RequiredArgsConstructor`). Ubicarlo después del último campo `final` (`productoAuditoriaService`):
```java
    // @Lazy para romper el ciclo de dependencias (ProductoServiceImpl -> DuxService -> RecalculoPrecioFacade -> ...).
    @Lazy
    @Autowired
    private DuxService duxService;
```

4. Agregar el método helper (package-private, testeable) — ubicarlo cerca de `crear`:
```java
    /**
     * Bloquea el alta si el SKU ya existe en Dux. Fail-closed: si no se puede verificar
     * (Dux caído/no configurado/timeout), también bloquea, con un mensaje distinto.
     */
    void verificarSkuLibreEnDux(String sku) {
        boolean existeEnDux;
        try {
            existeEnDux = duxService.obtenerProductoPorCodigo(sku) != null;
        } catch (Exception e) {
            log.warn("No se pudo verificar el SKU '{}' en Dux: {}", sku, e.getMessage());
            throw new ConflictException("No se pudo verificar el SKU en Dux (¿Dux no disponible?). Intentá de nuevo en un momento.");
        }
        if (existeEnDux) {
            throw new ConflictException("El SKU ya existe en Dux: " + sku);
        }
    }
```

5. En `crear`, llamar al gate después de la validación de SKU duplicado en supermaster y antes de `toEntity`:
```java
    @Override
    @Transactional
    public ProductoDTO crear(ProductoCreateDTO dto) {
        // Validar SKU único
        if (productoRepository.findBySku(dto.sku()).isPresent()) {
            throw new ConflictException("Ya existe un producto con el SKU: " + dto.sku());
        }
        // Validar que el SKU no exista ya en Dux (no pisar un ítem existente con el upsert ciego).
        verificarSkuLibreEnDux(dto.sku());

        Producto entity = productoMapper.toEntity(dto);
        validarAlMenosUnaClasificacion(entity);
        validarProductoSimpleCompleto(entity);
        productoRepository.save(entity);
        productoAuditoriaService.registrarCreacion(entity);
        programarRecalculoPostCommit("Producto creado", entity.getId());
        return productoMapper.toDTO(entity);
    }
```

- [ ] **Step 4: Correr el test para verificar que pasa**

Run: `./mvnw -o test -Dtest=ProductoServiceCrearDuxTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Compilar todo y confirmar que no se rompió nada (incluido el arranque del contexto de tests que cargan Spring)**

Run: `./mvnw -o test-compile`
Expected: BUILD SUCCESS.

> Nota: si algún test de integración que levanta el contexto Spring corre en CI/local, confirmar que el `@Lazy` evita un fallo de ciclo de dependencias al crear el bean `ProductoServiceImpl`. Si el arranque del contexto fallara por ciclo, el `@Lazy` ya está puesto justamente para eso; reportarlo si ocurre.

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/service/ProductoServiceImpl.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/service/ProductoServiceCrearDuxTest.java
git commit -m "feat(producto): bloquear alta si el SKU ya existe en Dux (no pisar)"
```

---

## Verificación final

- [ ] `./mvnw -o test -Dtest=ProductoServiceCrearDuxTest` → PASS (3 tests).
- [ ] **Smoke (usuario):** crear un producto con un SKU que exista en Dux → 409 "El SKU ya existe en Dux"; con uno nuevo → se crea; con Dux caído/no configurado → 409 "No se pudo verificar…". El modal queda abierto en los casos de error.

## Notas de diseño
- El gate va SOLO en `crear`. `actualizar` no se toca (al editar se asume que el producto ya es tuyo en Dux).
- `verificarSkuLibreEnDux` es package-private a propósito: permite testearlo directo sin construir el `ProductoCreateDTO` posicional completo ni mockear toda la cadena de `crear`.
- El frontend no cambia: el 409 se muestra como toast y el modal no se cierra ante error (comportamiento actual verificado).
