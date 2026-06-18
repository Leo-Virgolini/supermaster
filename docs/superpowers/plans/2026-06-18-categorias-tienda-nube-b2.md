# Categorías en el alta de Tienda Nube — Fase B2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Al dar de alta un producto en Tienda Nube (KT HOGAR/GASTRO), resolver y asignar la jerarquía de categorías derivada de su clasif (gral/gastro según la tienda) + tipo, creando en TN las categorías que falten.

**Architecture:** Tres unidades puras y testeables sin red — un árbol de categorías cacheado (`NubeCategoriaArbol`), un aplanador de jerarquía a nombres (`NubeCategoriaRuta`) y un resolver find-or-create (`NubeCategoriaResolver`) — más la integración en `TiendaNubeService` (carga del árbol vía `GET /categories`, creación vía `POST /categories`, y armado de la ruta + validación en el core del alta) y en `NubeExportService` (caché del árbol por tienda). El payload del producto suma el campo `categories`.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven; Jackson 3 (`tools.jackson.databind`); JUnit 5 + AssertJ.

## Global Constraints

- Jackson 3: importar de `tools.jackson.databind` (NO `com.fasterxml.jackson`). Sus excepciones son unchecked.
- Todo el código nuevo vive en el paquete `ar.com.leo.super_master_backend.apis.nube.*`.
- **Ninguna llamada real a Tienda Nube** en los tests: usar lambdas/estructuras en memoria. Los wrappers de red (`cargarArbolCategorias`, `crearCategoria`) no se testean unitariamente (consistente con `listarCategorias`).
- Match de nombres de categoría **case-insensitive** (`trim().toLowerCase(Locale.ROOT)`), consistente con el match de imágenes por SKU.
- KT HOGAR → `clasifGral`; KT GASTRO → `clasifGastro`. El tipo cuelga debajo de la hoja de la clasif. Ruta ordenada **raíz→hoja**.
- Clasif del canal **y** tipo obligatorios: si falta cualquiera → `ResultadoAltaNube.error("falta clasif/tipo para categorizar")`, sin postear.
- Sin cambios de schema de BD. Sin cambios de frontend.
- Maven offline: correr tests con el Maven cacheado (`mvn -o test ...`); el shell del controlador puede no tener Maven en PATH — delegar la corrida si hace falta.
- Commits terminan con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: `NubeCategoriaArbol` (árbol cacheado en memoria)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaArbol.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaArbolTest.java`

**Interfaces:**
- Produces: `class NubeCategoriaArbol` con `Long buscarHijo(Long parentId, String nombre)` (null si no existe) y `void registrar(Long id, Long parentId, String nombre)`. `parentId == null` representa la raíz. Match case-insensitive.

- [ ] **Step 1: Write the failing test**

`NubeCategoriaArbolTest.java`:
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NubeCategoriaArbolTest {

    @Test
    void buscarHijo_inexistente_devuelveNull() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        assertThat(a.buscarHijo(null, "Cocina")).isNull();
    }

    @Test
    void registrarYBuscar_caseInsensitiveYConTrim() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        a.registrar(10L, null, "Cocina");
        assertThat(a.buscarHijo(null, "cocina")).isEqualTo(10L);
        assertThat(a.buscarHijo(null, "  COCINA ")).isEqualTo(10L);
    }

    @Test
    void registrar_distinguePorPadre() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        a.registrar(10L, null, "Cocina");
        a.registrar(20L, 10L, "Ollas");
        assertThat(a.buscarHijo(10L, "Ollas")).isEqualTo(20L);
        assertThat(a.buscarHijo(null, "Ollas")).isNull(); // mismo nombre, otro padre
    }

    @Test
    void registrar_noPisaExistente() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        a.registrar(10L, null, "Cocina");
        a.registrar(11L, null, "Cocina");
        assertThat(a.buscarHijo(null, "Cocina")).isEqualTo(10L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o test -Dtest=NubeCategoriaArbolTest -pl supermaster-backend`
Expected: FAIL de compilación ("cannot find symbol: class NubeCategoriaArbol").

