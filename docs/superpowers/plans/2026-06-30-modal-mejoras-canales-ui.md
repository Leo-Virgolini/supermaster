# Mejoras de UI del modal (Grupo 1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 9 mejoras de UI del modal de producto: carga progresiva de canales (un endpoint por canal), cantidad de imágenes y colores por canal, indicador de carga en paquete Nube, editor WYSIWYG de descripción, validación de categoría ML, links a ML y tooltip de rutas de imágenes.

**Architecture:** Backend separa la lectura de estado en 4 endpoints por canal (DTOs por canal) y suma el conteo de imágenes al `EstadoCanalDTO`. Frontend reemplaza el flag único `cargandoEstado` por 4 flags y carga cada tarjeta independiente; el resto son cambios de presentación.

**Tech Stack:** Spring Boot 4 / Java 25 / JUnit + Mockito + AssertJ (backend); Next.js / React / TypeScript / Tailwind (frontend).

## Global Constraints

- Tests backend offline: `mvn -o test` desde `supermaster-backend/`. Frontend: `npx tsc --noEmit` (+ `npm run build` para cambios de JSX grandes) desde `supermaster-frontend/`.
- Records DTO con constructor posicional: agregar componentes rompe `new XDTO(...)`; compilar con `mvn -o test`.
- Commits: `git add` SOLO rutas explícitas; NUNCA `git add -A`/`.` (hay `application.properties`/`application-dev.properties` sin commitear que NO deben entrar).
- Permisos: lectura `Permisos.PRODUCTOS_VER` (estado) / `INTEGRACIONES_VER`; escritura `INTEGRACIONES_EDITAR`.
- Endpoint de estado actual: `GET /api/productos/{id}/estado-publicacion` (a reemplazar por 4 por canal); el `PUT` (aplicar) se mantiene intacto.
- El helper `indicadorCarga(texto)` ya existe en `ProductoFormModal.tsx` (creado en sesión previa).
- Colores por canal: Dux índigo, KT HOGAR azul, KT GASTRO esmeralda, Mercado Libre amarillo.

---

## Task 1: Cantidad de imágenes por canal en `EstadoCanalDTO` (backend)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/EstadoCanalDTO.java`
- Modify: `.../estado/MlEstadoParser.java`, `.../estado/NubeEstadoParser.java`
- Test: `.../estado/MlEstadoParserTest.java`, `.../estado/NubeEstadoParserTest.java`

**Interfaces:**
- Produces: `EstadoCanalDTO` con nuevo componente final `Integer imagenes`. Helpers `noPublicado()`/`ofError()` lo pasan `null`.

- [ ] **Step 1: Tests que fallan**

En `MlEstadoParserTest.java` agregar:
```java
    @Test
    void parse_cuentaImagenesDePictures() {
        JsonNode item = json("""
            {"status":"active","available_quantity":5,
             "pictures":[{"id":"1"},{"id":"2"},{"id":"3"}]}
            """);
        assertThat(MlEstadoParser.parse(item).imagenes()).isEqualTo(3);
    }
```
En `NubeEstadoParserTest.java` agregar:
```java
    @Test
    void parse_cuentaImagenesDeImages() {
        JsonNode product = json("""
            {"published":true,"variants":[{"price":"10","stock":2}],
             "images":[{"id":1},{"id":2}]}
            """);
        assertThat(NubeEstadoParser.parse(product).imagenes()).isEqualTo(2);
    }
```

- [ ] **Step 2: Correr y verificar que fallan**

Run: `mvn -o test -Dtest=MlEstadoParserTest,NubeEstadoParserTest`
Expected: FAIL — `imagenes()` no existe.

- [ ] **Step 3: Implementar**

