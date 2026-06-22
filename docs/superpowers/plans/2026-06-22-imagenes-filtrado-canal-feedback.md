# Filtrado de imágenes por canal + feedback en el form — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Filtrar las imágenes por formato y tamaño según el canal (ML / Tienda Nube) antes de subirlas, reportar las omitidas como advertencia, y mostrar en el formulario de producto cuántas imágenes hay y cuáles quedarían afuera.

**Architecture:** Lógica de validación centralizada en `ImagenService` (resolución con tamaño + filtro por formato/tamaño). Nube y ML consumen el filtro al subir y reportan las rechazadas vía sus mecanismos de advertencia. Un endpoint nuevo expone el detalle por SKU; el formulario lo consume y avisa según los canales tildados.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven; JUnit 5 + AssertJ + `@TempDir`. Frontend Next.js 16 / React 19 / TypeScript.

## Global Constraints

- Trabajar directo en `main` (sin ramas).
- Backend Maven OFFLINE desde `supermaster-backend/`: `./mvnw -o ...`. Frontend desde `supermaster-frontend/`: `npx tsc --noEmit`.
- **NO ejecutar nada que llame a las APIs reales de Dux/Nube/ML** — solo tests offline (`@TempDir`, helpers puros). No crear productos reales.
- **Formatos por canal** (de las docs): ML = `jpg, jpeg, png`; Nube = `gif, jpg, jpeg, png, webp`. Límite de tamaño común = **10 MB** (`10 * 1024 * 1024` bytes).
- **Dux NO se toca** (no sube imágenes).
- El backend es el filtro autoritativo al subir; el front da feedback preventivo (constantes espejo).
- Las rechazadas se reportan como **advertencia** (no rompen el alta), distinguiendo motivo: formato o tamaño.
- Commits terminan EXACTAMENTE con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: `ImagenService` — detalle por SKU, filtro por canal y descripción de rechazadas

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceFiltroTest.java` (nuevo)

**Interfaces:**
- Consumes: `resolverArchivosPorSku(String)` (existente), `baseDir`, `Files.size`.
- Produces:
  - `ImagenService.MAX_BYTES_CANAL` (long, 10 MB)
  - `record ImagenService.ImagenDetalle(String nombre, String extension, long bytes)`
  - `enum ImagenService.MotivoRechazo { FORMATO, TAMANO }`
  - `record ImagenService.ImagenRechazada(String nombre, MotivoRechazo motivo)`
  - `record ImagenService.FiltroImagenes(List<String> validas, List<ImagenRechazada> rechazadas)`
  - `List<ImagenDetalle> resolverDetallePorSku(String sku)`
  - `FiltroImagenes filtrarParaCanal(String sku, Set<String> extensionesPermitidas)` (+ overload package-private con `long maxBytes` para tests)
  - `static String describirRechazadas(List<ImagenRechazada> rechazadas)` (null si vacío)

- [ ] **Step 1: Escribir el test (falla a compilar)**

Crear `ImagenServiceFiltroTest.java`:

```java
package ar.com.leo.super_master_backend.dominio.imagen.service;