- [ ] **Step 3: Write minimal implementation**

`NubeCategoriaArbol.java`:
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Árbol de categorías de Tienda Nube cacheado en memoria: (padre, nombre) → id. Match case-insensitive. */
public class NubeCategoriaArbol {

    // parentId normalizado (0 = raíz) → (nombre normalizado → categoriaId)
    private final Map<Long, Map<String, Long>> porPadre = new HashMap<>();

    private static long clavePadre(Long parentId) { return parentId == null ? 0L : parentId; }

    private static String claveNombre(String nombre) {
        return nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT);
    }

    /** Id del hijo con ese nombre bajo ese padre, o null si no existe. */
    public Long buscarHijo(Long parentId, String nombre) {
        Map<String, Long> hijos = porPadre.get(clavePadre(parentId));
        return hijos == null ? null : hijos.get(claveNombre(nombre));
    }

    /** Registra una categoría. Idempotente: no pisa una ya registrada. */
    public void registrar(Long id, Long parentId, String nombre) {
        porPadre.computeIfAbsent(clavePadre(parentId), k -> new HashMap<>())
                .putIfAbsent(claveNombre(nombre), id);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -o test -Dtest=NubeCategoriaArbolTest -pl supermaster-backend`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaArbol.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaArbolTest.java
git commit -m "feat(nube): NubeCategoriaArbol cacheado (padre,nombre)->id case-insensitive

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `NubeCategoriaRuta` (aplanar jerarquía a nombres)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaRuta.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaRutaTest.java`

**Interfaces:**
- Produces: `final class NubeCategoriaRuta` con `static <T> List<String> aplanar(T hoja, Function<T,T> getPadre, Function<T,String> getNombre)` → nombres ordenados raíz→hoja. Las entidades `ClasifGral`/`ClasifGastro`/`Tipo` tienen `getPadre()` (de su mismo tipo) y `getNombre()`, y constructor sin args + setters (`@NoArgsConstructor`/`@Setter`), por lo que se construyen en memoria en los tests.

- [ ] **Step 1: Write the failing test**

`NubeCategoriaRutaTest.java`:
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NubeCategoriaRutaTest {

    @Test
    void unNivel_soloRaiz() {
        ClasifGral raiz = new ClasifGral();
        raiz.setNombre("Cocina");
        List<String> ruta = NubeCategoriaRuta.aplanar(raiz, ClasifGral::getPadre, ClasifGral::getNombre);
        assertThat(ruta).containsExactly("Cocina");
    }

    @Test
    void variosNiveles_ordenRaizAHoja() {
        ClasifGral raiz = new ClasifGral(); raiz.setNombre("Cocina");
        ClasifGral hijo = new ClasifGral(); hijo.setNombre("Ollas"); hijo.setPadre(raiz);
        ClasifGral nieto = new ClasifGral(); nieto.setNombre("Acero"); nieto.setPadre(hijo);
        List<String> ruta = NubeCategoriaRuta.aplanar(nieto, ClasifGral::getPadre, ClasifGral::getNombre);
        assertThat(ruta).containsExactly("Cocina", "Ollas", "Acero");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o test -Dtest=NubeCategoriaRutaTest -pl supermaster-backend`
Expected: FAIL de compilación ("cannot find symbol: class NubeCategoriaRuta").

- [ ] **Step 3: Write minimal implementation**

`NubeCategoriaRuta.java`:
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/** Aplana una jerarquía padre→hijos a la lista de nombres ordenada raíz→hoja. */
public final class NubeCategoriaRuta {

    private NubeCategoriaRuta() {}

    public static <T> List<String> aplanar(T hoja, Function<T, T> getPadre, Function<T, String> getNombre) {
        LinkedList<String> ruta = new LinkedList<>();
        for (T n = hoja; n != null; n = getPadre.apply(n)) {
            ruta.addFirst(getNombre.apply(n));
        }
        return ruta;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -o test -Dtest=NubeCategoriaRutaTest -pl supermaster-backend`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaRuta.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaRutaTest.java
git commit -m "feat(nube): NubeCategoriaRuta aplana jerarquia a nombres raiz->hoja

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `NubeCategoriaResolver` (find-or-create de la ruta)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaResolver.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaResolverTest.java`

**Interfaces:**
- Consumes: `NubeCategoriaArbol` (Task 1).
- Produces: `final class NubeCategoriaResolver` con `static List<Long> resolver(NubeCategoriaArbol arbol, List<String> rutaNombres, BiFunction<Long,String,Long> creador)`. `creador.apply(parentId, nombre)` devuelve el id de la categoría creada (en prod: `POST /categories`). Devuelve los ids de toda la ruta, raíz→hoja.

- [ ] **Step 1: Write the failing test**

`NubeCategoriaResolverTest.java`:
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class NubeCategoriaResolverTest {

    @Test
    void todosExisten_devuelveIdsYNoCrea() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        a.registrar(1L, null, "Cocina");
        a.registrar(2L, 1L, "Ollas");
        List<String> creadas = new ArrayList<>();
        BiFunction<Long, String, Long> creador = (p, n) -> { creadas.add(n); return 99L; };

        List<Long> ids = NubeCategoriaResolver.resolver(a, List.of("Cocina", "Ollas"), creador);

        assertThat(ids).containsExactly(1L, 2L);
        assertThat(creadas).isEmpty();
    }

    @Test
    void caseInsensitive_reusaSinCrear() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        a.registrar(1L, null, "Cocina");
        List<Long> ids = NubeCategoriaResolver.resolver(a, List.of("cocina"), (p, n) -> 50L);
        assertThat(ids).containsExactly(1L);
    }

    @Test
    void creaFaltantes_conParentCorrectoYAnidado() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        AtomicLong seq = new AtomicLong(100);
        List<String> trazas = new ArrayList<>();
        BiFunction<Long, String, Long> creador = (p, n) -> { trazas.add(p + ">" + n); return seq.incrementAndGet(); };

        List<Long> ids = NubeCategoriaResolver.resolver(a, List.of("Cocina", "Ollas", "Acero"), creador);

        assertThat(ids).containsExactly(101L, 102L, 103L);
        assertThat(trazas).containsExactly("null>Cocina", "101>Ollas", "102>Acero");
    }

    @Test
    void segundaResolucion_reusaLoCreadoEnLaPrimera() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        AtomicLong seq = new AtomicLong(0);
        BiFunction<Long, String, Long> creador = (p, n) -> seq.incrementAndGet();

        NubeCategoriaResolver.resolver(a, List.of("Cocina", "Ollas"), creador);
        List<Long> ids2 = NubeCategoriaResolver.resolver(a, List.of("Cocina", "Ollas", "Acero"), creador);

        assertThat(ids2).containsExactly(1L, 2L, 3L); // Cocina=1, Ollas=2 reusados; Acero=3 nuevo
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o test -Dtest=NubeCategoriaResolverTest -pl supermaster-backend`
Expected: FAIL de compilación ("cannot find symbol: class NubeCategoriaResolver").

- [ ] **Step 3: Write minimal implementation**

`NubeCategoriaResolver.java`:
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** Resuelve una ruta de nombres a ids de categoría de Tienda Nube, creando los niveles faltantes. */
public final class NubeCategoriaResolver {

    private NubeCategoriaResolver() {}

    /**
     * @param creador (parentId, nombre) → id de la categoría creada (POST /categories en prod).
     * @return ids de toda la ruta, en orden raíz→hoja.
     */
    public static List<Long> resolver(NubeCategoriaArbol arbol, List<String> rutaNombres,
                                      BiFunction<Long, String, Long> creador) {
        List<Long> ids = new ArrayList<>();
        Long parentId = null;
        for (String nombre : rutaNombres) {
            Long id = arbol.buscarHijo(parentId, nombre);
            if (id == null) {
                id = creador.apply(parentId, nombre);
                arbol.registrar(id, parentId, nombre);
            }
            ids.add(id);
            parentId = id;
        }
        return ids;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -o test -Dtest=NubeCategoriaResolverTest -pl supermaster-backend`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaResolver.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeCategoriaResolverTest.java
git commit -m "feat(nube): NubeCategoriaResolver find-or-create de la ruta de categorias

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: `NubeProductoPayloadBuilder` con `categories`

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilderTest.java`

**Interfaces:**
- Produces: nueva firma `static Map<String,Object> construir(Producto p, BigDecimal pvp, BigDecimal pvpInflado, List<Long> categoriaIds)`. Si `categoriaIds` es no nulo y no vacío → agrega `"categories": [ids]`; si es `null`/vacío → omite la clave. **Esta firma de 4 argumentos reemplaza la de 3** (los callers se actualizan en Task 6).

- [ ] **Step 1: Update existing tests + write the new failing tests**

En `NubeProductoPayloadBuilderTest.java`, los 3 tests existentes pasan a la firma de 4 args agregando `null` como `categoriaIds`. Reemplazar las 3 llamadas:
```java
// línea ~24:
Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), null, null);
// línea ~45:
Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), new BigDecimal("2000.00"), null);
// línea ~55:
Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), new BigDecimal("1500.00"), null);
```
Y agregar dos tests nuevos al final de la clase:
```java
    @Test
    @SuppressWarnings("unchecked")
    void conCategorias_incluyeArrayCategories() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(
                base(), new BigDecimal("1500.00"), null, List.of(1L, 2L, 3L));
        assertThat((List<Long>) payload.get("categories")).containsExactly(1L, 2L, 3L);
    }

    @Test
    void sinCategorias_noIncluyeClave() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(
                base(), new BigDecimal("1500.00"), null, null);
        assertThat(payload).doesNotContainKey("categories");
    }
```
(El import `java.util.List` ya está en el archivo de test.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -o test -Dtest=NubeProductoPayloadBuilderTest -pl supermaster-backend`
Expected: FAIL de compilación (la firma de 4 args no existe todavía).

- [ ] **Step 3: Update the implementation**

En `NubeProductoPayloadBuilder.java`, cambiar la firma e insertar el bloque de categorías antes del `return`:
```java
    public static Map<String, Object> construir(Producto p, BigDecimal pvp, BigDecimal pvpInflado, List<Long> categoriaIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", Map.of("es", p.getTituloNube() != null ? p.getTituloNube() : ""));
        payload.put("description", Map.of("es", NubeDescripcionBuilder.construir(p)));
        payload.put("published", false);
        payload.put("free_shipping", false);

        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("sku", p.getSku());
        if (pvpInflado != null && pvp != null && pvpInflado.compareTo(pvp) > 0) {
            variant.put("price", pvpInflado.toPlainString());
            variant.put("promotional_price", pvp.toPlainString());
        } else if (pvp != null) {
            variant.put("price", pvp.toPlainString());
        }
        if (p.getCosto() != null) variant.put("cost", p.getCosto().toPlainString());
        variant.put("weight", "0.050");
        variant.put("depth", "8.00");
        variant.put("width", "5.00");
        variant.put("height", "5.00");
        variant.put("stock", "");

        List<Map<String, Object>> variants = new ArrayList<>();
        variants.add(variant);
        payload.put("variants", variants);

        if (categoriaIds != null && !categoriaIds.isEmpty()) {
            payload.put("categories", new ArrayList<>(categoriaIds));
        }
        return payload;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -o test -Dtest=NubeProductoPayloadBuilderTest -pl supermaster-backend`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilderTest.java
git commit -m "feat(nube): payload de producto incluye categories cuando hay ids

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: `TiendaNubeService` — cargar árbol (`GET /categories`) y crear categoría (`POST /categories`)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java`

**Interfaces:**
- Consumes: `NubeCategoriaArbol` (Task 1); helpers privados existentes `extraerNombreProducto(JsonNode)`, `parseLinkNext(headers)`, `getStore(String)`, `verificarCredenciales()`, `retryHandler.getWithHeaders(uri, token)`, `retryHandler.postJson(uri, token, body)`.
- Produces: `public NubeCategoriaArbol cargarArbolCategorias(String storeName)` y `private Long crearCategoria(StoreCredentials store, Long parentId, String nombre)`. Usados por Task 6.

> **Nota:** estos dos métodos son wrappers de red; no llevan test unitario (igual que `listarCategorias`). La verificación de esta task es que el módulo compila. Su comportamiento se ejercita indirectamente por los tests del core en Task 6 (vía lambdas).

- [ ] **Step 1: Implementar `cargarArbolCategorias`**

Agregar el import `import ar.com.leo.super_master_backend.apis.nube.model.NubeCredentials.StoreCredentials;` solo si no está ya importado (en este archivo `StoreCredentials` suele referenciarse por su nombre simple; usar la misma forma que el resto del archivo). Agregar el método junto a `listarCategorias` (después de su cierre, ~línea 613):
```java
    /**
     * Carga el árbol de categorías de la tienda (id, name, parent) en una estructura cacheada
     * para find-or-create. Devuelve un árbol vacío si la tienda no está configurada o falla la lectura.
     */
    public NubeCategoriaArbol cargarArbolCategorias(String storeName) {
        NubeCategoriaArbol arbol = new NubeCategoriaArbol();
        StoreCredentials store;
        try {
            verificarCredenciales();
            store = getStore(storeName);
        } catch (Exception e) {
            log.warn("NUBE - No se pudo cargar árbol de categorías de '{}': {}", storeName, e.getMessage());
            return arbol;
        }
        if (store == null) return arbol;

        String uri = String.format("/%s/categories?per_page=200&fields=id,name,parent", store.getStoreId());
        int paginas = 0;

        while (uri != null) {
            NubeRetryHandler.HttpResponse httpResponse;
            try {
                httpResponse = retryHandler.getWithHeaders(uri, store.getAccessToken());
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 && e.getResponseBodyAsString().contains("Last page is 0")) {
                    break;
                }
                log.warn("NUBE ({}) - Error cargando árbol de categorías en página {}: {}. Abortando.",
                        storeName, paginas + 1, e.getMessage());
                return arbol;
            }

            if (httpResponse.body() == null) break;

            try {
                JsonNode categorias = objectMapper.readTree(httpResponse.body());
                if (!categorias.isArray() || categorias.isEmpty()) break;

                for (JsonNode cat : categorias) {
                    long id = cat.path("id").asLong(0);
                    if (id == 0) continue;
                    String nombre = extraerNombreProducto(cat.path("name"));
                    if (nombre == null) continue;
                    long parent = cat.path("parent").asLong(0);
                    arbol.registrar(id, parent == 0 ? null : parent, nombre);
                }
                paginas++;
            } catch (Exception e) {
                log.warn("NUBE ({}) - Error parseando árbol de categorías en página {}: {}. Abortando.",
                        storeName, paginas + 1, e.getMessage());
                return arbol;
            }

            uri = parseLinkNext(httpResponse.headers());
        }

        log.info("NUBE ({}) - Árbol de categorías cargado ({} páginas)", storeName, paginas);
        return arbol;
    }
```

- [ ] **Step 2: Implementar `crearCategoria`**

Agregar el método privado en la sección de alta (cerca de `crearProductoEnNube`):
```java
    /** Crea una categoría en TN ({name:{es},parent}) y devuelve su id. parentId null = raíz. */
    private Long crearCategoria(StoreCredentials store, Long parentId, String nombre) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", Map.of("es", nombre));
        if (parentId != null) body.put("parent", parentId);
        String resp = retryHandler.postJson(
                "/" + store.getStoreId() + "/categories", store.getAccessToken(),
                objectMapper.writeValueAsString(body));
        return objectMapper.readTree(resp).path("id").asLong();
    }
```
(`LinkedHashMap` y `Map` ya se usan en el archivo; verificar que estén importados — si no, agregarlos.)

- [ ] **Step 3: Compilar el módulo**

Run: `mvn -o -q compile -pl supermaster-backend`
Expected: BUILD SUCCESS (sin errores de compilación).

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java
git commit -m "feat(nube): cargar arbol de categorias (GET) y crear categoria (POST)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Integrar categorías en el alta (`crearProductoEnNubeCore` + `crearProductoEnNube`)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/CrearProductoEnNubeTest.java`

**Interfaces:**
- Consumes: `NubeCategoriaArbol` (Task 1), `NubeCategoriaRuta.aplanar` (Task 2), `NubeCategoriaResolver.resolver` (Task 3), `NubeProductoPayloadBuilder.construir(p,pvp,inflado,ids)` (Task 4), `cargarArbolCategorias`/`crearCategoria` (Task 5).
- Produces:
  - Público: `ResultadoAltaNube crearProductoEnNube(String storeName, Producto producto, BigDecimal pvp, BigDecimal pvpInflado, NubeCategoriaArbol arbol)` (suma el parámetro `arbol`). Usado por Task 7.
  - Core: `static ResultadoAltaNube crearProductoEnNubeCore(StoreCredentials store, Producto producto, BigDecimal pvp, BigDecimal pvpInflado, ObjectMapper om, List<String> clasifNombres, List<String> tipoNombres, NubeCategoriaArbol arbol, BiFunction<Long,String,Long> creadorCategoria, BiFunction<String,String,JsonNode> buscador, BiFunction<String,String,String> poster)`.

- [ ] **Step 1: Update existing tests + write the new failing tests**

En `CrearProductoEnNubeTest.java`:

(a) Agregar imports:
```java
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
```
(b) Helpers nuevos dentro de la clase:
```java
    private NubeCategoriaArbol arbol() { return new NubeCategoriaArbol(); }

    // creador que simula POST /categories: ids incrementales desde 100.
    private BiFunction<Long, String, Long> creador() {
        AtomicLong seq = new AtomicLong(100);
        return (parentId, nombre) -> seq.incrementAndGet();
    }

    private static final List<String> CLASIF = List.of("Cocina", "Ollas");
    private static final List<String> TIPO = List.of("Acero", "Inoxidable");
```
(c) Reescribir las 3 llamadas existentes al core a la firma nueva (clasif+tipo válidos, árbol, creador). Reemplazar los cuerpos de los 3 tests:
```java
    @Test
    void yaExiste_noPostea() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                CLASIF, TIPO, arbol(), creador(),
                (sku, token) -> om.createObjectNode().put("id", 1), // existe
                (uri, body) -> { posted.set(body); return "{}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.YA_EXISTIA);
        assertThat(posted.get()).isNull();
    }

    @Test
    void sinTitulo_error() {
        Producto p = producto(); p.setTituloNube("  ");
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), p, new BigDecimal("1500"), null, om,
                CLASIF, TIPO, arbol(), creador(),
                (sku, token) -> null, (uri, body) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("título");
    }

    @Test
    void ok_posteaYDevuelveCreado() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                CLASIF, TIPO, arbol(), creador(),
                (sku, token) -> null, // no existe
                (uri, body) -> { posted.set(body); return "{\"id\": 5}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.CREADO);
        assertThat(posted.get()).contains("\"sku\":\"SKU1\"").contains("\"published\":false");
    }
```
(d) Tests nuevos para categorías:
```java
    @Test
    void faltaClasif_error() {
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                null, TIPO, arbol(), creador(),
                (sku, token) -> null, (uri, body) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("clasif");
    }

    @Test
    void faltaTipo_error() {
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                CLASIF, List.of(), arbol(), creador(),
                (sku, token) -> null, (uri, body) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("tipo");
    }

    @Test
    void ok_posteaConCategoriasResueltas() {
        AtomicReference<String> posted = new AtomicReference<>();
        // árbol vacío → se crean las 4: Cocina(101) Ollas(102) Acero(103) Inoxidable(104)
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                CLASIF, TIPO, arbol(), creador(),
                (sku, token) -> null,
                (uri, body) -> { posted.set(body); return "{\"id\": 5}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.CREADO);
        assertThat(posted.get()).contains("\"categories\":[101,102,103,104]");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -o test -Dtest=CrearProductoEnNubeTest -pl supermaster-backend`
Expected: FAIL de compilación (la firma del core de 12 args y el parámetro `arbol` del público no existen).

- [ ] **Step 3: Update the implementation**

En `TiendaNubeService.java`:

(a) Asegurar imports de las entidades (agregar si faltan):
```java
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import java.util.function.BiFunction;
```

(b) Reemplazar el método público `crearProductoEnNube` por:
```java
    public ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube crearProductoEnNube(
            String storeName, ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto,
            java.math.BigDecimal pvp, java.math.BigDecimal pvpInflado, NubeCategoriaArbol arbol) {
        StoreCredentials store;
        try {
            verificarCredenciales();
            store = getStore(storeName);
        } catch (Exception e) {
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("Tienda Nube no configurada: " + e.getMessage());
        }
        if (store == null)
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("Tienda '" + storeName + "' no configurada");

        // KT GASTRO → clasif gastro; resto (KT HOGAR) → clasif gral. El tipo cuelga debajo de la clasif.
        boolean esGastro = "KT GASTRO".equalsIgnoreCase(storeName);
        ClasifGral clasifGral = producto.getClasifGral();
        ClasifGastro clasifGastro = producto.getClasifGastro();
        Tipo tipo = producto.getTipo();
        List<String> clasifNombres = esGastro
                ? (clasifGastro == null ? null : NubeCategoriaRuta.aplanar(clasifGastro, ClasifGastro::getPadre, ClasifGastro::getNombre))
                : (clasifGral == null ? null : NubeCategoriaRuta.aplanar(clasifGral, ClasifGral::getPadre, ClasifGral::getNombre));
        List<String> tipoNombres = tipo == null ? null
                : NubeCategoriaRuta.aplanar(tipo, Tipo::getPadre, Tipo::getNombre);

        NubeCategoriaArbol arbolUsar = arbol != null ? arbol : new NubeCategoriaArbol();
        return crearProductoEnNubeCore(store, producto, pvp, pvpInflado, objectMapper,
                clasifNombres, tipoNombres, arbolUsar,
                (parentId, nombre) -> crearCategoria(store, parentId, nombre),
                (sku, token) -> buscarProductoPorSku(sku, storeName),
                (uri, body) -> retryHandler.postJson(uri, store.getAccessToken(), body));
    }
```

(c) Reemplazar el core por la firma de 12 args, agregando validación de clasif/tipo y la resolución de categorías:
```java
    static ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube crearProductoEnNubeCore(
            StoreCredentials store, ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto,
            java.math.BigDecimal pvp, java.math.BigDecimal pvpInflado,
            ObjectMapper om,
            List<String> clasifNombres, List<String> tipoNombres,
            NubeCategoriaArbol arbol,
            BiFunction<Long, String, Long> creadorCategoria,
            java.util.function.BiFunction<String, String, JsonNode> buscador,
            java.util.function.BiFunction<String, String, String> poster) {
        try {
            if (producto.getTituloNube() == null || producto.getTituloNube().isBlank())
                return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("Falta Título Nube");
            if (buscador.apply(producto.getSku(), store.getAccessToken()) != null)
                return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.yaExistia();
            if (clasifNombres == null || clasifNombres.isEmpty() || tipoNombres == null || tipoNombres.isEmpty())
                return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("Falta clasif/tipo para categorizar");

            List<String> rutaNombres = new java.util.ArrayList<>(clasifNombres);
            rutaNombres.addAll(tipoNombres);
            List<Long> categoriaIds = NubeCategoriaResolver.resolver(arbol, rutaNombres, creadorCategoria);

            Map<String, Object> payload = NubeProductoPayloadBuilder.construir(producto, pvp, pvpInflado, categoriaIds);
            String body = om.writeValueAsString(payload);
            String uri = "/" + store.getStoreId() + "/products";
            poster.apply(uri, body);
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.creado();
        } catch (Exception e) {
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error(e.getMessage());
        }
    }
```
(`List` y `Map` ya están en uso en el archivo; agregar `import java.util.List;` si no estuviera.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -o test -Dtest=CrearProductoEnNubeTest -pl supermaster-backend`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/CrearProductoEnNubeTest.java
git commit -m "feat(nube): alta resuelve y asigna categorias (clasif+tipo obligatorios)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: `NubeExportService` — caché del árbol por tienda

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeExportService.java`

**Interfaces:**
- Consumes: `cargarArbolCategorias(String)` y la nueva firma `crearProductoEnNube(tienda, producto, pvp, pvpInflado, arbol)` (Task 6).

> **Nota:** `NubeExportService` orquesta llamadas de red (vía `tiendaNubeService`); no tiene test unitario propio (consistente con su estado actual). La verificación es: compila y la suite del paquete `nube` sigue verde.

- [ ] **Step 1: Cachear el árbol por tienda y pasarlo al alta**

En `exportar(...)`, declarar el caché antes del loop de productos (después de cargar `productos`, ~línea 39) e inyectar el árbol en la llamada:
```java
        // Árbol de categorías por tienda, cargado una vez por corrida (lazy por tienda usada).
        java.util.Map<String, NubeCategoriaArbol> arbolesPorTienda = new java.util.HashMap<>();

        for (Producto producto : productos) {
            for (ExportNubeRequestDTO.DestinoNube destino : request.tiendas()) {
                String tienda = destino.tienda();
                String etiqueta = producto.getSku() + " / " + tienda;
                Optional<Canal> canal = canalRepository.findByNombreIgnoreCase(tienda);
                if (canal.isEmpty()) { errores.add(etiqueta + ": canal '" + tienda + "' no existe"); continue; }
                Optional<ProductoCanalPrecio> precio = precioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.get().getId(), destino.cuotas());
                if (precio.isEmpty()) { errores.add(etiqueta + ": sin precio calculado para esa cuota"); continue; }
                if (precio.get().isObsoleto()) { errores.add(etiqueta + ": precio desactualizado (recalcular antes de subir)"); continue; }

                NubeCategoriaArbol arbol = arbolesPorTienda.computeIfAbsent(
                        tienda, tiendaNubeService::cargarArbolCategorias);

                ResultadoAltaNube r = tiendaNubeService.crearProductoEnNube(
                        tienda, producto, precio.get().getPvp(), precio.get().getPvpInflado(), arbol);
                switch (r.estado()) {
                    case CREADO -> creados++;
                    case YA_EXISTIA -> yaExistian.add(etiqueta);
                    case ERROR -> errores.add(etiqueta + ": " + r.motivo());
                }
            }
        }
```

- [ ] **Step 2: Compilar el módulo**

Run: `mvn -o -q compile -pl supermaster-backend`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Correr toda la suite del paquete nube**

Run: `mvn -o test -Dtest='ar.com.leo.super_master_backend.apis.nube.**' -pl supermaster-backend`
Expected: PASS — incluye `NubeCategoriaArbolTest`, `NubeCategoriaRutaTest`, `NubeCategoriaResolverTest`, `NubeProductoPayloadBuilderTest` (5), `NubeDescripcionBuilderTest`, `CrearProductoEnNubeTest` (6).

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeExportService.java
git commit -m "feat(nube): cachear arbol de categorias por tienda en la exportacion

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Verificación final (tras todas las tasks)

- [ ] **Suite completa del backend:** `mvn -o test -pl supermaster-backend` → 0 failures, 0 errors. (B2 suma ~17 tests nuevos sobre los 157 de B1; ninguno hace red.)
- [ ] **Sin cambios de frontend** (los checkboxes y el disparo del alta ya existen de B1; B2 es solo backend).
- [ ] **Ninguna llamada real a Tienda Nube** se ejecuta en los tests.

## Notas de cierre

- El frontend no cambia. El comportamiento nuevo aplica al próximo alta que se dispare desde "Canales de venta".
- En producción, `cargarArbolCategorias` y `crearCategoria` ejercitan la API real; la verificación contra una tienda la hará el usuario al configurar tokens.
- Fuera de alcance: B3 (imágenes base64), B4 (SEO con IA), re-categorizar productos ya existentes.
