# Imágenes por SKU (galería + carousel) y eliminar imagenUrl — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminar el campo manual `imagenUrl` de punta a punta (las imágenes se resuelven siempre por SKU) y mejorar la visualización: galería en el form, primera imagen en la tabla, y carousel con todas en tabla y form.

**Architecture:** Backend — se quita `imagenUrl` de entity/BD/DTOs/mapper/auditoría y se adaptan sus consumidores (estadística, Excel, Catálogo PDF) a resolver por SKU con `ImagenService` (índice cacheado). Frontend — un componente `ImagenesCarousel` reusable (carga `GET /api/imagenes/detalle/{sku}`); la celda de la tabla muestra la primera imagen y abre el carousel; el form muestra una galería; se elimina `imagenUrl` de tipos/estados/payload.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven + JUnit; Next.js 16 / React 19 / TS (`npx tsc --noEmit`, sin test runner).

## Global Constraints

- Trabajar en `main`. Backend OFFLINE: `./mvnw -o test ...` / `./mvnw -o test-compile`. Frontend: `npx tsc --noEmit` desde `supermaster-frontend/`.
- **NO ejecutar nada que llame a APIs reales** — solo tests offline / typecheck.
- ddl-auto=validate → el `DROP COLUMN` va en un script SQL en `src/main/resources/db/` y lo aplica el usuario a mano antes de arrancar.
- Imagen SIEMPRE por SKU; `imagenUrl` se elimina por completo (no queda código muerto).
- NO tocar `apis/dux/model/Item.java` (`imagen_url` ahí es de la API de Dux, ajeno).
- Resolver por SKU usa `ImagenService.resolverArchivoPorSku(sku)` (índice cacheado O(1)) / `resolverDetallePorSku(sku)` para la lista.
- Commits terminan EXACTAMENTE con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: Backend — eliminar `imagenUrl` del núcleo (entity, DTOs, mapper, aplicarPatch, auditoría) + SQL

**Files:**
- Modify: `dominio/producto/entity/Producto.java` (quitar campo `imagenUrl` + `@Column`)
- Modify: `dominio/producto/dto/ProductoDTO.java`, `ProductoCreateDTO.java`, `ProductoUpdateDTO.java`, `ProductoConPreciosDTO.java`, `ProductoPatchDTO.java` (quitar componente/campo)
- Modify: `dominio/producto/mapper/ProductoMapper.java` (quitar `imagenUrl` de `toEntity` y `toDTO`)
- Modify: `dominio/producto/service/ProductoServiceImpl.java` (quitar ramas de `imagenUrl` en `aplicarPatch`, ~1123 y ~1206-1207)
- Modify: `dominio/producto/service/ProductoAuditoriaServiceImpl.java` (quitar `imagenUrl` del snapshot, ~36)
- Create: `src/main/resources/db/2026-06-22-drop-imagen-url.sql`
- Tests: actualizar los que construyen estos DTOs/entity con `imagenUrl` posicional (p. ej. `RecalculoAutomaticoIntegrationTest` y cualquiera que rompa al compilar).

- [ ] **Step 1: Crear el script SQL**

`src/main/resources/db/2026-06-22-drop-imagen-url.sql`:
```sql
-- Se elimina la imagen manual del producto: las imágenes se resuelven siempre por SKU.
ALTER TABLE productos DROP COLUMN imagen_url;
```

- [ ] **Step 2: Quitar `imagenUrl` de entity, DTOs, mapper, aplicarPatch, auditoría**

