# Selector de categoría de Mercado Libre — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el usuario pueda elegir la categoría de Mercado Libre entre las top-3 predicciones del predictor (desde el form del producto) y que esa elección se persista y se use al publicar; si no eligió, cae al predictor automático.

**Architecture:** Backend expone el predictor con `limit=3` por un GET nuevo; se persisten `ml_category_id`/`ml_category_nombre` en `productos`; el alta de ML usa la categoría guardada si existe, sino el predictor (la lambda que ya recibe `crearItemEnMlCore`). Frontend agrega un botón "Predecir categorías" + dropdown junto al Título ML.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven; Jackson 3 (`tools.jackson`); MapStruct; JUnit 5 + AssertJ; Next.js/React/TS.

## Global Constraints

- Trabajar directo en `main` (sin ramas).
- Maven OFFLINE desde `supermaster-backend/`: `./mvnw -o ...`.
- **NO ejecutar nada que llame a las APIs reales de ML/Dux/Nube** — solo tests unitarios offline (lambdas/JSON mock). No crear ni modificar publicaciones reales.
- Jackson 3 (`tools.jackson`): usar `asString` (no `asText`).
- `ddl-auto=validate`: los cambios de schema requieren script SQL manual en `src/main/resources/db/` y aplicarlo a la BD antes de arrancar.
- **Records DTO:** agregar un componente rompe los constructores posicionales en TESTS. `./mvnw -o compile` (solo src/main) NO lo detecta; hay que correr `./mvnw -o test` (test-compile). El único test que construye `ProductoCreateDTO`/`ProductoUpdateDTO` posicionalmente es `RecalculoAutomaticoIntegrationTest`.
- MapStruct auto-mapea por nombre (GlobalMapperConfig `unmappedTargetPolicy = IGNORE`): campos homónimos entre DTO y entity se mapean solos.
- `category_id` de ML son strings tipo `"MLA1055"` (~10 chars).
- Commits terminan EXACTAMENTE con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: Backend — predictor top-3 + endpoint

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/PrediccionCategoriaMlDTO.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (nuevo `parsePredicciones` + `predecirCategorias`; reimplementar `predecirCategoria`)
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/controller/MercadoLibreController.java` (GET `/predecir-categorias`)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/PredecirCategoriasMlTest.java` (nuevo)

**Interfaces:**
- Produces:
  - `record PrediccionCategoriaMlDTO(String categoryId, String categoryName)`
  - `static List<PrediccionCategoriaMlDTO> MercadoLibreService.parsePredicciones(JsonNode arr)` — núcleo testeable.
  - `public List<PrediccionCategoriaMlDTO> MercadoLibreService.predecirCategorias(String titulo, int limit)` — usa red.
  - `GET /api/ml/predecir-categorias?titulo=...` → `List<PrediccionCategoriaMlDTO>`.

- [ ] **Step 1: Crear el DTO**

Crear `PrediccionCategoriaMlDTO.java`:

```java
package ar.com.leo.super_master_backend.apis.ml.dto;

/** Una predicción de categoría de ML (del predictor domain_discovery). */
public record PrediccionCategoriaMlDTO(String categoryId, String categoryName) {
}
```

- [ ] **Step 2: Escribir el test del parseo (falla)**

Crear `PredecirCategoriasMlTest.java`:

```java
package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.dto.PrediccionCategoriaMlDTO;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PredecirCategoriasMlTest {

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode tree(String json) {
        return om.readTree(json);
    }

    @Test
    void parsePredicciones_mapeaIdYNombre() {
        JsonNode arr = tree("[{\"category_id\":\"MLA1055\",\"category_name\":\"Celulares y Smartphones\"},"
                + "{\"category_id\":\"MLA1000\",\"category_name\":\"Electrónica\"}]");
        List<PrediccionCategoriaMlDTO> preds = MercadoLibreService.parsePredicciones(arr);
        assertThat(preds).hasSize(2);
        assertThat(preds.get(0).categoryId()).isEqualTo("MLA1055");
        assertThat(preds.get(0).categoryName()).isEqualTo("Celulares y Smartphones");
        assertThat(preds.get(1).categoryId()).isEqualTo("MLA1000");
    }

    @Test
    void parsePredicciones_arrayVacio_listaVacia() {
        assertThat(MercadoLibreService.parsePredicciones(tree("[]"))).isEmpty();
    }

    @Test
    void parsePredicciones_salteaSinId() {
        JsonNode arr = tree("[{\"category_name\":\"Sin id\"},{\"category_id\":\"MLA1\",\"category_name\":\"Ok\"}]");
        List<PrediccionCategoriaMlDTO> preds = MercadoLibreService.parsePredicciones(arr);
        assertThat(preds).hasSize(1);
        assertThat(preds.get(0).categoryId()).isEqualTo("MLA1");
    }
}
```

