# Carga completa de datos de canales al editar (ML + Nube) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Al abrir un producto para editar, pre-cargar desde la publicación real las dimensiones del paquete de ML y el título de Tienda Nube, y mostrar el indicador "Cargando…" en los campos de ML (categoría, atributos, paquete) y de Nube (título, SEO) mientras se leen los canales.

**Architecture:** Backend extiende el parser de ML para extraer `SELLER_PACKAGE_*` y `EstadoPublicacionService`/`DatosCanalDTO` para devolver paquete ML y título Nube. Frontend pre-carga esos campos (el canal manda, fallback al producto) y replica el indicador de carga existente en los campos que llegan async.

**Tech Stack:** Java 25 / Spring Boot 4 / JUnit 5 + Mockito + AssertJ (backend); Next.js / React / TypeScript (frontend).

## Global Constraints

- Tests backend offline: `mvn -o test` desde `supermaster-backend/`.
- Records DTO con constructor posicional: agregar componentes a `DatosCanalDTO` rompe `new DatosCanalDTO(...)` en `EstadoPublicacionService` y en tests; compilar con `mvn -o test`.
- **El canal manda con fallback al producto (solo pre-carga):** en el frontend, setear el campo desde `e.datos` **solo si no es null**; si el canal no trae el dato, queda el valor del producto.
- **No se cambia la persistencia ni el guardado:** `tituloNube`/`mlPaq*` se siguen guardando en BD y propagando al canal (ya funciona: `actualizarItemEnMlCore` manda las dimensiones). Este plan solo toca **lectura/pre-carga + indicadores**.
- Unidades del paquete ML: `SELLER_PACKAGE_HEIGHT/WIDTH/LENGTH` en **cm** (directo a Alto/Ancho/Largo); `SELLER_PACKAGE_WEIGHT` en **gramos** → input Peso en **kg** (`gramos / 1000`).
- El indicador de carga reusa el patrón existente: `{cargandoEstado ? <indicador/> : <campo/>}` (ver [ProductoFormModal.tsx:2349-2351](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L2349-L2351)).

---

## Task 1: `MlDatosParser.paquete()` — dimensiones del paquete ML (backend)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlDatosParser.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlDatosParserTest.java`

**Interfaces:**
- Produces: `MlDatosParser.PaqueteMl` (record `Double altoCm, anchoCm, largoCm, pesoKg`); `MlDatosParser.paquete(JsonNode item) : PaqueteMl` (campos null si el atributo no está o no se puede parsear). `atributos(...)` sigue omitiendo los `SELLER_PACKAGE_*`.

- [ ] **Step 1: Escribir el test que falla**

Agregar a `MlDatosParserTest.java`:

