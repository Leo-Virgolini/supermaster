# Extraer ProductoFormModal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminar el lag al tipear extrayendo el formulario del modal a un componente `ProductoFormModal` con su propio estado, de modo que tipear no re-renderice la tabla.

**Architecture:** Refactor "mover X a Y": se corta todo el modal del producto (estado, handlers, effects, JSX) de `ProductosPage` a `ProductoFormModal.tsx`. La página queda con tabla/filtros + `isModalOpen`/`productoAEditar` y renderiza `{isModalOpen && <ProductoFormModal .../>}` (montaje condicional). La lógica interna del form NO cambia.

**Tech Stack:** Next.js 16 / React 19 / TS. Verificación: `npx tsc --noEmit` (no hay test runner) + smoke.

## Global Constraints

- Trabajar en `main`. Frontend: `npx tsc --noEmit` desde `supermaster-frontend/`.
- **Movimiento mecánico:** la lógica del alta/edición/exports/validación/imágenes/MLA/predicciones se mueve **tal cual**; NO se reescribe comportamiento.
- Montaje condicional (`{isModalOpen && <ProductoFormModal/>}`), estado fresco al abrir.
- El hook `useProductos` queda en la página; el form recibe `createProducto`/`updateProductoMargen`/`onSuccess` por props.
- La apertura es por dato: la tabla setea `productoAEditar`; el form se precarga al montar (la lógica de `abrirEdicion` se muda al form). Se elimina `abrirEdicionRef`.
- Commits terminan EXACTAMENTE con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: Crear `ProductoFormModal.tsx` con todo el form

