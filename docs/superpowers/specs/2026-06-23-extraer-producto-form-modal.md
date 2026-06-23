# Extraer el formulario de producto a `ProductoFormModal` (fix lag al tipear)

**Fecha:** 2026-06-23
**Estado:** Diseño aprobado, pendiente de plan.

## Objetivo

Tipear en cualquier input del modal de alta/edición de producto tiene **lag**. Causa confirmada (no es blur — el modal usa `bg-black/60` sin desenfoque): `ProductosPage` es un componente monolítico que contiene **a la vez el formulario del modal y la tabla** de productos. Cada tecla actualiza un `useState` del form → re-renderiza todo `ProductosPage` → **re-renderiza la `<Table>` de 50–100 filas** (que no está memoizada y está montada detrás del modal). Ese re-render por tecla es el lag.

Esta feature extrae el formulario a un componente separado **`ProductoFormModal`** con su propio estado. Así, **tipear en el form re-renderiza solo el form** — la página y la tabla no se vuelven a renderizar.

## Decisiones tomadas (brainstorming 2026-06-23)

1. **Componente separado `ProductoFormModal`** con TODO el estado del form adentro.
2. **Montaje condicional:** la página renderiza `{isModalOpen && <ProductoFormModal .../>}`. El form se monta al abrir (estado fresco) y se desmonta al cerrar.
3. **Apertura por datos, no imperativa:** la tabla deja de llamar `abrirEdicionRef.current(producto)`; en su lugar la página guarda `productoAEditar` y el form se **precarga** al montarse (la lógica de `abrirEdicion` se muda al form).
4. **El hook `useProductos` queda en la página**; el form recibe los métodos que usa (`createProducto`, `updateProductoMargen`) y un `onSuccess`/`refresh` como props.
5. **Movimiento mecánico:** no se cambia la lógica interna del form (handlers, validación, flujo de exports) — solo se muda de archivo y se ajusta la frontera por props. Verificación: `npx tsc --noEmit` + smoke.

## Alcance

### Incluye
- Crear `ProductoFormModal.tsx` con el modal completo (estado, handlers, effects del form).
- Reducir `ProductosPage` (`page.tsx`) a la tabla/filtros + control del modal (`isModalOpen`, `productoAEditar`).

### NO incluye
- Cambiar la lógica/comportamiento del alta, edición, exports, validación, imágenes, etc. (debe quedar idéntico).
- Memoizar la tabla (innecesario una vez extraído el form: el estado del form ya no vive en `ProductosPage`).
- Tocar `columns.tsx` salvo el callback de "editar producto" (ver más abajo).

## Frontera (mapeo confirmado de `page.tsx`)

### Se MUEVE a `ProductoFormModal`
- **Estados del form** (~75 `useState`): `sku`, `lastSuggestedSku`, `skuYaExiste`, `codExt`, `tituloDux`, `tituloMl`, `mlCategoryId/Nombre`, `prediccionesMl`, `cargandoPrediccionesMl`, `tituloNube`, `esCombo`, `subirADux/KtHogar/KtGastro/Ml`, `imagenesDetectadas`, `cuotaHogar/Gastro`, `cuotasHogarOpts/GastroOpts`, `resultadosCanal`, `skuSubida`, `reintentando`, `uxb`, `activo`, `carouselSku`, dimensiones (`capacidad/largo/ancho/alto/diamboca/diambase/espesor`), `costo`, `iva`, los IDs de maestros (`marcaId/origenId/clasifGralId/clasifGastroId/tipoId/proveedorId/materialId/sectorDepositoId`) y sus `*Display`, MLA (`mlaId/mlaDisplay/showNuevoMla/mlaCodigo/mlaMlau/mlaPrecioEnvio/mlaTope/mlaComision/obteniendoMla/creandoMla`), `margenMinorista/Mayorista`, `catalogosSel/aptosSel/clientesSel`, `preciosInfladosSel`, `editandoProductoId`, `panelTab`, `catalogosOriginal/aptosOriginal/clientesOriginal`, `moq/stock/tagReposicion/tag`, `formErrors`, `isSaving`.
- **Handlers del form**: `validateForm`, `asociarMargenYRelaciones`, `canalesMarcados`, `ejecutarExportsCanales`, `handleCreate`, `abrirEdicion` (→ precarga al montar), `handleGuardarEdicion`, `reintentarFallidos`, `cargarSkuSugerido`, `handleToggleCombo`, `handleObtenerMlaDeML`, `handlePredecirCategoriasMl`, `handleCrearMla`, `resetForm`.
- **useEffect del form**: validación SKU duplicado (debounce 400 ms), carga de `imagenesDetectadas` por SKU (debounce 400 ms), carga de `cuotasHogarOpts/GastroOpts` al montar.
- **Subcomponentes del modal** que solo usa el form: las secciones del form, `ImagenesCarousel`, `PreciosInfladosSection`, `HistorialSection`, etc. (siguen importándose desde el nuevo archivo).

