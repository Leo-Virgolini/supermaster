# Imágenes en el alta de Tienda Nube — Fase B3 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Al dar de alta un producto en Tienda Nube, subirle todas las imágenes que existen en la carpeta local resueltas por su SKU (principal `{sku}.ext` + adicionales `{sku}_N.ext`), vía `POST /products/{id}/images` con base64.

**Architecture:** `ImagenService` gana la resolución de múltiples archivos por SKU y la lectura en base64. Un builder puro arma el payload de imagen. El core del alta captura el `id` del producto creado; el método público, tras un alta exitosa, sube las imágenes (I/O) y enriquece el resultado con una advertencia (sin imagen / parcial). Los DTOs y el frontend suman un canal de "advertencias" para el resumen.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven; Jackson 3 (`tools.jackson.databind`); JUnit 5 + AssertJ; frontend Next.js/TypeScript.

## Global Constraints

- Jackson 3: importar de `tools.jackson.databind` (NO `com.fasterxml.jackson`); excepciones unchecked. Parsear enteros de JSON con `.asLong(0)` (patrón ya usado en la clase).
- Todo el código backend nuevo de Nube vive en `ar.com.leo.super_master_backend.apis.nube.*`; los cambios de imágenes en `dominio.imagen`.
- **Ninguna llamada real a Tienda Nube** en los tests: usar lambdas/archivos temporales. Los wrappers de red (`subirImagenesProducto`) no se testean unitariamente (consistente con `listarCategorias`/`crearCategoria`).
- Convención de imágenes: principal `{sku}.{ext}` (posición 1), adicionales `{sku}_N.{ext}` con N entero ≥ 2 (posiciones siguientes, N ascendente). Match **case-insensitive**. Ante varias extensiones de un mismo slot, gana la prioridad existente (jpg > jpeg > png > gif > webp > bmp > svg).
- Sin imagen → producto CREADO con advertencia "creado sin imagen". Falla de una imagen → CREADO con advertencia "N de M imágenes subidas"; nunca revierte ni aborta el resto.
- Maven offline en Windows: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=...` (el wrapper funciona offline). Frontend: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`.
- Commits terminan con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: `ImagenService` — resolver múltiples archivos por SKU + leer base64

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceB3Test.java` (nuevo)

**Interfaces:**
- Produces:
  - `List<String> resolverArchivosPorSku(String sku)` — nombres de archivo del SKU ordenados raíz→adicionales (principal primero, luego `_N` ascendente); lista vacía si no hay.
  - `String leerBase64(String filename)` — contenido de `{baseDir}/{filename}` en Base64; propaga `UncheckedIOException` si no se puede leer.

- [ ] **Step 1: Write the failing test**

`ImagenServiceB3Test.java`:
```java
package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenServiceB3Test {

    // ttl 0 => el índice se re-escanea en cada acceso (sin caché entre llamadas en el test).
    private ImagenService servicioSobre(Path dir) {
        return new ImagenService(dir.toString(), 0L);
    }

    @Test
    void resolverArchivos_principalYAdicionalesEnOrden(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("ABC123.jpg"), "x");
        Files.writeString(dir.resolve("ABC123_2.png"), "x");
        Files.writeString(dir.resolve("ABC123_3.jpg"), "x");
        Files.writeString(dir.resolve("OTRO.jpg"), "x"); // otro SKU, no debe aparecer

        List<String> archivos = servicioSobre(dir).resolverArchivosPorSku("ABC123");

        assertThat(archivos).containsExactly("ABC123.jpg", "ABC123_2.png", "ABC123_3.jpg");
    }

    @Test
    void resolverArchivos_caseInsensitive(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("abc123.JPG"), "x");
        Files.writeString(dir.resolve("abc123_2.jpg"), "x");

        List<String> archivos = servicioSobre(dir).resolverArchivosPorSku("ABC123");

        assertThat(archivos).containsExactly("abc123.JPG", "abc123_2.jpg");
    }

    @Test
    void resolverArchivos_sinArchivos_listaVacia(@TempDir Path dir) {
        List<String> archivos = servicioSobre(dir).resolverArchivosPorSku("NOEXISTE");
        assertThat(archivos).isEmpty();
    }

    @Test
    void resolverArchivos_skuNuloOBlank_listaVacia(@TempDir Path dir) {
        assertThat(servicioSobre(dir).resolverArchivosPorSku(null)).isEmpty();
        assertThat(servicioSobre(dir).resolverArchivosPorSku("  ")).isEmpty();
    }

    @Test
    void leerBase64_devuelveContenidoCodificado(@TempDir Path dir) throws Exception {
        byte[] contenido = {1, 2, 3, 4, 5};
        Files.write(dir.resolve("ABC123.jpg"), contenido);

        String base64 = servicioSobre(dir).leerBase64("ABC123.jpg");

        assertThat(base64).isEqualTo(Base64.getEncoder().encodeToString(contenido));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=ImagenServiceB3Test`
Expected: FAIL de compilación ("cannot find symbol: method resolverArchivosPorSku / leerBase64").

- [ ] **Step 3: Write minimal implementation**

En `ImagenService.java`:

(a) agregar imports que falten:
```java
import java.util.Base64;
import java.util.TreeMap;
```

(b) agregar los dos métodos públicos (después de `resolverArchivoPorSku`, ~línea 58):
```java
    /**
     * Resuelve TODOS los archivos de imagen de un SKU: la principal ({sku}.{ext}) primero y luego
     * las adicionales ({sku}_N.{ext}) por N ascendente. Case-insensitive. Lista vacía si no hay.
     * Ante varias extensiones de un mismo slot, gana la de mayor prioridad (jpg primero).
     */
    public List<String> resolverArchivosPorSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return List.of();
        }
        String skuLower = sku.trim().toLowerCase(Locale.ROOT);
        // slot 1 = principal; slot N (>=2) = sufijo _N. TreeMap ordena 1,2,3... (principal primero).
        TreeMap<Integer, String> porSlot = new TreeMap<>();
        for (String nombre : obtenerIndice()) {
            int dot = nombre.lastIndexOf('.');
            if (dot <= 0) continue;
            String base = nombre.substring(0, dot).toLowerCase(Locale.ROOT);
            Integer slot = slotDe(base, skuLower);
            if (slot == null) continue;
            String existente = porSlot.get(slot);
            if (existente == null || prioridadExtension(nombre) < prioridadExtension(existente)) {
                porSlot.put(slot, nombre);
            }
        }
        return List.copyOf(porSlot.values());
    }

    /** Lee {baseDir}/{filename} y devuelve su contenido en Base64. */
    public String leerBase64(String filename) {
        try {
            byte[] bytes = Files.readAllBytes(baseDir.resolve(filename));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen " + filename, e);
        }
    }

    /** Slot del archivo respecto del SKU: 1 si es la principal ({sku}), N si es {sku}_N (N>=2), null si no matchea. */
    private static Integer slotDe(String base, String skuLower) {
        if (base.equals(skuLower)) {
            return 1;
        }
        String prefijo = skuLower + "_";
        if (base.startsWith(prefijo)) {
            String resto = base.substring(prefijo.length());
            if (!resto.isEmpty() && resto.chars().allMatch(Character::isDigit)) {
                int n = Integer.parseInt(resto);
                if (n >= 2) {
                    return n;
                }
            }
        }
        return null;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=ImagenServiceB3Test`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceB3Test.java
git commit -m "feat(imagen): resolverArchivosPorSku (principal + _N) y leerBase64

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `NubeImagenPayloadBuilder` (payload de imagen)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeImagenPayloadBuilder.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeImagenPayloadBuilderTest.java`

**Interfaces:**
- Produces: `static Map<String,Object> construir(String filename, String base64, int position)` → `{filename, attachment, position}`.

- [ ] **Step 1: Write the failing test**

`NubeImagenPayloadBuilderTest.java`:
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NubeImagenPayloadBuilderTest {

    @Test
    void construir_armaFilenameAttachmentPosition() {
        Map<String, Object> payload = NubeImagenPayloadBuilder.construir("ABC123.jpg", "QUJD", 1);
        assertThat(payload.get("filename")).isEqualTo("ABC123.jpg");
        assertThat(payload.get("attachment")).isEqualTo("QUJD");
        assertThat(payload.get("position")).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=NubeImagenPayloadBuilderTest`
Expected: FAIL de compilación ("cannot find symbol: class NubeImagenPayloadBuilder").

- [ ] **Step 3: Write minimal implementation**

`NubeImagenPayloadBuilder.java`:
```java
package ar.com.leo.super_master_backend.apis.nube.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Construye el body de POST /products/{id}/images de Tienda Nube (attachment base64). */
public final class NubeImagenPayloadBuilder {

    private NubeImagenPayloadBuilder() {}

    public static Map<String, Object> construir(String filename, String base64, int position) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("filename", filename);
        payload.put("attachment", base64);
        payload.put("position", position);
        return payload;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=NubeImagenPayloadBuilderTest`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeImagenPayloadBuilder.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeImagenPayloadBuilderTest.java
git commit -m "feat(nube): NubeImagenPayloadBuilder (filename/attachment/position)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `ResultadoAltaNube` con id + advertencia, y captura del id en el core

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/dto/ResultadoAltaNube.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java` (solo el core `crearProductoEnNubeCore`)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/CrearProductoEnNubeTest.java`

**Interfaces:**
- Produces:
  - `record ResultadoAltaNube(Estado estado, String motivo, Long productoNubeId, String advertencia)` con factories `creado(Long productoNubeId)`, `yaExistia()`, `error(String motivo)` y método de instancia `conAdvertencia(String advertencia)`.
  - El core `crearProductoEnNubeCore` ahora parsea el `id` de la respuesta del POST y devuelve `creado(id)`.

- [ ] **Step 1: Update existing tests + write the new assertion**

En `CrearProductoEnNubeTest.java`, el test `ok_posteaConCategoriasResueltas` ya usa un poster que devuelve `"{\"id\": 5}"`. Agregar al final de ese test la verificación del id capturado:
```java
        assertThat(r.productoNubeId()).isEqualTo(5L);
```
(Los demás tests del archivo no cambian: siguen verificando `r.estado()`.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=CrearProductoEnNubeTest`
Expected: FAIL de compilación ("cannot find symbol: method productoNubeId()").

- [ ] **Step 3: Update the implementation**

(a) Reemplazar `ResultadoAltaNube.java` completo:
```java
package ar.com.leo.super_master_backend.apis.nube.dto;

public record ResultadoAltaNube(Estado estado, String motivo, Long productoNubeId, String advertencia) {
    public enum Estado { CREADO, YA_EXISTIA, ERROR }
    public static ResultadoAltaNube creado(Long productoNubeId) { return new ResultadoAltaNube(Estado.CREADO, null, productoNubeId, null); }
    public static ResultadoAltaNube yaExistia() { return new ResultadoAltaNube(Estado.YA_EXISTIA, null, null, null); }
    public static ResultadoAltaNube error(String motivo) { return new ResultadoAltaNube(Estado.ERROR, motivo, null, null); }

    /** Copia este resultado agregando una advertencia (p. ej. estado de las imágenes). */
    public ResultadoAltaNube conAdvertencia(String advertencia) {
        return new ResultadoAltaNube(estado, motivo, productoNubeId, advertencia);
    }
}
```

(b) En `TiendaNubeService.crearProductoEnNubeCore` (~líneas 950-952), capturar el id de la respuesta del poster y devolverlo. Reemplazar:
```java
            poster.apply(uri, body);
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.creado();
```
por:
```java
            String respuesta = poster.apply(uri, body);
            Long productoNubeId = respuesta == null ? null : om.readTree(respuesta).path("id").asLong(0);
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.creado(productoNubeId);
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest=CrearProductoEnNubeTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/dto/ResultadoAltaNube.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/CrearProductoEnNubeTest.java
git commit -m "feat(nube): ResultadoAltaNube con id+advertencia; core captura id del producto

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Subida de imágenes en el alta (`subirImagenesProducto` + flujo público)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java`

**Interfaces:**
- Consumes: `ImagenService.resolverArchivosPorSku`/`leerBase64` (Task 1), `NubeImagenPayloadBuilder.construir` (Task 2), `ResultadoAltaNube.conAdvertencia`/`productoNubeId` (Task 3), `retryHandler.postJson`, `objectMapper`.
- Produces: `private String subirImagenesProducto(StoreCredentials store, Long productoNubeId, String sku)` (advertencia o null) y el enriquecimiento del resultado en `crearProductoEnNube`.

> **Nota:** `subirImagenesProducto` es I/O (disco + red); no lleva test unitario. La verificación es que el módulo compila y la suite del paquete nube sigue verde.

- [ ] **Step 1: Inyectar `ImagenService`**

(a) Agregar import:
```java
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;
```
(b) Agregar el campo (junto a `objectMapper`, ~línea 43):
```java
    private final ImagenService imagenService;
```
(c) Sumar el parámetro al constructor (~línea 51) y asignarlo:
```java
    public TiendaNubeService(RestClient nubeRestClient, NubeProperties properties, ObjectMapper objectMapper,
                             ImagenService imagenService) {
        this.restClient = nubeRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.imagenService = imagenService;
    }
```

- [ ] **Step 2: Implementar `subirImagenesProducto`**

Agregar el método privado cerca de `crearProductoEnNube` (después del core, ~línea 956):
```java
    /**
     * Sube a TN todas las imágenes del SKU (principal + adicionales) al producto recién creado.
     * Devuelve una advertencia para el resumen, o null si subió todas. Un fallo de una imagen
     * (lectura o POST) no frena las demás ni revierte el alta.
     */
    private String subirImagenesProducto(StoreCredentials store, Long productoNubeId, String sku) {
        List<String> archivos = imagenService.resolverArchivosPorSku(sku);
        if (archivos.isEmpty()) {
            return "creado sin imagen";
        }
        int ok = 0;
        for (int i = 0; i < archivos.size(); i++) {
            String filename = archivos.get(i);
            try {
                String base64 = imagenService.leerBase64(filename);
                Map<String, Object> body = NubeImagenPayloadBuilder.construir(filename, base64, i + 1);
                retryHandler.postJson(
                        "/" + store.getStoreId() + "/products/" + productoNubeId + "/images",
                        store.getAccessToken(), objectMapper.writeValueAsString(body));
                ok++;
            } catch (Exception e) {
                log.warn("NUBE - Falló subir imagen {} del producto {}: {}", filename, productoNubeId, e.getMessage());
            }
        }
        return ok == archivos.size() ? null : (ok + " de " + archivos.size() + " imágenes subidas");
    }
```

- [ ] **Step 3: Enriquecer el resultado en `crearProductoEnNube` (público)**

En `crearProductoEnNube`, reemplazar el `return crearProductoEnNubeCore(...)` final (~líneas 919-923) por una variante que capture el resultado y suba imágenes si fue CREADO:
```java
        ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube r = crearProductoEnNubeCore(
                store, producto, pvp, pvpInflado, objectMapper,
                clasifNombres, tipoNombres, arbolUsar,
                (parentId, nombre) -> crearCategoria(store, parentId, nombre),
                (sku, token) -> buscarProductoPorSku(sku, storeName),
                (uri, body) -> retryHandler.postJson(uri, store.getAccessToken(), body));

        if (r.estado() == ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.Estado.CREADO
                && r.productoNubeId() != null && r.productoNubeId() > 0) {
            String advertencia = subirImagenesProducto(store, r.productoNubeId(), producto.getSku());
            if (advertencia != null) {
                r = r.conAdvertencia(advertencia);
            }
        }
        return r;
```

- [ ] **Step 4: Compilar y correr la suite del paquete nube**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest="ar.com.leo.super_master_backend.apis.nube.**"`
Expected: PASS — todos los tests del paquete nube verdes (incluye `CrearProductoEnNubeTest` 6, `NubeImagenPayloadBuilderTest` 1, y los de B2). Compila con la nueva dependencia `ImagenService`.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java
git commit -m "feat(nube): subir imagenes del SKU al alta (base64) con aviso sin imagen/parcial

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: `ExportNubeResultDTO` + `NubeExportService` — canal de advertencias

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/dto/ExportNubeResultDTO.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeExportService.java`

**Interfaces:**
- Consumes: `ResultadoAltaNube.advertencia()` (Task 3).
- Produces: `record ExportNubeResultDTO(int creados, List<String> yaExistian, List<String> errores, List<String> advertencias)`. `NubeExportService` acumula las advertencias por SKU/tienda.

> **Nota:** `NubeExportService` orquesta red; no tiene test unitario. Verificación: compila y la suite nube sigue verde.

- [ ] **Step 1: Agregar `advertencias` al DTO**

Reemplazar `ExportNubeResultDTO.java`:
```java
package ar.com.leo.super_master_backend.apis.nube.dto;

import java.util.List;

public record ExportNubeResultDTO(int creados, List<String> yaExistian, List<String> errores, List<String> advertencias) {}
```

- [ ] **Step 2: Acumular advertencias en `NubeExportService`**

En `exportar(...)`:
(a) declarar la lista junto a las otras (~línea 42):
```java
        List<String> advertencias = new ArrayList<>();
```
(b) en el `return` temprano de request inválido (~línea 45), incluirla:
```java
            return new ExportNubeResultDTO(0, yaExistian, errores, advertencias);
```
(c) en el `switch`, en la rama CREADO, agregar la advertencia si vino:
```java
                switch (r.estado()) {
                    case CREADO -> {
                        creados++;
                        if (r.advertencia() != null) advertencias.add(etiqueta + ": " + r.advertencia());
                    }
                    case YA_EXISTIA -> yaExistian.add(etiqueta);
                    case ERROR -> errores.add(etiqueta + ": " + r.motivo());
                }
```
(d) en el `return` final (~línea 76):
```java
        return new ExportNubeResultDTO(creados, yaExistian, errores, advertencias);
```

- [ ] **Step 3: Compilar y correr la suite del paquete nube**

Run: `cd supermaster-backend && ./mvnw.cmd -o test -Dtest="ar.com.leo.super_master_backend.apis.nube.**"`
Expected: PASS — todo el paquete nube verde, compila con el DTO de 4 componentes.

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/dto/ExportNubeResultDTO.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeExportService.java
git commit -m "feat(nube): ExportNubeResultDTO suma advertencias (sin imagen / parcial)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Frontend — mostrar advertencias en el toast

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

**Interfaces:**
- Consumes: el campo `advertencias` que ahora devuelve `POST /api/nube/exportar-productos`.

- [ ] **Step 1: Agregar `advertencias` al tipo TS**

En `productosService.ts`, en `export type ExportNubeResultDTO` (~línea 232), agregar el campo (respetando la indentación con tabs del archivo):
```ts
export type ExportNubeResultDTO = {
	creados: number;
	yaExistian: string[];
	errores: string[];
	advertencias: string[];
};
```

- [ ] **Step 2: Mostrar advertencias en el toast (handleCreate)**

En `page.tsx`, en el bloque del alta a Nube de la creación (~líneas 657-662), agregar la línea de advertencias antes de decidir el toast. Reemplazar:
```ts
                    const partes: string[] = [];
                    if (r.creados > 0) partes.push(`${r.creados} creado(s) en Nube`);
                    if (r.yaExistian.length) partes.push(`${r.yaExistian.length} ya existía(n)`);
                    if (r.errores.length) partes.push(`${r.errores.length} con error: ${r.errores.join("; ")}`);
                    if (r.errores.length) notificar.error(`Tienda Nube: ${partes.join(" · ")}`);
                    else notificar.success(`Tienda Nube: ${partes.join(" · ") || "sin cambios"}`);
```
por:
```ts
                    const partes: string[] = [];
                    if (r.creados > 0) partes.push(`${r.creados} creado(s) en Nube`);
                    if (r.yaExistian.length) partes.push(`${r.yaExistian.length} ya existía(n)`);
                    if (r.advertencias?.length) partes.push(`avisos: ${r.advertencias.join("; ")}`);
                    if (r.errores.length) partes.push(`${r.errores.length} con error: ${r.errores.join("; ")}`);
                    if (r.errores.length) notificar.error(`Tienda Nube: ${partes.join(" · ")}`);
                    else notificar.success(`Tienda Nube: ${partes.join(" · ") || "sin cambios"}`);
```

- [ ] **Step 3: Mostrar advertencias en el toast (handleGuardarEdicion)**

En `page.tsx`, en el bloque equivalente de la edición (~líneas 814-819), aplicar el mismo cambio. Reemplazar:
```ts
                    const partes: string[] = [];
                    if (r.creados > 0) partes.push(`${r.creados} creado(s) en Nube`);
                    if (r.yaExistian.length) partes.push(`${r.yaExistian.length} ya existía(n)`);
                    if (r.errores.length) partes.push(`${r.errores.length} con error: ${r.errores.join("; ")}`);
                    if (r.errores.length) notificar.error(`Tienda Nube: ${partes.join(" · ")}`);
                    else notificar.success(`Tienda Nube: ${partes.join(" · ") || "sin cambios"}`);
```
por:
```ts
                    const partes: string[] = [];
                    if (r.creados > 0) partes.push(`${r.creados} creado(s) en Nube`);
                    if (r.yaExistian.length) partes.push(`${r.yaExistian.length} ya existía(n)`);
                    if (r.advertencias?.length) partes.push(`avisos: ${r.advertencias.join("; ")}`);
                    if (r.errores.length) partes.push(`${r.errores.length} con error: ${r.errores.join("; ")}`);
                    if (r.errores.length) notificar.error(`Tienda Nube: ${partes.join(" · ")}`);
                    else notificar.success(`Tienda Nube: ${partes.join(" · ") || "sin cambios"}`);
```

- [ ] **Step 4: Typecheck**

Run: `cd supermaster-frontend && cmd /c "npx tsc --noEmit"`
Expected: exit 0, sin errores.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): mostrar avisos de imagenes en el toast de Tienda Nube

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Verificación final (tras todas las tasks)

- [ ] **Suite completa del backend:** `cd supermaster-backend && ./mvnw.cmd -o test` → 0 failures, 0 errors (B3 suma ~7 tests).
- [ ] **Typecheck frontend:** `cd supermaster-frontend && cmd /c "npx tsc --noEmit"` → 0 errores.
- [ ] **Ninguna llamada real a Tienda Nube** se ejecuta en los tests.

## Notas de cierre

- El alta resuelve y sube las imágenes en backend por SKU; el front solo refleja los avisos.
- En producción, `subirImagenesProducto` ejercita `POST /products/{id}/images`; la verificación real contra una tienda la hará el usuario al configurar tokens.
- Fuera de alcance: B4 (SEO con IA); no re-sube imágenes a productos ya existentes.