```java
    @Test
    void paquete_leeDesdeValueStruct_pesoEnKg() {
        JsonNode item = parse("""
            {
              "attributes": [
                {"id":"SELLER_PACKAGE_HEIGHT","value_name":"21 cm","value_struct":{"number":21,"unit":"cm"}},
                {"id":"SELLER_PACKAGE_WIDTH","value_name":"10 cm","value_struct":{"number":10,"unit":"cm"}},
                {"id":"SELLER_PACKAGE_LENGTH","value_name":"10 cm","value_struct":{"number":10,"unit":"cm"}},
                {"id":"SELLER_PACKAGE_WEIGHT","value_name":"500 g","value_struct":{"number":500,"unit":"g"}}
              ]
            }
            """);
        MlDatosParser.PaqueteMl p = MlDatosParser.paquete(item);
        assertThat(p.altoCm()).isEqualTo(21.0);
        assertThat(p.anchoCm()).isEqualTo(10.0);
        assertThat(p.largoCm()).isEqualTo(10.0);
        assertThat(p.pesoKg()).isEqualTo(0.5); // 500 g → 0.5 kg
    }

    @Test
    void paquete_fallbackAValueName_siNoHayStruct() {
        JsonNode item = parse("""
            {"attributes":[{"id":"SELLER_PACKAGE_HEIGHT","value_name":"15 cm"}]}
            """);
        MlDatosParser.PaqueteMl p = MlDatosParser.paquete(item);
        assertThat(p.altoCm()).isEqualTo(15.0);
        assertThat(p.pesoKg()).isNull();
    }

    @Test
    void paquete_sinAtributos_todoNull() {
        MlDatosParser.PaqueteMl p = MlDatosParser.paquete(parse("{\"attributes\":[]}"));
        assertThat(p.altoCm()).isNull();
        assertThat(p.pesoKg()).isNull();
    }
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `mvn -o test -Dtest=MlDatosParserTest`
Expected: FAIL — `paquete`/`PaqueteMl` no existen.

- [ ] **Step 3: Implementar**

En `MlDatosParser.java`, agregar:

```java
    /** Dimensiones del paquete de envío leídas de ML. cm para alto/ancho/largo; kg para peso. */
    public record PaqueteMl(Double altoCm, Double anchoCm, Double largoCm, Double pesoKg) {}

    public static PaqueteMl paquete(JsonNode item) {
        Double alto = null, ancho = null, largo = null, pesoKg = null;
        if (item != null) {
            for (JsonNode a : item.path("attributes")) {
                String id = a.path("id").asString(null);
                if (id == null) continue;
                Double num = numeroDeAtributo(a);
                switch (id) {
                    case "SELLER_PACKAGE_HEIGHT" -> alto = num;
                    case "SELLER_PACKAGE_WIDTH" -> ancho = num;
                    case "SELLER_PACKAGE_LENGTH" -> largo = num;
                    case "SELLER_PACKAGE_WEIGHT" -> pesoKg = (num != null) ? num / 1000.0 : null; // g → kg
                    default -> { /* no es del paquete */ }
                }
            }
        }
        return new PaqueteMl(alto, ancho, largo, pesoKg);
    }

    /** Número del atributo: primero value_struct.number; fallback al número inicial de value_name. */
    private static Double numeroDeAtributo(JsonNode a) {
        JsonNode struct = a.path("value_struct");
        if (struct.isObject() && struct.path("number").isNumber()) {
            return struct.path("number").asDouble();
        }
        String vn = a.path("value_name").asString(null);
        if (vn != null) {
            var m = java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(vn);
            if (m.find()) return Double.parseDouble(m.group(1));
        }
        return null;
    }
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `mvn -o test -Dtest=MlDatosParserTest`
Expected: PASS (incluidos los tests viejos: `atributos` sigue omitiendo `SELLER_PACKAGE_*` porque están en `OMITIR`).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlDatosParser.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlDatosParserTest.java
git commit -m "feat(ml): MlDatosParser.paquete extrae dimensiones del paquete (cm/kg)"
```

---

## Task 2: `DatosCanalDTO` + `EstadoPublicacionService` (paquete ML + título Nube)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/DatosCanalDTO.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionServiceTest.java` (ajustar si rompe por el constructor)

**Interfaces:**
- Consumes: `MlDatosParser.paquete` (Task 1).
- Produces: `DatosCanalDTO` con `Double mlPaqAlto, mlPaqAncho, mlPaqLargo, mlPaqPeso` y `String nubeTitulo`. `MlPanel` con `PaqueteMl paquete`. `NubePanel` con `String titulo`.

- [ ] **Step 1: Ampliar el DTO**

En `DatosCanalDTO.java`, agregar componentes al final del record (mantener orden documentado):

```java
public record DatosCanalDTO(
        String mlCategoryId,
        String mlCategoryNombre,
        List<MlAtributoDTO> mlAtributos,
        String descripcionMl,
        String descripcionHogar,
        String descripcionGastro,
        SeoCanalDTO seoHogar,
        SeoCanalDTO seoGastro,
        String mlaResuelto,
        String nubePeso,
        String nubeProfundidad,
        String nubeAncho,
        String nubeAlto,
        /** Título de la publicación de Nube (name.es); null si no publicado. */
        String nubeTitulo,
        /** Dimensiones del paquete de ML leídas de la publicación (cm; peso en kg); null si no hay. */
        Double mlPaqAlto,
        Double mlPaqAncho,
        Double mlPaqLargo,
        Double mlPaqPeso
) {
}
```

