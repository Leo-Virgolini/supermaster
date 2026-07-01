# Variantes de producto en ML — Fase 2a (crear) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans / subagent-driven-development. Steps use checkbox syntax.

**Goal:** En el modal de creación de producto, poder marcar "tiene variantes", elegir el eje y cargar N variantes; al guardar, crear N productos y publicarlos con `family_name` compartido (ML los agrupa).

**Architecture:** Frontend puro. UI aislada en un componente nuevo `VariantesSection`. Estado y orquestación en `ProductoFormModal`. Se reutilizan los endpoints actuales (crear + export por SKU). El valor del eje viaja como atributo ML por variante. Backend sin cambios.

**Tech Stack:** Next.js / React / TypeScript / Tailwind.

## Global Constraints

- **Sin pruebas reales contra ML** (no ejecutar exports reales). Verificación por task: `npx tsc --noEmit -p tsconfig.json` (exit 0) + `npx eslint <archivos>` sin errores nuevos.
- No hay test runner en el frontend: no hay TDD; la verificación funcional la hace el usuario.
- Reusar patrones existentes del modal (estado con useState, `inputBaseClassName`, tarjetas `rounded-2xl`, panel de resultados por canal).
- Solo **crear** (modo alta). Editar familias es Fase 2b (no tocar el flujo de edición acá salvo ocultar el bloque en edición).
- Base = variante #1. Mínimo 2 variantes para considerar "tiene variantes".

---

### Task 1: Modelo y helpers de variantes (`variantes/types.ts`)

**Files:**
- Create: `supermaster-frontend/src/app/productos/variantes/types.ts`

**Interfaces:**
- Produces: `VarianteBorrador` (tipo), `nuevaVariante()` (factory), `validarVariantes(base, variantes, ejeAtributoId, subirMl, hayImagenSku)` → `string | null` (mensaje de error o null), `overrideDeVariante(v)` → `{ sku; stock; ean }`.

- [ ] **Step 1: Crear el archivo con el tipo y helpers puros**

```ts
// Borrador de una variante en el modal (variante #1 = el producto base; estas son las hermanas).
export type VarianteBorrador = {
    id: string;                 // key estable para React (no se envía)
    sku: string;
    ejeValorId: string | null;  // value_id del eje si es de lista
    ejeValorNombre: string;     // value_name (o valor libre)
    stock: number | "";
    ean: string;
    cuotaMl: number;
    cuotaHogar: number;
    cuotaGastro: number;
    expandida: boolean;
};

let _seq = 0;
export function nuevaVariante(cuotas: { ml: number; hogar: number; gastro: number }): VarianteBorrador {
    _seq += 1;
    return {
        id: `v${_seq}`, sku: "", ejeValorId: null, ejeValorNombre: "", stock: "", ean: "",
        cuotaMl: cuotas.ml, cuotaHogar: cuotas.hogar, cuotaGastro: cuotas.gastro, expandida: true,
    };
}

/** Valida el conjunto base + variantes. Devuelve el primer error, o null si está OK.
 *  hayImagenSku(sku) => true si el SKU tiene al menos una imagen válida para ML. */
export function validarVariantes(
    base: { sku: string; ejeValorNombre: string },
    variantes: VarianteBorrador[],
    ejeAtributoId: string,
    subirMl: boolean,
    hayImagenSku: (sku: string) => boolean,
): string | null {
    if (!ejeAtributoId) return "Elegí el eje de variación (ej. Color).";
    if (variantes.length < 1) return "Agregá al menos una variante además del producto base.";
    if (!base.ejeValorNombre.trim()) return "Cargá el valor del eje del producto base (variante #1).";

    const skus = [base.sku.trim().toLowerCase()];
    const valores = [base.ejeValorNombre.trim().toLowerCase()];
    for (const v of variantes) {
        if (!v.sku.trim()) return "Cada variante necesita su SKU.";
        if (!v.ejeValorNombre.trim()) return "Cada variante necesita su valor de eje.";
        const sk = v.sku.trim().toLowerCase();
        const val = v.ejeValorNombre.trim().toLowerCase();
        if (skus.includes(sk)) return `SKU repetido entre variantes: ${v.sku.trim()}.`;
        if (valores.includes(val)) return `Valor de eje repetido entre variantes: ${v.ejeValorNombre.trim()}.`;
        skus.push(sk); valores.push(val);
    }
    if (subirMl) {
        const todos = [base.sku, ...variantes.map(v => v.sku)];
        const sinImagen = todos.filter(s => s.trim() && !hayImagenSku(s.trim()));
        if (sinImagen.length > 0) return `Mercado Libre exige imagen; faltan para: ${sinImagen.join(", ")}.`;
    }
    return null;
}
```

