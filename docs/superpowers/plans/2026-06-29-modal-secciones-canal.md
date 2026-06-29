# Modal por secciones de canal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganizar `ProductoFormModal.tsx` en secciones por canal (Mercado Libre / Tienda Nube · KT HOGAR / Tienda Nube · KT GASTRO), mover EAN a Generales, sincronizar el título Nube, destacar el SKU, agregar un editor HTML con vista previa para las descripciones de Nube, indicadores de carga en los campos que vienen del canal, y un aviso al cambiar "Es combo" en edición.

**Architecture:** Cambio de presentación sobre un único componente grande + un componente nuevo `HtmlEditor`. NO cambian los `useState` de datos, los handlers de guardado/creación/export, ni la pre-carga desde `estado.datos`: los campos se reubican en nuevos `fieldset`. Única lógica nueva: estado `esComboOriginal` para el aviso.

**Tech Stack:** Next.js/React/TypeScript, Tailwind. Sin tests automáticos de frontend (gate = `tsc`).

## Global Constraints

- `cd supermaster-frontend && npx tsc --noEmit` exit 0; sin errores `error` de lint nuevos.
- **No agregar dependencias** (el editor HTML es sin librerías).
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos, nunca `-A` ni `.superpowers/`.
- Mantener intactos: estados de datos, handlers de guardar/crear/exportar, pre-carga desde `estado.datos`. Reubicar JSX, no reescribir su lógica interna.
- Respetar modo oscuro (`dark:`) en todo estilo nuevo.

## File Structure

- Create: `supermaster-frontend/src/app/productos/HtmlEditor.tsx` — editor HTML reusable (textarea + vista previa en vivo). Responsabilidad única: editar HTML con preview.
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` — reflow de secciones + uso de `HtmlEditor` + estilos SKU + aviso "Es combo" + indicadores de carga.

## Estado actual relevante (anclas)

- Estados de datos ya existen: `sku`, `tituloMl`, `tituloNube`, `descripcionMl`, `descripcionHogar`, `descripcionGastro`, `ean`, `esCombo`/`setEsCombo`, `cargandoEstado`, `seoHogar`/`setSeoHogar`, `seoGastro`/`setSeoGastro`, `mlCategoryId`, `mlFicha`, `mlAtributosVal`.
- Secciones actuales (legends): `Identificación` (~1677), `MercadoLibre` (~1983), `SEO de Tienda Nube` (~2168-2206), `Dimensiones Físicas` (~2152).
- Hoy en **Identificación** están: SKU (~1693-1694), Es Combo (~1688), Título Dux (~1710), Título ML (~1727), Título Nube (~1762).
- Hoy en **MercadoLibre** están: bloque MLA, categoría + predictor, las **3 descripciones** (~2044-2076), ficha técnica (~2077), paquete ML (~2100), **EAN** (~2136-2141).
- Helpers de estilo en el archivo: `sectionClassName`, `sectionTitleClassName`, `sectionDescriptionClassName`, `fieldLabelClassName`, `inputBaseClassName`, `inputErrorClassName`, y `SECTION_TINT` (tints por sección, p.ej. `SECTION_TINT.seo`).
- `handleToggleCombo(next)` (~805) ya evita recalcular el SKU en edición (solo recalcula si `sku===""||sku===lastSuggestedSku`). El aviso es informativo.

---

### Task 1: Componente `HtmlEditor`

**Files:**
- Create: `supermaster-frontend/src/app/productos/HtmlEditor.tsx`

**Interfaces:**
- Produces: `export default function HtmlEditor(props: { value: string; onChange: (v: string) => void; disabled?: boolean; placeholder?: string; rows?: number; id?: string }): JSX.Element`

- [ ] **Step 1: Crear el componente**

```tsx
"use client";

import React from "react";

type Props = {
    value: string;
    onChange: (v: string) => void;
    disabled?: boolean;
    placeholder?: string;
    rows?: number;
    id?: string;
};

/**
 * Editor de HTML sin dependencias: textarea con el HTML crudo + vista previa en vivo.
 * El contenido es interno/confiable (lo genera el sistema o lo edita el usuario); la
 * preview lo renderiza con dangerouslySetInnerHTML solo dentro del modal.
 */