- [ ] **Step 2: Ampliar `MlPanel`, `NubePanel` y la construcción del DTO**

En `EstadoPublicacionService.java`:

1. `MlPanel` suma el paquete:

```java
    private record MlPanel(EstadoCanalDTO estado, String categoryId, String categoryNombre,
                           List<MlAtributoDTO> atributos, String descripcion, String mlaResuelto,
                           MlDatosParser.PaqueteMl paquete) {}
```

2. En `leerMlPanel`, leer el paquete y pasarlo (y ajustar los `return` de error a paquete vacío):

```java
            MlDatosParser.PaqueteMl paquete = MlDatosParser.paquete(item);
            return new MlPanel(MlEstadoParser.parse(item), catId, catNombre,
                    MlDatosParser.atributos(item), descMl, mlaCode, paquete);
```

Para los 3 `return` de error/no-publicado de `leerMlPanel`, agregar el último argumento `new MlDatosParser.PaqueteMl(null, null, null, null)`.

3. `NubePanel` suma el título:

```java
    private record NubePanel(EstadoCanalDTO estado, String descripcion, SeoCanalDTO seo,
                             String peso, String profundidad, String ancho, String alto, String titulo) {}
```

4. En `leerNubePanel`, leer el nombre y pasarlo (confirmar el path `name.es` contra el JSON real de Nube; es análogo a `description.es`):

```java
        String titulo = (product != null) ? product.path("name").path("es").asString(null) : null;
        return new NubePanel(estadoNube(product), descripcionNube(product), NubeSeoParser.parse(product),
                peso, prof, ancho, alto, titulo);
```

Y el `return` de error de `leerNubePanel` agrega `, null` al final (título null).

5. Construir el `DatosCanalDTO` con los nuevos campos (al final):

```java
        DatosCanalDTO datos = new DatosCanalDTO(
                ml.categoryId(),
                ml.categoryNombre(),
                ml.atributos(),
                ml.descripcion(),
                hogar.descripcion(),
                gastro.descripcion(),
                hogar.seo(),
                gastro.seo(),
                ml.mlaResuelto(),
                hogar.peso() != null ? hogar.peso() : gastro.peso(),
                hogar.profundidad() != null ? hogar.profundidad() : gastro.profundidad(),
                hogar.ancho() != null ? hogar.ancho() : gastro.ancho(),
                hogar.alto() != null ? hogar.alto() : gastro.alto(),
                hogar.titulo() != null ? hogar.titulo() : gastro.titulo(),
                ml.paquete().altoCm(),
                ml.paquete().anchoCm(),
                ml.paquete().largoCm(),
                ml.paquete().pesoKg());
```

- [ ] **Step 3: Compilar y correr la suite (ajustar el test si rompe)**

Run: `mvn -o test -Dtest=EstadoPublicacionServiceTest`
Expected: si el test construye `DatosCanalDTO` o verifica posiciones, falla a compilar/aserción. Ajustarlo: agregar los nuevos argumentos en cualquier `new DatosCanalDTO(...)` del test, y si simula un item ML con `SELLER_PACKAGE_*` agregar aserciones del paquete. Si el test no toca esos campos, solo recompila.