Leer cada archivo y quitar el campo/componente/uso de `imagenUrl`:
- `Producto.java`: borrar el `@Column(name="imagen_url", length=500) private String imagenUrl;` (y, si hubiera, `@Size`).
- Cada DTO: borrar la línea del componente `String imagenUrl` (records) o el campo (`ProductoPatchDTO` usa `JsonNullable<String> imagenUrl`).
- `ProductoMapper`: borrar el argumento `entity.getImagenUrl()` (toDTO ~365) y el seteo/posición en `toEntity` (~65). Como son constructores/posicionales manuales, ajustar el orden.
- `ProductoServiceImpl.aplicarPatch`: borrar el `&& !presente(patchDto.getImagenUrl())` (~1123) y el bloque `if (presente(patchDto.getImagenUrl())) { entity.setImagenUrl(...); }` (~1206-1207).
- `ProductoAuditoriaServiceImpl`: borrar `snapshot.put("imagenUrl", ...)` (~36).

- [ ] **Step 3: Compilar y arreglar los tests que rompan**

Run: `./mvnw -o test-compile`
Expected: errores de compilación SOLO en tests que construyen los DTOs/entity con `imagenUrl` posicional. Quitar el argumento `imagenUrl` de esas construcciones. Repetir hasta BUILD SUCCESS.

- [ ] **Step 4: Correr la suite de producto (sin red)**

Run: `./mvnw -o test -Dtest=Producto*Test,Recalculo*Test`
Expected: BUILD SUCCESS (los tests de producto/recalculo compilan y pasan sin `imagenUrl`).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main supermaster-backend/src/test
git commit -m "refactor(producto): eliminar imagenUrl del nucleo (entity/DTOs/mapper/auditoria) + SQL drop"
```

---

### Task 2: Backend — adaptar consumidores de `imagenUrl` a resolver por SKU

**Files:**
- Modify: `dominio/producto/service/EstadisticasServiceImpl.java` (~233, `productosSinImagen`)
- Modify: `excel/service/ExcelServiceImpl.java` (~3141, celda de imagen)
- Modify: `catalogo_pdf/service/CatalogoPdfServiceImpl.java` (~114-115), `catalogo_pdf/pdf/CatalogoPdfItem.java` (~14, quitar componente `imagenUrl`), `catalogo_pdf/pdf/CellBuilder.java` (~53, usar SKU)

**Interfaces:**
- Consumes: `ImagenService.resolverArchivoPorSku(String sku)` → nombre del archivo (o null); `ImagenComponent.build(...)` (ya recibe el SKU como fallback).

- [ ] **Step 1: Estadística productos sin imagen**

En `EstadisticasServiceImpl`, donde hoy: `p.getImagenUrl() == null || p.getImagenUrl().isBlank()`, reemplazar por: `imagenService.resolverArchivoPorSku(p.getSku()) == null`. Inyectar `ImagenService` si no está. (Usa el índice cacheado — barato.)

- [ ] **Step 2: Excel**

En `ExcelServiceImpl` (~3141), donde hoy `producto.imagenUrl()`, reemplazar por el nombre resuelto por SKU: `imagenService.resolverArchivoPorSku(producto.sku())` (o `""` si null). Inyectar `ImagenService` si no está. (Si `producto` es un DTO sin `sku()`, usar el campo de SKU disponible.)

- [ ] **Step 3: Catálogo PDF**

- `CatalogoPdfItem.java`: quitar el componente `imagenUrl`.
- `CatalogoPdfServiceImpl.java` (~114-115): no setear `imagenUrl` en el item; `ImagenComponent` ya resuelve por SKU.
- `CellBuilder.java` (~53): cambiar `ImagenComponent.build(item.imagenUrl(), imageSize, imagenesDirActual, item.sku(), stats)` por la forma que resuelve solo por SKU (pasar `null`/quitar el primer arg según la firma de `ImagenComponent.build`). Verificar `ImagenComponent.build` y ajustar.

- [ ] **Step 4: Compilar + tests de los módulos afectados**

Run: `./mvnw -o test-compile && ./mvnw -o test -Dtest=*Catalogo*Test,*Excel*Test,*Estadistica*Test`
Expected: BUILD SUCCESS (o, si no hay tests de esos módulos, al menos test-compile OK).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src
git commit -m "refactor(producto): consumidores de imagen (stats/excel/pdf) resuelven por SKU"
```