import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.FiltroImagenes;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenDetalle;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenRechazada;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.MotivoRechazo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenServiceFiltroTest {

    private static final Set<String> EXT_ML = Set.of("jpg", "jpeg", "png");

    private ImagenService servicioSobre(Path dir) {
        return new ImagenService(dir.toString(), 0L);
    }

    @Test
    void resolverDetalle_devuelveExtensionYBytes(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("ABC123.JPG"), new byte[]{1, 2, 3, 4, 5});
        List<ImagenDetalle> det = servicioSobre(dir).resolverDetallePorSku("abc123");
        assertThat(det).hasSize(1);
        assertThat(det.getFirst().extension()).isEqualTo("jpg");
        assertThat(det.getFirst().bytes()).isEqualTo(5);
    }

    @Test
    void filtrar_formatoNoPermitido_vaARechazadasFormato(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("SKU1_2.webp"), new byte[]{1, 2});
        FiltroImagenes f = servicioSobre(dir).filtrarParaCanal("SKU1", EXT_ML);
        assertThat(f.validas()).isEmpty();
        assertThat(f.rechazadas()).containsExactly(new ImagenRechazada("SKU1_2.webp", MotivoRechazo.FORMATO));
    }

    @Test
    void filtrar_superaTamano_vaARechazadasTamano(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("SKU1.jpg"), new byte[]{1, 2, 3, 4});
        // overload con maxBytes chico para no crear 10MB en disco
        FiltroImagenes f = servicioSobre(dir).filtrarParaCanal("SKU1", EXT_ML, 2L);
        assertThat(f.validas()).isEmpty();
        assertThat(f.rechazadas()).containsExactly(new ImagenRechazada("SKU1.jpg", MotivoRechazo.TAMANO));
    }

    @Test
    void filtrar_formatoYTamanoOk_esValida(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("SKU1.jpg"), new byte[]{1, 2, 3, 4});
        FiltroImagenes f = servicioSobre(dir).filtrarParaCanal("SKU1", EXT_ML);
        assertThat(f.validas()).containsExactly("SKU1.jpg");
        assertThat(f.rechazadas()).isEmpty();
    }

    @Test
    void describirRechazadas_formateaConMotivo() {
        String txt = ImagenService.describirRechazadas(List.of(
                new ImagenRechazada("foto.webp", MotivoRechazo.FORMATO),
                new ImagenRechazada("grande.jpg", MotivoRechazo.TAMANO)));
        assertThat(txt).isEqualTo("2 omitida(s): foto.webp (formato), grande.jpg (supera 10MB)");
    }

    @Test
    void describirRechazadas_vacio_devuelveNull() {
        assertThat(ImagenService.describirRechazadas(List.of())).isNull();
    }
}
```

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `./mvnw -o test -Dtest=ImagenServiceFiltroTest`
Expected: FAIL de compilación — `resolverDetallePorSku`, `filtrarParaCanal`, `describirRechazadas` y los records no existen.

- [ ] **Step 3: Implementar en `ImagenService.java`**

Agregar imports (junto a los existentes): `import java.util.ArrayList;`, `import java.util.Set;`, `import java.util.stream.Collectors;`.

Agregar dentro de la clase (después de `resolverArchivosPorSku`):

```java
    /** Límite de tamaño por imagen aceptado por ML y Tienda Nube. */
    public static final long MAX_BYTES_CANAL = 10L * 1024 * 1024; // 10 MB

    /** Una imagen resuelta con su metadata (extensión en minúscula, sin punto). */
    public record ImagenDetalle(String nombre, String extension, long bytes) {}

    /** Motivo por el que una imagen no se sube a un canal. */
    public enum MotivoRechazo { FORMATO, TAMANO }

    /** Una imagen que no se sube, con su motivo. */
    public record ImagenRechazada(String nombre, MotivoRechazo motivo) {}

    /** Resultado de filtrar las imágenes de un SKU para un canal: las que se suben y las que no. */
    public record FiltroImagenes(List<String> validas, List<ImagenRechazada> rechazadas) {}

    /** Resuelve las imágenes del SKU con su extensión (minúscula) y tamaño en bytes. */
    public List<ImagenDetalle> resolverDetallePorSku(String sku) {
        List<ImagenDetalle> out = new ArrayList<>();
        for (String nombre : resolverArchivosPorSku(sku)) {
            int dot = nombre.lastIndexOf('.');
            String ext = dot > 0 ? nombre.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            try {
                out.add(new ImagenDetalle(nombre, ext, Files.size(baseDir.resolve(nombre))));
            } catch (java.io.IOException e) {
                // El archivo desapareció entre el índice y la lectura: se omite (best-effort).
            }
        }
        return out;
    }

    /** Filtra las imágenes del SKU para un canal: válida si su extensión está permitida y no supera 10 MB. */
    public FiltroImagenes filtrarParaCanal(String sku, Set<String> extensionesPermitidas) {
        return filtrarParaCanal(sku, extensionesPermitidas, MAX_BYTES_CANAL);
    }

    /** Variante con límite configurable (para tests). */
    FiltroImagenes filtrarParaCanal(String sku, Set<String> extensionesPermitidas, long maxBytes) {
        List<String> validas = new ArrayList<>();
        List<ImagenRechazada> rechazadas = new ArrayList<>();
        for (ImagenDetalle d : resolverDetallePorSku(sku)) {
            if (!extensionesPermitidas.contains(d.extension())) {
                rechazadas.add(new ImagenRechazada(d.nombre(), MotivoRechazo.FORMATO));
            } else if (d.bytes() > maxBytes) {
                rechazadas.add(new ImagenRechazada(d.nombre(), MotivoRechazo.TAMANO));
            } else {
                validas.add(d.nombre());
            }
        }
        return new FiltroImagenes(validas, rechazadas);
    }

    /** Texto para el resumen del export con las imágenes omitidas; null si no hay ninguna. */
    public static String describirRechazadas(List<ImagenRechazada> rechazadas) {
        if (rechazadas == null || rechazadas.isEmpty()) return null;
        String detalle = rechazadas.stream()
                .map(r -> r.nombre() + " (" + (r.motivo() == MotivoRechazo.FORMATO ? "formato" : "supera 10MB") + ")")
                .collect(Collectors.joining(", "));
        return rechazadas.size() + " omitida(s): " + detalle;
    }
