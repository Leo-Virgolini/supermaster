# Tres títulos por plataforma (Dux/ML/Nube) — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar los títulos del producto (`descripcion`, `titulo_web`) por tres títulos por plataforma: `titulo_dux` (NOT NULL, base), `titulo_nube` (nullable), `titulo_ml` (nullable, nuevo), en BD, backend y frontend.

**Architecture:** Rename de campos de la entidad `Producto` propagado por el compilador a sus consumidores (DTOs, mapper, specs, controllers, servicios, Excel, DUX) + un campo nuevo; en el frontend se renombran tipos/estados y se agrega un tercer título en modal y tabla. Búsqueda de texto global solo por `tituloDux`. Export a DUX: `item`=`tituloDux`, descripción DUX=`tituloNube`.

**Tech Stack:** Spring Boot 4 / Java 25 / JPA / MapStruct (backend); Next.js 16 / React 19 / TypeScript (frontend). `ddl-auto=validate` → DDL manual.

**Referencia de diseño:** `docs/superpowers/specs/2026-06-17-tres-titulos-producto-design.md`

## Global Constraints
- `titulo_dux` = NOT NULL (obligatorio, ex `descripcion`). `titulo_nube` (ex `titulo_web`) y `titulo_ml` = nullable, opcionales.
- Búsqueda de texto global de productos: **solo `tituloDux`** (+ sku/codExt/mla/mlau como hoy).
- Export DUX: `item` ← `tituloDux`; campo `descripcion` de DUX ← `tituloNube`.
- **CUIDADO:** `descripcion`/`getDescripcion()` existen en OTRAS entidades (CanalConceptoCuota, ConceptoCalculo, ReglaDescuento, Rol, Permiso, ConfigAutomatizacion). **NO tocarlas.** Solo el campo de `Producto`.
- Commits directo en `main`, terminando con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Backend compila: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"`. Tests: `cmd /c "mvnw.cmd -q -o test"`. Frontend: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`.

---

## FASE 1 — BD y entidad

### Task 1: Script SQL + entidad Producto

**Files:**
- Create: `supermaster-backend/src/main/resources/db/tres-titulos-producto.sql`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java:47-54`

- [ ] **Step 1: Script SQL**
```sql
-- Tres títulos por plataforma. Ejecutar manualmente (ddl-auto=validate).
-- CHANGE COLUMN preserva los datos existentes.
ALTER TABLE supermaster.productos
  CHANGE COLUMN descripcion titulo_dux VARCHAR(100) NOT NULL,
  CHANGE COLUMN titulo_web titulo_nube VARCHAR(100) NULL,
  ADD COLUMN titulo_ml VARCHAR(100) NULL AFTER titulo_dux;
```

- [ ] **Step 2: Entidad** — reemplazar (líneas 47-54):
```java
    @Size(max = 100)
    @NotNull
    @Column(name = "descripcion", nullable = false, length = 100)
    private String descripcion;

    @Size(max = 100)
    @Column(name = "titulo_web", nullable = false, length = 100)
    private String tituloWeb;
```
por:
```java
    @Size(max = 100)
    @NotNull
    @Column(name = "titulo_dux", nullable = false, length = 100)
    private String tituloDux;

    @Size(max = 100)
    @Column(name = "titulo_ml", length = 100)
    private String tituloMl;

    @Size(max = 100)
    @Column(name = "titulo_nube", length = 100)
    private String tituloNube;
