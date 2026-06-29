# Datos de canal en el modal — Plan 3: Frontend

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En el modal de producto: pre-cargar **categoría ML, atributos ML y descripciones por canal** desde el canal (al abrir), editar **3 descripciones** (ML/Hogar/Gastro) con botón "Componer descripción sugerida", agregar la tarjeta **Dux** al panel de estado (y quitar peso/dim), y enviar esos datos como **transitorios** al publicar (sin guardarlos en el producto).

**Architecture:** El modal ya llama a `getEstadoPublicacionAPI(id)` al abrir; ese endpoint (Plan 1) ahora trae `dux` y `datos` (campos editables). El modal pre-llena desde `datos` en vez de desde el producto persistido. Al guardar, el producto se crea/actualiza **sin** esos campos y el export los lleva (Plan 2): ML con `mlCategoryId`/`mlAtributos`/`descripcionMl`; Nube con la `descripcion` por tienda.

**Tech Stack:** Next.js/React/TS. Verificación: `npx tsc --noEmit` exit 0 + revisión manual. No hay tests automáticos de frontend.

## Global Constraints

- Frontend: `cd supermaster-frontend && npx tsc --noEmit` debe dar exit 0; sin errores `error` de lint nuevos.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos, nunca `-A` ni `.superpowers/`.
- **Requiere Plan 1 y Plan 2** desplegados en backend: `estado-publicacion` devuelve `dux`+`datos`; export ML/Nube acepta los transitorios; existe `GET /api/productos/{id}/descripcion-sugerida?canal=ml|nube`.
- Archivos: `supermaster-frontend/src/app/productos/productosService.ts`, `ProductoFormModal.tsx`, `types.ts`.

## File Structure

- Modify `productosService.ts` — tipos `EstadoPublicacion` (+dux, +datos `DatosCanal`), `DestinoNube` (+descripcion), `exportarProductosAMlAPI` (+transitorios), nuevo `getDescripcionSugeridaAPI`.
- Modify `ProductoFormModal.tsx` — estados de 3 descripciones, pre-carga desde `datos`, panel con Dux y sin peso/dim, UI de 3 descripciones + botones sugerir, wiring de export.
- Modify `types.ts` — quitar `descripcion`/`mlCategoryId`/`mlCategoryNombre`/`mlAtributos` de `ProductoCreateDTO`/`ProductoPatchDTO`/`Producto`.

---

### Task 1: Capa de servicio (tipos + API)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`

**Interfaces:**
- Produces: `DatosCanal`; `EstadoPublicacion` con `dux` y `datos`; `DestinoNube` con `descripcion?`; `exportarProductosAMlAPI(skus, cuotas, mlCategoryId?, mlAtributos?, descripcionMl?)`; `getDescripcionSugeridaAPI(id, canal)`.

- [ ] **Step 1: Ampliar `EstadoPublicacion` y agregar `DatosCanal`**

Reemplazar (líneas 525-526):
```ts
export type EstadoPublicacion = { ml: EstadoCanal; hogar: EstadoCanal; gastro: EstadoCanal };
export type EstadoPublicacionUpdate = { ml?: string | null; hogar?: boolean | null; gastro?: boolean | null };
```
por:
```ts
export type DatosCanal = {
	mlCategoryId: string | null;
	mlCategoryNombre: string | null;
	mlAtributos: ProductoMlAtributo[];
	descripcionMl: string | null;
	descripcionHogar: string | null;
	descripcionGastro: string | null;
};
export type EstadoPublicacion = { ml: EstadoCanal; hogar: EstadoCanal; gastro: EstadoCanal; dux: EstadoCanal; datos: DatosCanal };
export type EstadoPublicacionUpdate = { ml?: string | null; hogar?: boolean | null; gastro?: boolean | null };
```
(`ProductoMlAtributo` ya está definido en este archivo, línea 328.)

- [ ] **Step 2: `DestinoNube` con descripción por tienda**

Reemplazar (línea 258):
```ts
export type DestinoNube = { tienda: "KT HOGAR" | "KT GASTRO"; cuotas: number; seo?: SeoNube };
```
por:
```ts
export type DestinoNube = { tienda: "KT HOGAR" | "KT GASTRO"; cuotas: number; seo?: SeoNube; descripcion?: string | null };
```
(El body de `exportarProductosANubeAPI` ya manda `{ skus, tiendas }`; al incluir `descripcion` en cada destino viaja sola.)

- [ ] **Step 3: `exportarProductosAMlAPI` con transitorios**

