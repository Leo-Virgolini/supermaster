# Subidas a canales: paralelo + estado + reintento — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ejecutar las subidas a canales (Dux, Tienda Nube, Mercado Libre) en paralelo, mostrar un estado por canal en el modal, mantenerlo abierto si algo falla, y permitir reintentar los canales fallidos sin re-crear el producto.

**Architecture:** Se extrae una función reusable `ejecutarExportsCanales(sku, canales)` que corre los exports marcados en paralelo y devuelve `ResultadoCanal[]` (cada wrapper captura su error, nunca rechaza). `handleCreate` y la función de edición la usan tras crear/guardar + recalcular. Un estado `resultadosCanal` alimenta un panel en el modal con un botón "Reintentar los que fallaron". El cierre del modal es condicional: solo si todos los canales quedaron OK.

**Tech Stack:** Next.js 16 / React 19 / TypeScript. Verificación: `npx tsc --noEmit` + smoke (no hay test runner en el front).

## Global Constraints

- Trabajar en `main`. Frontend: `npx tsc --noEmit` desde `supermaster-frontend/`.
- **NO ejecutar nada que llame a APIs reales** — solo typecheck.
- **Paralelo** con `Promise.all` de wrappers que nunca rechazan (no `Promise.allSettled` necesario si los wrappers capturan). KT HOGAR + KT GASTRO siguen en UNA llamada a Nube.
- El recálculo de precio (`recalcularProductoAPI`) ya está y va **antes** de los exports — no se duplica.
- **Cierre condicional:** `resetForm()` + cerrar SOLO si todos los canales = `ok`. Si alguno = `error`, el modal queda abierto.
- **El producto queda creado**; el reintento solo re-dispara exports (idempotentes).
- Reintento: un solo botón "Reintentar los que fallaron".
- Solo frontend; sin cambios de backend.
- Commits terminan EXACTAMENTE con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: Tipo `ResultadoCanal` + `ejecutarExportsCanales` + reestructurar `handleCreate`

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

**Interfaces:**
- Produces:
  - `type CanalExport = "Dux" | "Tienda Nube" | "Mercado Libre"`
  - `type ResultadoCanal = { canal: CanalExport; estado: "ok" | "error"; detalle: string }`
  - `ejecutarExportsCanales(sku: string, canales: CanalExport[]): Promise<ResultadoCanal[]>` (closure del componente; usa los estados de cuotas/checkboxes actuales)
  - estado `resultadosCanal: ResultadoCanal[]` + setter; estado `skuSubida: string` (sku con el que se hicieron las subidas, para reintentar)

- [ ] **Step 1: Agregar tipos + estado**

Cerca de los otros `useState` del componente:
```ts
const [resultadosCanal, setResultadosCanal] = useState<ResultadoCanal[]>([]);
const [skuSubida, setSkuSubida] = useState("");
const [reintentando, setReintentando] = useState(false);
```
Tipos a nivel de módulo (junto a `reportarExportToast`):
```ts
type CanalExport = "Dux" | "Tienda Nube" | "Mercado Libre";
type ResultadoCanal = { canal: CanalExport; estado: "ok" | "error"; detalle: string };

// Convierte el resultado de un export de canal en ok/error + detalle legible.
function clasificarExport(canal: CanalExport, r: { creados?: number; actualizados?: string[]; yaExistian?: string[]; errores: string[]; advertencias?: string[] }): ResultadoCanal {
    if (r.errores.length) return { canal, estado: "error", detalle: r.errores.join("; ") };
    const partes: string[] = [];
    if (r.creados) partes.push(`${r.creados} creado(s)`);
    if (r.actualizados?.length) partes.push(`${r.actualizados.length} actualizado(s)`);
    if (r.yaExistian?.length) partes.push(`${r.yaExistian.length} ya existía(n)`);
    if (r.advertencias?.length) partes.push(`avisos: ${r.advertencias.join("; ")}`);
    return { canal, estado: "ok", detalle: partes.join(" · ") || "sin cambios" };
}
```

- [ ] **Step 2: Agregar `ejecutarExportsCanales` (closure del componente)**

