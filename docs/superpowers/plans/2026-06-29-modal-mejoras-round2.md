# Modal de producto — mejoras round 2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ocultar las secciones de canal según los checkboxes, agregar toolbar de formato al editor HTML de Nube, simplificar el render del SEO, pre-cargar el SEO de Nube leyéndolo del canal, y reordenar el panel de estado a Dux→Hogar→Gastro→ML.

**Architecture:** Mayormente frontend (`ProductoFormModal.tsx`, `HtmlEditor.tsx`, `productosService.ts`). Una pieza de backend: extender el bloque `datos` de `estado-publicacion` con el SEO por tienda, parseado del JSON del producto Nube que el service ya obtiene (sin llamadas nuevas).

**Tech Stack:** Backend Java 25 / Spring Boot 4 / Jackson 3 / JUnit 5 + AssertJ. Frontend Next.js/React/TS (gate = `tsc`).

## Global Constraints

- Backend: `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`); Jackson 3 (`tools.jackson`); cambio aditivo (no romper contrato). Paquete base `ar.com.leo.super_master_backend`.
- Frontend: `cd supermaster-frontend && npx tsc --noEmit` exit 0; sin errores `error` de lint nuevos; **sin dependencias nuevas**.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` SOLO de los archivos de cada task (hay cambios sueltos de backend Dux/proveedor en el working tree que NO deben tocarse ni agregarse), nunca `-A` ni `.superpowers/`.
- Records: agregar componentes rompe constructores posicionales en tests → correr `mvn -o test`.
- NO tocar la validación de proveedor comentada en `ProductoFormModal.tsx` (WIP del usuario).

## File Structure

- Create: `supermaster-backend/.../dominio/producto/estado/dto/SeoCanalDTO.java` — SEO por tienda leído del canal.
- Create: `supermaster-backend/.../dominio/producto/estado/NubeSeoParser.java` — parsea SEO del JSON del producto Nube.
- Modify: `.../estado/dto/DatosCanalDTO.java` — suma `seoHogar`/`seoGastro`.
- Modify: `.../estado/EstadoPublicacionService.java` — cablea el SEO en `datos`.
- Create test: `.../estado/NubeSeoParserTest.java`. Modify test: `.../estado/EstadoPublicacionServiceTest.java`.
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` — tipos `SeoCanal`/`DatosCanal`.
- Modify: `supermaster-frontend/src/app/productos/HtmlEditor.tsx` — toolbar.
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` — A/C/D-frontend/E.

---

### Task 1: Backend — leer el SEO de Nube en `datos`

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/SeoCanalDTO.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/NubeSeoParser.java`
- Modify: `.../dominio/producto/estado/dto/DatosCanalDTO.java`
- Modify: `.../dominio/producto/estado/EstadoPublicacionService.java`
- Create: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/NubeSeoParserTest.java`
- Modify: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionServiceTest.java`

**Interfaces:**
- Produces: `record SeoCanalDTO(String title, String description, String tags)`; `NubeSeoParser.parse(JsonNode product) -> SeoCanalDTO` (null si product null); `DatosCanalDTO` con 8 componentes (…, `SeoCanalDTO seoHogar`, `SeoCanalDTO seoGastro`).

- [ ] **Step 1: Crear `SeoCanalDTO`**

```java
package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

/** SEO de una tienda Nube leído del canal (no persistido). */
public record SeoCanalDTO(String title, String description, String tags) {}
```

- [ ] **Step 2: Test de `NubeSeoParser` (falla)**

```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.SeoCanalDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class NubeSeoParserTest {

    private static final ObjectMapper M = new ObjectMapper();
    private JsonNode json(String s) { return M.readTree(s); }

    @Test
    void productNull() {
        assertThat(NubeSeoParser.parse(null)).isNull();
    }

    @Test
    void i18nObjetoYTagsArray() {
        SeoCanalDTO seo = NubeSeoParser.parse(json("""
            {"seo_title":{"es":"Titulo"},"seo_description":{"es":"Desc"},"tags":["a","b","c"]}"""));
        assertThat(seo.title()).isEqualTo("Titulo");
        assertThat(seo.description()).isEqualTo("Desc");
        assertThat(seo.tags()).isEqualTo("a, b, c");
    }

    @Test
    void textualYTagsString() {
        SeoCanalDTO seo = NubeSeoParser.parse(json("""
            {"seo_title":"T","seo_description":"D","tags":"x, y"}"""));
        assertThat(seo.title()).isEqualTo("T");
        assertThat(seo.description()).isEqualTo("D");
        assertThat(seo.tags()).isEqualTo("x, y");
    }

    @Test
    void ausentesQuedanNull() {
        SeoCanalDTO seo = NubeSeoParser.parse(json("{}"));
        assertThat(seo).isNotNull();
        assertThat(seo.title()).isNull();
        assertThat(seo.description()).isNull();
        assertThat(seo.tags()).isNull();
    }
}
```