Reemplazar (líneas 311-319):
```ts
export const exportarProductosAMlAPI = async (skus: string[], cuotas: number): Promise<ExportCanalResultDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/ml/exportar-productos`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ skus, cuotas }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Mercado Libre"));
	return await res.json();
};
```
por:
```ts
export const exportarProductosAMlAPI = async (
	skus: string[], cuotas: number,
	mlCategoryId?: string | null,
	mlAtributos?: ProductoMlAtributo[],
	descripcionMl?: string | null,
): Promise<ExportCanalResultDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/ml/exportar-productos`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ skus, cuotas, mlCategoryId, mlAtributos, descripcionMl }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Mercado Libre"));
	return await res.json();
};
```

- [ ] **Step 4: Nuevo `getDescripcionSugeridaAPI`** (agregar junto a `getEstadoPublicacionAPI`, ~línea 531)

```ts
export async function getDescripcionSugeridaAPI(id: number, canal: "ml" | "nube"): Promise<string> {
	const r = await fetchAPI(`${API_BASE_URL}/api/productos/${id}/descripcion-sugerida?canal=${canal}`);
	const data = await r.json() as { texto: string };
	return data.texto;
}
```

- [ ] **Step 5: Verificar tipos y commit**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0 (cambios aditivos/opcionales; el modal aún no usa lo nuevo).

```bash
git add supermaster-frontend/src/app/productos/productosService.ts
git commit -m "feat(front): tipos y API de datos de canal (dux/datos, descripcion por tienda, sugerida)"
```

---

### Task 2: Descripción por canal (3 campos + sugerir + pre-carga + export Nube)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `getDescripcionSugeridaAPI`, `EstadoPublicacion.datos`, `DestinoNube.descripcion`.

- [ ] **Step 1: Reemplazar el estado `descripcion` por tres**

En la zona de estados (línea 146) reemplazar:
```ts
    const [descripcion, setDescripcion] = useState("");
```
por:
```ts
    const [descripcionMl, setDescripcionMl] = useState("");
    const [descripcionHogar, setDescripcionHogar] = useState("");
    const [descripcionGastro, setDescripcionGastro] = useState("");
    const [sugiriendoDesc, setSugiriendoDesc] = useState(false);
```

- [ ] **Step 2: Importar la API nueva**

En el import de `productosService` (líneas ~17-26) agregar `getDescripcionSugeridaAPI` a la lista de imports.

- [ ] **Step 3: Pre-cargar las 3 descripciones desde `datos` (edición)**

En el `.then` de `getEstadoPublicacionAPI` (línea 638) reemplazar:
```ts
            getEstadoPublicacionAPI(producto.id)
                .then(e => { setEstadoCanales(e); setEstadoOriginal(e); })
```
por:
```ts
            getEstadoPublicacionAPI(producto.id)
                .then(e => {
                    setEstadoCanales(e); setEstadoOriginal(e);
                    // Pre-carga editable desde el canal (no persistido).
                    setDescripcionMl(e.datos.descripcionMl ?? "");
                    setDescripcionHogar(e.datos.descripcionHogar ?? "");
                    setDescripcionGastro(e.datos.descripcionGastro ?? "");
                })
```
Y borrar la línea de carga vieja `setDescripcion(producto.descripcion ?? "");` (línea 561).

- [ ] **Step 4: Reset en alta (sin producto)**

En el bloque `else` de alta (línea 648) reemplazar `setDescripcion("");` por:
```ts
            setDescripcionMl(""); setDescripcionHogar(""); setDescripcionGastro("");
```

- [ ] **Step 5: Quitar `descripcion` del payload de crear y actualizar**

En `handleCreate` (línea 482) y en el update (línea 676) quitar `descripcion: descripcion.trim() || null,` del objeto payload.

- [ ] **Step 6: Mandar la descripción por tienda al exportar a Nube**

En `ejecutarExportsCanales`, donde se arma `tiendas` (líneas 439-441) reemplazar:
```ts
                const tiendas: DestinoNube[] = [];
                if (subirKtHogar) tiendas.push({ tienda: "KT HOGAR", cuotas: cuotaHogar, seo: seoH });
                if (subirKtGastro) tiendas.push({ tienda: "KT GASTRO", cuotas: cuotaGastro, seo: seoG });
```
por:
```ts
                const tiendas: DestinoNube[] = [];
                if (subirKtHogar) tiendas.push({ tienda: "KT HOGAR", cuotas: cuotaHogar, seo: seoH, descripcion: descripcionHogar.trim() || null });
                if (subirKtGastro) tiendas.push({ tienda: "KT GASTRO", cuotas: cuotaGastro, seo: seoG, descripcion: descripcionGastro.trim() || null });
```

- [ ] **Step 7: Reemplazar la UI de descripción única por 3 campos + botón sugerir**