- [ ] **Step 2: Typecheck**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/productos/variantes/types.ts
git commit -m "feat(variantes-ml): tipo VarianteBorrador + validaciones (Fase 2a)"
```

---

### Task 2: Componente `VariantesSection` (UI presentacional)

**Files:**
- Create: `supermaster-frontend/src/app/productos/variantes/VariantesSection.tsx`

**Interfaces:**
- Consumes: `VarianteBorrador`, `nuevaVariante` (Task 1); `MlAtributoDefDTO`-like eje options y sus values.
- Produces: componente `VariantesSection` con props:
  ```ts
  type Props = {
      tiene: boolean; onTiene: (v: boolean) => void;
      ejeOpciones: { id: string; name: string; values: { id: string | null; name: string }[] }[];
      ejeAtributoId: string; onEje: (id: string) => void;
      ejeValorBase: string; ejeValorBaseId: string | null; onEjeValorBase: (id: string | null, nombre: string) => void;
      skuBase: string;
      cuotasMlOpts: { cuotas: number; descripcion: string }[];
      cuotasHogarOpts: { cuotas: number; descripcion: string }[];
      cuotasGastroOpts: { cuotas: number; descripcion: string }[];
      cuotasDefault: { ml: number; hogar: number; gastro: number };
      variantes: VarianteBorrador[]; onVariantes: (v: VarianteBorrador[]) => void;
      subirMl: boolean; subirKtHogar: boolean; subirKtGastro: boolean;
      inputCls: string; selectCls: string;
  };
  ```

- [ ] **Step 1: Crear el componente** (toggle + eje + valor base + tarjetas expandibles con add/remove)

Contenido completo (usa las clases que ya usa el modal, pasadas por props `inputCls`/`selectCls`):

```tsx
"use client";
import { VarianteBorrador, nuevaVariante } from "./types";

type EjeOpcion = { id: string; name: string; values: { id: string | null; name: string }[] };
type Cuota = { cuotas: number; descripcion: string };

type Props = {
    tiene: boolean; onTiene: (v: boolean) => void;
    ejeOpciones: EjeOpcion[];
    ejeAtributoId: string; onEje: (id: string) => void;
    ejeValorBase: string; ejeValorBaseId: string | null; onEjeValorBase: (id: string | null, nombre: string) => void;
    skuBase: string;
    cuotasMlOpts: Cuota[]; cuotasHogarOpts: Cuota[]; cuotasGastroOpts: Cuota[];
    cuotasDefault: { ml: number; hogar: number; gastro: number };
    variantes: VarianteBorrador[]; onVariantes: (v: VarianteBorrador[]) => void;
    subirMl: boolean; subirKtHogar: boolean; subirKtGastro: boolean;
    inputCls: string; selectCls: string;
};