- [ ] **Step 3: Correr el test y verificar que falla**

Run: `cd supermaster-backend && mvn -o -q -Dtest=NubeSeoParserTest test`
Expected: FAIL (no compila: `NubeSeoParser` no existe).

- [ ] **Step 4: Implementar `NubeSeoParser`**

```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.SeoCanalDTO;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Extrae el SEO (title/description/tags) del JSON de un producto de Tienda Nube. */
public final class NubeSeoParser {

    private NubeSeoParser() {}

    public static SeoCanalDTO parse(JsonNode product) {
        if (product == null) return null;
        return new SeoCanalDTO(
                i18n(product.path("seo_title")),
                i18n(product.path("seo_description")),
                tags(product.path("tags")));
    }

    /** Campo que puede venir como objeto i18n ({"es": "..."}) o como string plano; null si ausente. */
    private static String i18n(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isObject()) return node.path("es").asString(null);
        return node.asString(null);
    }

    /** Tags: array de strings → unidos por ", "; string → tal cual; null si ausente. */
    private static String tags(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isArray()) {
            List<String> out = new ArrayList<>();
            for (JsonNode t : node) {
                String s = t.asString(null);
                if (s != null && !s.isBlank()) out.add(s.trim());
            }
            return out.isEmpty() ? null : String.join(", ", out);
        }
        return node.asString(null);
    }
}
```

- [ ] **Step 5: Correr el test y verificar que pasa**

Run: `cd supermaster-backend && mvn -o -q -Dtest=NubeSeoParserTest test`
Expected: PASS (4 tests).

- [ ] **Step 6: Ampliar `DatosCanalDTO` con el SEO por tienda**

```java
package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;

import java.util.List;

/** Campos editables leídos del canal para pre-llenar el modal (no persistidos). */
public record DatosCanalDTO(
        String mlCategoryId,
        String mlCategoryNombre,
        List<MlAtributoDTO> mlAtributos,
        String descripcionMl,
        String descripcionHogar,
        String descripcionGastro,
        SeoCanalDTO seoHogar,
        SeoCanalDTO seoGastro
) {
    public static DatosCanalDTO vacio() {
        return new DatosCanalDTO(null, null, List.of(), null, null, null, null, null);
    }
}
```

- [ ] **Step 7: Cablear el SEO en `EstadoPublicacionService.leer`**

En `EstadoPublicacionService.java`, donde se construye el `DatosCanalDTO datos = new DatosCanalDTO(...)` (reusa `hogarProd`/`gastroProd`, los `JsonNode` ya obtenidos por tienda), agregar los dos últimos argumentos:
```java
        DatosCanalDTO datos = new DatosCanalDTO(
                MlDatosParser.categoryId(mlItem),
                null, // nombre de categoría no viene en /items/{id}
                MlDatosParser.atributos(mlItem),
                descMl,
                descripcionNube(hogarProd),
                descripcionNube(gastroProd),
                NubeSeoParser.parse(hogarProd),
                NubeSeoParser.parse(gastroProd));
```

- [ ] **Step 8: Compilar y arreglar el test del service**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: BUILD SUCCESS.

En `EstadoPublicacionServiceTest`, si algún assert construye/inspecciona `DatosCanalDTO` posicionalmente, ajustarlo. El test `leer_cruzaMlYLasDosTiendas` ya stubea `buscarProductoPorSku` de HOGAR con un JSON sin `seo_*` → agregar un assert de que `dto.datos().seoHogar()` no es null pero sus campos son null:
```java
        assertThat(dto.datos().seoHogar()).isNotNull();
        assertThat(dto.datos().seoHogar().title()).isNull();
        assertThat(dto.datos().seoGastro()).isNull(); // gastro no encontrado (product null)
```
(Insertarlos junto a los asserts existentes de `datos` en ese test.)

- [ ] **Step 9: Correr la suite de estado y verificar verde**

Run: `cd supermaster-backend && mvn -o -q -Dtest=NubeSeoParserTest,EstadoPublicacionServiceTest,MlDatosParserTest,DuxEstadoParserTest test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/SeoCanalDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/NubeSeoParser.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/DatosCanalDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/NubeSeoParserTest.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionServiceTest.java
git commit -m "feat(canal): estado-publicacion lee el SEO de Nube por tienda en datos"
```

---