En `EstadoCanalDTO.java` agregar `Integer imagenes` como ÚLTIMO componente del record y actualizar `noPublicado()`/`ofError()` para pasar `null` al final:
```java
public record EstadoCanalDTO(boolean publicado, String estado, BigDecimal precio, BigDecimal promo,
        Integer stock, String peso, String dimensiones, boolean error, Integer imagenes) {
    public static EstadoCanalDTO noPublicado() { return new EstadoCanalDTO(false, null, null, null, null, null, null, false, null); }
    public static EstadoCanalDTO ofError() { return new EstadoCanalDTO(false, null, null, null, null, null, null, true, null); }
}
```
En `MlEstadoParser.parse`, calcular y pasar el conteo:
```java
        int imagenes = item.path("pictures").size();
        return new EstadoCanalDTO(true, status, precio, null, stock, peso, dims, false, imagenes);
```
En `NubeEstadoParser.parse`:
```java
        int imagenes = product.path("images").size();
        return new EstadoCanalDTO(true, published ? "visible" : "oculta", precio, promo, stock, peso, dims, false, imagenes);
```
`DuxEstadoParser` pasa `null` (agregar el arg final `, null` a su `new EstadoCanalDTO(...)`).

- [ ] **Step 4: Compilar + suite**

Run: `mvn -o test` (el cambio de record rompe otros `new EstadoCanalDTO(...)` — corregir cada uno agregando el arg final). Expected: verde.

- [ ] **Step 5: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/EstadoCanalDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlEstadoParser.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/NubeEstadoParser.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/DuxEstadoParser.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlEstadoParserTest.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/NubeEstadoParserTest.java
git commit -m "feat(canales): cuenta imagenes por canal en EstadoCanalDTO (ML pictures, Nube images)"
```

---

## Task 2: Backend — lectura de estado por canal (4 endpoints + DTOs por canal)

**Files:**
- Create: `.../estado/dto/MlCanalDTO.java`, `.../estado/dto/NubeCanalDTO.java`, `.../estado/dto/DuxCanalDTO.java`
- Modify: `.../estado/EstadoPublicacionService.java`, `.../estado/EstadoPublicacionController.java`
- Delete: `.../estado/dto/EstadoPublicacionDTO.java`, `.../estado/dto/DatosCanalDTO.java`
- Modify: `.../estado/EstadoPublicacionServiceTest.java`

**Interfaces:**
- Consumes: `MlDatosParser` (categoryId/atributos/paquete), `MlEstadoParser`, `NubeSeoParser`, `NubeEstadoParser`, `DuxEstadoParser`.
- Produces:
  - `MlCanalDTO(EstadoCanalDTO estado, String categoryId, String categoryNombre, List<MlAtributoDTO> atributos, String descripcion, String mlaResuelto, Double mlPaqAlto, Double mlPaqAncho, Double mlPaqLargo, Double mlPaqPeso)`
  - `NubeCanalDTO(EstadoCanalDTO estado, String descripcion, SeoCanalDTO seo, String titulo, String peso, String profundidad, String ancho, String alto)`
  - `DuxCanalDTO(EstadoCanalDTO estado)`
  - `EstadoPublicacionService.leerMl(Integer id)`, `leerNube(Integer id, String store)`, `leerDux(Integer id)` (cada uno: findById→sku→lee el canal→DTO; nunca lanza por canal caído).
  - Endpoints `GET /api/productos/{id}/estado-publicacion/{ml|hogar|gastro|dux}`.

> Abrí `EstadoPublicacionService.java` para reusar la lógica actual de `leerMlPanel`/`leerNubePanel`/`estadoDux` (mover su contenido a los métodos públicos por canal). El `aplicar(...)` (PUT) y los records privados `MlPanel`/`NubePanel` se reemplazan por los DTOs públicos.

- [ ] **Step 1: Crear los 3 DTOs por canal**

Crear `MlCanalDTO.java`, `NubeCanalDTO.java`, `DuxCanalDTO.java` en `.../estado/dto/` con los records de arriba (imports: `MlAtributoDTO`, `SeoCanalDTO`, `EstadoCanalDTO`).

- [ ] **Step 2: Reescribir el service — métodos por canal**

En `EstadoPublicacionService.java`:
- Agregar `leerMl(Integer id)`: `Producto p = repo.findById(id)...; String sku = p.getSku();` → reusar la lógica de `leerMlPanel(sku)` para armar `MlCanalDTO` (estado vía `MlEstadoParser.parse`, categoría/atributos/descripción/paquete vía `MlDatosParser`, mlaResuelto). Mantener el try/catch que nunca lanza (estado `ofError` ante fallo).
- Agregar `leerNube(Integer id, String store)`: findById→sku→`leerNubePanel(sku, store)` adaptado a `NubeCanalDTO` (estado, descripción, seo, titulo `name.es`, dims).
- Agregar `leerDux(Integer id)`: findById→sku→`DuxEstadoParser.parse` → `DuxCanalDTO`.
- Eliminar `leer(Integer id)` monolítico, los records `MlPanel`/`NubePanel`, y el `CompletableFuture`/pool si ya no se usan (el paralelismo ahora lo hace el frontend con 4 requests).
- `@Transactional(readOnly = true)` en cada método.

- [ ] **Step 3: Controller — 4 endpoints**

En `EstadoPublicacionController.java`, reemplazar el `@GetMapping` único por:
```java
    @GetMapping("/ml")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<MlCanalDTO> ml(@PathVariable @Positive Integer id) { return ResponseEntity.ok(service.leerMl(id)); }

    @GetMapping("/hogar")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<NubeCanalDTO> hogar(@PathVariable @Positive Integer id) { return ResponseEntity.ok(service.leerNube(id, TiendaNubeService.STORE_HOGAR)); }

    @GetMapping("/gastro")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<NubeCanalDTO> gastro(@PathVariable @Positive Integer id) { return ResponseEntity.ok(service.leerNube(id, TiendaNubeService.STORE_GASTRO)); }

    @GetMapping("/dux")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<DuxCanalDTO> dux(@PathVariable @Positive Integer id) { return ResponseEntity.ok(service.leerDux(id)); }