Reemplazar el bloque de la descripción (líneas 2041-2049) por:
```tsx
                        {/* Descripciones por canal (no se guardan; se leen del canal al abrir y se envían al publicar) */}
                        <div className="mt-6 border-t border-slate-200/70 pt-4 dark:border-slate-700/70">
                            {([
                                { label: "Descripción Mercado Libre", canal: "ml" as const, value: descripcionMl, set: setDescripcionMl, hint: "Texto plano (sin HTML). Lo que ves es lo que se publica en ML." },
                                { label: "Descripción Nube · KT HOGAR", canal: "nube" as const, value: descripcionHogar, set: setDescripcionHogar, hint: "Acepta HTML. Lo que ves es lo que se publica en KT HOGAR." },
                                { label: "Descripción Nube · KT GASTRO", canal: "nube" as const, value: descripcionGastro, set: setDescripcionGastro, hint: "Acepta HTML. Lo que ves es lo que se publica en KT GASTRO." },
                            ]).map(d => (
                                <label key={d.label} className="mb-4 block">
                                    <span className="flex items-center justify-between gap-2">
                                        <span className={fieldLabelClassName}>{d.label}</span>
                                        <button type="button" disabled={sugiriendoDesc || !editandoProductoId}
                                            className="text-xs font-medium text-blue-600 hover:underline disabled:opacity-50 dark:text-blue-400"
                                            onClick={async () => {
                                                if (!editandoProductoId) return;
                                                setSugiriendoDesc(true);
                                                try {
                                                    const txt = await getDescripcionSugeridaAPI(editandoProductoId, d.canal);
                                                    d.set(txt);
                                                } catch (e) {
                                                    if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo componer la descripción");
                                                } finally {
                                                    setSugiriendoDesc(false);
                                                }
                                            }}>
                                            Componer descripción sugerida
                                        </button>
                                    </span>
                                    <textarea className={inputBaseClassName} value={d.value} onChange={e => d.set(e.target.value)} rows={4} maxLength={20000} placeholder={d.hint} />
                                    <span className="mt-0.5 block text-[11px] text-slate-400 dark:text-slate-500">{d.hint}</span>
                                </label>
                            ))}
                        </div>
```
(El botón "Componer" requiere un producto existente; en alta queda deshabilitado.)

- [ ] **Step 8: Verificar tipos y commit**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(front): descripción por canal (3 campos + sugerir) leída del canal y enviada al publicar"
```

---

### Task 3: Categoría y atributos ML desde el canal

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

- [ ] **Step 1: Pre-cargar categoría + atributos desde `datos`**

En el `.then` de `getEstadoPublicacionAPI` (el ya editado en Task 2) agregar dentro del callback:
```ts
                    if (e.datos.mlCategoryId) {
                        setMlCategoryId(e.datos.mlCategoryId);
                        setMlCategoryNombre(e.datos.mlCategoryNombre);
                    }
                    if (e.datos.mlAtributos.length > 0) {
                        const map: Record<string, ProductoMlAtributo> = {};
                        for (const a of e.datos.mlAtributos) map[a.attributeId] = a;
                        setMlAtributosVal(map);
                    }
```

- [ ] **Step 2: Dejar de leer categoría/atributos del producto persistido**

Borrar las líneas que copian del DTO (ya no existen en el tipo tras Task 5, pero quitarlas ahora evita pisar la pre-carga del canal):
- Líneas 557-558: `setMlCategoryId(producto.mlCategoryId ?? null);` y `setMlCategoryNombre(producto.mlCategoryNombre ?? null);`
- Bloque 597-603: el `if (producto.mlAtributos && ...) { ... } else { setMlAtributosVal({}); }` → reemplazar por solo `setMlAtributosVal({});` (la pre-carga real viene del canal en el `.then`).

> El predictor de categoría (al tipear un Título ML nuevo) sigue funcionando igual para productos sin publicar.

- [ ] **Step 3: Quitar categoría/atributos del payload de crear y actualizar**

En `handleCreate` (líneas 491, 497) y en el update (líneas 682, 688) quitar:
- `mlCategoryId: mlCategoryId, mlCategoryNombre: mlCategoryNombre,`
- `mlAtributos: Object.values(mlAtributosVal),`

- [ ] **Step 4: Enviar categoría/atributos/descripción ML al exportar a ML**

En `ejecutarExportsCanales`, el bloque de Mercado Libre (línea 454) reemplazar:
```ts
                    const r = await exportarProductosAMlAPI([skuExport], cuotaMl);
```
por:
```ts
                    const r = await exportarProductosAMlAPI(
                        [skuExport], cuotaMl,
                        mlCategoryId, Object.values(mlAtributosVal), descripcionMl.trim() || null);
```

- [ ] **Step 5: Verificar tipos y commit**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(front): categoría y atributos ML leídos del canal y enviados al publicar"
```

---

### Task 4: Panel de estado — tarjeta Dux y quitar peso/dim

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

- [ ] **Step 1: Quitar peso y dimensiones de `renderEstadoCanal`**