### Task 2: Frontend — tipos `SeoCanal`/`DatosCanal`

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`

**Interfaces:**
- Produces: `type SeoCanal = { title: string | null; description: string | null; tags: string | null }`; `DatosCanal` con `seoHogar: SeoCanal | null` y `seoGastro: SeoCanal | null`.

- [ ] **Step 1: Ampliar los tipos**

Reemplazar el tipo `DatosCanal` (líneas ~530-537) por:
```ts
export type SeoCanal = { title: string | null; description: string | null; tags: string | null };
export type DatosCanal = {
	mlCategoryId: string | null;
	mlCategoryNombre: string | null;
	mlAtributos: ProductoMlAtributo[];
	descripcionMl: string | null;
	descripcionHogar: string | null;
	descripcionGastro: string | null;
	seoHogar: SeoCanal | null;
	seoGastro: SeoCanal | null;
};
```

- [ ] **Step 2: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0 (aditivo).

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts
git commit -m "feat(front): tipo SeoCanal en datos del canal"
```

---

### Task 3: Frontend — toolbar en `HtmlEditor`

**Files:**
- Modify: `supermaster-frontend/src/app/productos/HtmlEditor.tsx`

**Interfaces:**
- Las props públicas no cambian: `{ value, onChange, disabled?, placeholder?, rows?, id? }`.

- [ ] **Step 1: Reescribir `HtmlEditor.tsx` con toolbar**

```tsx
"use client";

import React, { useRef } from "react";

type Props = {
    value: string;
    onChange: (v: string) => void;
    disabled?: boolean;
    placeholder?: string;
    rows?: number;
    id?: string;
};

/**
 * Editor de HTML sin dependencias: toolbar de formato + textarea con el HTML crudo + vista previa.
 * Los botones envuelven la selección actual con el tag correspondiente. Contenido interno/confiable;
 * la preview lo renderiza con dangerouslySetInnerHTML solo dentro del modal.
 */
export default function HtmlEditor({ value, onChange, disabled, placeholder, rows = 8, id }: Props) {
    const taRef = useRef<HTMLTextAreaElement>(null);

    const inputClass =
        "w-full rounded-xl border border-slate-300 bg-white px-3 py-2 font-mono text-xs text-slate-800 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100";
    const btnClass =
        "rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600";

    const wrap = (prefix: string, suffix: string) => {
        const ta = taRef.current;
        if (!ta) return;
        const start = ta.selectionStart ?? value.length;
        const end = ta.selectionEnd ?? value.length;
        const sel = value.slice(start, end);
        const next = value.slice(0, start) + prefix + sel + suffix + value.slice(end);
        onChange(next);
        requestAnimationFrame(() => {
            const t = taRef.current;
            if (!t) return;
            t.focus();
            t.setSelectionRange(start + prefix.length, start + prefix.length + sel.length);
        });
    };

    const insertarLista = () => {
        const ta = taRef.current;
        if (!ta) return;
        const start = ta.selectionStart ?? value.length;
        const end = ta.selectionEnd ?? value.length;
        const sel = value.slice(start, end);
        const items = sel.includes("\n")
            ? sel.split("\n").filter(l => l.trim() !== "").map(l => `<li>${l.trim()}</li>`).join("")
            : `<li>${sel}</li>`;
        const replacement = `<ul>${items}</ul>`;
        const next = value.slice(0, start) + replacement + value.slice(end);
        onChange(next);
        requestAnimationFrame(() => { taRef.current?.focus(); });
    };

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-wrap items-center gap-1.5">
                <button type="button" className={btnClass} disabled={disabled} onClick={() => wrap("<b>", "</b>")} title="Negrita"><b>B</b></button>
                <button type="button" className={btnClass} disabled={disabled} onClick={() => wrap("<i>", "</i>")} title="Cursiva"><i>I</i></button>
                <button type="button" className={btnClass} disabled={disabled} onClick={() => wrap("<u>", "</u>")} title="Subrayado"><u>U</u></button>
                <button type="button" className={btnClass} disabled={disabled} onClick={insertarLista} title="Lista">• Lista</button>
                <label className="flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400" title="Color de texto">
                    Color
                    <input type="color" disabled={disabled} defaultValue="#1e40af"
                        className="h-6 w-7 cursor-pointer rounded border border-slate-300 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600"
                        onChange={e => wrap(`<span style="color:${e.target.value}">`, "</span>")} />
                </label>
            </div>
            <div className="flex flex-col gap-3 lg:flex-row lg:items-stretch">
                <textarea
                    ref={taRef}
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
        </div>
    );
}
```

- [ ] **Step 2: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 3: Revisión manual**