Dentro del componente, antes de `handleCreate`:
```ts
// Ejecuta en paralelo los exports de los canales pedidos. Cada wrapper captura su error
// y devuelve un ResultadoCanal — nunca rechaza, así un canal no corta a los demás.
const ejecutarExportsCanales = async (skuExport: string, canales: CanalExport[]): Promise<ResultadoCanal[]> => {
    const tareas: Promise<ResultadoCanal>[] = [];
    if (canales.includes("Dux")) {
        tareas.push((async (): Promise<ResultadoCanal> => {
            try {
                const r = await exportarProductosADuxAPI([skuExport]);
                if (r.productosEnviados > 0) return { canal: "Dux", estado: "ok", detalle: "subido" };
                return { canal: "Dux", estado: "error", detalle: r.errores?.length ? r.errores.join("; ") : "no se envió a Dux" };
            } catch (e) {
                return { canal: "Dux", estado: "error", detalle: e instanceof Error ? e.message : "error al subir" };
            }
        })());
    }
    if (canales.includes("Tienda Nube")) {
        const tiendas: { tienda: "KT HOGAR" | "KT GASTRO"; cuotas: number }[] = [];
        if (subirKtHogar) tiendas.push({ tienda: "KT HOGAR", cuotas: cuotaHogar });
        if (subirKtGastro) tiendas.push({ tienda: "KT GASTRO", cuotas: cuotaGastro });
        if (tiendas.length) {
            tareas.push((async (): Promise<ResultadoCanal> => {
                try {
                    const r = await exportarProductosANubeAPI([skuExport], tiendas);
                    return clasificarExport("Tienda Nube", r);
                } catch (e) {
                    return { canal: "Tienda Nube", estado: "error", detalle: e instanceof Error ? e.message : "error al subir" };
                }
            })());
        }
    }
    if (canales.includes("Mercado Libre")) {
        tareas.push((async (): Promise<ResultadoCanal> => {
            try {
                const r = await exportarProductosAMlAPI([skuExport]);
                return clasificarExport("Mercado Libre", r);
            } catch (e) {
                return { canal: "Mercado Libre", estado: "error", detalle: e instanceof Error ? e.message : "error al subir" };
            }
        })());
    }
    return Promise.all(tareas);
};

// Canales marcados según los checkboxes (Nube agrupa HOGAR/GASTRO).
const canalesMarcados = (): CanalExport[] => {
    const c: CanalExport[] = [];
    if (subirADux && canExportarDux) c.push("Dux");
    if ((subirKtHogar || subirKtGastro) && canExportarDux) c.push("Tienda Nube");
    if (subirMl && canExportarDux) c.push("Mercado Libre");
    return c;
};
```

- [ ] **Step 3: Reestructurar el final de `handleCreate`**

Reemplazar el bloque de subidas actual (de `// Subida a Dux (opcional...)` hasta `setIsModalOpen(false);`, líneas ~604-639) por:
```ts
            // Exportar a los canales marcados EN PARALELO. El producto ya está creado.
            const sk = sku.trim();
            setSkuSubida(sk);
            const resultados = await ejecutarExportsCanales(sk, canalesMarcados());
            setResultadosCanal(resultados);
            if (resultados.every(r => r.estado === "ok")) {
                resultados.forEach(r => notificar.success(`${r.canal}: ${r.detalle}`));
                resetForm();
                setIsModalOpen(false);
            } else {
                // Mantener el modal abierto con el panel de estado por canal.
                notificar.error("El producto se creó, pero falló la subida a algún canal. Revisá el detalle.");
            }
```
(El recálculo de precio y el cálculo de envío MLA, ya presentes arriba en `handleCreate`, se mantienen.)

- [ ] **Step 4: Limpiar `resultadosCanal` al abrir/cerrar el modal**

En `resetForm()` agregar: `setResultadosCanal([]); setSkuSubida("");`. (Buscar la función `resetForm` y sumar estas dos líneas junto a los otros reset.)

- [ ] **Step 5: Typecheck**

Run (desde `supermaster-frontend/`): `npx tsc --noEmit`
Expected: sin errores. (El panel todavía no se renderiza — eso es Task 3 — pero el estado ya se setea.)

- [ ] **Step 6: Commit**

```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): exports a canales en paralelo + resultado por canal (alta)"
```

---

### Task 2: Reestructurar la función de edición (mismo patrón)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

**Interfaces:**
- Consumes: `ejecutarExportsCanales`, `canalesMarcados`, `resultadosCanal`/`setResultadosCanal`, `setSkuSubida` (de Task 1).

- [ ] **Step 1: Reestructurar el bloque de subidas de la edición**