```
(Lombok genera `getTituloDux/setTituloDux`, `getTituloMl/...`, `getTituloNube/...`.)

- [ ] **Step 3: Commit** (compila después de Task 2-5; este commit puede no compilar solo)
```bash
git add supermaster-backend/src/main/resources/db/tres-titulos-producto.sql supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java
git commit -m "feat(producto): 3 titulos en entidad + script SQL (titulo_dux/ml/nube)"
```

---

## FASE 2 — DTOs backend y mapper

### Task 2: Renombrar campos en DTOs y mapper

**Files (modify):**
- `dto/ProductoDTO.java`, `dto/ProductoCreateDTO.java`, `dto/ProductoUpdateDTO.java`, `dto/ProductoPatchDTO.java`, `dto/ProductoConPreciosDTO.java`, `dto/ProductoResumenDTO.java`, `dto/ProductoFilter.java`, `mapper/ProductoMapper.java`

Regla de rename en TODOS: `descripcion`→`tituloDux`, `tituloWeb`→`tituloNube`, y agregar `tituloMl` donde corresponda.

- [ ] **Step 1: `ProductoDTO`** — renombrar `descripcion`→`tituloDux`, `tituloWeb`→`tituloNube`, y agregar `String tituloMl,` (ubicarlo entre tituloDux y tituloNube). Es un record: ajustar el orden de campos coherente.

- [ ] **Step 2: `ProductoCreateDTO`** — reemplazar los campos de título:
```java
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 100, message = "La descripción no puede exceder 100 caracteres")
        String descripcion,
        @NotBlank(message = "El título web es obligatorio")
        @Size(max = 100, message = "El título web no puede exceder 100 caracteres")
        String tituloWeb,
```
por:
```java
        @NotBlank(message = "El título Dux es obligatorio")
        @Size(max = 100, message = "El título Dux no puede exceder 100 caracteres")
        String tituloDux,
        @Size(max = 100, message = "El título ML no puede exceder 100 caracteres")
        String tituloMl,
        @Size(max = 100, message = "El título Nube no puede exceder 100 caracteres")
        String tituloNube,
```

- [ ] **Step 3: `ProductoUpdateDTO`** — `descripcion` (@Size) → `tituloDux` (@Size); `tituloWeb` → `tituloNube`; agregar `@Size(max=100) String tituloMl`. Todos opcionales (sin @NotBlank).

- [ ] **Step 4: `ProductoPatchDTO`** — renombrar los `JsonNullable<String>` `descripcion`→`tituloDux`, `tituloWeb`→`tituloNube`, agregar `private JsonNullable<String> tituloMl = JsonNullable.undefined();`.

- [ ] **Step 5: `ProductoConPreciosDTO`, `ProductoResumenDTO`, `ProductoFilter`** — renombrar `descripcion`→`tituloDux`, `tituloWeb`→`tituloNube` (agregar `tituloMl` solo en `ProductoFilter` si se quiere filtrar; en los otros dos, agregar `tituloMl` para completitud del DTO).

- [ ] **Step 6: `ProductoMapper`** — en `toDTO` (≈línea 57-58) y `toProductoConPreciosDTO` (≈352-353), reemplazar `entity.getDescripcion()`→`entity.getTituloDux()`, `entity.getTituloWeb()`→`entity.getTituloNube()`, y pasar `entity.getTituloMl()` en la posición del nuevo campo. Ajustar el orden de argumentos a la nueva firma del record.

- [ ] **Step 7: Commit** (compila tras Fase 3-5)
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mapper/ProductoMapper.java
git commit -m "refactor(producto): renombrar titulos en DTOs y mapper (+tituloMl)"
```

---

## FASE 3 — Búsqueda, controllers, servicio, auditoría

### Task 3: Specs (búsqueda solo Dux), controllers, servicio, auditoría

**Files (modify):**
- `repository/ProductoSpecifications.java`, `repository/PrecioSpecifications.java`
- `controller/ProductoController.java`, `calculo/controller/PrecioController.java`
- `service/ProductoServiceImpl.java`, `service/ProductoAuditoriaServiceImpl.java`

- [ ] **Step 1: `ProductoSpecifications` — búsqueda de texto solo Dux** (líneas 45-52): reemplazar
```java
            return cb.or(
                    cb.like(cb.lower(root.get("sku")), pattern),
                    cb.like(cb.lower(root.get("codExt")), pattern),
                    cb.like(cb.lower(root.get("descripcion")), pattern),
                    cb.like(cb.lower(root.get("tituloWeb")), pattern),
                    cb.like(cb.lower(mlaJoin.get("mla")), pattern),
                    cb.like(cb.lower(mlaJoin.get("mlau")), pattern)
            );
```
por:
```java
            return cb.or(
                    cb.like(cb.lower(root.get("sku")), pattern),
                    cb.like(cb.lower(root.get("codExt")), pattern),
                    cb.like(cb.lower(root.get("tituloDux")), pattern),
                    cb.like(cb.lower(mlaJoin.get("mla")), pattern),
                    cb.like(cb.lower(mlaJoin.get("mlau")), pattern)
            );
```