Luego: `mvn -o test` (suite completa) — verde.

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/DatosCanalDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionServiceTest.java
git commit -m "feat(canales): DatosCanalDTO incluye paquete ML y titulo Nube"
```

---

## Task 3: Frontend — tipos y pre-carga de paquete ML + título Nube

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `DatosCanal` ampliado.
- Produces: pre-carga de `tituloNube` y `mlPaq*` desde `e.datos` (solo si no son null).

- [ ] **Step 1: Ampliar el tipo `DatosCanal`**

En `productosService.ts`, en `type DatosCanal`, agregar tras `nubeAlto`:

```ts
	nubeAlto: string | null;
	nubeTitulo: string | null;
	mlPaqAlto: number | null;
	mlPaqAncho: number | null;
	mlPaqLargo: number | null;
	mlPaqPeso: number | null;
};
```

- [ ] **Step 2: Pre-cargar en el efecto de carga del modal**

En `ProductoFormModal.tsx`, dentro del `.then(e => { ... })` de `getEstadoPublicacionAPI` (junto a los otros `if (e.datos.X != null) setX(...)`, ~líneas 704-716), agregar:

```tsx
                    if (e.datos.nubeTitulo != null) setTituloNube(e.datos.nubeTitulo);
                    if (e.datos.mlPaqAlto  != null) setMlPaqAlto(e.datos.mlPaqAlto);
                    if (e.datos.mlPaqAncho != null) setMlPaqAncho(e.datos.mlPaqAncho);
                    if (e.datos.mlPaqLargo != null) setMlPaqLargo(e.datos.mlPaqLargo);
                    if (e.datos.mlPaqPeso  != null) setMlPaqPeso(e.datos.mlPaqPeso);
```

> No tocar la carga inicial desde el producto (`setTituloNube(producto.tituloNube ?? "")` línea 612 y `setMlPaq*` líneas 643-646): esos quedan como fallback; el efecto async solo pisa si el canal trae el dato.

- [ ] **Step 3: Verificar build**

Run: `npm run build` desde `supermaster-frontend/`
Expected: sin errores.

- [ ] **Step 4: Smoke manual**

Abrir un producto publicado en ML: los campos Alto/Ancho/Largo/Peso del "Paquete para envío" se llenan con los de la publicación (peso en kg). Un producto no publicado en ML conserva los valores del producto.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(canales): pre-carga paquete ML y titulo Nube desde el canal al editar"
```

---

## Task 4: Frontend — indicador de carga en campos de ML

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `cargandoEstado` (estado existente).

> Definir una vez un fragmento reutilizable del indicador. Cerca de los helpers de render, agregar:
>
> ```tsx
>     const indicadorCargaCanal = (
>         <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-4 text-xs text-slate-400 dark:border-slate-700 dark:bg-slate-800/60"><SpinnerIcon /> Cargando datos de ML…</div>
>     );
> ```

- [ ] **Step 1: Indicador en Categoría**

En el bloque de categoría (~líneas 2121-2150), envolver el contenido de chips/predicciones/leyenda con el indicador mientras carga. Reemplazar el `<div className="mt-2 flex flex-col gap-2"> ... </div>` por:

```tsx
                                <div className="mt-2 flex flex-col gap-2">
                                    {cargandoEstado ? indicadorCargaCanal : (<>
                                        {/* ...contenido actual del bloque: botón Predecir, chip categoría, predicciones, leyenda... */}
                                    </>)}
                                </div>
```

> Mover el contenido existente (el `<div className="flex items-center gap-2">` con el botón "Predecir categorías" y el chip, el bloque `prediccionesMl.length > 0`, y el `formErrors.mlCategory ? ... : ...`) dentro del fragmento `(<>...</>)`.

- [ ] **Step 2: Indicador en Atributos (ficha técnica)**

Localizar el bloque donde se renderiza la ficha técnica de atributos de ML (entre la categoría y el "Paquete para envío", usa `mlFicha` / `mlAtributosDef`). Envolver su contenido:

```tsx
{cargandoEstado ? indicadorCargaCanal : (
    /* ...render actual de la ficha técnica... */
)}
```

- [ ] **Step 3: Indicador en Paquete de envío**

En la subsección "Paquete para envío" (~líneas 2271-2305), envolver la grilla de inputs:

```tsx
                            {cargandoEstado ? indicadorCargaCanal : (
                                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                                    {/* ...los 4 labels Alto/Ancho/Largo/Peso actuales... */}
                                </div>
                            )}
```

- [ ] **Step 4: Verificar build**

Run: `npm run build`
Expected: sin errores.

- [ ] **Step 5: Smoke manual**