```
Mantener el `@PutMapping` (aplicar) sin cambios. Borrar el import de `EstadoPublicacionDTO`.

- [ ] **Step 4: Borrar DTOs monolíticos + actualizar test**

Borrar `EstadoPublicacionDTO.java` y `DatosCanalDTO.java`. Reescribir `EstadoPublicacionServiceTest.java` para los métodos por canal (mockear `findById` + los services ML/Nube/Dux; afirmar que `leerMl` devuelve el `MlCanalDTO` con categoría/paquete y `ofError` si el canal lanza). Buscar otros usos de los DTOs borrados y actualizarlos.

- [ ] **Step 5: Compilar + suite**

Run: `mvn -o test` → verde (distinguir fallos ambientales de MySQL "Public Key Retrieval" de reales).

- [ ] **Step 6: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/
git commit -m "feat(canales): lectura de estado por canal (4 endpoints + DTOs por canal)"
```

---

## Task 3: Frontend — services y tipos por canal

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`

**Interfaces:**
- Consumes: endpoints `GET .../estado-publicacion/{canal}`.
- Produces: tipos `MlCanal`, `NubeCanal`, `DuxCanal` (espejo de los DTOs backend) y `getEstadoMlAPI(id)`, `getEstadoHogarAPI(id)`, `getEstadoGastroAPI(id)`, `getEstadoDuxAPI(id)`. El `EstadoCanal` suma `imagenes: number | null`.

- [ ] **Step 1: Tipos + funciones**

En `productosService.ts`:
- Al tipo `EstadoCanal` agregar `imagenes: number | null;`.
- Agregar:
```ts
export type MlCanal = { estado: EstadoCanal; categoryId: string | null; categoryNombre: string | null; atributos: ProductoMlAtributo[]; descripcion: string | null; mlaResuelto: string | null; mlPaqAlto: number | null; mlPaqAncho: number | null; mlPaqLargo: number | null; mlPaqPeso: number | null };
export type NubeCanal = { estado: EstadoCanal; descripcion: string | null; seo: SeoCanal | null; titulo: string | null; peso: string | null; profundidad: string | null; ancho: string | null; alto: string | null };
export type DuxCanal = { estado: EstadoCanal };