- [ ] **Step 2: `ProductoSpecifications` — filtros por columna** (líneas 73-85): renombrar el método `descripcion(...)` → `tituloDux(...)` (usando `root.get("tituloDux")`) y `tituloWeb(...)` → `tituloNube(...)` (usando `root.get("tituloNube")`).

- [ ] **Step 3: `PrecioSpecifications`** — equivalente: en la búsqueda de texto (≈77-78) usar solo `tituloDux` (quitar `tituloWeb`); renombrar los métodos de filtro `descripcion(...)`→`tituloDux(...)`, `tituloWeb(...)`→`tituloNube(...)` con `producto.get("tituloDux")`/`producto.get("tituloNube")`.

- [ ] **Step 4: Controllers** — en `ProductoController` (≈50-51, 134-135) y `PrecioController` (≈73-74, 167-168): renombrar los `@RequestParam`/paso de filtro `descripcion`→`tituloDux`, `tituloWeb`→`tituloNube`. (Agregar `tituloMl` como `@RequestParam` opcional solo si el filtro lo soporta; mínimo renombrar los dos.)

- [ ] **Step 5: `ProductoServiceImpl`** — renombrar todas las referencias del filtro/patch/update de producto: `ProductoSpecifications.descripcion(filter.descripcion())`→`ProductoSpecifications.tituloDux(filter.tituloDux())`; `tituloWeb`→`tituloNube`; en el PATCH (≈1088-1089) `patchDto.getDescripcion()`→`getTituloDux()`, `getTituloWeb()`→`getTituloNube()`, agregar manejo de `getTituloMl()`; en el update (≈1151-1155) `entity.setDescripcion(...)`→`setTituloDux(...)`, `setTituloWeb(...)`→`setTituloNube(...)`, `setTituloMl(...)`. Seguir el patrón existente para los 3 campos.

- [ ] **Step 6: `ProductoAuditoriaServiceImpl`** (≈30-31): `snapshot.put("descripcion", normalizar(producto.getDescripcion()))`→`snapshot.put("tituloDux", normalizar(producto.getTituloDux()))`; `tituloWeb`→`tituloNube`; agregar `snapshot.put("tituloMl", normalizar(producto.getTituloMl()))`.

- [ ] **Step 7: Compilar** (puede fallar aún por Excel/Dux — Fase 4-5)
Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"` (continuar si solo quedan errores en ExcelServiceImpl/DuxService).

- [ ] **Step 8: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/
git commit -m "refactor(producto): busqueda solo por titulo_dux + renombrar filtros/patch/auditoria"
```

---

## FASE 4 — Excel

### Task 4: ExcelServiceImpl

**Files (modify):** `excel/service/ExcelServiceImpl.java`

- [ ] **Step 1: Headers de export** (≈línea 2860): en el array de headers reemplazar `"DESCRIPCION", "TITULO_WEB"` por `"TITULO_DUX", "TITULO_ML", "TITULO_NUBE"`. Ajustar la fila de datos correspondiente para escribir `getTituloDux()`, `getTituloMl()`, `getTituloNube()` en esas columnas (mantener el orden header↔valor).

- [ ] **Step 2: Defaults y setters** (≈1357-1358, 2115-2116): `nuevo.setDescripcion("")`→`nuevo.setTituloDux("")`; `nuevo.setTituloWeb("")`→`nuevo.setTituloNube("")`.

- [ ] **Step 3: Import** (≈1362-1388, 1782-1795, 2474-2479): donde se lee la columna "PRODUCTO"/"DESCRIPCION" y se setea `setDescripcion(...)` → `setTituloDux(...)`; donde se lee "TITULO WEB" y se setea `setTituloWeb(...)` → `setTituloNube(...)`. Si se quiere importar Título ML, agregar la lectura de la nueva columna `TITULO_ML` → `setTituloMl(...)`; si no, dejar `tituloMl` sin tocar en import.

- [ ] **Step 4: Ordenamiento** (≈3755-3769): el sort por `tituloWeb` (fallback `descripcion`) → ordenar por `getTituloDux()`.

- [ ] **Step 5: Compilar**
Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"` (continuar si queda DuxService).