Borrar los bloques `canal.peso` (líneas 1481-1486) y `canal.dimensiones` (1487-1492). Quedan solo Precio y Stock en el grid.

- [ ] **Step 2: Soportar el estado de Dux en el ícono**

En `estadoIcon` (~línea 1438) agregar los casos `"habilitado"` y `"deshabilitado"` (reusar el verde de "active"/visible y el gris de "oculta"). Ejemplo a integrar dentro del switch/mapa existente:
```ts
        if (estadoSel === "habilitado") return <CheckCircleIcon className="h-5 w-5 shrink-0 text-emerald-500" />;
        if (estadoSel === "deshabilitado") return <MinusCircleIcon className="h-5 w-5 shrink-0 text-slate-400" />;
```
(Usar los iconos ya importados `CheckCircleIcon`/`MinusCircleIcon`.)

- [ ] **Step 3: Agregar la tarjeta Dux (solo lectura) al panel**

Tras la tarjeta de KT GASTRO (línea 1562) agregar:
```tsx
                            {renderEstadoCanal("Dux", estadoCanales?.dux,
                                <span className="text-sm font-medium text-slate-600 dark:text-slate-300">
                                    {estadoCanales?.dux.estado === "habilitado" ? "Habilitado" : "Deshabilitado"}
                                </span>,
                                <BuildingStorefrontIcon className="h-5 w-5 shrink-0 text-slate-500" />, estadoCanales?.dux.estado ?? undefined)}
```
Importar `BuildingStorefrontIcon` de `@heroicons/react/24/outline` (junto a los otros iconos del archivo). Si el grid contenedor del panel limita a 3 columnas, ajustar su className para acomodar 4 (p.ej. `sm:grid-cols-2 xl:grid-cols-4`).

- [ ] **Step 4: Verificar tipos y commit**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(front): panel de estado con tarjeta Dux (solo lectura) y sin peso/dim"
```

---

### Task 5: Limpieza de tipos + verificación final

**Files:**
- Modify: `supermaster-frontend/src/app/productos/types.ts`

- [ ] **Step 1: Quitar campos externalizados de los tipos**

En `types.ts`:
- `ProductoCreateDTO`: quitar `descripcion`, `mlCategoryId`, `mlCategoryNombre`, `mlAtributos`.
- `ProductoPatchDTO`: quitar los mismos.
- El tipo del producto que recibe el modal (la prop `producto`, p.ej. `Producto` o `ProductoDTO`): quitar `descripcion`, `mlCategoryId`, `mlCategoryNombre`, `mlAtributos`.

- [ ] **Step 2: Verificar que no quedó ningún uso**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0. Si aparece algún `error` por un uso remanente de esos campos, resolverlo (debería estar todo cubierto por Tasks 2-3).

- [ ] **Step 3: Revisión manual**

Verificar en el navegador (con backend de Planes 1-2 corriendo):
- Abrir un producto **publicado**: se pre-cargan categoría, atributos y las 3 descripciones desde el canal; el panel muestra ML/Hogar/Gastro/Dux con estado+precio+stock (sin peso/dim).
- Botón "Componer descripción sugerida" rellena el campo correspondiente.
- Guardar con canales tildados publica con las descripciones/categoría/atributos enviados.
- Abrir un producto **no publicado**: campos vacíos, predictor de categoría funciona, botón sugerir deshabilitado en alta.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/types.ts
git commit -m "refactor(front): quita de los tipos los datos de canal externalizados"
```

---

## Self-Review

1. **Cobertura de la spec (Frontend):** 3 descripciones + sugerir → Task 2; pre-carga categoría/atributos/descr. desde canal → Tasks 2-3; envío de transitorios al export (ML y Nube) → Tasks 2-3; panel estado+precio+stock + Dux, sin peso/dim → Task 4; quitar campos de tipos → Task 5. ✅
2. **Placeholders:** los pasos muestran el código nuevo; las remociones referencian líneas y nombres exactos.
3. **Consistencia de tipos:** `EstadoPublicacion.datos: DatosCanal` (Task 1) consumido en Tasks 2-3; `exportarProductosAMlAPI(skus, cuotas, mlCategoryId?, mlAtributos?, descripcionMl?)` y `DestinoNube.descripcion?` (Task 1) usados en Tasks 2-3; `getDescripcionSugeridaAPI(id, "ml"|"nube")` (Task 1) usado en Task 2.
4. **tsc-clean por task:** Task 1 aditiva; Tasks 2-4 dejan de usar campos pero los tipos siguen existiendo hasta Task 5, que los quita al final.
5. **Interfaz con Planes 1-2:** depende de `dux`+`datos` en `estado-publicacion`, del request de export ampliado y del endpoint `descripcion-sugerida`.