async function getEstadoCanal<T>(id: number, canal: string): Promise<T> {
	const r = await fetchAPI(`${API_BASE_URL}/api/productos/${id}/estado-publicacion/${canal}`);
	if (!r.ok) throw new Error(await extraerMensajeError(r, "No se pudo leer el estado del canal"));
	return r.json();
}
export const getEstadoMlAPI = (id: number) => getEstadoCanal<MlCanal>(id, "ml");
export const getEstadoHogarAPI = (id: number) => getEstadoCanal<NubeCanal>(id, "hogar");
export const getEstadoGastroAPI = (id: number) => getEstadoCanal<NubeCanal>(id, "gastro");
export const getEstadoDuxAPI = (id: number) => getEstadoCanal<DuxCanal>(id, "dux");
```
- Eliminar `getEstadoPublicacionAPI`, `DatosCanal`, `EstadoPublicacion` (ya no se usan tras Task 4). Mantener `putEstadoPublicacionAPI` (aplicar).

- [ ] **Step 2: tsc**

Run: `npx tsc --noEmit` — habrá errores en `ProductoFormModal.tsx` (usa los tipos viejos); se resuelven en Task 4. Si querés commits verdes, hacé Task 3 y 4 juntas. **Este plan asume Task 3 + Task 4 se commitean juntas** (un solo commit al final de Task 4) para no dejar el build roto.

- [ ] **Step 3:** (sin commit propio — ver Task 4)

---

## Task 4: Frontend — carga progresiva en el modal (4 flags) + indicador paquete Nube

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `getEstadoMlAPI/getEstadoHogarAPI/getEstadoGastroAPI/getEstadoDuxAPI`, tipos `MlCanal`/`NubeCanal`/`DuxCanal` (Task 3).

- [ ] **Step 1: Estados de canal y flags**

Reemplazar `const [cargandoEstado, setCargandoEstado] = useState(false)` y `estadoCanales` por:
```tsx
    const [cargandoMl, setCargandoMl] = useState(false);
    const [cargandoHogar, setCargandoHogar] = useState(false);
    const [cargandoGastro, setCargandoGastro] = useState(false);
    const [cargandoDux, setCargandoDux] = useState(false);
    const [estadoMl, setEstadoMl] = useState<MlCanal | null>(null);
    const [estadoHogar, setEstadoHogar] = useState<NubeCanal | null>(null);
    const [estadoGastro, setEstadoGastro] = useState<NubeCanal | null>(null);
    const [estadoDux, setEstadoDux] = useState<DuxCanal | null>(null);