- [ ] **Step 6: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/excel/service/ExcelServiceImpl.java
git commit -m "refactor(excel): columnas TITULO_DUX/ML/NUBE en import-export"
```

---

## FASE 5 — DUX

### Task 5: DuxService (export + import)

**Files (modify):** `apis/dux/service/DuxService.java`

- [ ] **Step 1: Export — item y descripción** (≈1291-1313): reemplazar
```java
                itemDux.put("item", producto.getDescripcion() != null ? producto.getDescripcion() : "");
```
por:
```java
                itemDux.put("item", producto.getTituloDux() != null ? producto.getTituloDux() : "");
```
y el bloque de la descripción larga:
```java
                // Título web → descripción larga de Dux (dato que antes no se enviaba).
                if (producto.getTituloWeb() != null && !producto.getTituloWeb().isBlank()) {
                    itemDux.put("descripcion", producto.getTituloWeb());
                }
```
por:
```java
                // Título Nube → descripción larga de Dux.
                if (producto.getTituloNube() != null && !producto.getTituloNube().isBlank()) {
                    itemDux.put("descripcion", producto.getTituloNube());
                }
```

- [ ] **Step 2: Import desde DUX** (≈499 y 713): los `producto.setDescripcion(...)` que escriben el `item` de DUX en el producto → `producto.setTituloDux(...)` (el item de DUX corresponde al Título Dux).

- [ ] **Step 3: Compilar TODO el backend**
Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o compile"`
Expected: BUILD SUCCESS (sin referencias a getDescripcion/getTituloWeb del Producto). Resolver cualquier residual con grep `getDescripcion()|getTituloWeb()|setDescripcion()|setTituloWeb()` en `dominio/producto` y consumidores del Producto.

- [ ] **Step 4: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/dux/service/DuxService.java
git commit -m "refactor(dux): item=titulo_dux, descripcion=titulo_nube"
```

---

## FASE 6 — Frontend: tipos y modal

### Task 6: Tipos + modal de creación/edición

**Files (modify):** `productos/types.ts`, `productos/page.tsx`

- [ ] **Step 1: `types.ts`** — en `ProductoDTO` y `ProductoCreateDTO` renombrar `descripcion`→`tituloDux`, `tituloWeb`→`tituloNube`, y agregar `tituloMl: string | null;` (en ProductoDTO) / `tituloMl: string | null;` (en CreateDTO). Si existe `ProductoPatchDTO` en TS, igual.

- [ ] **Step 2: `page.tsx` — estados** (≈233-234): reemplazar
```tsx
const [tituloWeb, setTituloWeb] = useState("");
const [descripcion, setDescripcion] = useState("");
```
por:
```tsx
const [tituloDux, setTituloDux] = useState("");
const [tituloMl, setTituloMl] = useState("");
const [tituloNube, setTituloNube] = useState("");
```

- [ ] **Step 3: `validateForm`** (≈528-531): reemplazar las validaciones de descripcion/tituloWeb por (solo Dux obligatorio):
```tsx
        if (!tituloDux.trim()) errors.tituloDux = "El Título Dux es obligatorio";
        else if (tituloDux.trim().length > 100) errors.tituloDux = "Máximo 100 caracteres";
        if (tituloMl.trim().length > 100) errors.tituloMl = "Máximo 100 caracteres";
        if (tituloNube.trim().length > 100) errors.tituloNube = "Máximo 100 caracteres";
