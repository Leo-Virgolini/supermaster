# Unificar "Estado de publicación" + "Canales de venta" — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar las dos secciones por-canal del modal de producto ("Estado de publicación" y "Canales de venta") por una sola sección con una tarjeta por canal que combine Sincronizar + Estado + Cuota + Precio/Stock.

**Architecture:** Solo frontend, un archivo (`ProductoFormModal.tsx`). Se extrae el cuerpo del estado a un helper `renderEstadoBody` y se arma una única `<fieldset>` "Canales de venta" con 4 tarjetas (Dux/HOGAR/GASTRO/ML); estado y cuota se deshabilitan (`disabled`) cuando el canal no está tildado. Se elimina la sección "Estado de publicación" y el helper `renderEstadoCanal`. Sin cambios de backend ni de comportamiento.

**Tech Stack:** Next.js / React / TypeScript / Tailwind.

## Global Constraints

- `npx tsc --noEmit` exit 0.
- Trabajar en `main`. Commit termina con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` SOLO `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (hay WIP en el working tree que NO se toca), nunca `-A` ni `.superpowers/`.
- No cambiar backend, ni el flujo de guardado/export/aplicar, ni la semántica de estado/cuota/precio: solo reubicar y combinar los controles.
- Orden de canales: **Dux → KT HOGAR → KT GASTRO → Mercado Libre**.
- Destildado → estado y cuota **deshabilitados** (`disabled`); precio/estado real siguen visibles.

---