- [ ] **Step 3: Correr el test para verificar que falla**

Run: `./mvnw -o test -Dtest=PredecirCategoriasMlTest`
Expected: FAIL de compilación — `parsePredicciones` no existe.

- [ ] **Step 4: Implementar `parsePredicciones` + `predecirCategorias` y reimplementar `predecirCategoria`**

En `MercadoLibreService.java`, reemplazar el método `predecirCategoria` actual:

```java
    /** Predictor de categoría a partir del título. Devuelve el category_id de mayor probabilidad (o null). */
    private String predecirCategoria(String titulo) {
        try {
            String uri = "/sites/MLA/domain_discovery/search?limit=1&q="
                    + URLEncoder.encode(titulo, StandardCharsets.UTF_8);
            String resp = retryHandler.get(uri, () -> tokens.accessToken);
            JsonNode arr = objectMapper.readTree(resp);
            if (arr.isArray() && !arr.isEmpty()) {
                String cat = arr.get(0).path("category_id").asString("");
                return cat.isBlank() ? null : cat;
            }
            return null;
        } catch (Exception e) {
            log.warn("ML - Falló predecir categoría para '{}': {}", titulo, e.getMessage());
            return null;
        }
    }
```

por:

```java
    /** Parsea la respuesta del predictor (domain_discovery) a la lista de categorías predichas. Testeable sin red. */
    public static List<PrediccionCategoriaMlDTO> parsePredicciones(JsonNode arr) {
        List<PrediccionCategoriaMlDTO> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String id = n.path("category_id").asString("");
                if (!id.isBlank()) {
                    out.add(new PrediccionCategoriaMlDTO(id, n.path("category_name").asString("")));
                }
            }
        }
        return out;
    }

    /** Predictor de categorías de ML: top-N predicciones a partir del título. Lista vacía si falla. */
    public List<PrediccionCategoriaMlDTO> predecirCategorias(String titulo, int limit) {
        verificarTokens();
        try {
            String uri = "/sites/MLA/domain_discovery/search?limit=" + limit + "&q="
                    + URLEncoder.encode(titulo, StandardCharsets.UTF_8);
            String resp = retryHandler.get(uri, () -> tokens.accessToken);
            return parsePredicciones(objectMapper.readTree(resp));
        } catch (Exception e) {
            log.warn("ML - Falló predecir categorías para '{}': {}", titulo, e.getMessage());
            return List.of();
        }
    }

    /** Categoría de mayor probabilidad (o null) — fallback automático del alta. */
    private String predecirCategoria(String titulo) {
        List<PrediccionCategoriaMlDTO> preds = predecirCategorias(titulo, 1);
        return preds.isEmpty() ? null : preds.get(0).categoryId();
    }
```

Agregar el import: `import ar.com.leo.super_master_backend.apis.ml.dto.PrediccionCategoriaMlDTO;` (las clases `List`, `ArrayList`, `JsonNode`, `URLEncoder`, `StandardCharsets` ya están importadas).

- [ ] **Step 5: Correr el test para verificar que pasa**