export default function VariantesSection(p: Props) {
    const ejeDef = p.ejeOpciones.find(e => e.id === p.ejeAtributoId);
    const valoresEje = ejeDef?.values ?? [];
    const setV = (id: string, patch: Partial<VarianteBorrador>) =>
        p.onVariantes(p.variantes.map(v => v.id === id ? { ...v, ...patch } : v));

    const selectorValor = (
        valorId: string | null, valorNombre: string,
        onChange: (id: string | null, nombre: string) => void,
    ) => valoresEje.length > 0 ? (
        <select className={selectClsFull(p.selectCls)} value={valorId ?? valorNombre}
            onChange={e => {
                const opt = valoresEje.find(x => (x.id ?? x.name) === e.target.value);
                onChange(opt?.id ?? null, opt?.name ?? "");
            }}>
            <option value="">— elegir —</option>
            {valoresEje.map(x => <option key={x.id ?? x.name} value={x.id ?? x.name}>{x.name}</option>)}
        </select>
    ) : (
        <input className={p.inputCls} value={valorNombre} onChange={e => onChange(null, e.target.value)} placeholder="Valor del eje" />
    );

    return (
        <div className="rounded-2xl border border-slate-200 bg-white/60 p-3 dark:border-slate-700 dark:bg-slate-800/40">
            <label className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
                <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary"
                    checked={p.tiene} onChange={e => p.onTiene(e.target.checked)} />
                Este producto tiene variantes (Mercado Libre)
            </label>
            {p.tiene && (
                <div className="mt-3 space-y-3">
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                        <label className="block">
                            <span className="text-xs text-slate-500">Eje de variación</span>
                            <select className={selectClsFull(p.selectCls)} value={p.ejeAtributoId} onChange={e => p.onEje(e.target.value)}>
                                <option value="">— elegir eje —</option>
                                {p.ejeOpciones.map(e => <option key={e.id} value={e.id}>{e.name}</option>)}
                            </select>
                            {p.ejeOpciones.length === 0 && <span className="mt-0.5 block text-[11px] text-amber-600">Elegí primero la categoría ML; el eje sale de sus atributos.</span>}
                        </label>
                        <label className="block">
                            <span className="text-xs text-slate-500">Valor del eje · variante #1 ({p.skuBase || "SKU base"})</span>
                            {selectorValor(p.ejeValorBaseId, p.ejeValorBase, p.onEjeValorBase)}
                        </label>
                    </div>

                    <div className="space-y-2">
                        {p.variantes.map((v, i) => (
                            <div key={v.id} className="rounded-xl border border-slate-200 bg-slate-50/70 p-2.5 dark:border-slate-700 dark:bg-slate-800/60">
                                <div className="flex items-center gap-2">
                                    <button type="button" onClick={() => setV(v.id, { expandida: !v.expandida })}
                                        className="text-xs font-semibold text-slate-600 dark:text-slate-300">{v.expandida ? "▾" : "▸"} Variante {i + 2}</button>
                                    <span className="text-xs text-slate-400">{v.sku || "sin SKU"} · {v.ejeValorNombre || "sin valor"}</span>
                                    <button type="button" onClick={() => p.onVariantes(p.variantes.filter(x => x.id !== v.id))}
                                        className="ml-auto text-xs font-medium text-red-500 hover:underline">Quitar</button>
                                </div>
                                {v.expandida && (
                                    <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2">
                                        <label className="block"><span className="text-[11px] text-slate-500">SKU</span>
                                            <input className={p.inputCls} value={v.sku} onChange={e => setV(v.id, { sku: e.target.value })} placeholder="SKU de la variante" /></label>
                                        <label className="block"><span className="text-[11px] text-slate-500">Valor del eje</span>
                                            {selectorValor(v.ejeValorId, v.ejeValorNombre, (id, nombre) => setV(v.id, { ejeValorId: id, ejeValorNombre: nombre }))}</label>
                                        <label className="block"><span className="text-[11px] text-slate-500">Stock</span>
                                            <input type="number" min={0} step={1} className={p.inputCls} value={v.stock} onChange={e => setV(v.id, { stock: e.target.value === "" ? "" : Number(e.target.value) })} placeholder="0" /></label>
                                        <label className="block"><span className="text-[11px] text-slate-500">EAN</span>
                                            <input className={p.inputCls} value={v.ean} onChange={e => setV(v.id, { ean: e.target.value })} placeholder="Código de barras" /></label>
                                        {p.subirMl && <label className="block"><span className="text-[11px] text-slate-500">Cuota ML</span>
                                            <select className={selectClsFull(p.selectCls)} value={v.cuotaMl} onChange={e => setV(v.id, { cuotaMl: Number(e.target.value) })}>
                                                {p.cuotasMlOpts.map(c => <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>)}</select></label>}
                                        {p.subirKtHogar && <label className="block"><span className="text-[11px] text-slate-500">Cuota KT HOGAR</span>
                                            <select className={selectClsFull(p.selectCls)} value={v.cuotaHogar} onChange={e => setV(v.id, { cuotaHogar: Number(e.target.value) })}>
                                                {p.cuotasHogarOpts.map(c => <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>)}</select></label>}
                                        {p.subirKtGastro && <label className="block"><span className="text-[11px] text-slate-500">Cuota KT GASTRO</span>
                                            <select className={selectClsFull(p.selectCls)} value={v.cuotaGastro} onChange={e => setV(v.id, { cuotaGastro: Number(e.target.value) })}>
                                                {p.cuotasGastroOpts.map(c => <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>)}</select></label>}
                                    </div>
                                )}
                            </div>
                        ))}
                        <button type="button" onClick={() => p.onVariantes([...p.variantes, nuevaVariante(p.cuotasDefault)])}
                            className="rounded-lg border border-dashed border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300">+ Agregar variante</button>
                    </div>
                </div>
            )}
        </div>
    );
}