```

- [ ] **Step 4: Inputs del modal** (≈1220-1229): reemplazar los dos `<label>` (Título Web + Descripción) por tres:
```tsx
                            <label className="block xl:col-span-4">
                                <span className={fieldLabelClassName}>Título Dux <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloDux ? inputErrorClassName : ""}`} value={tituloDux} onChange={e => { setTituloDux(e.target.value); if (formErrors.tituloDux) setFormErrors(p => ({ ...p, tituloDux: "" })); }} placeholder="Título principal (Dux)" required />
                                {formErrors.tituloDux && <p className="mt-1 text-xs text-red-500">{formErrors.tituloDux}</p>}
                            </label>
                            <label className="block md:col-span-2">
                                <span className={fieldLabelClassName}>Título ML</span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloMl ? inputErrorClassName : ""}`} value={tituloMl} onChange={e => { setTituloMl(e.target.value); if (formErrors.tituloMl) setFormErrors(p => ({ ...p, tituloMl: "" })); }} placeholder="Título para Mercado Libre" />
                                {formErrors.tituloMl && <p className="mt-1 text-xs text-red-500">{formErrors.tituloMl}</p>}
                            </label>
                            <label className="block md:col-span-2">
                                <span className={fieldLabelClassName}>Título Nube</span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloNube ? inputErrorClassName : ""}`} value={tituloNube} onChange={e => { setTituloNube(e.target.value); if (formErrors.tituloNube) setFormErrors(p => ({ ...p, tituloNube: "" })); }} placeholder="Título para Tienda Nube" />
                                {formErrors.tituloNube && <p className="mt-1 text-xs text-red-500">{formErrors.tituloNube}</p>}
                            </label>
```

- [ ] **Step 5: Payload create** (≈614): en `payload`, reemplazar `tituloWeb: tituloWeb.trim(), descripcion: descripcion.trim(),` por `tituloDux: tituloDux.trim(), tituloMl: tituloMl.trim() || null, tituloNube: tituloNube.trim() || null,`.

- [ ] **Step 6: Patch edición** (≈722): en el `patch`, reemplazar `tituloWeb: tituloWeb.trim(), descripcion: descripcion.trim(),` por `tituloDux: tituloDux.trim(), tituloMl: tituloMl.trim() || null, tituloNube: tituloNube.trim() || null,`.

- [ ] **Step 7: Carga al editar** (≈656-657): reemplazar
```tsx
setTituloWeb(producto.tituloWeb ?? "");
setDescripcion(producto.descripcion ?? "");
```
por:
```tsx
setTituloDux(producto.tituloDux ?? "");
setTituloMl(producto.tituloMl ?? "");
setTituloNube(producto.tituloNube ?? "");
```

- [ ] **Step 8: Reset** (≈931): reemplazar `setTituloWeb(""); setDescripcion("");` por `setTituloDux(""); setTituloMl(""); setTituloNube("");`.

- [ ] **Step 9: Typecheck + commit**
```bash
cd supermaster-frontend && cmd /c "npx tsc --noEmit"
git add supermaster-frontend/src/app/productos/types.ts supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): 3 titulos en el modal (Dux oblig., ML/Nube opc.)"
```

---

## FASE 7 — Frontend: tabla y consumidores

### Task 7: Tabla de productos + buscador

**Files (modify):** `productos/columns.tsx`, `productos/page.tsx`

- [ ] **Step 1: `columns.tsx`** (≈351-357): reemplazar las columnas `descripcion` y `tituloWeb` por tres:
```tsx
        {
            accessorKey: "tituloDux", header: "Título Dux", size: 250, meta: { editable: true },
            cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={getValue() as string} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} className={FONT.text} disabled={!canEdit} />)
        },
        {
            accessorKey: "tituloMl", header: "Título ML", size: 220, meta: { editable: true },
            cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={(getValue() as string) ?? ""} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} className={FONT.title} disabled={!canEdit} />)
        },
        {
            accessorKey: "tituloNube", header: "Título Nube", size: 220, meta: { editable: true },
            cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={(getValue() as string) ?? ""} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} className={FONT.title} disabled={!canEdit} />)
        },
```

- [ ] **Step 2: Search placeholder** (`page.tsx` ≈1093): cambiar `"Buscar producto por SKU, MLA, cód. ext. o descripción..."` por `"Buscar producto por SKU, MLA, cód. ext. o título..."`.

- [ ] **Step 3: Typecheck + commit**
```bash
cd supermaster-frontend && cmd /c "npx tsc --noEmit"
git add supermaster-frontend/src/app/productos/columns.tsx supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): columnas Titulo Dux/ML/Nube en la tabla"
```

### Task 8: Consumidores (Monitor de precios + simulador)