---

### Task 3: Frontend — componente `ImagenesCarousel`

**Files:**
- Create: `supermaster-frontend/src/app/productos/ImagenesCarousel.tsx`

**Interfaces:**
- Consumes: `getImagenDetalleAPI(sku)` → `{nombre, extension, bytes}[]` (de productosService); `API_BASE_URL`.
- Produces: `export default function ImagenesCarousel({ sku, onClose }: { sku: string; onClose: () => void })`.

- [ ] **Step 1: Implementar el carousel**

```tsx
"use client";
import { useEffect, useState } from "react";
import { API_BASE_URL } from "../config/runtime";
import { getImagenDetalleAPI } from "./productosService";

export default function ImagenesCarousel({ sku, onClose }: { sku: string; onClose: () => void }) {
    const [nombres, setNombres] = useState<string[]>([]);
    const [idx, setIdx] = useState(0);
    const [cargando, setCargando] = useState(true);

    useEffect(() => {
        let cancel = false;
        getImagenDetalleAPI(sku)
            .then(d => { if (!cancel) { setNombres(d.map(i => i.nombre)); setIdx(0); } })
            .catch(() => { if (!cancel) setNombres([]); })
            .finally(() => { if (!cancel) setCargando(false); });
        return () => { cancel = true; };
    }, [sku]);

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if (e.key === "Escape") onClose();
            if (e.key === "ArrowRight") setIdx(i => (nombres.length ? (i + 1) % nombres.length : 0));
            if (e.key === "ArrowLeft") setIdx(i => (nombres.length ? (i - 1 + nombres.length) % nombres.length : 0));
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [onClose, nombres.length]);

    const ir = (d: number) => setIdx(i => (nombres.length ? (i + d + nombres.length) % nombres.length : 0));

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4" onClick={onClose}>
            <div className="relative flex max-h-full flex-col items-center gap-3" onClick={e => e.stopPropagation()}>
                <button onClick={onClose} className="absolute -top-3 -right-3 z-10 flex h-8 w-8 items-center justify-center rounded-full bg-white text-gray-600 shadow-lg hover:bg-gray-100 dark:bg-slate-700 dark:text-slate-200" title="Cerrar (Esc)">
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
                </button>
                {cargando ? (
                    <div className="text-sm text-white/80">Cargando…</div>
                ) : nombres.length === 0 ? (
                    <div className="rounded-lg bg-white px-6 py-10 text-sm text-slate-500 dark:bg-slate-800 dark:text-slate-400">Sin imágenes para este SKU</div>
                ) : (
                    <>
                        <div className="flex items-center gap-3">
                            {nombres.length > 1 && (
                                <button onClick={() => ir(-1)} className="flex h-10 w-10 items-center justify-center rounded-full bg-white/90 text-gray-700 shadow hover:bg-white" aria-label="Anterior">
                                    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" /></svg>
                                </button>
                            )}
                            <img src={`${API_BASE_URL}/api/imagenes/${nombres[idx]}`} alt={nombres[idx]} className="max-h-[70vh] max-w-[80vw] rounded-lg bg-white object-contain shadow-2xl" />
                            {nombres.length > 1 && (
                                <button onClick={() => ir(1)} className="flex h-10 w-10 items-center justify-center rounded-full bg-white/90 text-gray-700 shadow hover:bg-white" aria-label="Siguiente">
                                    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" /></svg>
                                </button>
                            )}
                        </div>
                        <div className="text-xs text-white/80">{idx + 1} / {nombres.length} · {nombres[idx]}</div>
                        {nombres.length > 1 && (
                            <div className="flex max-w-[80vw] flex-wrap justify-center gap-1.5">
                                {nombres.map((n, i) => (
                                    <button key={n} onClick={() => setIdx(i)} className={`h-12 w-12 overflow-hidden rounded border-2 ${i === idx ? "border-blue-400" : "border-transparent opacity-70 hover:opacity-100"}`}>
                                        <img src={`${API_BASE_URL}/api/imagenes/${n}`} alt={n} className="h-full w-full bg-white object-contain" />
                                    </button>
                                ))}
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
```