Run: `./mvnw -o test -Dtest=PredecirCategoriasMlTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Agregar el endpoint en el controller**

En `MercadoLibreController.java`, agregar el import `import ar.com.leo.super_master_backend.apis.ml.dto.PrediccionCategoriaMlDTO;` y `import java.util.List;`, y dentro de la clase (p. ej. después de `actualizarConfiguracion`):

```java
    /**
     * Predice las top-3 categorías de Mercado Libre para un título dado.
     * @param titulo título del producto (Título ML)
     * @return lista de predicciones {categoryId, categoryName} (vacía si no hay sugerencias)
     */
    @GetMapping("/predecir-categorias")
    @PreAuthorize(Permisos.MLAS_VER)
    public ResponseEntity<?> predecirCategorias(@RequestParam String titulo) {
        if (titulo == null || titulo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El título es obligatorio", "path", "/api/ml/predecir-categorias"));
        }
        List<PrediccionCategoriaMlDTO> predicciones = mercadoLibreService.predecirCategorias(titulo, 3);
        return ResponseEntity.ok(predicciones);
    }
```

- [ ] **Step 7: Compilar y correr el test**

Run: `./mvnw -o test -Dtest=PredecirCategoriasMlTest`
Expected: PASS (compila el controller + 3 tests verdes).

- [ ] **Step 8: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/PrediccionCategoriaMlDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/controller/MercadoLibreController.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/PredecirCategoriasMlTest.java
git commit -m "feat(ml): endpoint predecir-categorias (top-3) + parseo testeable"
```

---

### Task 2: Backend — persistencia de la categoría en `productos`

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java` (2 campos, tras `tituloNube`)
- Create: `supermaster-backend/src/main/resources/db/ml-categoria.sql`
- Modify: `ProductoDTO.java`, `ProductoCreateDTO.java`, `ProductoUpdateDTO.java`, `ProductoPatchDTO.java`
- Modify (test): `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/calculo/service/RecalculoAutomaticoIntegrationTest.java`

**Interfaces:**
- Produces: `Producto.getMlCategoryId()/setMlCategoryId(String)`, `getMlCategoryNombre()/setMlCategoryNombre(String)`; campos `mlCategoryId`/`mlCategoryNombre` en los 4 DTOs.

**Nota:** esta tarea no agrega lógica nueva testeable por unidad; el "test" es que la suite compila y pasa tras alinear los DTOs. El mapper MapStruct auto-mapea los campos homónimos.

- [ ] **Step 1: Agregar los campos a la entidad**

En `Producto.java`, después del bloque de `tituloNube` (línea ~59), agregar:

```java
    @Size(max = 20)
    @Column(name = "ml_category_id", length = 20)
    private String mlCategoryId;

    @Size(max = 255)
    @Column(name = "ml_category_nombre", length = 255)
    private String mlCategoryNombre;
```

- [ ] **Step 2: Crear el script SQL**

Crear `src/main/resources/db/ml-categoria.sql`:

```sql
-- Categoría de Mercado Libre elegida para el producto (predictor): se manda en el alta del item.
-- ml_category_id es el ID que va a ML (ej. "MLA1055"); ml_category_nombre es el nombre denormalizado
-- (solo para mostrar en la UI sin re-llamar al predictor).
ALTER TABLE supermaster.productos
    ADD COLUMN ml_category_id VARCHAR(20) NULL,
    ADD COLUMN ml_category_nombre VARCHAR(255) NULL;