function selectClsFull(base: string) { return `${base} w-full`; }
```

- [ ] **Step 2: Typecheck + lint**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json && npx eslint src/app/productos/variantes/VariantesSection.tsx`
Expected: exit 0, sin errores nuevos.

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/productos/variantes/VariantesSection.tsx
git commit -m "feat(variantes-ml): componente VariantesSection (UI, Fase 2a)"
```

---

### Task 3: Wire estado + render en `ProductoFormModal`

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `VariantesSection`, `VarianteBorrador`, `nuevaVariante` (Tasks 1-2).
- Produces: estado `tieneVariantes`, `ejeAtributoId`, `ejeValorBase`, `ejeValorBaseId`, `variantesBorrador`; el bloque renderizado en la sección ML (solo en alta: `!editandoProductoId`).

- [ ] **Step 1: Importar y agregar estado** (cerca de los otros useState del modal)

```tsx
import VariantesSection from "./variantes/VariantesSection";
import { VarianteBorrador } from "./variantes/types";
// ...
const [tieneVariantes, setTieneVariantes] = useState(false);
const [ejeAtributoId, setEjeAtributoId] = useState("");
const [ejeValorBase, setEjeValorBase] = useState("");
const [ejeValorBaseId, setEjeValorBaseId] = useState<string | null>(null);
const [variantesBorrador, setVariantesBorrador] = useState<VarianteBorrador[]>([]);
```

- [ ] **Step 2: Derivar las opciones de eje** desde `mlAtributosDef` (atributos con `allowVariations`)

Cerca de donde se usa `mlAtributosDef`:
```tsx
const ejeOpciones = mlAtributosDef
    .filter(d => d.allowVariations)
    .map(d => ({ id: d.id, name: d.name, values: (d.values ?? []).map(x => ({ id: x.id, name: x.name })) }));