- [ ] **Step 2: Typecheck**

Run: `npx tsc --noEmit`
Expected: sin errores nuevos (el componente aún no se usa).

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/productos/ImagenesCarousel.tsx
git commit -m "feat(front/productos): componente ImagenesCarousel (todas las imagenes del SKU)"
```

---

### Task 4: Frontend — tabla (celda por SKU + carousel) y quitar `imagenUrl` de tipos

**Files:**
- Modify: `supermaster-frontend/src/app/productos/columns.tsx`
- Modify: `supermaster-frontend/src/app/productos/types.ts` (quitar `imagenUrl`, líneas 16 y 88)

**Interfaces:**
- Consumes: `ImagenesCarousel` (Task 3).

- [ ] **Step 1: Reemplazar `ImageUrlCell` por una celda solo-lectura**

En `columns.tsx`: reemplazar el componente `ImageUrlCell` por uno que solo muestra la primera imagen por SKU y abre el carousel:
```tsx
function ImagenCeldaSku({ sku }: { sku: string }) {
    const [carouselOpen, setCarouselOpen] = useState(false);
    const [imgError, setImgError] = useState(false);
    const src = sku ? `${API_BASE_URL}/api/imagenes/producto/${encodeURIComponent(sku)}` : "";
    useEffect(() => { setImgError(false); }, [src]);
    const mostrar = !!src && !imgError;
    return (
        <>
            <button onClick={() => { if (mostrar) setCarouselOpen(true); }} title={mostrar ? "Ver imágenes" : "Sin imagen"}
                className={`shrink-0 overflow-hidden rounded-lg border transition ${mostrar ? "border-gray-200 dark:border-slate-600 hover:border-blue-400 hover:shadow-md cursor-pointer" : "border-dashed border-gray-300 dark:border-slate-600 cursor-default"}`}>
                {mostrar ? (
                    <img src={src} alt="" className="h-8 w-8 bg-gray-50 object-contain dark:bg-slate-700/50" onError={() => setImgError(true)} />
                ) : (
                    <div className="flex h-8 w-8 items-center justify-center bg-gray-50 text-gray-400 dark:bg-slate-800 dark:text-slate-500">
                        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.41a2.25 2.25 0 013.182 0l2.909 2.909M3.75 21h16.5a1.5 1.5 0 001.5-1.5V4.5a1.5 1.5 0 00-1.5-1.5H3.75a1.5 1.5 0 00-1.5 1.5v15a1.5 1.5 0 001.5 1.5z" /></svg>
                    </div>
                )}
            </button>
            {carouselOpen && <ImagenesCarousel sku={sku} onClose={() => setCarouselOpen(false)} />}
        </>
    );
}
```
Agregar `import ImagenesCarousel from "./ImagenesCarousel";` y, si quedan sin uso, **eliminar** `ImagePickerModal` y `ImageViewerModal` de `columns.tsx` (y sus imports).

- [ ] **Step 2: Cambiar la columna**

Reemplazar el bloque de la columna (`accessorKey:"imagenUrl"`, ~312-321) por:
```tsx
    {
        id: "imagen", header: "Imagen", size: 100, enableSorting: false, enableColumnFilter: false,
        cell: ({ row }) => <ImagenCeldaSku sku={row.original.sku || ""} />
    },