export default function HtmlEditor({ value, onChange, disabled, placeholder, rows = 8, id }: Props) {
    const inputClass =
        "w-full rounded-xl border border-slate-300 bg-white px-3 py-2 font-mono text-xs text-slate-800 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100";
    return (
        <div className="flex flex-col gap-3 lg:flex-row lg:items-stretch">
            <textarea
                id={id}
                className={`${inputClass} lg:w-1/2`}
                value={value}
                onChange={e => onChange(e.target.value)}
                disabled={disabled}
                placeholder={placeholder}
                rows={rows}
            />
            <div className="lg:w-1/2">
                <span className="mb-1 block text-xs font-medium text-slate-400 dark:text-slate-500">Vista previa</span>
                <div
                    className="prose prose-sm max-w-none overflow-auto rounded-xl border border-dashed border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-800 dark:border-slate-600 dark:bg-slate-800/60 dark:text-slate-100"
                    style={{ minHeight: `${rows * 1.5}rem` }}
                    dangerouslySetInnerHTML={{ __html: value || "<span style=\"color:#94a3b8\">(sin contenido)</span>" }}
                />
            </div>
        </div>
    );
}
```

- [ ] **Step 2: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/productos/HtmlEditor.tsx
git commit -m "feat(front): componente HtmlEditor (textarea + vista previa, sin dependencias)"
```

---

### Task 2: SKU destacado + aviso "Es combo" en edición

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: estado `esCombo`, `editandoProductoId`, pre-carga de edición (~563) y reset de alta.
- Produces: estado nuevo `esComboOriginal` (boolean).

- [ ] **Step 1: Agregar estado `esComboOriginal`**

Junto a `const [esCombo, setEsCombo] = useState(false);` (~151) agregar:
```tsx
    const [esComboOriginal, setEsComboOriginal] = useState(false);
```

- [ ] **Step 2: Setearlo en la pre-carga de edición y en el reset de alta**

En el bloque de edición, donde está `setEsCombo(!!producto.esCombo);` (~563), agregar debajo:
```tsx
            setEsComboOriginal(!!producto.esCombo);
```
En el bloque `else` de alta (donde se resetean los campos, junto a las descripciones que se limpian) agregar:
```tsx
            setEsComboOriginal(false);
```

- [ ] **Step 3: Destacar el SKU (label + input, acento índigo)**

Reemplazar el `<span className={fieldLabelClassName}>SKU ...` (~1693) por una etiqueta índigo, y agregar al `className` del input del SKU (~1694) un acento índigo con fuente monoespaciada. Label:
```tsx
                                <span className="mb-1 block text-sm font-bold tracking-wide text-indigo-600 dark:text-indigo-400">SKU <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
```
Input: agregar `border-l-4 border-l-indigo-500 font-mono font-bold tracking-wide` al inicio del template de className existente (sin quitar la lógica condicional de edit/error/skuYaExiste). Es decir, el `className` pasa a:
```tsx
                                <input type="text" disabled={!!editandoProductoId} className={`${inputBaseClassName} border-l-4 border-l-indigo-500 font-mono font-bold tracking-wide ${editandoProductoId ? "cursor-not-allowed border-slate-300 bg-slate-100 font-semibold text-slate-800 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-100" : ""} ${formErrors.sku ? inputErrorClassName : (skuYaExiste ? "border-amber-400 bg-amber-50 dark:bg-amber-900/20" : "")}`} value={sku} onChange={e => { setSku(e.target.value); if (formErrors.sku) setFormErrors(p => ({ ...p, sku: "" })); }} placeholder="Ej: CUT-001" autoFocus required />
```

- [ ] **Step 4: Aviso al cambiar "Es combo" en edición**

Debajo del `<label htmlFor="esCombo" ...>Es Combo</label>` y su input (~1688-1689), dentro del mismo contenedor del checkbox, agregar:
```tsx
                            {editandoProductoId && esCombo !== esComboOriginal && (
                                <p className="mt-1 w-full text-xs font-medium text-amber-600 dark:text-amber-400">
                                    ⚠ Cambiar "Es combo" no modifica el SKU: se mantiene el actual y no se reasigna al rango de SKUs de simple/combo.
                                </p>
                            )}
```
(Si el checkbox y su label están en un contenedor `flex` que no envuelve, poner el `<p>` con `w-full` para que caiga en su propia línea, como arriba.)