```

- [ ] **Step 4: Correr el test para verificar que pasa**

Run: `./mvnw -o test -Dtest=ImagenServiceFiltroTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceFiltroTest.java
git commit -m "feat(imagen): detalle por SKU + filtro por canal (formato/tamano)"
```

---

### Task 2: Endpoint `GET /api/imagenes/detalle/{sku}`

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/controller/ImagenController.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/controller/ImagenControllerDetalleTest.java` (nuevo)

**Interfaces:**
- Consumes: `ImagenService.resolverDetallePorSku(String)` → `List<ImagenDetalle>` (de Task 1).
- Produces: `GET /api/imagenes/detalle/{sku}` → 200 con `List<ImagenDetalle>` (lista vacía si no hay).

- [ ] **Step 1: Escribir el test (falla a compilar)**

Crear `ImagenControllerDetalleTest.java`:

```java
package ar.com.leo.super_master_backend.dominio.imagen.controller;

import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenDetalle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenControllerDetalleTest {

    private ImagenController controllerSobre(Path dir) {
        return new ImagenController(new ImagenService(dir.toString(), 0L));
    }

    @Test
    void detalle_devuelveImagenesDelSku(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("SKU9.jpg"), new byte[]{1, 2, 3});
        List<ImagenDetalle> body = controllerSobre(dir).detalle("SKU9").getBody();
        assertThat(body).hasSize(1);
        assertThat(body.getFirst().nombre()).isEqualTo("SKU9.jpg");
    }

    @Test
    void detalle_sinImagenes_listaVacia(@TempDir Path dir) {
        assertThat(controllerSobre(dir).detalle("NADA").getBody()).isEmpty();
    }
}
```

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `./mvnw -o test -Dtest=ImagenControllerDetalleTest`
Expected: FAIL de compilación — `detalle(...)` no existe.

- [ ] **Step 3: Implementar el endpoint**

En `ImagenController.java`, agregar imports `import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenDetalle;` y `import java.util.List;`, y el método (después de `listar`):

```java
    @GetMapping("/detalle/{sku}")
    public ResponseEntity<List<ImagenDetalle>> detalle(@PathVariable String sku) {
        return ResponseEntity.ok(imagenService.resolverDetallePorSku(sku));
    }
```

- [ ] **Step 4: Correr el test para verificar que pasa**

Run: `./mvnw -o test -Dtest=ImagenControllerDetalleTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/controller/ImagenController.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/controller/ImagenControllerDetalleTest.java
git commit -m "feat(imagen): endpoint GET /api/imagenes/detalle/{sku}"
```

---

