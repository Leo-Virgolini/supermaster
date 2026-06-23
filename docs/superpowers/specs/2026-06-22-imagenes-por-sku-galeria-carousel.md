# Imágenes siempre por SKU: galería + carousel, y eliminar imagenUrl

**Fecha:** 2026-06-22
**Estado:** Diseño aprobado, pendiente de plan.

## Objetivo

Las imágenes de un producto se resuelven automáticamente por **SKU** desde la carpeta de imágenes (`{SKU}.ext`, `{SKU}_2.ext`, …). El campo manual `imagenUrl` (elegir/quitar una imagen a mano) dejó de tener sentido: duplica un concepto que ya es automático. Esta feature elimina `imagenUrl` de punta a punta y mejora la visualización:

- **Formulario:** se quita el selector "Seleccionar/Quitar imagen"; en su lugar, una **galería** de las imágenes detectadas del SKU.
- **Tabla:** la celda muestra la **primera** imagen del SKU (sin edición). Al click → **carousel** con todas.
- **Carousel** (tabla y formulario): visor que muestra todas las imágenes del SKU con flechas y miniaturas.
- **Backend:** se elimina `imagenUrl` (entity, columna BD, DTOs, mapper, auditoría) y se adaptan sus consumidores (Catálogo PDF, Excel, estadística) a resolver por SKU.

## Decisiones tomadas (brainstorming 2026-06-22)

1. **Imagen siempre por SKU.** Se elimina el campo manual `imagenUrl` por completo (form + tabla + backend + BD).
2. **Tabla:** muestra la primera imagen (resuelta por SKU); sin picker ni botón de quitar. Click → carousel.
3. **Carousel en tabla y formulario:** todas las imágenes del SKU, flechas ◀▶ + tira de miniaturas, cierre con Esc/click afuera.
4. **Eliminar `imagenUrl` de la BD** (DROP COLUMN) y adaptar todos los consumidores a resolver por SKU con el índice cacheado de `ImagenService` (O(1), sin stat a disco).

## Alcance

### Incluye
- **Backend:** eliminar `imagenUrl` de entity, DTOs, mapper, `aplicarPatch`, auditoría; adaptar Catálogo PDF, Excel y la estadística "productos sin imagen"; script SQL `DROP COLUMN`.
- **Frontend:** quitar el selector del form y el editor de la celda; galería en el form; carousel (tabla y form); quitar `imagenUrl` de tipos/payload/estados.

### NO incluye
- Cambiar la convención de nombres de imágenes por SKU.
- Subir/borrar imágenes desde la app (siguen gestionándose en la carpeta).
- El picker por búsqueda (`ImagePickerModal`) — se elimina su uso (ya no se elige imagen a mano).

## Contexto del código existente

**Backend — consumidores de `imagenUrl` (todos a adaptar):**
- `dominio/producto/entity/Producto.java:73-74` — `@Column(name="imagen_url")`.
- DTOs: `ProductoDTO:20`, `ProductoCreateDTO:28`, `ProductoUpdateDTO:26`, `ProductoConPreciosDTO:28`, `ProductoPatchDTO:22`.
- `mapper/ProductoMapper.java:65,365` (toEntity / toDTO — constructores posicionales manuales).
- `service/ProductoServiceImpl.java:1123,1206-1207` (`aplicarPatch`).
- `service/ProductoAuditoriaServiceImpl.java:36` (snapshot).
- `service/EstadisticasServiceImpl.java:233` (`productosSinImagen` = `getImagenUrl()==null`).
- Catálogo PDF: `CatalogoPdfServiceImpl.java:114-115`, `pdf/CatalogoPdfItem.java:14`, `pdf/CellBuilder.java:53` (usan `imagenUrl` con fallback por SKU vía `ImagenComponent`).
- `excel/service/ExcelServiceImpl.java:3141` (exporta `imagenUrl`).
- ⚠️ NO tocar `apis/dux/model/Item.java:70-71` (`imagen_url` es el campo del modelo de Dux, ajeno al producto).

**Frontend:**
- `productos/types.ts:16,88` — `imagenUrl`.
- `productos/page.tsx` — estado `imagenUrl`/`setImagenUrl`, `imagenTocadaManualmenteRef`, `useEffect` de autocompletado (~926-941), bloque selector (~1418-1453), `ImagePickerModal` del form (~1698), `isImagePickerOpen`; payload (`crear`/editar).
- `productos/columns.tsx` — `ImageUrlCell` (192-267, editable con `ImagePickerModal`+`ImageViewerModal`), `ImagePickerModal` (31), `ImageViewerModal` (151, single-image), columna `accessorKey:"imagenUrl"` (312).
- Endpoint existente `GET /api/imagenes/detalle/{sku}` → `[{nombre, extension, bytes}]` (todas las del SKU, en orden). Sirve las imágenes por `GET /api/imagenes/{nombre}`.

## Diseño