```

- [ ] **Step 3: Agregar los campos a los DTOs**

En `ProductoDTO.java`, agregar al FINAL del record (después de `BigDecimal margenMayorista`, antes del `) {`), precedido de coma:

```java
,

        // Categoría de Mercado Libre (predictor)
        String mlCategoryId,
        String mlCategoryNombre
```

En `ProductoCreateDTO.java`, agregar al FINAL (después de `Tag tag`), precedido de coma:

```java
,

        @Size(max = 20, message = "La categoría ML no puede exceder 20 caracteres")
        String mlCategoryId,
        @Size(max = 255, message = "El nombre de categoría ML no puede exceder 255 caracteres")
        String mlCategoryNombre
```

En `ProductoUpdateDTO.java`, agregar al FINAL (después de `Tag tag`), precedido de coma — mismo bloque que en CreateDTO:

```java
,

        @Size(max = 20, message = "La categoría ML no puede exceder 20 caracteres")
        String mlCategoryId,
        @Size(max = 255, message = "El nombre de categoría ML no puede exceder 255 caracteres")
        String mlCategoryNombre
```

En `ProductoPatchDTO.java`, agregar después de `private JsonNullable<Tag> tag = JsonNullable.undefined();`:

```java
    private JsonNullable<String> mlCategoryId = JsonNullable.undefined();
    private JsonNullable<String> mlCategoryNombre = JsonNullable.undefined();
```

- [ ] **Step 4: Correr la suite para detectar los constructores posicionales rotos**

Run: `./mvnw -o test -Dtest=RecalculoAutomaticoIntegrationTest`
Expected: FAIL de compilación — `RecalculoAutomaticoIntegrationTest` construye `ProductoCreateDTO`/`ProductoUpdateDTO` con los argumentos viejos (faltan 2).

- [ ] **Step 5: Arreglar las construcciones posicionales en el test**

En `RecalculoAutomaticoIntegrationTest.java`, en cada `new ProductoCreateDTO(...)` y `new ProductoUpdateDTO(...)`, agregar `, null, null` al final de la lista de argumentos (los dos nuevos componentes `mlCategoryId`, `mlCategoryNombre`). Buscar todas las ocurrencias con `grep -n "new ProductoCreateDTO\|new ProductoUpdateDTO"` y agregarles los dos `null` finales antes del `)`.

- [ ] **Step 6: Correr la suite afectada (offline, sin BD si no está la columna)**

Run: `./mvnw -o test-compile`
Expected: BUILD SUCCESS (compila test + main; el mapper MapStruct auto-mapea `mlCategoryId`/`mlCategoryNombre` por nombre).

> Nota: los tests de integración que tocan la BD real fallarán hasta aplicar `ml-categoria.sql` (validate). Eso es esperado y lo aplica el usuario; este step solo verifica compilación.

- [ ] **Step 7: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java supermaster-backend/src/main/resources/db/ml-categoria.sql supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ProductoDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ProductoCreateDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ProductoUpdateDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ProductoPatchDTO.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/calculo/service/RecalculoAutomaticoIntegrationTest.java
git commit -m "feat(producto): persistir ml_category_id/nombre (categoria ML elegida)"
```

---

### Task 3: Backend — usar la categoría guardada en el alta de ML

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (nuevo `resolverCategoriaMl` + la lambda de categoría en `crearItemEnMl`)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/PredecirCategoriasMlTest.java` (agregar tests de `resolverCategoriaMl`)

**Interfaces:**
- Consumes: `Producto.getMlCategoryId()` (Task 2); `predecirCategoria(titulo)` (Task 1).
- Produces: `static String MercadoLibreService.resolverCategoriaMl(String categoriaGuardada, String tituloMl, Function<String,String> autoPredictor)`.

- [ ] **Step 1: Escribir los tests de `resolverCategoriaMl` (fallan)**

Agregar a `PredecirCategoriasMlTest.java`:

```java
    @Test
    void resolverCategoriaMl_usaLaGuardadaSiExiste() {
        String cat = MercadoLibreService.resolverCategoriaMl("MLA999", "un título", t -> "AUTO");
        assertThat(cat).isEqualTo("MLA999");
    }

    @Test
    void resolverCategoriaMl_caeAlPredictorSiNoHayGuardada() {
        assertThat(MercadoLibreService.resolverCategoriaMl(null, "un título", t -> "AUTO")).isEqualTo("AUTO");
        assertThat(MercadoLibreService.resolverCategoriaMl("", "un título", t -> "AUTO")).isEqualTo("AUTO");
        assertThat(MercadoLibreService.resolverCategoriaMl("   ", "un título", t -> "AUTO")).isEqualTo("AUTO");
    }