### Task 3: Tienda Nube — filtrar por formato/tamaño y reportar omitidas

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeAdvertenciaImagenesTest.java` (nuevo)

**Interfaces:**
- Consumes: `imagenService.filtrarParaCanal(sku, Set)`, `ImagenService.describirRechazadas(...)` (de Task 1).
- Produces: `static String TiendaNubeService.combinarAdvertencias(String... partes)` (junta con "; ", null si todo vacío).

- [ ] **Step 1: Escribir el test (falla a compilar)**

Crear `TiendaNubeAdvertenciaImagenesTest.java`:

```java
package ar.com.leo.super_master_backend.apis.nube.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TiendaNubeAdvertenciaImagenesTest {

    @Test
    void combinar_uneNoNulos() {
        assertThat(TiendaNubeService.combinarAdvertencias("2 de 3 imágenes subidas", "1 omitida(s): x.webp (formato)"))
                .isEqualTo("2 de 3 imágenes subidas; 1 omitida(s): x.webp (formato)");
    }

    @Test
    void combinar_ignoraNullYBlank() {
        assertThat(TiendaNubeService.combinarAdvertencias(null, "1 omitida(s): x.bmp (formato)"))
                .isEqualTo("1 omitida(s): x.bmp (formato)");
    }

    @Test
    void combinar_todoVacio_devuelveNull() {
        assertThat(TiendaNubeService.combinarAdvertencias(null, "")).isNull();
    }
}
```

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `./mvnw -o test -Dtest=TiendaNubeAdvertenciaImagenesTest`
Expected: FAIL de compilación — `combinarAdvertencias` no existe.

- [ ] **Step 3: Implementar el filtro + helper**

En `TiendaNubeService.java`:

1. Agregar imports: `import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;`, `import java.util.Arrays;`, `import java.util.Set;`, `import java.util.stream.Collectors;` (si no están).

2. Agregar la constante de formatos (junto a los demás campos estáticos de la clase):
```java
    private static final Set<String> EXT_NUBE = Set.of("gif", "jpg", "jpeg", "png", "webp");
```

3. Agregar el helper estático:
```java
    /** Une partes de advertencia no vacías con "; "; null si no hay ninguna. */
    static String combinarAdvertencias(String... partes) {
        String r = Arrays.stream(partes)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.joining("; "));
        return r.isBlank() ? null : r;
    }
```

4. Reescribir `subirImagenesProducto` (filtra y reporta rechazadas):
```java
    private String subirImagenesProducto(StoreCredentials store, Long productoNubeId, String sku) {
        ImagenService.FiltroImagenes filtro = imagenService.filtrarParaCanal(sku, EXT_NUBE);
        if (filtro.validas().isEmpty() && filtro.rechazadas().isEmpty()) {
            return "creado sin imagen";
        }
        String advSubida = filtro.validas().isEmpty() ? null : subirImagenes(store, productoNubeId, filtro.validas());
        return combinarAdvertencias(advSubida, ImagenService.describirRechazadas(filtro.rechazadas()));
    }
```

5. En `sincronizarImagenesNube`, reemplazar el paso 2 (subir las locales). Cambiar:
```java
        // 2) Subir las locales actuales (misma lógica que el alta).
        List<String> archivos = imagenService.resolverArchivosPorSku(sku);
        if (archivos.isEmpty()) {
            return null; // sin imágenes locales: nada que sincronizar, no es advertencia en update
        }
        return subirImagenes(store, productoNubeId, archivos);
```
por:
```java
        // 2) Subir las locales válidas (filtradas por formato/tamaño); reportar omitidas.
        ImagenService.FiltroImagenes filtro = imagenService.filtrarParaCanal(sku, EXT_NUBE);
        if (filtro.validas().isEmpty() && filtro.rechazadas().isEmpty()) {
            return null; // sin imágenes locales: nada que sincronizar
        }
        String advSubida = filtro.validas().isEmpty() ? null : subirImagenes(store, productoNubeId, filtro.validas());
        return combinarAdvertencias(advSubida, ImagenService.describirRechazadas(filtro.rechazadas()));
```

- [ ] **Step 4: Correr el test para verificar que pasa**

Run: `./mvnw -o test -Dtest=TiendaNubeAdvertenciaImagenesTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Compilar el módulo (la firma de `subirImagenes` no cambió, pero verificamos que todo cierra)**

Run: `./mvnw -o test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeAdvertenciaImagenesTest.java
git commit -m "feat(nube): filtrar imagenes por formato/tamano y reportar omitidas"
```

---