```
(Conservar `estadoOriginal` para el diff de `aplicar` si lo usa el PUT — derivar de los 4, o leer `estado` de cada uno.)

- [ ] **Step 2: Carga progresiva en el efecto de edición**

En el efecto que corre al abrir en edición, reemplazar la única llamada por 4 independientes (cada una baja su flag y respeta `cancelled`):
```tsx
            setCargandoMl(true); setCargandoHogar(true); setCargandoGastro(true); setCargandoDux(true);
            getEstadoMlAPI(producto.id).then(e => { if (cancelled) return; setEstadoMl(e);
                if (e.categoryId) { setMlCategoryId(e.categoryId); setMlCategoryNombre(e.categoryNombre); }
                if (e.atributos.length) { const m: Record<string, ProductoMlAtributo> = {}; for (const a of e.atributos) m[a.attributeId] = a; setMlAtributosVal(m); }
                setDescripcionMl(e.descripcion ?? "");
                setMlaResuelto(e.mlaResuelto ?? null);
                if (e.mlPaqAlto != null) setMlPaqAlto(e.mlPaqAlto);
                if (e.mlPaqAncho != null) setMlPaqAncho(e.mlPaqAncho);
                if (e.mlPaqLargo != null) setMlPaqLargo(e.mlPaqLargo);
                if (e.mlPaqPeso != null) setMlPaqPeso(e.mlPaqPeso);
            }).catch(err => { if (!cancelled && !esSesionExpirada(err)) notificar.error("No se pudo leer Mercado Libre"); })
              .finally(() => { if (!cancelled) setCargandoMl(false); });
            getEstadoHogarAPI(producto.id).then(e => { if (cancelled) return; setEstadoHogar(e);
                setDescripcionHogar(e.descripcion ?? "");
                if (e.seo) setSeoHogar({ title: e.seo.title ?? "", description: e.seo.description ?? "", tags: e.seo.tags ?? "" });
                if (e.titulo != null) setTituloNube(e.titulo);
                if (e.peso != null) setNubePeso(e.peso); if (e.profundidad != null) setNubeProfundidad(e.profundidad);
                if (e.ancho != null) setNubeAncho(e.ancho); if (e.alto != null) setNubeAlto(e.alto);
            }).catch(err => { if (!cancelled && !esSesionExpirada(err)) notificar.error("No se pudo leer KT HOGAR"); })
              .finally(() => { if (!cancelled) setCargandoHogar(false); });
            getEstadoGastroAPI(producto.id).then(e => { if (cancelled) return; setEstadoGastro(e);
                setDescripcionGastro(e.descripcion ?? "");
                if (e.seo) setSeoGastro({ title: e.seo.title ?? "", description: e.seo.description ?? "", tags: e.seo.tags ?? "" });
                // título/dims Nube ya los toma HOGAR (compartidos); si HOGAR no publica, fallback:
                if (e.titulo != null) setTituloNube(t => t || e.titulo!);
            }).catch(err => { if (!cancelled && !esSesionExpirada(err)) notificar.error("No se pudo leer KT GASTRO"); })
              .finally(() => { if (!cancelled) setCargandoGastro(false); });
            getEstadoDuxAPI(producto.id).then(e => { if (cancelled) return; setEstadoDux(e); })
              .catch(err => { if (!cancelled && !esSesionExpirada(err)) notificar.error("No se pudo leer Dux"); })
              .finally(() => { if (!cancelled) setCargandoDux(false); });
```
> Nota: el título/dimensiones Nube eran compartidos HOGAR→GASTRO en el backend monolítico; ahora HOGAR los setea y GASTRO solo como fallback (`t => t || ...`). Ajustar si el comportamiento deseado difiere.

- [ ] **Step 3: Re-mapear indicadores y referencias a `cargandoEstado`**

Buscar TODOS los usos de `cargandoEstado` y `estadoCanales` y reemplazar por el flag/estado del canal correspondiente:
- Categoría, ficha técnica, paquete ML, descripción ML → `cargandoMl`.
- Título Nube, SEO (renderSeoNube), descripción Nube HOGAR → `cargandoHogar`; descripción/SEO GASTRO → `cargandoGastro`.
- **Paquete de envío Nube (#3):** envolver su grilla con `{cargandoHogar ? indicadorCarga("Cargando datos del canal…") : (...)}` (hoy no tiene indicador).
- `renderEstadoBody`: recibe ahora el flag del canal (agregar parámetro `cargando: boolean`) y usa ese en vez de `cargandoEstado` (línea "Leyendo estado…"). Cada tarjeta pasa su flag (`cargandoDux`/`cargandoHogar`/`cargandoGastro`/`cargandoMl`) y su `estadoX.estado`.

- [ ] **Step 4: tsc + build**

Run: `npx tsc --noEmit` && `npm run build` desde `supermaster-frontend/` → 0 errores, build OK.

- [ ] **Step 5: Commit (Tasks 3 + 4 juntas)**
```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(canales): carga progresiva por canal (4 flags) + indicador paquete Nube"
```

---

## Task 5: Frontend — colores y cantidad de imágenes por tarjeta de canal

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `EstadoCanal.imagenes` (Task 1/3), flags por canal (Task 4).

- [ ] **Step 1: Tinte por canal**

Cerca de `canalCardClassName`, definir:
```tsx
    const CANAL_TINT = {
        dux:    "!border-indigo-200 !bg-indigo-50/60 dark:!border-indigo-900/40 dark:!bg-indigo-950/20",
        hogar:  "!border-blue-200 !bg-blue-50/60 dark:!border-blue-900/40 dark:!bg-blue-950/20",
        gastro: "!border-emerald-200 !bg-emerald-50/60 dark:!border-emerald-900/40 dark:!bg-emerald-950/20",
        ml:     "!border-yellow-200 !bg-yellow-50/70 dark:!border-yellow-900/40 dark:!bg-yellow-950/20",
    } as const;
