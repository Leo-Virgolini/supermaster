# Post-alta en Mercado Libre + ajustes de la tabla Productos — Diseño

**Fecha:** 2026-06-18
**Estado:** Aprobado
**Continúa la Fase C1** (alta a Mercado Libre) y agrega ajustes de UI en la tabla de Productos.

## Objetivo

Dos piezas independientes:
- **Parte 1 (backend):** completar el alta a Mercado Libre con el flujo post-creación — dejar la
  publicación pausada (stock 0), asociar el MLA resultante al producto y calcular comisión y envío.
- **Parte 2 (frontend):** tres ajustes de la tabla de Productos para ver más filas y menos ruido.

---

## Parte 1 — Post-alta en Mercado Libre

### Decisiones tomadas (brainstorming)

| Tema | Decisión |
|---|---|
| Pausar | **Crear con `available_quantity=0` directo** (la doc de ML: 0 al crear pausa `out_of_stock` para `condition=new` no-free / Fulfillment). **No** hay PUT de pausa separado. |
| Reintento de stock | **Se elimina** el reintento 0→1 del núcleo de C1 (y su advertencia/test). Si ML rechaza el 0 → ese SKU es error. |
| MLA resultante | Capturar el `id` (código MLA) **y** el `user_product_id` (MLAU) de la respuesta del alta. |
| Asociación | Asegurar/crear el `Mla` en la BD (con el código MLA + MLAU) y asociarlo al producto (`producto.setMla(...)`). |
| Costos | Calcular **comisión** (`obtenerCostoVenta`) y **envío** (`calcularCostoEnvioGratis`) reusando los métodos existentes (persisten en el `Mla` y disparan recálculo de precios). |
| Manejo de fallos | El post-alta es **best-effort**: pausa/asociación/costos que fallen se informan como **aviso** en el resumen; el ítem ya está en ML, no se revierte ni se frena el resto de los SKUs. |

### Contexto del sistema (verificado)

- **`crearItemEnMl` / `crearItemEnMlCore`** (C1, en `MercadoLibreService`): el núcleo arma el payload (con
  `available_quantity=0`), postea `/items` y captura el `id`. Hoy reintenta 0→1 si el error menciona
  "quantity" — eso **se elimina**.
- **`ResultadoAltaMl`** (record): `(Estado{CREADO,YA_EXISTIA,ERROR}, motivo, itemId, advertencia)`.
- **`MlaService.asegurarMla(mlaCode, mlau)` → `Integer mlaId`** (`@Transactional`, tx corta): crea/reusa el
  `Mla`. Patrón de referencia: `MlaService.obtenerOcrearPorSkuDesdeML` ya hace asegurar + comisión + envío.
- **`MercadoLibreService.obtenerCostoVenta(mlaCode)`** (`@Transactional`): comisión → persiste en `Mla`.
- **`MercadoLibreService.calcularCostoEnvioGratis(mlaCode)`** (`@Transactional`): envío → persiste en `Mla`.
- **`Producto.setMla(Mla)`** / `getMla()` (ManyToOne LAZY); `MlaRepository.findFirstByMla(code)`.
- **`MlRetryHandler.putJson(uri, tokenSupplier, json)`**: disponible (no se usa para pausar en este diseño,
  porque el 0 al crear ya pausa).
- **`MlExportService.exportar`** hoy es `@Transactional(readOnly=true)` — incompatible con la escritura de
  la asociación. Pasa a procesar **una transacción por SKU** (lazy del alta + escritura de asociación,
  aislando cada SKU).

### Alcance (Parte 1)

1. **Núcleo del alta** (`crearItemEnMlCore`): quitar el reintento 0→1; crear siempre con
   `available_quantity=0`; si ML devuelve error → `ResultadoAltaMl.error(...)`. Capturar el
   `user_product_id` de la respuesta además del `id`.

2. **`ResultadoAltaMl`**: sumar el campo `String mlau`. `creado(itemId, mlau)`; los demás factories lo
   dejan en null.

3. **Asociación MLA→producto** (`MlaService`, nuevo método `@Transactional`):
   `asegurarYAsociar(productoId, mlaCode, mlau)` → asegura el `Mla` (reusa `asegurarMla`) y hace
   `producto.setMla(mla)` + save. Devuelve el `mlaId`.