## Task 1: Unificar las dos secciones en una

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`
  - `renderEstadoCanal` (~líneas 1534-1570) → renombrar/recortar a `renderEstadoBody`.
  - Las dos `<fieldset>` (~líneas 1650-1791: "Estado de publicación" envuelta en `{editandoProductoId && (...)}` y "Canales de venta") → una sola.

**Interfaces:**
- Consumes (ya existen en el componente): estados `estadoCanales` (`EstadoPublicacion | null`), `cargandoEstado`, `setEstadoCanales`; `subirADux`/`subirKtHogar`/`subirKtGastro`/`subirMl` (+ setters); `cuotaHogar`/`cuotaGastro`/`cuotaMl` (+ setters) y `cuotasHogarOpts`/`cuotasGastroOpts`/`cuotasMlOpts`; `canExportarDux`; helpers `estadoIcon`, `opcionesConSeleccion`; class names `sectionClassName`, `SECTION_TINT`, `sectionTitleClassName`, `sectionDescriptionClassName`, `canalCardClassName`, `selectBaseClassName`; iconos `BuildingStorefrontIcon`, `CubeIcon`, `HomeIcon`, `FireIcon`, `ShoppingBagIcon`, `MinusCircleIcon`, `InformationCircleIcon`; `Tooltip`; tipo `EstadoCanal`.
- Produces: helper `renderEstadoBody(canal: EstadoCanal | undefined, control: React.ReactNode, estadoSel?: string)`.

Los números de línea son REFERENCIA (el archivo es grande). Localizá cada bloque por su contenido.

- [ ] **Step 1: Reemplazar `renderEstadoCanal` por `renderEstadoBody`**

Reemplazar TODO el helper actual `renderEstadoCanal` (desde `const renderEstadoCanal = (` hasta su `);` de cierre, ~líneas 1534-1570) por este `renderEstadoBody` (devuelve solo el cuerpo del estado; el contenedor y el encabezado los pone la tarjeta del canal):

```tsx
    const renderEstadoBody = (
        canal: EstadoCanal | undefined,
        control: React.ReactNode,
        estadoSel?: string,
    ) => (
        <div className="border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
            {cargandoEstado ? <span className="text-xs text-slate-400">Leyendo estado…</span>
              : !canal || canal.error ? <span className="text-xs text-amber-600">No se pudo leer el estado</span>
              : !canal.publicado ? <span className="flex items-center gap-1 text-xs text-slate-400"><MinusCircleIcon className="h-4 w-4 shrink-0" /> No publicado</span>
              : (<>
                  <div className="flex items-center gap-2">
                      {estadoIcon(estadoSel)}
                      <div className="flex-1">{control}</div>
                  </div>
                  <div className="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
                      {canal.precio != null && (
                          <div className="flex justify-between gap-1">
                              <span className="text-slate-400">Precio</span>
                              <span className="font-medium text-slate-600 dark:text-slate-300">{canal.precio.toLocaleString("es-AR", { style: "currency", currency: "ARS", minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                          </div>
                      )}
                      {canal.stock != null && (
                          <div className="flex justify-between gap-1">
                              <span className="text-slate-400">Stock</span>
                              <span className="font-medium text-slate-600 dark:text-slate-300">{canal.stock}</span>
                          </div>
                      )}
                  </div>
              </>)}
        </div>
    );
```

- [ ] **Step 2: Reemplazar las dos `<fieldset>` por una sola**

Reemplazar TODO el bloque que va desde `{editandoProductoId && (` que abre la `<fieldset>` de **"Estado de publicación"** (~línea 1650) hasta el cierre `</fieldset>` de **"Canales de venta"** (~línea 1791) por esta única `<fieldset>`:

```tsx
                    <fieldset className={`${sectionClassName} ${SECTION_TINT.canales}`}>
                        <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> Canales de venta</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Dónde publicar el producto y su estado en cada canal (se aplica al guardar).</p>
                        <div className="grid grid-cols-1 items-start gap-3 md:grid-cols-2 xl:grid-cols-4">
                            {/* DUX */}
                            {canExportarDux && (
                                <div className={canalCardClassName}>
                                    <div className="flex items-center gap-3">
                                        <CubeIcon className="h-5 w-5 shrink-0 text-indigo-500" />
                                        <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirADux} onChange={e => setSubirADux(e.target.checked)} id="subirADux" />
                                        <label htmlFor="subirADux" className="flex-1 cursor-pointer select-none">Sincronizar con Dux</label>
                                        <Tooltip content={(
                                            <>
                                                Sube o actualiza en Dux (alta o actualización): título, costo, IVA, rubro/subrubro, marca, proveedor, código de barras, código externo, unidades por bulto (UxB), y habilita o deshabilita según el estado elegido.
                                                <span className="mt-1 block text-red-300">No se suben a Dux: la unidad de medida / sector de depósito (la API de Dux no expone su id), el stock, las imágenes ni el precio de venta (a Dux va el costo).</span>
                                            </>
                                        )} className="flex items-center">
                                            <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                        </Tooltip>
                                    </div>
                                    {editandoProductoId && renderEstadoBody(estadoCanales?.dux,
                                        <select className={`${selectBaseClassName} w-full`} disabled={!subirADux} value={estadoCanales?.dux.estado === "deshabilitado" ? "deshabilitado" : "habilitado"}
                                            onChange={e => setEstadoCanales(p => p && ({ ...p, dux: { ...p.dux, estado: e.target.value } }))}>
                                            <option value="habilitado">Habilitado</option>
                                            <option value="deshabilitado">Deshabilitado</option>
                                        </select>,
                                        estadoCanales?.dux.estado ?? undefined)}
                                </div>
                            )}
                            {/* KT HOGAR */}
                            <div className={canalCardClassName}>
                                <div className="flex items-center gap-3">
                                    <HomeIcon className="h-5 w-5 shrink-0 text-sky-500" />
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirKtHogar} onChange={e => setSubirKtHogar(e.target.checked)} id="subirKtHogar" disabled={!canExportarDux} />
                                    <label htmlFor="subirKtHogar" className="flex-1 cursor-pointer select-none">Sincronizar con KT HOGAR (Nube)</label>
                                    <Tooltip content="Sube o actualiza en Tienda Nube: título, descripción, precio (según el plan de cuotas), categorías e imágenes. El producto se sube oculto (no visible en la tienda); la visibilidad se controla con el estado de esta tarjeta al editar." className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                    </Tooltip>
                                </div>
                                {editandoProductoId && renderEstadoBody(estadoCanales?.hogar,
                                    <select className={`${selectBaseClassName} w-full`} disabled={!subirKtHogar} value={estadoCanales?.hogar.estado ?? "visible"}
                                        onChange={e => setEstadoCanales(p => p && ({ ...p, hogar: { ...p.hogar, estado: e.target.value } }))}>
                                        <option value="visible">Visible</option>
                                        <option value="oculta">Oculta</option>
                                    </select>,
                                    estadoCanales?.hogar.estado ?? "visible")}
                                <div className="flex items-center gap-2 border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
                                    <span className="text-xs font-normal text-slate-500 dark:text-slate-400">Cuota del precio</span>
                                    <Tooltip content="Plan de cuotas del canal con el que se publica el precio en Tienda Nube (cada plan aplica su recargo/descuento de financiación)." className="flex-1">
                                        <select className={`${selectBaseClassName} w-full`} disabled={!subirKtHogar} value={cuotaHogar} onChange={e => setCuotaHogar(Number(e.target.value))}>
                                            {opcionesConSeleccion(cuotasHogarOpts, cuotaHogar).map(c => (
                                                <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                            ))}
                                        </select>
                                    </Tooltip>
                                </div>
                            </div>
                            {/* KT GASTRO */}
                            <div className={canalCardClassName}>
                                <div className="flex items-center gap-3">
                                    <FireIcon className="h-5 w-5 shrink-0 text-emerald-500" />
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirKtGastro} onChange={e => setSubirKtGastro(e.target.checked)} id="subirKtGastro" disabled={!canExportarDux} />
                                    <label htmlFor="subirKtGastro" className="flex-1 cursor-pointer select-none">Sincronizar con KT GASTRO (Nube)</label>
                                    <Tooltip content="Sube o actualiza en Tienda Nube: título, descripción, precio (según el plan de cuotas), categorías e imágenes. El producto se sube oculto (no visible en la tienda); la visibilidad se controla con el estado de esta tarjeta al editar." className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                    </Tooltip>
                                </div>
                                {editandoProductoId && renderEstadoBody(estadoCanales?.gastro,
                                    <select className={`${selectBaseClassName} w-full`} disabled={!subirKtGastro} value={estadoCanales?.gastro.estado ?? "visible"}
                                        onChange={e => setEstadoCanales(p => p && ({ ...p, gastro: { ...p.gastro, estado: e.target.value } }))}>
                                        <option value="visible">Visible</option>
                                        <option value="oculta">Oculta</option>
                                    </select>,
                                    estadoCanales?.gastro.estado ?? "visible")}
                                <div className="flex items-center gap-2 border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
                                    <span className="text-xs font-normal text-slate-500 dark:text-slate-400">Cuota del precio</span>
                                    <Tooltip content="Plan de cuotas del canal con el que se publica el precio en Tienda Nube (cada plan aplica su recargo/descuento de financiación)." className="flex-1">
                                        <select className={`${selectBaseClassName} w-full`} disabled={!subirKtGastro} value={cuotaGastro} onChange={e => setCuotaGastro(Number(e.target.value))}>
                                            {opcionesConSeleccion(cuotasGastroOpts, cuotaGastro).map(c => (
                                                <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                            ))}
                                        </select>
                                    </Tooltip>
                                </div>
                            </div>
                            {/* MERCADO LIBRE */}
                            <div className={canalCardClassName}>
                                <div className="flex items-center gap-3">
                                    <ShoppingBagIcon className="h-5 w-5 shrink-0 text-yellow-500" />
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirMl} onChange={e => setSubirMl(e.target.checked)} id="subirMl" disabled={!canExportarDux} />
                                    <label htmlFor="subirMl" className="flex-1 cursor-pointer select-none">Sincronizar con Mercado Libre</label>
                                    <Tooltip content="Sube o actualiza en Mercado Libre: título (si no tiene ventas), descripción, precio, imágenes. El estado (activa/pausada) se controla con el estado de esta tarjeta al editar. La categoría (la elegida o la que predice ML) se aplica solo al crear; no se modifica en publicaciones existentes." className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                    </Tooltip>
                                </div>
                                {editandoProductoId && renderEstadoBody(estadoCanales?.ml,
                                    <select className={`${selectBaseClassName} w-full`} disabled={!subirMl} value={estadoCanales?.ml.estado ?? "active"}
                                        onChange={e => setEstadoCanales(p => p && ({ ...p, ml: { ...p.ml, estado: e.target.value } }))}>
                                        <option value="active">Activa</option>
                                        <option value="paused">Pausada</option>
                                    </select>,
                                    estadoCanales?.ml.estado ?? "active")}
                                <div className="flex items-center gap-2 border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
                                    <span className="text-xs font-normal text-slate-500 dark:text-slate-400">Cuota del precio</span>
                                    <Tooltip content="Plan de cuotas con el que se publica el precio en Mercado Libre (cada plan aplica su recargo de financiación)." className="flex-1">
                                        <select className={`${selectBaseClassName} w-full`} disabled={!subirMl} value={cuotaMl} onChange={e => setCuotaMl(Number(e.target.value))}>
                                            {opcionesConSeleccion(cuotasMlOpts, cuotaMl).map(c => (
                                                <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                            ))}
                                        </select>
                                    </Tooltip>
                                </div>
                            </div>
                        </div>
                        {(() => {
                            const avisos = imagenesDetectadas.flatMap((img) => {
                                const a: string[] = [];
                                if (img.bytes > MAX_BYTES_IMG) a.push(`${img.nombre} supera 10 MB — no se subirá`);
                                if (subirMl && !EXT_ML.has(img.extension)) a.push(`${img.nombre} — Mercado Libre no acepta .${img.extension}`);
                                if ((subirKtHogar || subirKtGastro) && !EXT_NUBE.has(img.extension)) a.push(`${img.nombre} — Tienda Nube no acepta .${img.extension}`);
                                return a;
                            });
                            if (!avisos.length) return null;
                            return (
                                <div className="mt-3 space-y-0.5 text-xs">
                                    {avisos.map((a, i) => (
                                        <div key={i} className="text-amber-600 dark:text-amber-400">&#9888; {a}</div>
                                    ))}
                                </div>
                            );
                        })()}
                    </fieldset>
```

Notas de equivalencia (para no romper comportamiento):
- El estado por canal usa los MISMOS `value`/`onChange`/opciones que la sección vieja "Estado de publicación".
- La cuota usa los MISMOS `cuota*`/`setCuota*`/`cuotas*Opts` que la sección vieja "Canales de venta"; antes la fila de cuota se mostraba sólo con el canal tildado (`{subir* && ...}`), ahora se muestra siempre pero **deshabilitada** (`disabled={!subir*}`) cuando está destildado (requisito "grisar").
- El estado sólo se renderiza en edición (`editandoProductoId`); en alta la tarjeta queda con el checkbox + cuota, como antes.
- El bloque de avisos de imágenes se conserva igual, ahora dentro de la única fieldset.

- [ ] **Step 3: Verificar que no quedan referencias muertas**

Run: `cd supermaster-frontend && grep -n "renderEstadoCanal\|Estado de publicación\|Dónde publicar/subir" src/app/productos/ProductoFormModal.tsx`
Expected: SIN resultados (se renombró a `renderEstadoBody`, y los textos viejos de las dos secciones ya no existen). Si aparece `renderEstadoCanal`, falta migrar/eliminar su uso o su definición.

- [ ] **Step 4: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0, sin errores.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(modal): unifica Estado de publicación y Canales de venta en una sección por canal"
```

---

## Verificación manual final

- En **edición** de un producto publicado: aparece **una sola** sección "Canales de venta" con 4 tarjetas; cada una con Sincronizar + Estado + (Cuota) + (Precio/Stock). Dux sin cuota/precio.
- **Destildar** "Sincronizar [canal]" → el `<select>` de estado y el de cuota de esa tarjeta quedan **deshabilitados** (grises); el Precio/Stock y el estado real siguen visibles.
- Cambiar un estado y guardar → se aplica igual que antes (mismo toast "Estado de publicación").
- En **alta**: las tarjetas muestran solo Sincronizar (+ Cuota); sin estado/precio.
- Ya **no existe** la sección separada "Estado de publicación".