```

Agregar el import `import java.util.function.Function;` si no está.

- [ ] **Step 2: Correr para verificar que falla**

Run: `./mvnw -o test -Dtest=PredecirCategoriasMlTest`
Expected: FAIL de compilación — `resolverCategoriaMl` no existe.

- [ ] **Step 3: Implementar `resolverCategoriaMl` y usarlo en el alta**

En `MercadoLibreService.java`, agregar el método (cerca de `predecirCategoria`):

```java
    /** Categoría a usar en el alta de ML: la guardada en el producto si existe, sino la del predictor automático. */
    static String resolverCategoriaMl(String categoriaGuardada, String tituloMl, Function<String, String> autoPredictor) {
        if (categoriaGuardada != null && !categoriaGuardada.isBlank()) return categoriaGuardada;
        return autoPredictor.apply(tituloMl);
    }
```

En `crearItemEnMl`, cambiar la lambda de categoría. Reemplazar:

```java
                titulo -> predecirCategoria(titulo),
```

por:

```java
                titulo -> resolverCategoriaMl(producto.getMlCategoryId(), titulo, this::predecirCategoria),
```

- [ ] **Step 4: Correr el test para verificar que pasa**

Run: `./mvnw -o test -Dtest=PredecirCategoriasMlTest`
Expected: PASS (5 tests: 3 de parseo + 2 de resolverCategoriaMl).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/PredecirCategoriasMlTest.java
git commit -m "feat(ml): el alta usa la categoria guardada del producto o el predictor"
```

---

### Task 4: Frontend — selector de categoría ML en el form

**Files:**
- Modify: `supermaster-frontend/src/app/productos/types.ts` (2 campos)
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` (`predecirCategoriasMlAPI`)
- Modify: `supermaster-frontend/src/app/productos/page.tsx` (estado + botón/dropdown + payload create/edit + preload + reset)

**Interfaces:**
- Consumes: `GET /api/ml/predecir-categorias?titulo=...` (Task 1); campos `mlCategoryId`/`mlCategoryNombre` del producto (Task 2).

- [ ] **Step 1: Tipos**

En `types.ts`, agregar a los tipos del producto (donde está `tituloMl`), en `ProductoDTO` y `ProductoCreateDTO`:

```ts
    mlCategoryId: string | null;
    mlCategoryNombre: string | null;
```

- [ ] **Step 2: Service**

En `productosService.ts`, agregar (siguiendo el patrón de `exportarProductosAMlAPI`, con `fetchAPI`/`API_BASE_URL`/`extraerMensajeError` ya importados):

```ts
export type PrediccionCategoriaMl = { categoryId: string; categoryName: string };

export const predecirCategoriasMlAPI = async (titulo: string): Promise<PrediccionCategoriaMl[]> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/ml/predecir-categorias?titulo=${encodeURIComponent(titulo)}`);
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudieron predecir categorías"));
	return await res.json();
};
```

- [ ] **Step 3: Estado en el form**

En `page.tsx`, junto a `const [tituloMl, setTituloMl] = useState("");` (línea ~216), agregar:

```tsx
    const [mlCategoryId, setMlCategoryId] = useState<string | null>(null);
    const [mlCategoryNombre, setMlCategoryNombre] = useState<string | null>(null);
    const [prediccionesMl, setPrediccionesMl] = useState<PrediccionCategoriaMl[]>([]);
    const [cargandoPrediccionesMl, setCargandoPrediccionesMl] = useState(false);
```

Importar `predecirCategoriasMlAPI` y el tipo `PrediccionCategoriaMl` desde `./productosService`.

- [ ] **Step 4: Handler de predicción**

En `page.tsx`, agregar un handler (cerca de los otros del form):

```tsx
    const handlePredecirCategoriasMl = async () => {
        if (!tituloMl.trim()) return;
        setCargandoPrediccionesMl(true);
        try {
            const preds = await predecirCategoriasMlAPI(tituloMl.trim());
            setPrediccionesMl(preds);
            if (preds.length === 0) notificar.info("Sin sugerencias de categoría para ese título");
        } catch (e) {
            notificar.error(e instanceof Error ? e.message : "No se pudieron predecir categorías");
        } finally {
            setCargandoPrediccionesMl(false);
        }
    };
```

- [ ] **Step 5: UI bajo el input Título ML**

En `page.tsx`, justo después del `<input ... placeholder="Título para Mercado Libre" />` (línea ~1263) y dentro del mismo `<label>`/contenedor, agregar:

```tsx
                                <div className="mt-2 flex flex-col gap-2">
                                    <div className="flex items-center gap-2">
                                        <Button variant="light" type="button" onClick={handlePredecirCategoriasMl} disabled={!tituloMl.trim() || cargandoPrediccionesMl}>
                                            {cargandoPrediccionesMl ? "Prediciendo..." : "Predecir categorías"}
                                        </Button>
                                        {mlCategoryId && (
                                            <span className="inline-flex items-center gap-1 rounded-full border border-yellow-300 bg-yellow-50 px-2 py-0.5 text-xs font-medium text-yellow-800 dark:border-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300">
                                                {mlCategoryNombre || mlCategoryId}
                                                <button type="button" onClick={() => { setMlCategoryId(null); setMlCategoryNombre(null); }} className="leading-none hover:text-red-500" aria-label="Quitar categoría">×</button>
                                            </span>
                                        )}
                                    </div>
                                    {prediccionesMl.length > 0 && (
                                        <div className="flex flex-wrap gap-1.5">
                                            {prediccionesMl.map(p => (
                                                <button
                                                    key={p.categoryId}
                                                    type="button"
                                                    onClick={() => { setMlCategoryId(p.categoryId); setMlCategoryNombre(p.categoryName); setPrediccionesMl([]); }}
                                                    className={`rounded-lg border px-2 py-1 text-xs transition-colors ${mlCategoryId === p.categoryId ? "border-yellow-400 bg-yellow-100 text-yellow-900" : "border-slate-300 hover:bg-slate-100 dark:border-slate-600 dark:hover:bg-slate-700"}`}
                                                >
                                                    {p.categoryName} <span className="text-slate-400">({p.categoryId})</span>
                                                </button>
                                            ))}
                                        </div>
                                    )}
                                    <p className="text-xs text-slate-500 dark:text-slate-400">Si no elegís una, al publicar se usa la categoría que el predictor considere más probable.</p>
                                </div>
```

- [ ] **Step 6: Incluir en el payload de create y edit**

En `handleCreate` (payload línea ~570) y en `handleGuardarEdicion` (payload línea ~702), agregar al objeto del payload:

```tsx
                mlCategoryId: mlCategoryId, mlCategoryNombre: mlCategoryNombre,
```

- [ ] **Step 7: Preload al editar y reset**

En `abrirEdicion` (línea ~633, junto a `setTituloMl(producto.tituloMl ?? "")`), agregar:

```tsx
        setMlCategoryId(producto.mlCategoryId ?? null);
        setMlCategoryNombre(producto.mlCategoryNombre ?? null);
        setPrediccionesMl([]);
```

En `resetForm` (línea ~931), agregar:

```tsx
        setMlCategoryId(null); setMlCategoryNombre(null); setPrediccionesMl([]);
```

- [ ] **Step 8: Typecheck**

Run (desde `supermaster-frontend/`): `npx tsc --noEmit`
Expected: EXIT 0 (sin errores de tipo).

- [ ] **Step 9: Commit**

```bash
git add supermaster-frontend/src/app/productos/types.ts supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): selector de categoria ML (predictor top-3)"
```

---

## Verificación final (tras las 4 tareas)

- [ ] Backend: `./mvnw -o test -Dtest=PredecirCategoriasMlTest,ActualizarItemEnMlTest,CrearItemEnMlTest` → PASS.
- [ ] Frontend: `npx tsc --noEmit` → EXIT 0.
- [ ] **Pendiente del usuario:** aplicar `ml-categoria.sql` a la BD (las 2 columnas) antes de arrancar; smoke real: predecir categorías de un título, elegir una, publicar y verificar que el item queda en esa categoría.

## Notas de diseño
- `crearItemEnMlCore` no cambia su firma: la decisión "guardada vs predictor" vive en la lambda que le pasa `crearItemEnMl` (Task 3).
- No se toca `Mla` ni la categoría de publicaciones existentes (la API de ML no permite cambiarla).
- El mapper MapStruct auto-mapea los campos nuevos por nombre; no requiere edición.