```

- [ ] **Step 3: Quitar `imagenUrl` de `types.ts`** (líneas 16 y 88 — `imagenUrl: string | null;`).

- [ ] **Step 4: Typecheck**

Run: `npx tsc --noEmit`
Expected: errores SOLO en `page.tsx` (que aún usa `imagenUrl`) — se resuelven en Task 5. Si querés un checkpoint limpio, hacé Task 5 antes de typecheck final.

- [ ] **Step 5: Commit** (junto con Task 5, ver abajo — el typecheck queda verde recién al terminar Task 5).

---

### Task 5: Frontend — formulario (quitar selector, galería) y `imagenUrl` del payload

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

- [ ] **Step 1: Quitar `imagenUrl` del form**

- Borrar el estado `imagenUrl`/`setImagenUrl`, `imagenTocadaManualmenteRef`, `isImagePickerOpen`/`setIsImagePickerOpen`.
- Borrar el `useEffect` de autocompletado de `imagenUrl` (~926-941) y el `ImagePickerModal` del form (~1698-1703) y la función `ImagePickerModal` definida en `page.tsx` si no se usa en otro lado.
- Quitar `imagenUrl` del payload en `crear` (~639) y en la edición (~758).
- Quitar el reset de `imagenUrl` en `resetForm`.

- [ ] **Step 2: Reemplazar el bloque selector por la galería + carousel**

Reemplazar el bloque "Imagen" (`Seleccionar/Quitar`, ~1418-1453) por una galería de `imagenesDetectadas` (ya cargada por el `useEffect` del SKU) con thumbnails que abren el carousel:
```tsx
{/* Imagen — galería de las detectadas por SKU (solo lectura) */}
<div className="block xl:col-span-4">
    <span className={fieldLabelClassName}>Imágenes (por SKU)</span>
    <div className="mt-1 rounded-2xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-800/70">
        {imagenesDetectadas.length === 0 ? (
            <div className="text-xs text-slate-500 dark:text-slate-400">No hay imágenes para este SKU en la carpeta.</div>
        ) : (
            <div className="flex flex-wrap gap-2">
                {imagenesDetectadas.map((img) => (
                    <button key={img.nombre} type="button" onClick={() => setCarouselSku(sku.trim())}
                        className="h-16 w-16 overflow-hidden rounded-xl border border-slate-200 hover:border-blue-400 dark:border-slate-700" title={img.nombre}>
                        <img src={`${API_BASE_URL}/api/imagenes/${img.nombre}`} alt={img.nombre} className="h-full w-full bg-white object-contain" onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }} />
                    </button>
                ))}
            </div>
        )}
    </div>
</div>
```
Agregar estado `const [carouselSku, setCarouselSku] = useState<string | null>(null);` y, cerca del `ImagePickerModal` removido, renderizar el carousel:
```tsx
{carouselSku && <ImagenesCarousel sku={carouselSku} onClose={() => setCarouselSku(null)} />}
```
Importar `ImagenesCarousel`.

- [ ] **Step 3: Typecheck**

Run: `npx tsc --noEmit`
Expected: sin errores (ya no queda `imagenUrl` en el front).

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/columns.tsx supermaster-frontend/src/app/productos/types.ts supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): imagenes por SKU - galeria en form + tabla sin edicion + carousel"
```

---

## Verificación final
- [ ] Backend: `./mvnw -o test-compile` y la suite de producto verde; el `grep imagenUrl` en `src/main` no devuelve nada del producto (solo Dux `Item.java`).
- [ ] Frontend: `npx tsc --noEmit` sin errores; `grep imagenUrl` en `productos/` no devuelve nada.
- [ ] **Smoke (usuario):** aplicar el SQL `DROP COLUMN`; arrancar; tabla muestra la primera imagen; click → carousel navega por todas (←/→, miniaturas, Esc); form muestra la galería; crear/editar sin el campo imagen; PDF y Excel muestran la imagen por SKU.

## Notas de diseño
- `ImagenesCarousel` es la única fuente del visor multi-imagen; lo usan tabla y form (DRY). Reemplaza a `ImageViewerModal` (single-image), que se elimina.
- Los consumidores backend resuelven por SKU con el índice cacheado de `ImagenService` (sin stat a disco por producto).
- El borrado de `imagenUrl` es total: si `grep imagenUrl` (fuera de Dux) devuelve algo al final, falta un sitio.