Abrir un producto con publicación ML: categoría, ficha técnica y paquete muestran "Cargando datos de ML…" hasta que terminan de cargar, luego aparecen los datos.

- [ ] **Step 6: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(canales): indicador de carga en categoria, atributos y paquete de ML"
```

---

## Task 5: Frontend — indicador de carga en campos de Nube (título + SEO)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `cargandoEstado`; `renderSeoNube` ([líneas 1613-1644](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L1613-L1644)).

> Definir el indicador con el texto de Nube (mismo estilo que la descripción). Cerca de `indicadorCargaCanal`, agregar:
>
> ```tsx
>     const indicadorCargaNube = (
>         <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-4 text-xs text-slate-400 dark:border-slate-700 dark:bg-slate-800/60"><SpinnerIcon /> Cargando datos del canal…</div>
>     );
> ```

- [ ] **Step 1: Indicador en los 3 campos SEO (renderSeoNube)**

En `renderSeoNube`, envolver el `<div className="grid grid-cols-1 gap-3"> ... </div>` (los tres labels SEO Title/Description/Tags) con el indicador mientras carga:

```tsx
            {cargandoEstado ? indicadorCargaNube : (
                <div className="grid grid-cols-1 gap-3">
                    {/* ...los 3 labels SEO Title / Description / Tags actuales... */}
                </div>
            )}
```

> El botón "Generar SEO con IA" queda visible (fuera del `cargandoEstado`); solo los inputs muestran el indicador. Aplica a HOGAR y GASTRO porque `renderSeoNube` es compartido.

- [ ] **Step 2: Indicador en el input Título Nube**

Hay dos inputs de Título Nube (uno en la sección KT HOGAR ~línea 2334, otro en KT GASTRO ~línea 2394). En cada uno, mostrar el indicador mientras carga en lugar del input:

```tsx
                                {cargandoEstado ? indicadorCargaNube : (
                                    <input type="text" className={`${inputBaseClassName} ${formErrors.tituloNube ? inputErrorClassName : ""}`} value={tituloNube} onChange={e => { setTituloNube(e.target.value); if (formErrors.tituloNube) setFormErrors(p => ({ ...p, tituloNube: "" })); }} placeholder="Título para Tienda Nube" />
                                )}
```

> Mantener el `{formErrors.tituloNube && <p>...}` debajo, fuera del condicional.

- [ ] **Step 3: Verificar build**

Run: `npm run build`
Expected: sin errores.

- [ ] **Step 4: Smoke manual**

Abrir un producto publicado en Nube (KT GASTRO): el Título Nube y los campos SEO muestran "Cargando datos del canal…" hasta que llegan, y luego se completan desde la publicación. Confirmar que el SEO ya **no aparece vacío durante la carga**.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(canales): indicador de carga en titulo y SEO de Tienda Nube"
```

---

## Self-Review

- **Cobertura del spec B:** B2 paquete desde ML (T1 parser + T2 DTO/service + T3 pre-carga); B1 indicador ML en categoría/atributos/paquete (T4); B3 título Nube desde canal (T2 backend + T3 pre-carga) + indicador en título/SEO Nube (T5). ✔
- **Placeholders:** los pasos de frontend que dicen "mover el contenido actual" referencian bloques existentes concretos con números de línea; el código nuevo (wrappers, indicadores) está completo. Sin TBD.
- **Consistencia de tipos:** `DatosCanal` (front) refleja `DatosCanalDTO` (back) — `nubeTitulo: string|null`, `mlPaq*: number|null` ↔ `String nubeTitulo`, `Double mlPaq*`. `PaqueteMl` ↔ campos del DTO.
- **Verificaciones marcadas:** confirmar el path `name.es` del título Nube contra el JSON real de Tienda Nube (Task 2 step 2.4); ajustar `EstadoPublicacionServiceTest` si el constructor ampliado lo rompe (Task 2 step 3).
- **Sin trabajo de guardado:** confirmado que ML ya actualiza el paquete al editar; este plan no toca el guardado.