### Backend — eliminar `imagenUrl`
- **Entity:** quitar el campo y la columna. Script SQL en `src/main/resources/db/`: `ALTER TABLE productos DROP COLUMN imagen_url;` (ddl-auto=validate → aplicar a mano antes de arrancar).
- **DTOs (5):** quitar el componente `imagenUrl`. Esto rompe constructores posicionales en tests (p. ej. `RecalculoAutomaticoIntegrationTest`); actualizarlos.
- **Mapper:** quitar `imagenUrl` de `toEntity`/`toDTO` (constructores manuales).
- **`aplicarPatch`:** quitar las ramas de `imagenUrl`.
- **Auditoría:** quitar `imagenUrl` del snapshot.
- **Consumidores → resolver por SKU** (con `ImagenService`, índice cacheado O(1)):
  - **Estadística** `productosSinImagen`: `imagenService.resolverArchivoPorSku(p.getSku()) == null`.
  - **Excel** (`ExcelServiceImpl`): la celda "Imagen" usa `imagenService.resolverArchivoPorSku(sku)` (nombre del archivo) en vez de `imagenUrl`.
  - **Catálogo PDF:** `CatalogoPdfItem` ya no lleva `imagenUrl`; `ImagenComponent`/`CellBuilder` resuelven por SKU (ya es el fallback actual). Confirmar en el plan que `ImagenComponent.build` con solo SKU funciona.

### Frontend — Carousel (componente nuevo)
Un componente `ImagenesCarousel({ sku, onClose })` (en `columns.tsx` o un archivo propio reutilizable por tabla y form):
- Al montar, `getImagenDetalleAPI(sku)` → lista de nombres (en orden: principal, _2, _3…).
- Muestra la imagen actual en grande (`/api/imagenes/{nombre}`), flechas ◀▶ (y teclas ←/→), índice "i/N", tira de **miniaturas** clickeables, cierre con Esc/click en el fondo.
- Si hay 1 sola imagen, oculta flechas/miniaturas. Si no hay ninguna, muestra "Sin imágenes".
- Reemplaza a `ImageViewerModal` (single-image).

### Frontend — Tabla (`ImageUrlCell` → `ImagenCeldaSku`)
- Muestra la **primera** imagen por SKU (`GET /api/imagenes/producto/{sku}`), o placeholder si no hay.
- Sin picker, sin botón de quitar, sin `currentUrl`/`onSave`. Click → abre `ImagenesCarousel` con el SKU.
- La columna pasa de `accessorKey:"imagenUrl"` (editable) a `id:"imagen"` con `accessorFn: row => row.sku`, `meta` sin `editable`.
- Se elimina `ImagePickerModal` y `ImageViewerModal` de `columns.tsx` (ya no se usan).

### Frontend — Formulario
- Quitar el bloque selector "Imagen" (`Seleccionar/Quitar`), el `ImagePickerModal` del form, `isImagePickerOpen`, `imagenTocadaManualmenteRef`, el `useEffect` de autocompletado y el estado `imagenUrl`.
- En su lugar, una **galería**: las miniaturas de `imagenesDetectadas` (ya cargadas por el `useEffect` del SKU) en línea, en orden; click en cualquiera → `ImagenesCarousel`. (El bloque de "N imágenes detectadas" + avisos de formato/tamaño ya existe y se mantiene; la galería se integra ahí o reemplaza el bloque "Imagen".)
- Quitar `imagenUrl` del payload de `crear`/editar.

## Manejo de errores
- Imagen que no carga (404 por SKU) → placeholder, sin romper la fila/galería.
- `getImagenDetalleAPI` falla → carousel muestra "Sin imágenes" (o no abre); la tabla cae al placeholder.

## Pruebas
- **Backend (unitario):** la estadística `productosSinImagen` resuelve por SKU (con un `ImagenService` sobre `@TempDir`); el resto es refactor verificado por `./mvnw -o test` (los tests de DTO/mapper/integración que tocan `imagenUrl` se actualizan y deben pasar).
- **Frontend:** `npx tsc --noEmit`.
- **Smoke (usuario):** SKU con 1 imagen y SKU con varias (`_2`, `_3`); la tabla muestra la primera; click → carousel navega por todas; el form muestra la galería; crear/editar sin el campo imagen funciona; el PDF y el Excel siguen mostrando la imagen por SKU; el backend arranca tras el `DROP COLUMN`.

## Archivos afectados (resumen)
**Backend:** `Producto.java`, `ProductoDTO/CreateDTO/UpdateDTO/ConPreciosDTO/PatchDTO`, `ProductoMapper`, `ProductoServiceImpl` (aplicarPatch), `ProductoAuditoriaServiceImpl`, `EstadisticasServiceImpl`, `CatalogoPdfServiceImpl`/`CatalogoPdfItem`/`CellBuilder`, `ExcelServiceImpl`, script SQL `db/`. Tests que construyen esos DTOs.
**Frontend:** `types.ts`, `page.tsx`, `columns.tsx`, (nuevo) componente carousel.

## Pendiente de validar en smoke (usuario)
- Aplicar el script SQL `DROP COLUMN imagen_url` antes de arrancar.
- Tabla muestra la primera imagen; carousel navega por todas (tabla y form).
- PDF y Excel siguen con la imagen por SKU.