En la función de guardar edición (la que contiene `tiendasNubeEdit`, ~líneas 760-802), reemplazar el bloque de subidas (de `// Actualización en Dux` / el recálculo previo, hasta antes de `resetForm(); setEditandoProductoId(null); setIsModalOpen(false);`) por la versión en paralelo:
```ts
            // Recálculo SÍNCRONO antes de exportar a Nube (ya presente arriba si aplica).
            const sk = sku.trim();
            setSkuSubida(sk);
            const resultados = await ejecutarExportsCanales(sk, canalesMarcados());
            setResultadosCanal(resultados);
            if (resultados.every(r => r.estado === "ok")) {
                resultados.forEach(r => notificar.success(`${r.canal}: ${r.detalle}`));
                resetForm();
                setEditandoProductoId(null);
                setIsModalOpen(false);
                await refresh();
            } else {
                notificar.error("Los cambios se guardaron, pero falló la subida a algún canal. Revisá el detalle.");
                await refresh();
            }
```
Conservar el `await recalcularProductoAPI(id)` previo (del #1) antes de este bloque.

- [ ] **Step 2: Typecheck**

Run: `npx tsc --noEmit`
Expected: sin errores.

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): exports a canales en paralelo + resultado por canal (edicion)"
```

---

### Task 3: Panel de estado por canal + botón "Reintentar los que fallaron"

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

**Interfaces:**
- Consumes: `resultadosCanal`, `skuSubida`, `ejecutarExportsCanales`, `reintentando`/`setReintentando` (Task 1).

- [ ] **Step 1: Agregar el handler de reintento (dentro del componente)**

```ts
const reintentarFallidos = async () => {
    const fallidos = resultadosCanal.filter(r => r.estado === "error").map(r => r.canal);
    if (!fallidos.length || !skuSubida) return;
    setReintentando(true);
    try {
        const nuevos = await ejecutarExportsCanales(skuSubida, fallidos);
        // Combinar: reemplazar los resultados de los canales reintentados.
        setResultadosCanal(prev => prev.map(p => nuevos.find(n => n.canal === p.canal) ?? p));
        if (nuevos.every(r => r.estado === "ok")) {
            nuevos.forEach(r => notificar.success(`${r.canal}: ${r.detalle}`));
            resetForm();
            setEditandoProductoId(null);
            setIsModalOpen(false);
            await refresh();
        }
    } finally {
        setReintentando(false);
    }
};
```

- [ ] **Step 2: Renderizar el panel en el modal**

Ubicarlo al pie del modal (cerca de los botones Crear/Guardar; el implementer localiza ese contenedor). Render condicional cuando hay errores:
```tsx
{resultadosCanal.some(r => r.estado === "error") && (
    <div className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm dark:border-amber-800 dark:bg-amber-950/20">
        <div className="mb-2 font-semibold text-amber-800 dark:text-amber-300">
            El producto se guardó. Faltó publicar en algún canal:
        </div>
        <ul className="space-y-1">
            {resultadosCanal.map(r => (
                <li key={r.canal} className="flex items-start gap-2">
                    <span>{r.estado === "ok" ? "✅" : "❌"}</span>
                    <span className="font-medium">{r.canal}:</span>
                    <span className="text-slate-600 dark:text-slate-300">{r.detalle}</span>
                </li>
            ))}
        </ul>
        <Button variant="dark" className="mt-3" onClick={reintentarFallidos} disabled={reintentando}>
            {reintentando ? "Reintentando…" : "Reintentar los que fallaron"}
        </Button>
    </div>
)}
```
(Usar el componente `Button` ya importado; ajustar `variant`/clases al estilo del modal.)

- [ ] **Step 3: Typecheck**

Run: `npx tsc --noEmit`
Expected: sin errores.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): panel de estado por canal + reintentar los que fallaron"
```

---

## Verificación final
- [ ] `npx tsc --noEmit` → sin errores.
- [ ] **Smoke (usuario):** crear un producto con los 3 canales; forzar un fallo en uno → el modal queda abierto, el panel muestra ✅/❌ + motivo por canal; "Reintentar los que fallaron" re-dispara solo los fallidos; cuando todos quedan ✅, el modal limpia y cierra. Verificar que las subidas corren en paralelo (más rápido) y que un fallo no corta a los demás.

## Notas de diseño
- `ejecutarExportsCanales` es la única fuente de verdad de la lógica de subida; la usan alta, edición y reintento (DRY).
- Los wrappers capturan su error y devuelven `ResultadoCanal` → un canal nunca corta a los otros (equivalente a `allSettled` pero con tipo de retorno uniforme).
- El reintento de Nube re-manda las tiendas marcadas (idempotente: una que ya existía vuelve como "ya existía").
