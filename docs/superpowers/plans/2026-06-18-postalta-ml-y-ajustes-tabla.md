# Post-alta en Mercado Libre + ajustes tabla Productos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Completar el alta a Mercado Libre (crear con stock 0 = pausada, asociar el MLA resultante al producto y calcular comisión/envío) y aplicar tres ajustes de UI en la tabla de Productos.

**Architecture:** Backend — el núcleo del alta deja de reintentar stock 0→1 y captura el MLAU; `MlExportService` procesa cada SKU con el alta en una transacción de lectura (lazy del producto) y luego, fuera de esa transacción, hace el post-alta best-effort (asociar MLA + costos, cada uno en su propia transacción, reusando métodos existentes). Frontend — ajustes acotados a la página de Productos.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven; Jackson 3 (`tools.jackson.databind`); JUnit 5 + AssertJ; frontend Next.js/TypeScript/Tailwind.

## Global Constraints

- Jackson 3: `tools.jackson.databind` (NO `com.fasterxml`); unchecked; parsear con `.path(...).asString("")`.
- Sitio MLA; token ML `() -> tokens.accessToken`. **Ninguna llamada real a ML** en tests (núcleo con lambdas).
- El alta crea con `available_quantity = 0` (sin reintento). El stock 0 deja la publicación pausada `out_of_stock` (no hay PUT de pausa separado).
- Post-alta **best-effort**: pausar (implícito en stock 0), asociar MLA y costos que fallen se informan como **aviso** por SKU; no se revierte el ítem ni se frenan los demás SKUs.
- Transaccionalidad: el alta corre en una tx de lectura por SKU (lazy del producto); el post-alta corre fuera de esa tx, con `asegurarYAsociar` y los métodos de costo iniciando cada uno su propia transacción (patrón de `MlaService.obtenerOcrearPorSkuDesdeML`).
- UI: cambios acotados a `productos/` (no tocar el `TableToolbar` genérico ni el botón de columnas).
- Maven offline (Windows): `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=...`. Front: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`.
- Commits terminan con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: Núcleo del alta — sin reintento, stock 0, capturar MLAU

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/ResultadoAltaMl.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (`crearItemEnMlCore`)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/CrearItemEnMlTest.java`

**Interfaces:**
- Produces: `record ResultadoAltaMl(Estado, motivo, itemId, mlau, advertencia)` con `creado(itemId, mlau)`; el núcleo crea con `available_quantity=0` (sin reintento) y captura `user_product_id` → `mlau`.

- [ ] **Step 1: Update the tests**

En `CrearItemEnMlTest.java`:
(a) **Eliminar** el test `cantidad0Rechazada_reintentaCon1` por completo (ya no hay reintento), y su `import java.util.concurrent.atomic.AtomicInteger;` si quedara sin uso.
(b) Reemplazar el cuerpo de `ok_creadoConItemId` para verificar también el MLAU (el poster devuelve `id` + `user_product_id`):
```java
    @Test
    void ok_creadoConItemIdYMlau() {
        AtomicReference<String> descripcion = new AtomicReference<>();
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, conImagen, subeOk, predice,
                json -> "{\"id\":\"MLA999\",\"user_product_id\":\"MLAU99\"}",
                (id, txt) -> { descripcion.set(id + "|" + txt); return "{}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(r.itemId()).isEqualTo("MLA999");
        assertThat(r.mlau()).isEqualTo("MLAU99");
        assertThat(descripcion.get()).startsWith("MLA999|CARACTERÍSTICAS");
    }
```
(Si había un test `ok_creadoConItemId` con el nombre viejo, renombralo/reemplazalo por este.) Los tests `sinTitulo_error`, `yaExiste_noPostea`, `sinImagenes_error`, `respuestaConError_devuelveError`, `respuestaConSoloWarnings_creado` quedan igual.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=CrearItemEnMlTest`
Expected: FAIL de compilación (`r.mlau()` no existe).

- [ ] **Step 3: Update `ResultadoAltaMl`**

Reemplazar `ResultadoAltaMl.java`:
```java
package ar.com.leo.super_master_backend.apis.ml.dto;