```
> Verificar el nombre real del estado de defs (`mlAtributosDef`) y del tipo de `values` (`ProductoMlAtributoValor`/`MlAtributoValor`). Ajustar el `.map` a los campos reales (`id`, `name`).

- [ ] **Step 3: Renderizar el bloque** en la sección MercadoLibre, solo en alta (arriba de "Título ML"):

```tsx
{!editandoProductoId && (
    <div className="mb-4">
        <VariantesSection
            tiene={tieneVariantes} onTiene={setTieneVariantes}
            ejeOpciones={ejeOpciones}
            ejeAtributoId={ejeAtributoId} onEje={setEjeAtributoId}
            ejeValorBase={ejeValorBase} ejeValorBaseId={ejeValorBaseId}
            onEjeValorBase={(id, nombre) => { setEjeValorBaseId(id); setEjeValorBase(nombre); }}
            skuBase={sku}
            cuotasMlOpts={cuotasMlOpts} cuotasHogarOpts={cuotasHogarOpts} cuotasGastroOpts={cuotasGastroOpts}
            cuotasDefault={{ ml: cuotaMl, hogar: cuotaHogar, gastro: cuotaGastro }}
            variantes={variantesBorrador} onVariantes={setVariantesBorrador}
            subirMl={subirMl} subirKtHogar={subirKtHogar} subirKtGastro={subirKtGastro}
            inputCls={inputBaseClassName} selectCls={selectBaseClassName}
        />
    </div>
)}
```
> Ajustar los nombres reales de `cuotasMlOpts`/`cuotasHogarOpts`/`cuotasGastroOpts`, `inputBaseClassName`, `selectBaseClassName`, `subirMl` etc. (ya existen en el modal).

- [ ] **Step 4: Typecheck + lint**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json && npx eslint src/app/productos/ProductoFormModal.tsx`
Expected: exit 0, sin errores nuevos (ignorar los preexistentes de set-state-in-effect/img).

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(variantes-ml): bloque de variantes en el modal (estado + render, Fase 2a)"
```

---

### Task 4: Orquestación de guardado (crear N productos)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `validarVariantes` (Task 1); estado de Task 3.
- Produces: en `handleCreate`, cuando `tieneVariantes`, se crea + exporta cada variante (base + hermanas) inyectando el eje como atributo ML y usando las cuotas por variante; resultados por variante.

- [ ] **Step 1: Extraer un helper `crearYExportarUno`** a partir del cuerpo actual de `handleCreate`

Firma:
```tsx
type OverrideVariante = {
    sku: string; stock: number | ""; ean: string;
    ejeValorId: string | null; ejeValorNombre: string;
    cuotaMl: number; cuotaHogar: number; cuotaGastro: number;
};
// Crea el producto (clon del base con override) y exporta a sus canales; devuelve ResultadoCanal[].
const crearYExportarUno = async (ov: OverrideVariante): Promise<ResultadoCanal[]> => { /* ... */ };
```
Dentro:
- Arma el `ProductoCreateDTO` como hoy pero con `sku: ov.sku.trim()`, `stock`, `ean: ov.ean.trim() || null`.
- `createProducto(payload, asociarMargenYRelaciones)`.
- Exporta a canales reusando la lógica de `ejecutarExportsCanales`, pero para ML pasa
  `mlAtributos = [...Object.values(mlAtributosVal), { attributeId: ejeAtributoId, valueId: ov.ejeValorId, valueName: ov.ejeValorNombre, noAplica: false }]`
  y `cuotas = ov.cuotaMl`; Nube con `ov.cuotaHogar/ov.cuotaGastro`.
> Reusar/parametrizar `ejecutarExportsCanales` para aceptar overrides (sku, cuotas por canal, mlAtributos). Si es más simple, duplicar la parte de export dentro del helper.

- [ ] **Step 2: En `handleCreate`, ramificar por variantes**

```tsx
if (!validateForm()) return;
if (tieneVariantes) {
    const errVar = validarVariantes(
        { sku, ejeValorNombre: ejeValorBase }, variantesBorrador, ejeAtributoId, subirMl,
        (s) => imagenesDetectadasPorSku(s));  // ver Step 3
    if (errVar) { notificar.error(errVar); return; }
    setIsSaving(true);
    try {
        const todas: OverrideVariante[] = [
            { sku, stock, ean, ejeValorId: ejeValorBaseId, ejeValorNombre: ejeValorBase, cuotaMl, cuotaHogar, cuotaGastro },
            ...variantesBorrador.map(v => ({ sku: v.sku, stock: v.stock, ean: v.ean, ejeValorId: v.ejeValorId, ejeValorNombre: v.ejeValorNombre, cuotaMl: v.cuotaMl, cuotaHogar: v.cuotaHogar, cuotaGastro: v.cuotaGastro })),
        ];
        const porVariante: { sku: string; resultados: ResultadoCanal[] }[] = [];
        for (const ov of todas) {
            try { porVariante.push({ sku: ov.sku, resultados: await crearYExportarUno(ov) }); }
            catch (e) { porVariante.push({ sku: ov.sku, resultados: [{ canal: "Producto", estado: "error", detalle: e instanceof Error ? e.message : "falló la creación" }] }); }
        }
        // Aplanar a resultadosCanal con prefijo de SKU para el panel:
        setResultadosCanal(porVariante.flatMap(pv => pv.resultados.map(r => ({ ...r, canal: `${pv.sku} · ${r.canal}` }))));
        const huboError = porVariante.some(pv => pv.resultados.some(r => r.estado === "error"));
        if (!huboError) { notificar.success("Variantes creadas"); onSaved?.(); onClose(); }
    } finally { setIsSaving(false); }
    return;
}
// ... flujo actual de producto simple ...
```
> Ajustar `notificar`, `onSaved`/`onClose`, `setResultadosCanal`, `ResultadoCanal` a las firmas reales del modal.

- [ ] **Step 3: Helper de imágenes por SKU**

Como `imagenesDetectadas` es solo del SKU del base, para validar imágenes de las hermanas hace
falta consultar por cada SKU. Agregar:
```tsx
const imagenesPorSkuCache = useRef<Map<string, boolean>>(new Map());
const cargarImagenesVariantes = async (skus: string[]) => {
    await Promise.all(skus.map(async s => {
        if (imagenesPorSkuCache.current.has(s)) return;
        try { const imgs = await getImagenDetalleAPI(s); imagenesPorSkuCache.current.set(s, imgs.some(i => EXT_ML.has(i.extension) && i.bytes <= MAX_BYTES_IMG)); }
        catch { imagenesPorSkuCache.current.set(s, false); }
    }));
};
const imagenesDetectadasPorSku = (s: string) => s.trim() === sku.trim()
    ? imagenesDetectadas.some(i => EXT_ML.has(i.extension) && i.bytes <= MAX_BYTES_IMG)
    : (imagenesPorSkuCache.current.get(s.trim()) ?? false);