**Files:**
- Create: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`
- Modify: `supermaster-frontend/src/app/productos/page.tsx` (se vacía de lo movido en Task 2; en Task 1 se deja intacto y se construye el nuevo archivo en paralelo)

**Interfaces:**
- Produces: `export default function ProductoFormModal(props: ProductoFormModalProps)` con
  ```ts
  type ProductoFormModalProps = {
      producto: ProductoDTO | null;
      canEditProductos: boolean;
      canExportarDux: boolean;
      createProducto: (data: ProductoCreateDTO, afterCreate?: (id: number) => Promise<void>) => Promise<ProductoDTO>;
      updateProductoMargen: (id: number, data: { margenMinorista?: number; margenMayorista?: number }) => Promise<void>;
      onClose: () => void;
      onSuccess: () => void | Promise<void>;
  };
  ```

- [ ] **Step 1: Crear el esqueleto del componente con imports y firma**

Crear el archivo con `"use client"`, los imports que usa el form (copiar de `page.tsx` los que el modal usa: `useState/useEffect/useCallback/useRef`, `toast`/`notificar`, `API_BASE_URL`, heroicons usados en el modal, `Button`, `Tooltip`, `Modal`, `AsyncSelect`/`MultiAsyncSelect`, `PreciosInfladosSection`, `HistorialSection`, `ImagenesCarousel`, los `search*` de `productosService`, `createMlaAPI`, `getMlaPorSkuAPI`, `getSiguienteSkuAPI`, `existeSkuAPI`, `getProductosForExportAPI` (no), `updateProductoAPI`, `exportarProductos*API`, `recalcularProductoAPI`, `calcularEnvioMlaAPI`, `predecirCategoriasMlAPI`, `getImagenDetalleAPI`, `asignarPrecioInfladoAPI`, `add/removeProducto*API`, `calcularEnvioMlaAPI`, `getCuotasPorCanalAPI`, los tipos `ProductoDTO/ProductoCreateDTO/ProductoPatchDTO`, `ImagenDetalle`, `PrecioInfladoDraft`), la firma `ProductoFormModalProps` y `export default function ProductoFormModal({ producto, canEditProductos, canExportarDux, createProducto, updateProductoMargen, onClose, onSuccess }: ProductoFormModalProps) { ... }`.

- [ ] **Step 2: Mover las constantes/tipos de módulo que usa el form**

Mover a la cabecera de `ProductoFormModal.tsx` (fuera del componente): `type CanalExport`, `type ResultadoCanal`, `function clasificarExport(...)`, `const etiquetaCuota`, `type CuotaOpcion`, `const EXT_ML/EXT_NUBE/MAX_BYTES_IMG`. (Quedarán también referenciados; se quitan de `page.tsx` en Task 2.)

- [ ] **Step 3: Mover TODOS los estados del form**

Copiar al cuerpo del componente todos los `useState` clasificados como FORM en la spec (sku, títulos, esCombo, canales, cuotas, dimensiones, costo/iva, IDs+display de maestros, MLA, márgenes, N-a-N, preciosInfladosSel, panelTab, moq/stock/tag, formErrors, isSaving, imagenesDetectadas, carouselSku, prediccionesMl, resultadosCanal/skuSubida/reintentando, etc.). **Quitar** `isModalOpen` y `editandoProductoId` del set de props: en su lugar, derivar `const editandoProductoId = producto?.id ?? null;` (o mantener `editandoProductoId` como estado seteado en la precarga — ver Step 5). Mantener `editandoProductoId` como estado local (se setea en la precarga) para no tocar las ~30 referencias.

- [ ] **Step 4: Mover handlers y effects del form**

Copiar al componente: `validateForm`, `asociarMargenYRelaciones`, `canalesMarcados`, `ejecutarExportsCanales`, `handleCreate`, `handleGuardarEdicion`, `reintentarFallidos`, `cargarSkuSugerido`, `handleToggleCombo`, `handleObtenerMlaDeML`, `handlePredecirCategoriasMl`, `handleCrearMla`, `resetForm`, y los 2 `useEffect` (SKU duplicado, imágenes detectadas) + el `useEffect` de carga de cuotas. **Reemplazos mecánicos:**
- `createProducto(...)` y `updateProductoMargen(...)` → ahora son props (ya disponibles).
- Donde hoy hace `resetForm(); setIsModalOpen(false);` (cierre exitoso del alta) → `onClose();`.
- En `handleGuardarEdicion`, `resetForm(); setEditandoProductoId(null); setIsModalOpen(false);` → `onClose();`. Donde hace `await refresh();` → `await onSuccess();`. Igual en `reintentarFallidos`.
- `handleAbrirCrear`/`abrirEdicion` NO se mueven como tales: su lógica de precarga va al `useEffect` del Step 5.

- [ ] **Step 5: Precarga al montar (reemplaza abrirEdicion/handleAbrirCrear)**

Agregar un `useEffect` que corre una vez al montar:
```tsx
useEffect(() => {
    if (producto) {
        // ... mover acá el cuerpo de abrirEdicion(producto): setSku(producto.sku ?? ""), ... setEditandoProductoId(producto.id),
        //     carga de N-a-N (aptos/catalogos/clientes) y displays, etc.
    } else {
        // creación: setEditandoProductoId(null) + cargarSkuSugerido(esCombo=false)
        void cargarSkuSugerido(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
}, []);
```
(El cuerpo de `abrirEdicion` se copia tal cual acá, sin el `setIsModalOpen(true)` final.)

- [ ] **Step 6: Mover el JSX del modal**

Copiar el `return (...)` con el `<Modal isOpen onClose={onClose} ...>...</Modal>` y todo su contenido (el form). Cambios: `isOpen` del Modal = siempre `true` (el montaje condicional ya controla la visibilidad) o `isOpen={true}`. El `onClose` del Modal → `onClose` prop. Los botones del footer (Guardar/Crear/Cancelar) usan `onClose` para cancelar. Render del carousel: `{carouselSku && <ImagenesCarousel sku={carouselSku} onClose={() => setCarouselSku(null)} />}` queda dentro o al lado del Modal.

- [ ] **Step 7: Typecheck del archivo nuevo (con page.tsx aún intacto puede haber duplicados — se resuelve en Task 2)**

No commitear todavía; Task 1 y Task 2 forman un solo cambio atómico. Avanzar a Task 2 antes del typecheck final.

---

### Task 2: Reducir `ProductosPage` y conectar el nuevo componente

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

- [ ] **Step 1: Quitar de `page.tsx` todo lo movido**

Borrar: los `useState`/`useEffect`/handlers del form (los movidos en Task 1), las constantes/tipos de módulo movidos (`CanalExport`, `ResultadoCanal`, `clasificarExport`, `etiquetaCuota`, `CuotaOpcion`, `EXT_ML/EXT_NUBE/MAX_BYTES_IMG`), el `<Modal>` completo del producto y el render del carousel del form, `abrirEdicionRef`, `handleAbrirCrear`/`abrirEdicion`, e imports que ya no se usen (se detectan por typecheck).

- [ ] **Step 2: Agregar el control del modal en `page.tsx`**

```tsx
const [isModalOpen, setIsModalOpen] = useState(false);
const [productoAEditar, setProductoAEditar] = useState<ProductoDTO | null>(null);
const abrirCrear = useCallback(() => { setProductoAEditar(null); setIsModalOpen(true); }, []);
const abrirEdicion = useCallback((p: ProductoDTO) => { setProductoAEditar(p); setIsModalOpen(true); }, []);
const cerrarModal = useCallback(() => { setIsModalOpen(false); setProductoAEditar(null); }, []);
```
`columns` (useMemo): pasar `abrirEdicion` como `onEditarProducto` (en vez de `abrirEdicionRef.current`); deps `[canEditProductos, abrirEdicion]` (abrirEdicion es estable por useCallback).
El botón "Crear Producto" / `<CreateButton onClick={...}>` usa `abrirCrear`.

- [ ] **Step 3: Renderizar el modal condicionalmente**

Donde estaba el `<Modal>` del producto, poner:
```tsx
{isModalOpen && (
    <ProductoFormModal
        producto={productoAEditar}
        canEditProductos={canEditProductos}
        canExportarDux={canExportarDux}
        createProducto={createProducto}
        updateProductoMargen={updateProductoMargen}
        onClose={cerrarModal}
        onSuccess={refresh}
    />
)}
```
Importar `ProductoFormModal from "./ProductoFormModal"`.

- [ ] **Step 4: Typecheck e iterar**

Run (desde `supermaster-frontend/`): `npx tsc --noEmit`
Expected: iterar hasta 0 errores. Errores esperados y su arreglo:
- "X is not defined" en `page.tsx` → era del form; ya está en `ProductoFormModal` → borrar la referencia residual en `page.tsx`.
- "X is not defined" en `ProductoFormModal.tsx` → falta un import o un estado que no se movió → agregarlo.
- Imports sin usar → quitarlos del archivo que corresponda.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx supermaster-frontend/src/app/productos/page.tsx
git commit -m "refactor(front/productos): extraer ProductoFormModal (fix lag al tipear)"
```

---

## Verificación final
- [ ] `npx tsc --noEmit` → sin errores.
- [ ] **Smoke (usuario):** el lag al tipear desaparece; alta completa (margen/canales/imágenes/MLA/categoría) igual; edición precarga y guarda; panel de canales + reintento OK; edición inline/filtros/selección/borrado de la tabla OK; abrir/cerrar el modal varias veces no deja estado pegado.

## Notas de implementación
- Es un cambio **atómico** (Task 1 + Task 2 en un commit): no hay estado intermedio con typecheck verde porque los nombres no pueden coexistir en los dos archivos.
- Estrategia segura: tener `page.tsx` abierto como referencia, construir `ProductoFormModal.tsx` copiando bloques, y luego vaciar `page.tsx`. El typecheck final guía qué quedó colgado.
- Mantener `editandoProductoId` como **estado local** del form (seteado en la precarga) evita reescribir sus ~30 usos en los handlers/JSX.