4. **Orquestación post-alta** en `MlExportService` (por SKU, tras `CREADO` con `itemId`):
   - `asegurarYAsociar(producto.id, itemId, mlau)` (escritura).
   - `obtenerCostoVenta(itemId)` (comisión).
   - `calcularCostoEnvioGratis(itemId)` (envío).
   - Cada paso en su try/catch; un fallo agrega un aviso (`itemId + ": no se pudo <paso>"`) sin marcar
     error. El recálculo de precios se dispara solo (post-commit de los métodos de costo).

5. **Transaccionalidad** de `MlExportService.exportar`: el loop procesa cada SKU dentro de su propia
   transacción (read-write) — vía un método transaccional por SKU o invocando el procesamiento a través
   del proxy (`self`). Mantiene el lazy-loading del producto para el alta y permite la escritura de la
   asociación; un fallo de un SKU no revierte los otros. Los métodos de costo conservan su `@Transactional`.

6. **Resumen**: `MlExportResultDTO` ya tiene `advertencias` (de C1); los avisos del post-alta se acumulan
   ahí por SKU.

### Manejo de errores
- Alta rechazada (incl. stock 0 no admitido si la cuenta no es Fulfillment) → error por SKU.
- Asociación/costos fallidos → aviso por SKU; no revierte el ítem ni frena el resto.

### Testing
- Núcleo `crearItemEnMlCore` (lambdas, sin red): ya no reintenta; crea con stock 0; captura `id` + `mlau`.
  Se **elimina** el test del reintento 0→1; se ajusta el test "ok" para verificar `itemId` + `mlau`.
- La orquestación post-alta (asegurar MLA, asociar, costos) es I/O/persistencia: no se testea contra ML
  real (consistente con el resto). `asegurarYAsociar` se puede testear con repos mockeados si es barato.

---

## Parte 2 — Ajustes de la tabla de Productos (frontend)

### Decisiones tomadas

| Ajuste | Decisión |
|---|---|
| Filtros | Hacer el panel de filtros **más compacto** (menos alto). |
| Vistas guardadas | **Eliminar** la sección "Vistas / guardar vista" por completo. |
| Filas | **Mismo alto** para todas: títulos truncados a **1 línea con `…`**. |
| Botón de columnas | **Sin cambios** (se mantiene). |

### Alcance (Parte 2)

1. **Filtros más compactos** (`productos/ProductosFilterBar.tsx`): reducir el espaciado del panel —
   `space-y-4`→`space-y-2`, `py-4`→`py-2`/`py-3`, `gap-3`→`gap-2`, `GroupTitle` `mb-2`→`mb-1` — para que
   ocupe bastante menos alto sin cambiar la funcionalidad ni los campos.

2. **Quitar Vistas guardadas** (`productos/page.tsx`): eliminar la barra superior (Vistas / Seleccionar /
   Aplicar / Guardar / Borrar, ~líneas 1104-1146), el modal de guardar vista (~1619-1647), y todo el
   estado/handlers asociados (`savedViews`, `selectedViewId`, `viewName`, `isViewModalOpen`,
   `handleSaveView`, `handleApplyView`, `handleDeleteView`, `openSaveView`, `closeSaveView`) y la constante
   de `localStorage` (`PRODUCTOS_VIEWS_STORAGE_KEY`). No deben quedar imports/íconos huérfanos
   (`BookmarkIcon`).

3. **Filas de igual alto** (`productos/columns.tsx`): las celdas de título (`tituloDux`, `tituloMl`,
   `tituloNube`) se truncan a 1 línea con ellipsis. Se logra agregando al `className` que reciben esas
   celdas una clase de truncado (`truncate`/`line-clamp-1` con ancho acotado al `size` de la columna), o
   envolviendo el valor mostrado del `EditableCell` en un contenedor truncado **sin** alterar el
   comportamiento de edición. El cambio se aplica **solo a las columnas de Productos** (no al `EditableCell`
   genérico). Al pasar el mouse (title nativo) o al editar, el texto se ve completo.

4. **Botón de columnas:** sin cambios (queda en el `TableToolbar` genérico).

### Testing
- Typecheck (`npx tsc --noEmit`) y verificación visual (sin tests automatizados de UI en el proyecto).

---

## Fuera de alcance
- C2 (ficha técnica / atributos por categoría de ML).
- Republicar/actualizar ítems ya existentes en ML; variaciones; garantía.
- Cambios en otras tablas del sistema (el botón de columnas del toolbar genérico no se toca).