```
Aplicar a cada tarjeta: el `<div className={canalCardClassName}>` de Dux/HOGAR/GASTRO/ML pasa a `` className={`${canalCardClassName} ${CANAL_TINT.dux}`} `` (y `.hogar`/`.gastro`/`.ml` respectivamente).

- [ ] **Step 2: Cantidad de imágenes en `renderEstadoBody`**

Tras el bloque de `canal.stock` (dentro de la grilla), agregar:
```tsx
                      {canal.imagenes != null && (
                          <div className="flex justify-between gap-1">
                              <span className="text-slate-400">Imágenes</span>
                              <span className="font-medium text-slate-600 dark:text-slate-300">{canal.imagenes}</span>
                          </div>
                      )}
```

- [ ] **Step 3: tsc + build** → 0 errores.

- [ ] **Step 4: Commit**
```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(canales): color de fondo por canal + cantidad de imagenes en la tarjeta"
```

---

## Task 6: Frontend — editor WYSIWYG (`HtmlEditor`)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/HtmlEditor.tsx`

**Interfaces:**
- Produces: mismo contrato (`value`, `onChange`, `disabled`, `placeholder`, `rows`, `id`); internamente `contentEditable`.

- [ ] **Step 1: Reescribir el componente**

Reemplazar el textarea+preview por un `<div contentEditable>` controlado. Toolbar con `document.execCommand`. Sincronización sin pisar el cursor:
```tsx
"use client";
import React, { useRef, useEffect } from "react";

type Props = { value: string; onChange: (v: string) => void; disabled?: boolean; placeholder?: string; rows?: number; id?: string };

export default function HtmlEditor({ value, onChange, disabled, placeholder, rows = 8, id }: Props) {
    const ref = useRef<HTMLDivElement>(null);
    // Solo re-escribir el HTML cuando el valor externo difiere y el editor NO tiene foco (no pisar cursor al tipear).
    useEffect(() => {
        const el = ref.current;
        if (el && document.activeElement !== el && el.innerHTML !== value) el.innerHTML = value || "";
    }, [value]);

    const exec = (cmd: string, arg?: string) => { ref.current?.focus(); document.execCommand(cmd, false, arg); onChange(ref.current?.innerHTML ?? ""); };
    const btn = "rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200";

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-wrap items-center gap-1.5">
                <button type="button" className={btn} disabled={disabled} onClick={() => exec("bold")} title="Negrita"><b>B</b></button>
                <button type="button" className={btn} disabled={disabled} onClick={() => exec("italic")} title="Cursiva"><i>I</i></button>
                <button type="button" className={btn} disabled={disabled} onClick={() => exec("underline")} title="Subrayado"><u>U</u></button>
                <button type="button" className={btn} disabled={disabled} onClick={() => exec("insertUnorderedList")} title="Lista">• Lista</button>
                <label className="flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400" title="Color de texto">
                    Color
                    <input type="color" disabled={disabled} defaultValue="#1e40af"
                        className="h-6 w-7 cursor-pointer rounded border border-slate-300 disabled:opacity-50 dark:border-slate-600"
                        onChange={e => exec("foreColor", e.target.value)} />
                </label>
            </div>
            <div
                ref={ref}
                id={id}
                contentEditable={!disabled}
                suppressContentEditableWarning
                onInput={() => onChange(ref.current?.innerHTML ?? "")}
                data-placeholder={placeholder}
                className="prose prose-sm max-w-none overflow-auto rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 empty:before:text-slate-400 empty:before:content-[attr(data-placeholder)] dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100"
                style={{ minHeight: `${rows * 1.5}rem` }}
            />
        </div>
    );
}
```