```
Llamar `await cargarImagenesVariantes(variantesBorrador.map(v => v.sku).filter(Boolean))` antes de `validarVariantes` en `handleCreate`.
> `getImagenDetalleAPI`, `EXT_ML`, `MAX_BYTES_IMG` ya existen.

- [ ] **Step 4: Typecheck + lint**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json && npx eslint src/app/productos/ProductoFormModal.tsx`
Expected: exit 0, sin errores nuevos.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(variantes-ml): crear N productos-variante al guardar (Fase 2a)"
```

---

### Task 5: Repaso final

- [ ] **Step 1:** `npx tsc --noEmit -p tsconfig.json` → exit 0.
- [ ] **Step 2:** `npx eslint src/app/productos/ProductoFormModal.tsx src/app/productos/variantes/*.tsx` → sin errores nuevos.
- [ ] **Step 3:** Verificación manual (usuario): crear un producto con 1 eje y 2 variantes, confirmar que se crean 2 productos y que en ML comparten family (sin ejecutar en producción si no se desea).

## Notas de implementación

- **No** se toca el flujo de edición (Fase 2b). En alta, si `tieneVariantes` está apagado, todo funciona como hoy.
- El valor del eje NO se persiste en el producto; viaja como atributo ML por variante. Coherente con datos-canal-externalizados.
- Si `ejecutarExportsCanales` resulta difícil de parametrizar sin romper el flujo simple, es válido
  extraer una versión `ejecutarExportsCanalesCon(overrides)` y que el flujo simple la llame con los
  valores del base.