### Task 4: Mercado Libre — filtrar por formato/tamaño y reportar omitidas

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlRechazadasImagenesTest.java` (nuevo)

**Interfaces:**
- Consumes: `imagenService.filtrarParaCanal(sku, Set)`, `ImagenService.describirRechazadas(...)` (Task 1), `concatAdv` (existente), `ResultadoAltaMl`.
- Produces: `static ResultadoAltaMl MercadoLibreService.aplicarRechazadasImagenes(ResultadoAltaMl r, List<ImagenService.ImagenRechazada> rechazadas)`.

- [ ] **Step 1: Escribir el test (falla a compilar)**

Crear `MlRechazadasImagenesTest.java`:

```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenRechazada;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.MotivoRechazo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlRechazadasImagenesTest {

    private static final List<ImagenRechazada> UNA = List.of(new ImagenRechazada("x.webp", MotivoRechazo.FORMATO));

    @Test
    void creado_conRechazadas_agregaAdvertencia() {
        ResultadoAltaMl r = MercadoLibreService.aplicarRechazadasImagenes(ResultadoAltaMl.creado("MLA1", null), UNA);
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(r.advertencia()).isEqualTo("1 omitida(s): x.webp (formato)");
    }

    @Test
    void creado_conRechazadas_preservaAdvertenciaPrevia() {
        ResultadoAltaMl base = ResultadoAltaMl.creado("MLA1", null).conAdvertencia("ítem creado pero falló la descripción");
        ResultadoAltaMl r = MercadoLibreService.aplicarRechazadasImagenes(base, UNA);
        assertThat(r.advertencia()).isEqualTo("ítem creado pero falló la descripción; 1 omitida(s): x.webp (formato)");
    }

    @Test
    void error_noSeModifica() {
        ResultadoAltaMl base = ResultadoAltaMl.error("falta título ML");
        ResultadoAltaMl r = MercadoLibreService.aplicarRechazadasImagenes(base, UNA);
        assertThat(r).isSameAs(base);
    }

    @Test
    void sinRechazadas_noSeModifica() {
        ResultadoAltaMl base = ResultadoAltaMl.creado("MLA1", null);
        assertThat(MercadoLibreService.aplicarRechazadasImagenes(base, List.of())).isSameAs(base);
    }
}
```

- [ ] **Step 2: Correr el test para verificar que falla**

Run: `./mvnw -o test -Dtest=MlRechazadasImagenesTest`
Expected: FAIL de compilación — `aplicarRechazadasImagenes` no existe.

- [ ] **Step 3: Implementar el helper + cablear el filtro**

En `MercadoLibreService.java`:

1. Agregar imports: `import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;`, `import java.util.Set;` (si no están).

2. Agregar la constante de formatos (junto a los campos estáticos):
```java
    private static final Set<String> EXT_ML = Set.of("jpg", "jpeg", "png");
```

3. Agregar el helper estático (cerca de `concatAdv`):
```java
    /** Si el alta/actualización fue exitosa y hubo imágenes omitidas, las agrega como advertencia. */
    static ResultadoAltaMl aplicarRechazadasImagenes(ResultadoAltaMl r, List<ImagenService.ImagenRechazada> rechazadas) {
        if (rechazadas == null || rechazadas.isEmpty()) return r;
        if (r.estado() != ResultadoAltaMl.Estado.CREADO && r.estado() != ResultadoAltaMl.Estado.ACTUALIZADO) return r;
        return r.conAdvertencia(concatAdv(r.advertencia(), ImagenService.describirRechazadas(rechazadas)));
    }
```

4. En `crearItemEnMl`, filtrar antes y aplicar rechazadas. Cambiar el cuerpo (líneas ~1909-1927):
```java
        ImagenService.FiltroImagenes filtro = imagenService.filtrarParaCanal(producto.getSku(), EXT_ML);
        ResultadoAltaMl r = crearItemEnMlCore(
                producto, objectMapper,
                sku -> false,  // existencia ya verificada por el caller (upsert en MlExportService)
                sku -> filtro.validas(),
                filename -> subirImagenItem(filename),
                titulo -> resolverCategoriaMl(producto.getMlCategoryId(), titulo, this::predecirCategoria),
                json -> postearItem(json),
                (itemId, plainText) -> retryHandler.postJson("/items/" + itemId + "/description",
                        () -> tokens.accessToken, objectMapper.writeValueAsString(Map.of("plain_text", plainText))));
        r = aplicarRechazadasImagenes(r, filtro.rechazadas());

        // Producto inactivo: dejar la publicación recién creada en paused (best-effort).
        // concatAdv preserva una advertencia previa del alta (descripción / imágenes omitidas).
        if (r.estado() == ResultadoAltaMl.Estado.CREADO
                && r.itemId() != null
                && !Boolean.TRUE.equals(producto.getActivo())
                && !updateItemStatus(r.itemId(), "paused")) {
            return r.conAdvertencia(MercadoLibreService.concatAdv(r.advertencia(), "estado de publicación no actualizado (no se pudo pausar)"));
        }
        return r;