- [ ] **Step 2: tsc + build** → 0 errores. (Los 3 usos en el modal no cambian: misma API.)

- [ ] **Step 3: Smoke manual**

En el modal, descripción Nube: editar sobre la vista previa; B/I/U/Lista/Color aplican; al guardar el HTML resultante es el `innerHTML`. Verificar que tipear no resetea el cursor.

- [ ] **Step 4: Commit**
```bash
git add supermaster-frontend/src/app/productos/HtmlEditor.tsx
git commit -m "feat(modal): editor WYSIWYG de descripcion (contentEditable, sin deps)"
```

---

## Task 7: Frontend — validación de categoría ML solo-lectura si publicado

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `estadoMl?.estado?.publicado` / `mlaResuelto` (Task 4).

- [ ] **Step 1: Render condicional de la categoría**

En la sección de categoría ML, derivar `const mlPublicado = !!(estadoMl?.estado?.publicado || mlaResuelto);`. Envolver el bloque de categoría editable (botón "Predecir categorías", chip con la "x" de quitar, predicciones, leyenda) de modo que si `mlPublicado`:
- Se muestre la categoría como **texto solo-lectura** (`mlCategoryNombre`), sin botón Predecir ni quitar ni elegir.
- Debajo, un aviso ámbar: "Mercado Libre no permite cambiar la categoría de una publicación existente. Para cambiarla hay que republicar (se elimina la publicación con sus visitas y ventas)."
- Si no `mlPublicado`, comportamiento actual.

```tsx
{mlPublicado ? (
    <div className="mt-2 flex flex-col gap-1">
        <span className="inline-block rounded-lg border border-indigo-300 bg-white px-2 py-1 text-xs font-medium text-indigo-800 dark:border-indigo-600 dark:bg-slate-800 dark:text-indigo-200">
            {mlCategoryNombre ? pathConHojaResaltada(mlCategoryNombre) : (mlCategoryId || "—")}
        </span>
        <span className="text-xs text-amber-600 dark:text-amber-400">Mercado Libre no permite cambiar la categoría de una publicación existente. Para cambiarla hay que republicar (se elimina la publicación con sus visitas y ventas).</span>
    </div>
) : (
    /* ...bloque editable actual (Predecir / chip / predicciones / leyenda)... */
)}
```

- [ ] **Step 2: tsc + build** → 0 errores.

- [ ] **Step 3: Commit**
```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(ml): categoria solo-lectura con aviso si la publicacion ya existe"
```

---

## Task 8: Frontend — links a ML (modal + tabla MLAs)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`, `supermaster-frontend/src/app/mlas/columns.tsx`

**Interfaces:**
- Consumes: `mlaResuelto` (modal), el código MLA de la fila (tabla MLAs — abrir `mlas/types.ts` para el nombre exacto del campo del código, p. ej. `mla`).

- [ ] **Step 1: Helpers de URL**