### QUEDA en `ProductosPage`
- **Tabla y filtros**: `productos`, `totalRecords`, `isLoading`, paginación (`pageIndex/pageSize`), `filters`, `sorting`, `rowSelection`, `activeOverrides`, `columnVisibilityVersion`, `filtrosExpanded`, `filterValueLabels`, y sus handlers (`handleUpdate`, `handleDelete`, `handleExportAll`, `handleGlobalSearch`, `handleColumnFilterChange`, `handlePanelFilterChange`, `clearFilter`, `clearAllFilters`, `toggleFiltros`) y sus effects (sync URL↔filtros, resolver labels de filtros, limpiar `activeOverrides`, carga inicial).
- **El hook `useProductos`** (línea ~242).
- **2 estados nuevos**: `const [isModalOpen, setIsModalOpen] = useState(false)` y `const [productoAEditar, setProductoAEditar] = useState<ProductoDTO | null>(null)`.

## Diseño

### Interfaz de `ProductoFormModal`
```ts
type ProductoFormModalProps = {
    producto: ProductoDTO | null;            // null = crear; ProductoDTO = editar
    canEditProductos: boolean;
    canExportarDux: boolean;
    createProducto: (data: ProductoCreateDTO, afterCreate?: (id: number) => Promise<void>) => Promise<ProductoDTO>;
    updateProductoMargen: (id: number, data: { margenMinorista?: number; margenMayorista?: number }) => Promise<void>;
    onClose: () => void;                     // cerrar (la página baja isModalOpen + limpia productoAEditar)
    onSuccess: () => void | Promise<void>;   // refrescar la tabla tras guardar/exportar OK
};
```
- El form usa los **servicios API directos** (`createMlaAPI`, `updateProductoAPI`, `updateProductoMargenAPI`, `exportarProductos*API`, `recalcularProductoAPI`, `calcularEnvioMlaAPI`, `getImagenDetalleAPI`, etc.) que ya importaba, salvo `createProducto`/`updateProductoMargen` que vienen del hook por props (para que el refetch/estado local de la tabla siga funcionando como hoy).
- **Precarga (edición):** un `useEffect` al montar — si `producto != null`, ejecuta la precarga (lo que hoy hace `abrirEdicion`); si es null, sugiere el SKU (`cargarSkuSugerido`).
- **Cierre exitoso:** donde hoy hace `resetForm() + setIsModalOpen(false)`, ahora hace `onClose()` (la página cierra). Donde hace `refresh()`, ahora `onSuccess()`.

### Cambios en `ProductosPage`
- Quitar todo el estado/handlers/effects del form (movidos).
- `getColumns` recibe un `onEditarProducto` que hace `setProductoAEditar(producto); setIsModalOpen(true)` (reemplaza `abrirEdicionRef`). El `abrirEdicionRef` se elimina (ya no hace falta el indirecto: la apertura es por dato, y `columns` se memoiza con `onEditarProducto` estable vía `useCallback`).
- El botón "Crear Producto" / `handleAbrirCrear`: `setProductoAEditar(null); setIsModalOpen(true)`.
- Render: `{isModalOpen && <ProductoFormModal producto={productoAEditar} ... onClose={...} onSuccess={refresh} createProducto={createProducto} updateProductoMargen={updateProductoMargen} canEditProductos={canEditProductos} canExportarDux={canExportarDux} />}`.

## Manejo de errores
- El comportamiento de errores del form (validación, fallo de exports → panel + reintento, modal abierto) se mantiene **idéntico**; solo cambia que el cierre/refresh se delega a la página vía `onClose`/`onSuccess`.

## Pruebas
- No hay test runner en el front: verificación con **`npx tsc --noEmit`** tras cada paso del plan + **smoke**.
- **Smoke (usuario):** (1) **el lag desaparece** al tipear en el form; (2) alta completa (con margen, canales, imágenes, MLA, predicción de categoría) funciona igual; (3) edición precarga todos los campos y guarda; (4) el panel de canales + "Reintentar los que fallaron" sigue andando; (5) la edición inline de la tabla, los filtros, la selección y el borrado siguen funcionando; (6) abrir/cerrar el modal varias veces no deja estado pegado.

## Archivos afectados
**Frontend:**
- Crear: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`.
- Modificar: `supermaster-frontend/src/app/productos/page.tsx` (reducir a tabla + control del modal).
- Modificar: `supermaster-frontend/src/app/productos/columns.tsx` (solo si el callback de editar cambia de firma; hoy `getColumns(onEditarProducto, canEdit)` ya recibe la función — se le pasa la nueva).

## Pendiente de validar en smoke (usuario)
- El lag al tipear desaparece.
- Todos los flujos de alta/edición/exports/inline siguen igual que antes del refactor.