```

5. En `actualizarItemEnMl`, filtrar antes, usar las válidas en el lambda de imágenes y aplicar rechazadas. Cambiar el cuerpo (líneas ~1762-1795) para capturar el filtro y el resultado:
```java
    public ResultadoAltaMl actualizarItemEnMl(ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto, String mla) {
        if (!isConfigured()) return ResultadoAltaMl.error("Mercado Libre no configurado");
        verificarTokens();
        ImagenService.FiltroImagenes filtro = imagenService.filtrarParaCanal(producto.getSku(), EXT_ML);
        ResultadoAltaMl r = actualizarItemEnMlCore(
                producto, mla,
                this::leerSoldQuantity,
                (m, title) -> {
                    try { retryHandler.putJson("/items/" + m, () -> tokens.accessToken,
                            objectMapper.writeValueAsString(Map.of("title", title))); }
                    catch (Exception e) { throw new RuntimeException("título: " + e.getMessage(), e); }
                },
                (m, plainText) -> {
                    try { retryHandler.putJson("/items/" + m + "/description", () -> tokens.accessToken,
                            objectMapper.writeValueAsString(Map.of("plain_text", plainText))); }
                    catch (Exception e) { throw new RuntimeException("descripción: " + e.getMessage(), e); }
                },
                this::actualizarPrecioItemConDeteccionVariaciones,
                sku -> {
                    List<String> ids = new ArrayList<>();
                    for (String filename : filtro.validas()) {
                        String picId = subirImagenItem(filename);
                        if (picId != null && !picId.isBlank()) ids.add(picId);
                    }
                    return ids;
                },
                (m, pictureIds) -> {
                    try {
                        List<Map<String, Object>> pics = new ArrayList<>();
                        for (String id : pictureIds) pics.add(Map.of("id", id));
                        retryHandler.putJson("/items/" + m, () -> tokens.accessToken,
                                objectMapper.writeValueAsString(Map.of("pictures", pics)));
                    } catch (Exception e) { throw new RuntimeException("imágenes: " + e.getMessage(), e); }
                },
                this::updateItemStatus);
        return aplicarRechazadasImagenes(r, filtro.rechazadas());
    }
```

- [ ] **Step 4: Correr el test para verificar que pasa**

Run: `./mvnw -o test -Dtest=MlRechazadasImagenesTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Compilar y correr los tests de ML existentes (no romper el core)**

Run: `./mvnw -o test -Dtest=MlRechazadasImagenesTest,MercadoLibre*Test`
Expected: BUILD SUCCESS, todos verdes.

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlRechazadasImagenesTest.java
git commit -m "feat(ml): filtrar imagenes por formato/tamano y reportar omitidas"
```

---

### Task 5: Frontend — servicio + bloque de feedback en el formulario

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

**Interfaces:**
- Consumes: `GET /api/imagenes/detalle/{sku}` (Task 2); estados `subirMl`, `subirKtHogar`, `subirKtGastro`, `sku`, `editandoProductoId`, `isModalOpen` (existentes en `page.tsx`).
- Produces: `getImagenDetalleAPI(sku): Promise<ImagenDetalle[]>`.

- [ ] **Step 1: Agregar el servicio**

En `productosService.ts`, agregar (después de los imports / junto a otras APIs):

```ts
export type ImagenDetalle = { nombre: string; extension: string; bytes: number };