En `productosService.ts` (o un util compartido) agregar:
```ts
// "MLA1234" -> "https://articulo.mercadolibre.com.ar/MLA-1234" (ML redirige al artículo)
export function mlVerURL(codigo: string): string {
	const conGuion = codigo.replace(/^(MLAU?|MLA)(\d)/, "$1-$2");
	return `https://articulo.mercadolibre.com.ar/${conGuion}`;
}
export function mlEditarURL(codigo: string): string {
	return `https://www.mercadolibre.com.ar/publicaciones/${codigo}/modificar`;
}
```

- [ ] **Step 2: Link "Editar en ML" en el modal (#8)**

Junto al MLA resuelto en el modal (donde se muestra `mlaResuelto`), si hay código:
```tsx
{mlaResuelto && (
    <a href={mlEditarURL(mlaResuelto)} target="_blank" rel="noreferrer" className="text-xs text-blue-600 hover:underline dark:text-blue-400">Editar en ML ↗</a>
)}
```

- [ ] **Step 3: Botón "Ver" en la tabla MLAs (#7)**

En `mlas/columns.tsx`, agregar una columna de acción que renderice un link `mlVerURL(row.mla)` (`target="_blank"`). Seguir el patrón de columnas existente del archivo (abrirlo para ver cómo se define una celda custom).

- [ ] **Step 4: tsc + build** → 0 errores.

- [ ] **Step 5: Smoke manual**

El link "Editar en ML" abre el panel de modificación; el "Ver" de la tabla abre el artículo público. *(Si el redirect del "Ver" no resuelve, evaluar exponer `permalink` desde backend — fuera de este plan.)*

- [ ] **Step 6: Commit**
```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx supermaster-frontend/src/app/mlas/columns.tsx
git commit -m "feat(ml): links ver (tabla MLAs) y editar (modal) la publicacion en ML"
```

---

## Task 9: Frontend — tooltip de rutas de imágenes

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `crudasDisp` (del selector de carátula, tiene `crudaDir.ruta`/`destinoDir.ruta`).

- [ ] **Step 1: Extender el tooltip de "Imágenes (por SKU)"**

En el encabezado "Imágenes (por SKU)" (junto al ícono de info ya presente), extender el contenido del `Tooltip` para indicar dónde se guardan:
- "Imágenes publicadas: carpeta de imágenes (`{crudasDisp?.destinoDir.ruta ?? 'app.imagenes-dir'}`)."
- "Imágenes crudas: carpeta de entrada (`{crudasDisp?.crudaDir.ruta ?? 'app.imagenes-raw-dir'}`)."
Si `crudasDisp` aún no se cargó (no se abrió el selector), mostrar la descripción genérica de las carpetas.

- [ ] **Step 2: tsc + build** → 0 errores.

- [ ] **Step 3: Commit**
```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(modal): tooltip con rutas de imagenes normales y crudas"
```

---

## Self-Review

- **Cobertura del spec:** #4 carga progresiva (T2+T3+T4); #3 indicador paquete Nube (T4 step 3); #6 cantidad imágenes (T1+T5); #1 colores (T5); #2 editor WYSIWYG (T6); #9 categoría solo-lectura (T7); #7/#8 links ML (T8); #5 tooltip rutas (T9). ✔
- **Placeholders:** los pasos de frontend que dicen "bloque editable actual" referencian código existente concreto; el código nuevo está completo.
- **Consistencia de tipos:** `EstadoCanal.imagenes` (T1 backend ↔ T3 frontend); `MlCanal`/`NubeCanal`/`DuxCanal` (T2 ↔ T3) consumidos en T4; flags `cargandoMl/Hogar/Gastro/Dux` definidos en T4 y usados en T4/T5.
- **Orden:** el refactor de carga (T2-T4) va antes de T5 (que depende de los flags y de `EstadoCanal.imagenes`); T6-T9 son independientes.
- **Riesgo:** T2/T4 es el refactor grande (elimina DTOs monolíticos del Tema B y reescribe la carga del modal) — la verificación es `mvn -o test` + `tsc`/`build`; el smoke visual lo hace el usuario.