Seleccionar texto en el textarea → Negrita/Cursiva/Subrayado/Lista lo envuelven; elegir un color envuelve con `<span style="color:…">`; la vista previa refleja el cambio. Sin selección, el tag se inserta vacío.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/HtmlEditor.tsx
git commit -m "feat(front): toolbar de formato (negrita/cursiva/subrayado/color/lista) en HtmlEditor"
```

---

### Task 4: Frontend — modal (ocultar secciones, simplificar SEO, pre-cargar SEO, reordenar panel)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `e.datos.seoHogar`/`e.datos.seoGastro` (Task 1/2); checkboxes `subirMl`/`subirKtHogar`/`subirKtGastro`.

- [ ] **Step 1 (E): Reordenar el panel "Estado de publicación"**

En el grid del panel (las 4 llamadas a `renderEstadoCanal`, ~líneas 1579-1607), reordenar los bloques para que queden en este orden: **Dux, Nube · KT HOGAR, Nube · KT GASTRO, Mercado Libre**. Es mover los 4 bloques `{renderEstadoCanal(...)}` completos (cada uno con su `<select>`/control), sin cambiar su contenido. (El bloque Dux es el de `estadoCanales?.dux`; ML el de `estadoCanales?.ml`.)

- [ ] **Step 2 (A): Ocultar las secciones de canal por checkbox**

Envolver cada `fieldset` de canal en su condicional:
- La sección **Mercado Libre** (el `<fieldset>` cuyo legend es `MercadoLibre`, ~1990): envolver TODO el fieldset en `{subirMl && ( ... )}`.
- La sección **Tienda Nube · KT HOGAR** (legend ~2207): envolver en `{subirKtHogar && ( ... )}`.
- La sección **Tienda Nube · KT GASTRO** (legend ~2234): envolver en `{subirKtGastro && ( ... )}`.
Cada envoltura va desde la apertura `<fieldset ...>` hasta su `</fieldset>` de cierre.

- [ ] **Step 3 (C): Simplificar `renderSeoNube` (sacar `activoCanal`)**

En la definición de `renderSeoNube` (~1501), quitar el parámetro `activoCanal` y sus usos: el `opacity-50` condicional del contenedor, los `disabled={!activoCanal}` de inputs/textarea/tags y del botón "Generar SEO con IA" (dejar el botón solo con `disabled={generandoSeo.has(canal)}`). Firma nueva: `renderSeoNube(canal, titulo, seo, setSeo)`. Actualizar las dos llamadas:
- `{renderSeoNube("HOGAR", "KT Hogar", seoHogar, setSeoHogar)}` (~2215).
- `{renderSeoNube("GASTRO", "KT Gastro", seoGastro, setSeoGastro)}` (~2242).

- [ ] **Step 4 (D): Pre-cargar el SEO desde el canal**

En el `.then` de `getEstadoPublicacionAPI` (~líneas 641-651), después de pre-cargar las descripciones, agregar:
```ts
                    if (e.datos.seoHogar) setSeoHogar({
                        title: e.datos.seoHogar.title ?? "",
                        description: e.datos.seoHogar.description ?? "",
                        tags: e.datos.seoHogar.tags ?? "",
                    });
                    if (e.datos.seoGastro) setSeoGastro({
                        title: e.datos.seoGastro.title ?? "",
                        description: e.datos.seoGastro.description ?? "",
                        tags: e.datos.seoGastro.tags ?? "",
                    });
```
(En alta —bloque `else`— `seoHogar`/`seoGastro` siguen reseteándose a vacío, sin cambios.)

- [ ] **Step 5: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0. Resolver cualquier referencia rota (p.ej. usos remanentes de `activoCanal`).

- [ ] **Step 6: Revisión manual**

- Tildar/destildar Dux/Hogar/Gastro/ML en "Canales de venta" muestra/oculta cada sección de canal.
- El panel ordena Dux → KT HOGAR → KT GASTRO → Mercado Libre.
- Dentro de una sección Nube visible, el SEO está habilitado (sin gris).
- Abrir un producto con SEO cargado en Nube precarga SEO Title/Description/Tags.

- [ ] **Step 7: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(front): secciones de canal ocultables, panel reordenado (Dux primero), SEO precargado del canal y renderSeoNube simplificado"
```

---

## Self-Review

1. **Cobertura de la spec:** A (ocultar secciones) → Task 4 Step 2; B (toolbar) → Task 3; C (simplificar renderSeoNube) → Task 4 Step 3; D (SEO del canal) → Task 1 (backend) + Task 2 (tipos) + Task 4 Step 4 (pre-carga); E (reordenar panel) → Task 4 Step 1. ✅
2. **Placeholders:** código completo para piezas nuevas (parser, DTO, HtmlEditor) y edits anclados para el modal.
3. **Consistencia de tipos:** `SeoCanalDTO(title, description, tags)` (back) ↔ `SeoCanal{title,description,tags}` (front); `DatosCanalDTO` 8 componentes ↔ `DatosCanal` con `seoHogar/seoGastro`; `renderSeoNube(canal, titulo, seo, setSeo)` nueva firma usada en ambas llamadas; pre-carga usa `e.datos.seoHogar/seoGastro`.
4. **No tocar:** validación de proveedor comentada (WIP); cambios sueltos de backend Dux.