export async function getImagenDetalleAPI(sku: string): Promise<ImagenDetalle[]> {
	return fetchAPI(`${API_BASE_URL}/api/imagenes/detalle/${encodeURIComponent(sku)}`);
}
```

- [ ] **Step 2: Verificar typecheck del servicio**

Run (desde `supermaster-frontend/`): `npx tsc --noEmit`
Expected: sin errores nuevos.

- [ ] **Step 3: Agregar estado, carga y constantes en `page.tsx`**

1. Importar el servicio: en el import de `./productosService`, agregar `getImagenDetalleAPI, ImagenDetalle`.

2. Constantes (cerca del top del archivo, junto a otras constantes de módulo):
```ts
const EXT_ML = new Set(["jpg", "jpeg", "png"]);
const EXT_NUBE = new Set(["gif", "jpg", "jpeg", "png", "webp"]);
const MAX_BYTES_IMG = 10 * 1024 * 1024; // 10 MB
```

3. Estado (junto a los demás `useState` del componente):
```ts
const [imagenesDetectadas, setImagenesDetectadas] = useState<ImagenDetalle[]>([]);
```

4. Carga al cambiar el SKU (junto a los otros `useEffect` del modal). Carga cuando el modal está abierto y hay SKU; debounce para el alta:
```ts
useEffect(() => {
    if (!isModalOpen) { setImagenesDetectadas([]); return; }
    const skuTrim = sku.trim();
    if (!skuTrim) { setImagenesDetectadas([]); return; }
    const t = setTimeout(() => {
        getImagenDetalleAPI(skuTrim)
            .then(setImagenesDetectadas)
            .catch(() => setImagenesDetectadas([]));
    }, 400);
    return () => clearTimeout(t);
}, [sku, isModalOpen]);
```

- [ ] **Step 4: Renderizar el bloque de feedback (cerca de los checkboxes de canales)**

Agregar, debajo del grupo de checkboxes de canales en el JSX del modal, un bloque que muestre el conteo y los avisos. Insertar este fragmento (ubicarlo justo después del contenedor de los checkboxes `subirADux`/`subirKtHogar`/`subirKtGastro`/`subirMl`):

```tsx
{imagenesDetectadas.length > 0 && (
    <div className="text-xs text-slate-600 dark:text-slate-300 mt-1 space-y-0.5">
        <div className="font-medium">{imagenesDetectadas.length} imagen{imagenesDetectadas.length === 1 ? "" : "es"} detectada{imagenesDetectadas.length === 1 ? "" : "s"} para este SKU</div>
        {imagenesDetectadas.flatMap((img) => {
            const avisos: string[] = [];
            if (img.bytes > MAX_BYTES_IMG) avisos.push(`${img.nombre} supera 10 MB — no se subirá`);
            if (subirMl && !EXT_ML.has(img.extension)) avisos.push(`${img.nombre} — Mercado Libre no acepta .${img.extension}`);
            if ((subirKtHogar || subirKtGastro) && !EXT_NUBE.has(img.extension)) avisos.push(`${img.nombre} — Tienda Nube no acepta .${img.extension}`);
            return avisos.map((a, i) => (
                <div key={`${img.nombre}-${i}`} className="text-amber-600 dark:text-amber-400">⚠ {a}</div>
            ));
        })}
    </div>
)}
```

> Nota: el implementer debe localizar el contenedor real de los checkboxes de canales en `page.tsx` (estados `subirADux`, `subirKtHogar`, `subirKtGastro`, `subirMl`) e insertar el bloque inmediatamente después, dentro del mismo contenedor de la sección. Ajustar las clases para que matcheen el estilo de los mensajes/tooltips vecinos.

- [ ] **Step 5: Verificar typecheck**

Run (desde `supermaster-frontend/`): `npx tsc --noEmit`
Expected: sin errores.

- [ ] **Step 6: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/page.tsx
git commit -m "feat(front/productos): aviso de imagenes detectadas + formato/tamano por canal"
```

---

## Verificación final

- [ ] `./mvnw -o test -Dtest=ImagenServiceFiltroTest,ImagenControllerDetalleTest,TiendaNubeAdvertenciaImagenesTest,MlRechazadasImagenesTest` → todos verdes.
- [ ] `npx tsc --noEmit` (frontend) → sin errores.
- [ ] **Smoke (usuario):** un producto con una imagen `.webp` y otra `>10MB` → ML omite ambas, Nube omite solo la `>10MB`; las advertencias aparecen en el resultado del export; el form muestra el conteo y los avisos según los canales tildados.

## Notas de diseño
- El filtrado vive en `ImagenService` (un solo lugar); cada canal aporta su `Set` de extensiones. Las rechazadas se describen con `describirRechazadas` (compartido).
- ML no cambia la firma de su core testeable: el filtrado ocurre en los métodos públicos (`crearItemEnMl`/`actualizarItemEnMl`), que pasan las válidas y concatenan las rechazadas con `aplicarRechazadasImagenes`.
- El front replica las reglas (constantes espejo) solo para el aviso preventivo; el backend sigue siendo el filtro autoritativo.