public record ResultadoAltaMl(Estado estado, String motivo, String itemId, String mlau, String advertencia) {
    public enum Estado { CREADO, YA_EXISTIA, ERROR }
    public static ResultadoAltaMl creado(String itemId, String mlau) { return new ResultadoAltaMl(Estado.CREADO, null, itemId, mlau, null); }
    public static ResultadoAltaMl yaExistia() { return new ResultadoAltaMl(Estado.YA_EXISTIA, null, null, null, null); }
    public static ResultadoAltaMl error(String motivo) { return new ResultadoAltaMl(Estado.ERROR, motivo, null, null, null); }
    public ResultadoAltaMl conAdvertencia(String advertencia) {
        return new ResultadoAltaMl(estado, motivo, itemId, mlau, advertencia);
    }
}
```

- [ ] **Step 4: Update the core**

En `MercadoLibreService.crearItemEnMlCore`, reemplazar el bloque del POST + reintento + captura del id (las líneas que hoy van desde el comentario `// Intento con cantidad 0...` hasta el `return advertencia == null ? r : ...`) por:
```java
            // Precio de alta en ML: costo x 5 (regla de negocio de la Fase C1).
            BigDecimal price = producto.getCosto().multiply(BigDecimal.valueOf(5));

            // Crear con stock 0: deja la publicación pausada (out_of_stock) si la cuenta lo admite.
            String respuesta = poster.apply(om.writeValueAsString(
                    MlItemPayloadBuilder.construir(producto, categoryId, price, 0, pictureIds)));
            String error = extraerErrorMl(om, respuesta);
            if (error != null) return ResultadoAltaMl.error(error);

            JsonNode creado = om.readTree(respuesta);
            String itemId = creado.path("id").asString("");
            if (itemId.isBlank()) return ResultadoAltaMl.error("ML no devolvió id del ítem");
            String mlau = creado.path("user_product_id").asString("");

            String advertencia = null;
            try {
                posterDescripcion.apply(itemId, MlDescripcionBuilder.construir(producto));
            } catch (Exception e) {
                advertencia = "ítem creado pero falló la descripción";
            }

            ResultadoAltaMl r = ResultadoAltaMl.creado(itemId, mlau.isBlank() ? null : mlau);
            return advertencia == null ? r : r.conAdvertencia(advertencia);
```
(El resto del método —validaciones de título/costo/yaExiste/imágenes/categoría— no cambia. `JsonNode` ya está importado.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=CrearItemEnMlTest`
Expected: PASS (6 tests: se eliminó el del reintento, quedan los demás + el de mlau).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/ResultadoAltaMl.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/CrearItemEnMlTest.java
git commit -m "feat(ml): alta crea con stock 0 (sin reintento) y captura el MLAU

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `MlaService.asegurarYAsociar` — asegurar MLA y asociarlo al producto

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mla/service/MlaService.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mla/service/MlaServiceImpl.java`

**Interfaces:**
- Consumes: el `asegurarMla(mlaCode, mlau)` existente del impl (vía `self`), `MlaRepository.findById`, `ProductoRepository.findById`.
- Produces: `void asegurarYAsociar(Integer productoId, String mlaCode, String mlau)` — asegura el `Mla` y hace `producto.setMla(mla)` + save.

> **Nota:** persistencia simple; sin test unitario (mockear `MlaServiceImpl` con todas sus deps no es barato). Verificación: compila y la suite no se rompe. Su comportamiento se ejercita por el flujo de Task 3.

- [ ] **Step 1: Agregar a la interfaz**

En `MlaService.java`, agregar el método (con su Javadoc):
```java
    /**
     * Asegura que exista el registro MLA (código + MLAU) y lo asocia al producto indicado.
     * Usado por el post-alta de Mercado Libre tras crear la publicación.
     */
    void asegurarYAsociar(Integer productoId, String mlaCode, String mlau);
```

- [ ] **Step 2: Implementar en `MlaServiceImpl`**

Agregar el método (junto a `asegurarMla`). Verificá el import de la entidad `Producto`
(`ar.com.leo.super_master_backend.dominio.producto.entity.Producto`) — agregalo si falta:
```java
    @Override
    @Transactional
    public void asegurarYAsociar(Integer productoId, String mlaCode, String mlau) {
        Integer mlaId = self.asegurarMla(mlaCode, mlau);
        Mla mla = repo.findById(mlaId)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado tras asegurarlo: " + mlaCode));
        ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado: " + productoId));
        producto.setMla(mla);
        productoRepository.save(producto);
    }
```

- [ ] **Step 3: Compilar**

Run: `cd supermaster-backend && ./mvnw.cmd -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mla/service/MlaService.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mla/service/MlaServiceImpl.java
git commit -m "feat(mla): asegurarYAsociar (crear MLA y vincularlo al producto)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `MlExportService` — alta por SKU en tx de lectura + post-alta best-effort

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlExportService.java`

**Interfaces:**
- Consumes: `MercadoLibreService.crearItemEnMl` (Task 1), `MlaService.asegurarYAsociar` (Task 2), `MercadoLibreService.obtenerCostoVenta(mlaCode)` / `calcularCostoEnvioGratis(mlaCode)`, `ProductoRepository.findById`/`findBySkuIn`.
- Produces: `exportar` con post-alta. Métodos `altaConProductoCargado(Integer productoId)` (`@Transactional(readOnly=true)`) y `postAlta(productoId, itemId, mlau)`.

> **Nota:** orquesta red/persistencia; sin test unitario. Verificación: compila y la suite ML/imagen sigue verde.

- [ ] **Step 1: Reescribir `MlExportService`**

Reemplazar el contenido de la clase (imports + campos + métodos) por:
```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.MlExportRequestDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlExportResultDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.mla.service.MlaService;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MlExportService {

    private final ProductoRepository productoRepository;
    private final MercadoLibreService mercadoLibreService;
    private final MlaService mlaService;

    // Self-proxy: altaConProductoCargado debe correr en su propia transacción aunque
    // se la invoque desde exportar() (this.* no pasa por el proxy de Spring).
    @Lazy
    @Autowired
    private MlExportService self;

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
            Integer productoId = producto.getId();
            String etiqueta = producto.getSku();
            // Alta en una tx de lectura por SKU: mantiene el lazy-loading del producto
            // (marca/material/aptos) para armar el payload, con open-in-view=false.
            ResultadoAltaMl r = self.altaConProductoCargado(productoId);
            switch (r.estado()) {
                case CREADO -> {
                    creados++;
                    List<String> avisos = new ArrayList<>();
                    if (r.advertencia() != null) avisos.add(r.advertencia());
                    // Post-alta best-effort, FUERA de la tx del alta: cada paso en su propia tx.
                    avisos.addAll(postAlta(productoId, r.itemId(), r.mlau()));
                    for (String a : avisos) advertencias.add(etiqueta + ": " + a);
                }
                case YA_EXISTIA -> yaExistian.add(etiqueta);
                case ERROR -> errores.add(etiqueta + ": " + r.motivo());
            }
        }
        return new MlExportResultDTO(creados, yaExistian, errores, advertencias);
    }

    /** Recarga el producto (managed) y hace el alta; la tx de lectura mantiene el lazy abierto. */
    @Transactional(readOnly = true)
    public ResultadoAltaMl altaConProductoCargado(Integer productoId) {
        Producto p = productoRepository.findById(productoId).orElse(null);
        if (p == null) return ResultadoAltaMl.error("Producto no encontrado");
        return mercadoLibreService.crearItemEnMl(p);
    }

    /** Asocia el MLA y calcula comisión + envío. Cada paso es best-effort en su propia tx; devuelve avisos. */
    private List<String> postAlta(Integer productoId, String itemId, String mlau) {
        List<String> avisos = new ArrayList<>();
        try {
            mlaService.asegurarYAsociar(productoId, itemId, mlau);
        } catch (Exception e) {
            log.warn("ML - No se pudo asociar el MLA {} al producto {}: {}", itemId, productoId, e.getMessage());
            avisos.add("no se pudo asociar el MLA");
        }
        try {
            mercadoLibreService.obtenerCostoVenta(itemId);
        } catch (Exception e) {
            log.warn("ML - No se pudo calcular la comisión de {}: {}", itemId, e.getMessage());
            avisos.add("no se pudo calcular la comisión");
        }
        try {
            mercadoLibreService.calcularCostoEnvioGratis(itemId);
        } catch (Exception e) {
            log.warn("ML - No se pudo calcular el envío de {}: {}", itemId, e.getMessage());
            avisos.add("no se pudo calcular el envío");
        }
        return avisos;
    }
}
```

- [ ] **Step 2: Compilar y correr la suite ML/imagen**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest="ar.com.leo.super_master_backend.apis.ml.**,ImagenService*Test"`
Expected: PASS — compila con la nueva dependencia `MlaService` y el self-proxy; los tests del núcleo (Task 1) verdes.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlExportService.java
git commit -m "feat(ml): post-alta - asociar MLA y calcular costos (best-effort, tx por SKU)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Frontend — panel de filtros más compacto

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductosFilterBar.tsx`

**Interfaces:** sin cambios de API; solo clases Tailwind de espaciado.

- [ ] **Step 1: Compactar el panel**

En `ProductosFilterBar.tsx`, en el contenedor del panel de filtros (el `<div>` con
`space-y-4 ... px-4 py-4`) y los grupos, reducir el espaciado. Cambios concretos (verificá los valores
reales en el archivo y ajustá si difieren levemente):
- Contenedor del panel: `space-y-4` → `space-y-2`; `py-4` → `py-2`.
- Los grids de cada grupo: `gap-3` → `gap-2`.
- El `GroupTitle` (helper del archivo): `mb-2` → `mb-1`; si tiene `text-sm`/padding extra, reducir a
  `text-xs` el label del grupo para ganar alto.
No cambiar la cantidad de columnas de los grids ni la funcionalidad. El objetivo es solo reducir el alto.

- [ ] **Step 2: Typecheck**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductosFilterBar.tsx
git commit -m "style(front/productos): panel de filtros mas compacto

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Frontend — quitar la sección de Vistas guardadas

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

**Interfaces:** elimina estado/handlers/JSX de "vistas guardadas"; no debe quedar nada huérfano.

- [ ] **Step 1: Eliminar la barra, el modal, el estado y los handlers**

En `page.tsx`, eliminar TODO lo relacionado con "vistas guardadas":
- La barra superior JSX (el `<div>` con "Vistas" / `<select>` "Seleccionar vista guardada..." / botones
  Aplicar / Guardar / Borrar — alrededor de las líneas 1104-1146).
- El modal de guardar vista (alrededor de las líneas 1619-1647).
- Los estados: `savedViews`, `selectedViewId`, `viewName`, `isViewModalOpen` (y sus `useState`).
- Los handlers: `handleSaveView`, `handleApplyView`, `handleDeleteView`, `openSaveView`, `closeSaveView`.
- La constante `PRODUCTOS_VIEWS_STORAGE_KEY` y cualquier lectura/escritura a `localStorage` de vistas
  (incluido el `useEffect` que cargue/persista `savedViews`).
- Imports que queden sin uso por esto (p.ej. `BookmarkIcon`).
Verificá tras la edición que no quede ninguna referencia a esos identificadores (grep en el archivo).
NO toques el resto del header (búsqueda, botón "Filtros", botón "Crear Producto", botón de columnas).

- [ ] **Step 2: Typecheck**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: exit 0 (sin errores de "declared but never used" ni referencias colgadas).

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): quitar seccion de Vistas guardadas

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Frontend — filas de igual alto (títulos truncados a 1 línea)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/columns.tsx`

**Interfaces:** las celdas de título truncan a 1 línea; el resto de la tabla no cambia.

- [ ] **Step 1: Truncar los títulos**

En `columns.tsx`, en las columnas `tituloDux`, `tituloMl` y `tituloNube`, el `cell` renderiza un
`<EditableCell className={...} />`. Agregar al `className` que reciben esas tres columnas una clase de
truncado a 1 línea: `truncate` (Tailwind: `overflow-hidden text-ellipsis whitespace-nowrap`). Es decir,
pasar `className={\`${FONT.text} truncate\`}` (y `${FONT.title} truncate` para tituloMl/tituloNube,
respetando el FONT que ya usa cada una). El `<td>` ya fija el ancho por `size`, así que el truncado se
aplica a 1 línea y todas las filas quedan del mismo alto.
- Si al probarlo el truncado no toma efecto porque el div interno de `EditableCell` no ocupa el ancho,
  agregar también `block w-full` a ese className (`truncate block w-full`).
- **Tooltip (opcional, recomendado):** para ver el título completo al pasar el mouse, en
  `Table/core/EditableCell.tsx` agregar al `<div>` de display el atributo
  `title={typeof value === "string" ? value : undefined}`. Es un tooltip nativo, inofensivo para las
  demás tablas. Si preferís no tocar el componente genérico, omití este sub-paso (el título se ve completo
  igual al hacer click para editar).

- [ ] **Step 2: Typecheck**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/productos/columns.tsx supermaster-frontend/src/app/components/Table/core/EditableCell.tsx
git commit -m "feat(front/productos): truncar titulos a 1 linea (filas de igual alto)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
(Si NO tocaste `EditableCell.tsx`, quitalo del `git add`.)

---

## Verificación final (tras todas las tasks)

- [ ] **Suite completa backend:** `cd supermaster-backend && ./mvnw.cmd -o test` → 0 failures, 0 errors.
- [ ] **Typecheck frontend:** `cd supermaster-frontend && cmd /c "npx tsc --noEmit"` → 0 errores.
- [ ] **Ninguna llamada real a Mercado Libre** en los tests.

## Notas de cierre

- En producción, el post-alta (pausa por stock 0, asociación del MLA, comisión y envío) se ejercita con la
  cuenta ML real; los pasos que fallen se ven como avisos por SKU en el toast.
- Los ajustes de UI se validan visualmente (no hay tests de UI en el proyecto).