- [ ] **Step 5: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 6: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(front): SKU destacado (indigo) y aviso al cambiar Es combo en edicion"
```

---

### Task 3: Reflow en secciones por canal + EAN a Generales + título Nube sincronizado + editor HTML + loadings

Edición cohesiva del JSX del modal: ningún cambio de lógica de datos, solo reubicación + el `HtmlEditor` + indicadores de carga. Se verifica con `tsc` 0 y revisión manual; un commit.

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `HtmlEditor` (Task 1); estados `tituloMl`, `tituloNube`, `ean`, las 3 descripciones, `seoHogar/Gastro`, `cargandoEstado`, `mlCategoryId`, `mlFicha`, `mlAtributosVal`.

- [ ] **Step 1: Importar `HtmlEditor`**

En los imports del modal agregar:
```tsx
import HtmlEditor from "./HtmlEditor";
```

- [ ] **Step 2: Mover EAN a Identificación**

Cortar el bloque del input de **EAN** que hoy vive en la sección "MercadoLibre" (~2136-2151, el `label` con `value={ean}` y su comentario `{/* EAN / Código de barras */}`) y pegarlo dentro de la sección **Identificación** (después del SKU/identificadores, antes de cerrar ese `fieldset`). No cambiar su lógica interna (validación EAN, etc.).

- [ ] **Step 3: Sacar Título ML y Título Nube de Identificación**

Quitar de la sección Identificación los `label` de **Título ML** (~1727) y **Título Nube** (~1762). El Título Dux (~1710) queda en Identificación. (Estos campos se re-insertan en las nuevas secciones, abajo.)

- [ ] **Step 4: Renombrar la sección "MercadoLibre" y agregarle Título ML + Descripción ML**

En el `fieldset` de la sección ML (legend ~1983), mantener el legend "Mercado Libre". Al **inicio** de la sección (antes del bloque MLA) insertar el **Título ML** (el `label` que se sacó de Identificación). De las **3 descripciones** que hoy están en esa sección (~2044-2076), dejar **solo la de Mercado Libre** (texto plano) con su indicador de carga:
```tsx
                            <label className="block">
                                <span className="flex items-center justify-between gap-2">
                                    <span className={fieldLabelClassName}>Descripción Mercado Libre</span>
                                    <button type="button" disabled={sugiriendoDesc || !editandoProductoId}
                                        className="text-xs font-medium text-blue-600 hover:underline disabled:opacity-50 dark:text-blue-400"
                                        onClick={async () => {
                                            if (!editandoProductoId) return;
                                            setSugiriendoDesc(true);
                                            try { setDescripcionMl(await getDescripcionSugeridaAPI(editandoProductoId, "ml")); }
                                            catch (e) { if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo componer la descripción"); }
                                            finally { setSugiriendoDesc(false); }
                                        }}>
                                        Componer descripción sugerida
                                    </button>
                                </span>
                                {cargandoEstado
                                    ? <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-4 text-xs text-slate-400 dark:border-slate-700 dark:bg-slate-800/60"><SpinnerIcon /> Cargando datos del canal…</div>
                                    : <textarea className={inputBaseClassName} value={descripcionMl} onChange={e => setDescripcionMl(e.target.value)} rows={4} maxLength={20000} disabled={cargandoEstado} placeholder="Texto plano (sin HTML). Lo que ves es lo que se publica en ML." />}
                            </label>
```
> Las descripciones de Hogar y Gastro se quitan de acá y se reubican en las secciones Nube (Step 6). La categoría + ficha técnica + paquete ML quedan en esta sección, sin cambios de lógica.

- [ ] **Step 5: Indicador de carga en categoría/ficha ML**

Envolver el bloque de la ficha técnica de ML (~2077, el `{mlCategoryId && mlFicha && ...}`) para que muestre el indicador mientras carga. Antes de ese bloque agregar:
```tsx
                        {cargandoEstado && (
                            <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-xs text-slate-400 dark:border-slate-700 dark:bg-slate-800/60"><SpinnerIcon /> Cargando categoría y ficha técnica del canal…</div>
                        )}
```
(El predictor/los selects de categoría siguen visibles; el indicador comunica que se está trayendo lo publicado.)

- [ ] **Step 6: Crear las secciones Nube (Hogar y Gastro) con título sincronizado, SEO y descripción HTML**

Reemplazar la sección actual "SEO de Tienda Nube" (~2168-2206 completa, incluido el wrapper `{(subirKtHogar || subirKtGastro) && (...)}`) por **dos** secciones, una por tienda. Cada una: título Nube (sincronizado, mismo `tituloNube`/`setTituloNube`), su bloque SEO (reusando el contenido por-tienda que ya existía en el `.map`), y la descripción con `HtmlEditor`. Insertarlas en el lugar donde corresponde el orden (después de la sección Mercado Libre). Código:

```tsx
                    {/* TIENDA NUBE · KT HOGAR */}
                    <fieldset className={`${sectionClassName} ${SECTION_TINT.seo}`}>
                        <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> Tienda Nube · KT HOGAR</legend>
                        <div className="grid grid-cols-1 gap-4">
                            <label className="block">
                                <span className={fieldLabelClassName}>Título Nube</span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloNube ? inputErrorClassName : ""}`} value={tituloNube} onChange={e => { setTituloNube(e.target.value); if (formErrors.tituloNube) setFormErrors(p => ({ ...p, tituloNube: "" })); }} placeholder="Título para Tienda Nube" />
                                <span className="mt-0.5 block text-[11px] text-slate-400 dark:text-slate-500">Compartido entre KT HOGAR y KT GASTRO.</span>
                            </label>
                            {renderSeoNube("HOGAR", "KT Hogar", subirKtHogar, seoHogar, setSeoHogar)}
                            <label className="block">
                                <span className="flex items-center justify-between gap-2">
                                    <span className={fieldLabelClassName}>Descripción · KT HOGAR</span>
                                    <button type="button" disabled={sugiriendoDesc || !editandoProductoId}
                                        className="text-xs font-medium text-blue-600 hover:underline disabled:opacity-50 dark:text-blue-400"
                                        onClick={async () => { if (!editandoProductoId) return; setSugiriendoDesc(true); try { setDescripcionHogar(await getDescripcionSugeridaAPI(editandoProductoId, "nube")); } catch (e) { if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo componer la descripción"); } finally { setSugiriendoDesc(false); } }}>
                                        Componer descripción sugerida
                                    </button>
                                </span>
                                {cargandoEstado
                                    ? <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-4 text-xs text-slate-400 dark:border-slate-700 dark:bg-slate-800/60"><SpinnerIcon /> Cargando datos del canal…</div>
                                    : <HtmlEditor value={descripcionHogar} onChange={setDescripcionHogar} placeholder="HTML. Lo que ves es lo que se publica en KT HOGAR." />}
                            </label>
                        </div>
                    </fieldset>

                    {/* TIENDA NUBE · KT GASTRO */}
                    <fieldset className={`${sectionClassName} ${SECTION_TINT.seo}`}>
                        <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> Tienda Nube · KT GASTRO</legend>
                        <div className="grid grid-cols-1 gap-4">
                            <label className="block">
                                <span className={fieldLabelClassName}>Título Nube</span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloNube ? inputErrorClassName : ""}`} value={tituloNube} onChange={e => { setTituloNube(e.target.value); if (formErrors.tituloNube) setFormErrors(p => ({ ...p, tituloNube: "" })); }} placeholder="Título para Tienda Nube" />
                                <span className="mt-0.5 block text-[11px] text-slate-400 dark:text-slate-500">Compartido entre KT HOGAR y KT GASTRO.</span>
                            </label>
                            {renderSeoNube("GASTRO", "KT Gastro", subirKtGastro, seoGastro, setSeoGastro)}
                            <label className="block">
                                <span className="flex items-center justify-between gap-2">
                                    <span className={fieldLabelClassName}>Descripción · KT GASTRO</span>
                                    <button type="button" disabled={sugiriendoDesc || !editandoProductoId}
                                        className="text-xs font-medium text-blue-600 hover:underline disabled:opacity-50 dark:text-blue-400"
                                        onClick={async () => { if (!editandoProductoId) return; setSugiriendoDesc(true); try { setDescripcionGastro(await getDescripcionSugeridaAPI(editandoProductoId, "nube")); } catch (e) { if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo componer la descripción"); } finally { setSugiriendoDesc(false); } }}>
                                        Componer descripción sugerida
                                    </button>
                                </span>
                                {cargandoEstado
                                    ? <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-4 text-xs text-slate-400 dark:border-slate-700 dark:bg-slate-800/60"><SpinnerIcon /> Cargando datos del canal…</div>
                                    : <HtmlEditor value={descripcionGastro} onChange={setDescripcionGastro} placeholder="HTML. Lo que ves es lo que se publica en KT GASTRO." />}
                            </label>
                        </div>
                    </fieldset>
```

- [ ] **Step 7: Extraer el render del bloque SEO por tienda a un helper**

El contenido interno de cada tarjeta SEO (SEO Title/Description/Tags + botón "Generar SEO con IA") que hoy vive en el `.map` de la sección "SEO de Tienda Nube" (~2178-2202) se extrae a un helper para reusarlo en ambas secciones Nube. Definirlo junto a los otros render-helpers del componente (p.ej. cerca de `renderEstadoCanal`):
```tsx
    const renderSeoNube = (
        canal: "HOGAR" | "GASTRO",
        titulo: string,
        activoCanal: boolean,
        seo: { title: string; description: string; tags: string },
        setSeo: React.Dispatch<React.SetStateAction<{ title: string; description: string; tags: string }>>,
    ) => (
        <div className={`rounded-2xl border border-slate-200 bg-white/70 p-4 transition-opacity dark:border-slate-700 dark:bg-slate-800/60 ${activoCanal ? "" : "opacity-50"}`}>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">SEO · {titulo}</span>
                <Button variant="dark" onClick={() => generarSeo(canal)} disabled={generandoSeo.has(canal) || !activoCanal}>
                    {generandoSeo.has(canal) ? <SpinnerIcon /> : <SparklesIcon className="h-4 w-4" />}
                    {generandoSeo.has(canal) ? "Generando..." : "Generar SEO con IA"}
                </Button>
            </div>
            <div className="grid grid-cols-1 gap-3">
                <label className="block">
                    <span className={fieldLabelClassName}>SEO Title</span>
                    <input type="text" maxLength={70} disabled={!activoCanal} className={`${inputBaseClassName} disabled:cursor-not-allowed disabled:opacity-60`} value={seo.title} onChange={e => setSeo(p => ({ ...p, title: e.target.value }))} placeholder="Título SEO" />
                    <span className="mt-1 block text-right text-xs text-slate-400">{seo.title.length}/70</span>
                </label>
                <label className="block">
                    <span className={fieldLabelClassName}>SEO Description</span>
                    <textarea maxLength={320} rows={3} disabled={!activoCanal} className={`${inputBaseClassName} disabled:cursor-not-allowed disabled:opacity-60`} value={seo.description} onChange={e => setSeo(p => ({ ...p, description: e.target.value }))} placeholder="Descripción SEO" />
                    <span className="mt-1 block text-right text-xs text-slate-400">{seo.description.length}/320</span>
                </label>
                <label className="block">
                    <span className={fieldLabelClassName}>Tags</span>
                    <input type="text" disabled={!activoCanal} className={`${inputBaseClassName} disabled:cursor-not-allowed disabled:opacity-60`} value={seo.tags} onChange={e => setSeo(p => ({ ...p, tags: e.target.value }))} placeholder="tag1, tag2, ..." />
                </label>
            </div>
        </div>
    );
```
(El `generarSeo`, `generandoSeo`, `Button`, `SpinnerIcon`, `SparklesIcon` ya existen y se siguen usando igual.)

- [ ] **Step 8: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0. Resolver cualquier referencia rota (imports sin usar de la sección SEO vieja, variables del `.map` eliminado, etc.).

- [ ] **Step 9: Revisión manual**

Verificar en el navegador (con backend corriendo):
- Secciones separadas: Mercado Libre (título ML, MLA, categoría/ficha, paquete, descripción ML), KT HOGAR y KT GASTRO (título Nube, SEO, descripción HTML), EAN en Identificación.
- Editar el título Nube en una sección actualiza la otra.
- La descripción Nube muestra textarea + vista previa que refleja el HTML.
- Al abrir un producto publicado aparecen los indicadores "Cargando datos del canal…" y luego los datos.
- Guardar y exportar siguen funcionando (ML manda categoría/atributos/descripciones; Nube por tienda).

- [ ] **Step 10: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(front): modal por secciones de canal (ML/Hogar/Gastro), EAN en Generales, editor HTML Nube y loadings"
```

---

## Self-Review

1. **Cobertura de la spec:** secciones por canal → Task 3 (Steps 3-7); EAN a Generales → Task 3 Step 2; título Nube sincronizado → Task 3 Step 6; SKU destacado → Task 2 Step 3; aviso Es combo → Task 2 Steps 1-2,4; editor HTML Nube → Task 1 + Task 3 Step 6; loadings por campo → Task 3 Steps 4-6; Dux sin sección → respetado (no se crea). ✅
2. **Placeholders:** los pasos muestran código real o reubicaciones con anclas concretas; los "mover intacto" referencian rangos/legends exactos.
3. **Consistencia de tipos:** `HtmlEditor` props usados igual en Task 1 y Task 3 Step 6; `renderSeoNube(canal, titulo, activoCanal, seo, setSeo)` definido (Step 7) y usado (Step 6); `esComboOriginal` definido y usado en Task 2.
4. **Sin backend:** no se tocan `productosService.ts` ni `types.ts`; los handlers de guardado/export quedan intactos.