**Files (modify):** `producto-canal-precios/types.ts`, `producto-canal-precios/MonitorPrecios.tsx`, `producto-canal-precios/sortMap.ts`, `calculadora-precios/simuladorService.ts`

- [ ] **Step 1: `producto-canal-precios/types.ts`** (≈59): en `ProductoCanalPrecioDTO`, renombrar `descripcion: string;` → `tituloDux: string;`.

- [ ] **Step 2: `MonitorPrecios.tsx`** — `FilaComparador.descripcion` (≈58) → `tituloDux`; en `buildFila`/`aplanarParaExport` (≈194) `descripcion: prod.descripcion` → `tituloDux: prod.tituloDux`; la columna (≈539-555) `accessorKey: "descripcion"` → `accessorKey: "tituloDux"` (header "Producto" se mantiene); el `getValue()` y la lógica de badges quedan igual.

- [ ] **Step 3: `sortMap.ts`** (≈5): `descripcion: "descripcion"` → `tituloDux: "tituloDux"` (clave y valor; el backend ordena por `tituloDux`).

- [ ] **Step 4: `simuladorService.ts`** (≈92 y 165): reemplazar `p.tituloWeb || p.descripcion || ""` por `p.tituloDux || ""` y `producto.tituloWeb || producto.descripcion || ""` por `producto.tituloDux || ""`.

- [ ] **Step 5: Typecheck + commit**
```bash
cd supermaster-frontend && cmd /c "npx tsc --noEmit"
git add supermaster-frontend/src/app/producto-canal-precios/ supermaster-frontend/src/app/calculadora-precios/simuladorService.ts
git commit -m "refactor(front): adaptar Monitor y simulador a titulo_dux"
```

---

## FASE 8 — Verificación

### Task 9: Verificación end-to-end

- [ ] **Step 1: Ejecutar el SQL** (lo corre el coordinador): `tres-titulos-producto.sql` en la BD local. Verificar con `DESCRIBE supermaster.productos;` que existen `titulo_dux` (NOT NULL), `titulo_ml` (NULL), `titulo_nube` (NULL) y que `descripcion`/`titulo_web` ya no están.

- [ ] **Step 2: Suite backend + arranque**
Run: `cd supermaster-backend && cmd /c "mvnw.cmd -q -o test"`
Expected: BUILD SUCCESS. Adaptar cualquier test que referencie `descripcion`/`tituloWeb` del producto (renombrar a `tituloDux`/`tituloNube`). Verificar arranque con `ddl-auto=validate` (entidad ↔ columnas nuevas).

- [ ] **Step 3: Frontend build**
Run: `cd supermaster-frontend && cmd /c "npm run build"`
Expected: compila sin errores; `git grep -niE "\.descripcion|tituloWeb" supermaster-frontend/src/app/productos supermaster-frontend/src/app/producto-canal-precios supermaster-frontend/src/app/calculadora-precios` no devuelve usos del producto (puede haber `descripcion` de otras entidades — revisar).

- [ ] **Step 4: Verificación manual** (requiere SQL corrido + backend levantado)
1. Crear producto: Título Dux obligatorio (rebota si falta), ML/Nube opcionales.
2. Editar producto: los 3 títulos se cargan/guardan.
3. Tabla: 3 columnas (Dux/ML/Nube), edición inline.
4. Búsqueda: matchea por Título Dux.
5. Export a DUX: `item`=Título Dux, descripción=Título Nube.
6. Monitor de precios y simulador muestran el Título Dux.

---

## Notas / riesgos
- **Rename guiado por el compilador:** tras renombrar la entidad (Task 1), `mvnw compile` señala cada consumidor del producto a corregir. Usar `getDescripcion()`/`getTituloWeb()` como guía, pero NO tocar las de otras entidades (CanalConceptoCuota, etc.).
- **Schema primero:** nada arranca (validate) hasta correr el SQL. La compilación Java sí funciona sin la BD.
- **`titulo_nube` pasa a nullable:** quitar `nullable=false`/`@NotNull` donde aplique (entidad ya lo refleja; DTO Create sin @NotBlank).
- **Fuera de alcance:** validación condicional de Nube/ML por canal; alta en Tienda Nube (Sub-proyecto B).
```
