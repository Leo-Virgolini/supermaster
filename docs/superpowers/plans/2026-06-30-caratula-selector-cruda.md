# Carátula: selector de cruda + progreso + config IA — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mejorar el flujo de carátula con IA (selector de imagen cruda, progreso por fases, tiempo, diagnóstico de carpeta, limpieza de crudas) y la pantalla de config IA (select de modelo, reset de uso, modelo en uso junto a los botones).

**Architecture:** Backend Spring Boot añade endpoints para listar/servir crudas, generar con una cruda elegida, borrar crudas al guardar y resetear el uso acumulado. Frontend React añade el panel selector en el modal de producto, el progreso por fases y los controles de config IA.

**Tech Stack:** Java 25 / Spring Boot 4 / JUnit 5 + Mockito + AssertJ (backend); Next.js / React / TypeScript / Tailwind (frontend).

## Global Constraints

- Paquete base backend: `ar.com.leo.super_master_backend` (underscore).
- Formato de error backend: `{ "message": "...", "path": "/api/..." }`; status codes estándar (200/201/204/400/404/409/500).
- Tests backend se corren **offline** con el Maven instalado: `mvn -o test` desde `supermaster-backend/` (el wrapper `mvnw` falla por red en el sandbox).
- Records DTO: los constructores son posicionales; agregar un componente rompe `new XDTO(...)` en tests — compilar con `mvn -o test` (no solo `compile`).
- Permisos: lectura `Permisos.INTEGRACIONES_VER`, escritura `Permisos.INTEGRACIONES_EDITAR`.
- Carpeta cruda = `app.imagenes-raw-dir` (`rawDir`); carpeta de imágenes publicadas = `app.imagenes-dir` (`baseDir`). Son distintas.
- Modelos de imagen (value = id que acepta OpenAI): `gpt-image-2`, `gpt-image-1.5`, `gpt-image-1`, `gpt-image-1-mini`.

---

## Task 1: Reset de uso acumulado de imagen (backend)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/repository/ImagenUsoRepository.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/ImagenUsoService.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/controller/ImagenIaController.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/ImagenUsoServiceResetTest.java`

**Interfaces:**
- Produces: `ImagenUsoRepository.reset() : int`, `ImagenUsoService.reset() : void`, endpoint `POST /api/imagen-ia/uso/reset` → 204.

- [ ] **Step 1: Escribir el test que falla**

Crear `ImagenUsoServiceResetTest.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.repository.ImagenUsoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImagenUsoServiceResetTest {

    @Mock ImagenUsoRepository repository;
    @Mock ImagenIaConfigService configService;
    @InjectMocks ImagenUsoService service;

    @Test
    void reset_llamaAlRepositorio() {
        when(repository.reset()).thenReturn(1);
        service.reset();
        verify(repository).reset();
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `mvn -o test -Dtest=ImagenUsoServiceResetTest` (desde `supermaster-backend/`)
Expected: FAIL — `reset()` no existe (no compila).

- [ ] **Step 3: Implementar**

En `ImagenUsoRepository.java`, agregar dentro de la interfaz:

```java
    @Modifying
    @Query("UPDATE ImagenUso s SET s.consultas = 0, s.tokensEntrada = 0, " +
           "s.tokensSalida = 0, s.costoUsd = 0 WHERE s.id = 1")
    int reset();
```

En `ImagenUsoService.java`, agregar:

```java
    @Transactional
    public void reset() {
        if (repository.reset() == 0) {
            log.warn("imagen_uso id=1 no existe; el reset no afectó filas");
        }
    }
```

En `ImagenIaController.java`, agregar el endpoint:

```java
    @PostMapping("/uso/reset")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Void> resetUso() {
        usoService.reset();
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `mvn -o test -Dtest=ImagenUsoServiceResetTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/repository/ImagenUsoRepository.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/ImagenUsoService.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/controller/ImagenIaController.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/ImagenUsoServiceResetTest.java
git commit -m "feat(imagen-ia): reset de uso acumulado de carátula"
```

---

## Task 2: Reset de uso acumulado de SEO (backend)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/repository/SeoUsoRepository.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/SeoUsoService.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/controller/SeoController.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/SeoUsoServiceResetTest.java`

**Interfaces:**
- Produces: `SeoUsoRepository.reset() : int`, `SeoUsoService.reset() : void`, endpoint `POST /api/seo/uso/reset` → 204.

> Antes de implementar, abrir `SeoUsoRepository.java` y `SeoUsoService.java` para copiar el nombre exacto de la entidad de uso (p. ej. `SeoUso`) y de sus campos; el método `registrar(...)` existente muestra el patrón `@Modifying @Query`. Usar esos nombres en el `UPDATE`.

- [ ] **Step 1: Escribir el test que falla**

Crear `SeoUsoServiceResetTest.java` (espejo del de imagen; ajustar los mocks a las dependencias reales de `SeoUsoService` — abrir la clase para ver su constructor):

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.repository.SeoUsoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeoUsoServiceResetTest {

    @Mock SeoUsoRepository repository;
    @InjectMocks SeoUsoService service;

    @Test
    void reset_llamaAlRepositorio() {
        when(repository.reset()).thenReturn(1);
        service.reset();
        verify(repository).reset();
    }
}
```

> Si `SeoUsoService` tiene más dependencias en su constructor, agregarlas como `@Mock` (Mockito las inyecta por `@InjectMocks`).

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `mvn -o test -Dtest=SeoUsoServiceResetTest`
Expected: FAIL — `reset()` no existe.

- [ ] **Step 3: Implementar**

En `SeoUsoRepository.java`, agregar (ajustar nombre de entidad/campos si difieren):

```java
    @Modifying
    @Query("UPDATE SeoUso s SET s.consultas = 0, s.tokensEntrada = 0, " +
           "s.tokensSalida = 0, s.costoUsd = 0 WHERE s.id = 1")
    int reset();
```

En `SeoUsoService.java`, agregar:

```java
    @Transactional
    public void reset() {
        if (repository.reset() == 0) {
            log.warn("seo_uso id=1 no existe; el reset no afectó filas");
        }
    }
```

> Si la clase no tiene `log`, agregar `@Slf4j` (lombok) en la clase, como en `ImagenUsoService`.

En `SeoController.java`, agregar:

```java
    @PostMapping("/uso/reset")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Void> resetUso() {
        seoUsoService.reset();
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `mvn -o test -Dtest=SeoUsoServiceResetTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/repository/SeoUsoRepository.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/SeoUsoService.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/controller/SeoController.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/SeoUsoServiceResetTest.java
git commit -m "feat(seo): reset de uso acumulado de SEO"
```

---

## Task 3: `resolverCrudasPorSku` (backend)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceCrudasTest.java`

**Interfaces:**
- Produces: `ImagenService.resolverCrudasPorSku(String sku) : List<String>` — nombres de archivos crudos del SKU en `rawDir`, principal (`{sku}.ext`) primero, luego `{sku}_N.ext` por N ascendente. Lista vacía si no hay. Lanza `IllegalArgumentException` si el SKU es inseguro.

- [ ] **Step 1: Escribir el test que falla**

Crear `ImagenServiceCrudasTest.java` (usa `@TempDir` como rawDir; el constructor de `ImagenService` toma `imagenesDir, ttl, imagenesRawDir`):

```java
package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagenServiceCrudasTest {

    private ImagenService nueva(Path baseDir, Path rawDir) {
        return new ImagenService(baseDir.toString(), 60000L, rawDir.toString());
    }

    @Test
    void resolverCrudas_principalPrimeroLuegoAdicionales(@TempDir Path baseDir, @TempDir Path rawDir) throws Exception {
        Files.writeString(rawDir.resolve("ABC.jpg"), "x");
        Files.writeString(rawDir.resolve("ABC_2.png"), "x");
        Files.writeString(rawDir.resolve("ABC_1.jpg"), "x");
        Files.writeString(rawDir.resolve("OTRO.jpg"), "x");

        List<String> r = nueva(baseDir, rawDir).resolverCrudasPorSku("ABC");

        assertThat(r).containsExactly("ABC.jpg", "ABC_1.jpg", "ABC_2.png");
    }

    @Test
    void resolverCrudas_skuSinArchivos_vacio(@TempDir Path baseDir, @TempDir Path rawDir) {
        assertThat(nueva(baseDir, rawDir).resolverCrudasPorSku("NADA")).isEmpty();
    }

    @Test
    void resolverCrudas_skuInseguro_lanza(@TempDir Path baseDir, @TempDir Path rawDir) {
        assertThatThrownBy(() -> nueva(baseDir, rawDir).resolverCrudasPorSku("../x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `mvn -o test -Dtest=ImagenServiceCrudasTest`
Expected: FAIL — `resolverCrudasPorSku` no existe.

- [ ] **Step 3: Implementar**

En `ImagenService.java`, agregar (reusa `validarNombreSeguro`, `slotDe`, `prioridadExtension`, ya presentes en la clase):

```java
    /**
     * Lista las imágenes crudas del SKU en la carpeta cruda (rawDir): principal ({sku}.ext) primero,
     * luego adicionales ({sku}_N.ext) por N ascendente. Ante varias extensiones de un slot, gana la de
     * mayor prioridad (jpg primero). Lista vacía si no hay. Case-insensitive.
     */
    public List<String> resolverCrudasPorSku(String sku) {
        validarNombreSeguro(sku);
        if (!Files.isDirectory(rawDir)) {
            return List.of();
        }
        String skuLower = sku.trim().toLowerCase(Locale.ROOT);
        TreeMap<Integer, String> porSlot = new TreeMap<>();
        try (Stream<Path> entries = Files.list(rawDir)) {
            for (Path p : (Iterable<Path>) entries::iterator) {
                if (!Files.isRegularFile(p)) continue;
                String nombre = p.getFileName().toString();
                if (!esImagen(nombre)) continue;
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
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo listar la carpeta cruda", e);
        }
        return List.copyOf(porSlot.values());
    }
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `mvn -o test -Dtest=ImagenServiceCrudasTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceCrudasTest.java
git commit -m "feat(imagen): resolverCrudasPorSku lista crudas del SKU en rawDir"
```

---

## Task 4: `eliminarCrudasPorSku` + `EstadoCarpeta` (backend)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceCarpetaTest.java`

**Interfaces:**
- Consumes: `resolverCrudasPorSku` (Task 3).
- Produces: `ImagenService.eliminarCrudasPorSku(String sku) : int` (cuántas borró); `ImagenService.EstadoCarpeta` (record `ruta, existe, esDirectorio, legible, escribible`); `ImagenService.estadoCrudaDir() : EstadoCarpeta`; `ImagenService.estadoDestinoDir() : EstadoCarpeta`.

- [ ] **Step 1: Escribir el test que falla**

Crear `ImagenServiceCarpetaTest.java`:

```java
package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenServiceCarpetaTest {

    private ImagenService nueva(Path baseDir, Path rawDir) {
        return new ImagenService(baseDir.toString(), 60000L, rawDir.toString());
    }

    @Test
    void eliminarCrudas_borraTodasLasDelSku(@TempDir Path baseDir, @TempDir Path rawDir) throws Exception {
        Files.writeString(rawDir.resolve("ABC.jpg"), "x");
        Files.writeString(rawDir.resolve("ABC_1.jpg"), "x");
        Files.writeString(rawDir.resolve("OTRO.jpg"), "x");

        int n = nueva(baseDir, rawDir).eliminarCrudasPorSku("ABC");

        assertThat(n).isEqualTo(2);
        assertThat(Files.exists(rawDir.resolve("ABC.jpg"))).isFalse();
        assertThat(Files.exists(rawDir.resolve("ABC_1.jpg"))).isFalse();
        assertThat(Files.exists(rawDir.resolve("OTRO.jpg"))).isTrue();
    }

    @Test
    void eliminarCrudas_sinArchivos_cero(@TempDir Path baseDir, @TempDir Path rawDir) {
        assertThat(nueva(baseDir, rawDir).eliminarCrudasPorSku("NADA")).isZero();
    }

    @Test
    void estadoCrudaDir_existente_legible(@TempDir Path baseDir, @TempDir Path rawDir) {
        ImagenService.EstadoCarpeta e = nueva(baseDir, rawDir).estadoCrudaDir();
        assertThat(e.existe()).isTrue();
        assertThat(e.esDirectorio()).isTrue();
        assertThat(e.legible()).isTrue();
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `mvn -o test -Dtest=ImagenServiceCarpetaTest`
Expected: FAIL — métodos no existen.

- [ ] **Step 3: Implementar**

En `ImagenService.java`, agregar:

```java
    /** Estado de una carpeta para diagnóstico en el frontend. */
    public record EstadoCarpeta(String ruta, boolean existe, boolean esDirectorio,
                                boolean legible, boolean escribible) {}

    private static EstadoCarpeta estadoDe(Path dir) {
        return new EstadoCarpeta(
                dir.toString(),
                Files.exists(dir),
                Files.isDirectory(dir),
                Files.isReadable(dir),
                Files.isWritable(dir));
    }

    /** Diagnóstico de la carpeta cruda (entrada). */
    public EstadoCarpeta estadoCrudaDir() { return estadoDe(rawDir); }

    /** Diagnóstico de la carpeta destino (donde se guarda la carátula). */
    public EstadoCarpeta estadoDestinoDir() { return estadoDe(baseDir); }

    /**
     * Borra todas las crudas del SKU de rawDir. Best-effort: si el borrado de alguna falla,
     * loguea y continúa. Devuelve cuántas borró efectivamente.
     */
    public int eliminarCrudasPorSku(String sku) {
        validarNombreSeguro(sku);
        int borradas = 0;
        for (String nombre : resolverCrudasPorSku(sku)) {
            try {
                if (Files.deleteIfExists(rawDir.resolve(nombre))) borradas++;
            } catch (IOException e) {
                log.warn("No se pudo borrar la cruda {}: {}", nombre, e.getMessage());
            }
        }
        return borradas;
    }
```

> `ImagenService` no tiene logger. Agregar `@Slf4j` (lombok) sobre la clase y el import `lombok.extern.slf4j.Slf4j`.

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `mvn -o test -Dtest=ImagenServiceCarpetaTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceCarpetaTest.java
git commit -m "feat(imagen): eliminarCrudasPorSku + diagnóstico de carpetas"
```

---

## Task 5: `CaratulaService.generar(sku, cruda)` + borrado al guardar (backend)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/CaratulaService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/CaratulaServiceTest.java`

**Interfaces:**
- Consumes: `ImagenService.resolverCrudasPorSku`, `ImagenService.eliminarCrudasPorSku`, `ImagenService.leerCrudaBytes`, `ImagenService.guardarCaratula`, `OpenAiImagenService.generarCaratula`.
- Produces: `CaratulaService.generar(String sku, String crudaNombre) : GeneracionCaratula` (si `crudaNombre` no es null, valida que pertenezca al SKU y la usa; si null, resuelve automático). `generar(String sku)` delega a `generar(sku, null)`. `guardar(sku, datos)` borra las crudas del SKU tras guardar OK.

- [ ] **Step 1: Escribir el test que falla**

Crear `CaratulaServiceTest.java`:

```java
package ar.com.leo.super_master_backend.dominio.imagen.service;

import ar.com.leo.super_master_backend.apis.openai.entity.ImagenConfig;
import ar.com.leo.super_master_backend.apis.openai.service.ImagenIaConfigService;
import ar.com.leo.super_master_backend.apis.openai.service.OpenAiImagenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaratulaServiceTest {

    @Mock ImagenService imagenService;
    @Mock OpenAiImagenService openAiImagenService;
    @Mock ImagenIaConfigService configService;
    @InjectMocks CaratulaService service;

    @Test
    void generar_conCrudaElegida_usaEsaCruda() {
        when(imagenService.resolverCrudasPorSku("ABC")).thenReturn(List.of("ABC.jpg", "ABC_1.jpg"));
        when(imagenService.leerCrudaBytes("ABC_1.jpg")).thenReturn(new byte[]{1});
        when(openAiImagenService.generarCaratula(new byte[]{1}, "ABC_1.jpg")).thenReturn(new byte[]{2});

        GeneracionCaratula g = service.generar("ABC", "ABC_1.jpg");

        assertThat(g.crudaNombre()).isEqualTo("ABC_1.jpg");
        assertThat(g.generada()).containsExactly(2);
    }

    @Test
    void generar_crudaNoPerteneceAlSku_lanza() {
        when(imagenService.resolverCrudasPorSku("ABC")).thenReturn(List.of("ABC.jpg"));
        assertThatThrownBy(() -> service.generar("ABC", "OTRO.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guardar_borraCrudasTrasGuardarOk() {
        ImagenConfig cfg = new ImagenConfig();
        cfg.setOutputFormat("jpeg");
        when(configService.cargar()).thenReturn(cfg);

        service.guardar("ABC", new byte[]{9});

        verify(imagenService).guardarCaratula("ABC", new byte[]{9}, "jpg");
        verify(imagenService).eliminarCrudasPorSku("ABC");
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `mvn -o test -Dtest=CaratulaServiceTest`
Expected: FAIL — la sobrecarga `generar(sku, cruda)` no existe y `guardar` no borra crudas.

- [ ] **Step 3: Implementar**

En `CaratulaService.java`, reemplazar el método `generar` y `guardar`:

```java
    /** Genera (sin guardar) a partir de la cruda automática del SKU. */
    public GeneracionCaratula generar(String sku) {
        return generar(sku, null);
    }

    /**
     * Genera (sin guardar) a partir de una cruda elegida del SKU. Si {@code crudaNombre} es null,
     * resuelve la cruda automáticamente. Si no es null, valida que pertenezca al SKU.
     */
    public GeneracionCaratula generar(String sku, String crudaNombre) {
        String nombre;
        if (crudaNombre == null) {
            nombre = imagenService.resolverCrudaPorSku(sku);
            if (nombre == null) throw new NotFoundException("No hay imagen cruda para el SKU " + sku);
        } else {
            if (!imagenService.resolverCrudasPorSku(sku).contains(crudaNombre))
                throw new IllegalArgumentException("La imagen elegida no pertenece al SKU " + sku);
            nombre = crudaNombre;
        }
        byte[] cruda = imagenService.leerCrudaBytes(nombre);
        byte[] generada = openAiImagenService.generarCaratula(cruda, nombre);
        return new GeneracionCaratula(cruda, nombre, generada);
    }

    public void guardar(String sku, byte[] datos) {
        imagenService.guardarCaratula(sku, datos, extDe(formato()));
        imagenService.eliminarCrudasPorSku(sku);
    }
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `mvn -o test -Dtest=CaratulaServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/CaratulaService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/CaratulaServiceTest.java
git commit -m "feat(caratula): generar con cruda elegida + borrar crudas al guardar"
```

---

## Task 6: Endpoints de crudas y miniatura (backend)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/CrudasDisponiblesDTO.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/controller/ImagenController.java`

**Interfaces:**
- Consumes: `resolverCrudasPorSku`, `estadoCrudaDir`, `estadoDestinoDir`, `leerCrudaBytes`, `CaratulaService.generar(sku, cruda)`.
- Produces:
  - `GET /api/imagenes/caratula/crudas/{sku}` → `CrudasDisponiblesDTO`.
  - `GET /api/imagenes/cruda/{nombre}` → bytes (Content-Type por extensión).
  - `POST /api/imagenes/caratula/generar/{sku}?cruda={nombre}` (param opcional).

- [ ] **Step 1: Crear el DTO**

Crear `CrudasDisponiblesDTO.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.dto;

import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.EstadoCarpeta;

import java.util.List;

/** Crudas disponibles de un SKU + diagnóstico de las carpetas, para el selector de carátula. */
public record CrudasDisponiblesDTO(EstadoCarpeta crudaDir, EstadoCarpeta destinoDir, List<String> imagenes) {}
```

- [ ] **Step 2: Modificar el controller**

En `ImagenController.java`:

1. Cambiar el método `generarCaratula` para aceptar `cruda` opcional:

```java
    @PostMapping("/caratula/generar/{sku}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<CaratulaGeneradaDTO> generarCaratula(@PathVariable String sku,
                                                               @RequestParam(required = false) String cruda) {
        GeneracionCaratula g = caratulaService.generar(sku, cruda);
        String generadaB64 = Base64.getEncoder().encodeToString(g.generada());
        String crudaB64 = Base64.getEncoder().encodeToString(g.cruda());
        return ResponseEntity.ok(new CaratulaGeneradaDTO(
                generadaB64, caratulaService.formato(), crudaB64, subtipoMimeDe(g.crudaNombre())));
    }
```

2. Agregar el endpoint de crudas disponibles:

```java
    @GetMapping("/caratula/crudas/{sku}")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<CrudasDisponiblesDTO> crudas(@PathVariable String sku) {
        return ResponseEntity.ok(new CrudasDisponiblesDTO(
                imagenService.estadoCrudaDir(),
                imagenService.estadoDestinoDir(),
                imagenService.resolverCrudasPorSku(sku)));
    }
```

3. Agregar el endpoint que sirve la miniatura cruda:

```java
    @GetMapping("/cruda/{nombre}")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<byte[]> cruda(@PathVariable String nombre) {
        byte[] bytes = imagenService.leerCrudaBytes(nombre);   // valida nombre seguro internamente
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("image/" + subtipoMimeDe(nombre)))
                .body(bytes);
    }
```

Agregar el import: `import ar.com.leo.super_master_backend.apis.openai.dto.CrudasDisponiblesDTO;`

- [ ] **Step 3: Compilar y correr la suite**

Run: `mvn -o test` (desde `supermaster-backend/`)
Expected: compila y la suite pasa (los records con constructor posicional no rompieron nada).

- [ ] **Step 4: Smoke manual con el backend levantado**

Levantar el backend (perfil dev) y verificar:
- `GET /api/imagenes/caratula/crudas/{sku}` con un SKU que tenga cruda → JSON con `crudaDir/destinoDir/imagenes`.
- `GET /api/imagenes/cruda/{nombre}` → la imagen.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/CrudasDisponiblesDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/controller/ImagenController.java
git commit -m "feat(caratula): endpoints de crudas disponibles, miniatura y generar con cruda"
```

---

## Task 7: Frontend — services y tipos de crudas/reset

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`
- Modify: `supermaster-frontend/src/app/config-ia/seoService.ts`

**Interfaces:**
- Produces (productosService):
  - `type EstadoCarpeta = { ruta: string; existe: boolean; esDirectorio: boolean; legible: boolean; escribible: boolean }`
  - `type CrudasDisponibles = { crudaDir: EstadoCarpeta; destinoDir: EstadoCarpeta; imagenes: string[] }`
  - `getCrudasAPI(sku: string): Promise<CrudasDisponibles>`
  - `generarCaratulaAPI(sku, crudaNombre?)` con `?cruda=`
  - `crudaMiniaturaURL(nombre: string): string`
- Produces (seoService): `resetImagenUsoAPI()`, `resetSeoUsoAPI()`.

- [ ] **Step 1: productosService — tipos y funciones**

En `productosService.ts`, reemplazar `generarCaratulaAPI` y agregar lo nuevo:

```ts
export type EstadoCarpeta = { ruta: string; existe: boolean; esDirectorio: boolean; legible: boolean; escribible: boolean };
export type CrudasDisponibles = { crudaDir: EstadoCarpeta; destinoDir: EstadoCarpeta; imagenes: string[] };

export async function getCrudasAPI(sku: string): Promise<CrudasDisponibles> {
	const r = await fetchAPI(`${API_BASE_URL}/api/imagenes/caratula/crudas/${encodeURIComponent(sku)}`);
	if (!r.ok) throw new Error(await extraerMensajeError(r, "No se pudieron leer las imágenes crudas"));
	return r.json();
}

export function crudaMiniaturaURL(nombre: string): string {
	return `${API_BASE_URL}/api/imagenes/cruda/${encodeURIComponent(nombre)}`;
}

export async function generarCaratulaAPI(sku: string, crudaNombre?: string): Promise<{ imagenBase64: string; formato: string; crudaBase64: string; crudaFormato: string }> {
	const q = crudaNombre ? `?cruda=${encodeURIComponent(crudaNombre)}` : "";
	const r = await fetchAPI(`${API_BASE_URL}/api/imagenes/caratula/generar/${encodeURIComponent(sku)}${q}`, { method: "POST" });
	if (!r.ok) throw new Error(await extraerMensajeError(r, "No se pudo generar la carátula"));
	return r.json();
}
```

- [ ] **Step 2: seoService — resets**

En `seoService.ts`, agregar:

```ts
export const resetImagenUsoAPI = async (): Promise<void> => {
    const res = await fetchAPI(`${IMAGEN_URL}/uso/reset`, { method: "POST" });
    if (!res.ok) throw new Error("Error al resetear el uso de carátula");
};

export const resetSeoUsoAPI = async (): Promise<void> => {
    const res = await fetchAPI(`${API_URL}/uso/reset`, { method: "POST" });
    if (!res.ok) throw new Error("Error al resetear el uso de SEO");
};
```

- [ ] **Step 3: Verificar tipos (build)**

Run: `npm run build` (o `npm run lint`) desde `supermaster-frontend/`
Expected: sin errores de TypeScript.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/config-ia/seoService.ts
git commit -m "feat(front): services de crudas, miniatura, generar con cruda y reset de uso"
```

---

## Task 8: Frontend — config IA: select de modelo, reset de uso, pestaña por query

**Files:**
- Modify: `supermaster-frontend/src/app/config-ia/types.ts`
- Modify: `supermaster-frontend/src/app/config-ia/useSeoIa.ts`
- Modify: `supermaster-frontend/src/app/config-ia/page.tsx`

**Interfaces:**
- Consumes: `resetImagenUsoAPI`, `resetSeoUsoAPI` (Task 7).
- Produces: `MODEL_IMAGEN_OPCIONES` (types); `resetImagenUso()`, `resetSeoUso()`, `isResettingImagen`, `isResettingSeo` (hook).

- [ ] **Step 1: types — opciones de modelo**

En `types.ts`, agregar:

```ts
export const MODEL_IMAGEN_OPCIONES: { value: string; label: string }[] = [
    { value: "gpt-image-2", label: "GPT Image 2" },
    { value: "gpt-image-1.5", label: "GPT Image 1.5" },
    { value: "gpt-image-1", label: "GPT Image 1" },
    { value: "gpt-image-1-mini", label: "GPT Image 1 Mini" },
];
```

- [ ] **Step 2: hook — resets**

En `useSeoIa.ts`:
1. Importar `resetImagenUsoAPI, resetSeoUsoAPI` desde `./seoService`.
2. Agregar estados `const [isResettingSeo, setIsResettingSeo] = useState(false);` y `const [isResettingImagen, setIsResettingImagen] = useState(false);`.
3. Agregar funciones (refrescan el uso desde el backend tras resetear):

```ts
    const resetSeoUso = async () => {
        setIsResettingSeo(true);
        try {
            await resetSeoUsoAPI();
            setUso(await getSeoUsoAPI());
            notificar.success("Uso de SEO reseteado");
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "No se pudo resetear el uso de SEO"));
        } finally {
            setIsResettingSeo(false);
        }
    };

    const resetImagenUso = async () => {
        setIsResettingImagen(true);
        try {
            await resetImagenUsoAPI();
            setImagenUso(await getImagenUsoAPI());
            notificar.success("Uso de carátula reseteado");
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "No se pudo resetear el uso de carátula"));
        } finally {
            setIsResettingImagen(false);
        }
    };
```

4. Agregarlas al `return { ... }`: `resetSeoUso, resetImagenUso, isResettingSeo, isResettingImagen`.

- [ ] **Step 3: page.tsx — select de modelo, botón reset, pestaña por query**

En `page.tsx`:

1. Importar `MODEL_IMAGEN_OPCIONES` desde `./types` y desestructurar del hook `resetSeoUso, resetImagenUso, isResettingSeo, isResettingImagen`.

2. Pestaña inicial por query param. Donde se inicializa `tab` (estado), leerlo de la URL:

```ts
import { useSearchParams } from "next/navigation";
// ...dentro del componente:
const searchParams = useSearchParams();
const tabInicial = searchParams.get("tab") === "caratula" ? "caratula" : "seo";
const [tab, setTab] = useState<"seo" | "caratula">(tabInicial);
```

> Si `tab` ya existía con otro inicializador, reemplazar solo el valor inicial por `tabInicial`.

3. Reemplazar el input de Modelo de imagen (Carátula) por un select que preserva el valor de BD si no está en la lista:

```tsx
                    <div><label className="text-xs text-slate-500">Modelo</label>
                        <select className={inputCls} value={imgModel} onChange={e => setImgModel(e.target.value)} disabled={isLoading}>
                            {!MODEL_IMAGEN_OPCIONES.some(o => o.value === imgModel) && imgModel !== "" && (
                                <option value={imgModel}>{imgModel}</option>
                            )}
                            {MODEL_IMAGEN_OPCIONES.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                        </select>
                    </div>
```

4. Agregar el botón "Resetear" en `usoBox`. Cambiar la firma de `usoBox` para recibir el handler y el flag:

```tsx
    const usoBox = (titulo: string, u: typeof uso, onReset: () => void, resetting: boolean) => (
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60">
            <div className="mb-3 flex items-center justify-between">
                <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">{titulo}</h2>
                <Button variant="light" onClick={() => { if (window.confirm("¿Resetear el uso acumulado a cero? No se puede deshacer.")) onReset(); }} disabled={resetting || isLoading}>
                    {resetting ? "Reseteando…" : "Resetear"}
                </Button>
            </div>
            {u ? (
                <div className="flex flex-wrap gap-x-8 gap-y-2 text-sm text-slate-700 dark:text-slate-200">
                    <span><b>Consultas:</b> {fmt(u.consultas)}</span>
                    <span><b>Tokens entrada:</b> {fmt(u.tokensEntrada)}</span>
                    <span><b>Tokens salida:</b> {fmt(u.tokensSalida)}</span>
                    <span><b>Costo:</b> US$ {u.costoUsd.toFixed(4)}</span>
                    <span className="text-slate-500 dark:text-slate-400">Modelo: {u.modelo} · in US${u.precioInput1m.toFixed(2)}/1M · out US${u.precioOutput1m.toFixed(2)}/1M</span>
                </div>
            ) : (
                <p className="text-sm text-slate-400">{isLoading ? "Cargando…" : "Sin datos de uso"}</p>
            )}
        </div>
    );
```

5. Actualizar las dos llamadas a `usoBox`:
- `{usoBox("Uso de IA — SEO (acumulado)", uso, resetSeoUso, isResettingSeo)}`
- `{usoBox("Uso de IA — Carátula (acumulado)", imagenUso, resetImagenUso, isResettingImagen)}`

- [ ] **Step 4: Verificar build**

Run: `npm run build` desde `supermaster-frontend/`
Expected: sin errores.

- [ ] **Step 5: Smoke manual**

Con backend + frontend levantados, en Configuración IA:
- Pestaña Carátula: el Modelo es un select con las 4 opciones; si la BD tenía un valor raro, aparece preservado.
- Botón "Resetear" en cada box pide confirmación y deja el uso en ceros.
- Abrir `/config-ia?tab=caratula` arranca en la pestaña Carátula.

- [ ] **Step 6: Commit**

```bash
git add supermaster-frontend/src/app/config-ia/
git commit -m "feat(config-ia): select de modelo, reset de uso y pestaña por query param"
```

---

## Task 9: Frontend — selector de cruda + progreso + tiempo (modal de producto)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `getCrudasAPI`, `crudaMiniaturaURL`, `generarCaratulaAPI(sku, cruda)` (Task 7), `CrudasDisponibles`.

- [ ] **Step 1: Estado nuevo**

Cerca de los estados de carátula existentes (`caratulaPreview`, etc.), agregar:

```tsx
    const [selectorCaratulaAbierto, setSelectorCaratulaAbierto] = useState(false);
    const [crudasDisp, setCrudasDisp] = useState<CrudasDisponibles | null>(null);
    const [crudaElegida, setCrudaElegida] = useState<string | null>(null);
    const [faseCaratula, setFaseCaratula] = useState("");
    const [duracionCaratula, setDuracionCaratula] = useState<number | null>(null);
```

Importar el tipo y funciones desde el service: `getCrudasAPI, crudaMiniaturaURL, type CrudasDisponibles` (sumar a los imports existentes de `productosService`).

- [ ] **Step 2: Helpers de progreso y apertura del selector**

Agregar, junto a `generarCaratula`:

```tsx
    // Fases de progreso (la llamada a OpenAI es opaca: la última fase permanece hasta que responde).
    const FASES_CARATULA: { ms: number; texto: string }[] = [
        { ms: 0, texto: "Preparando imagen…" },
        { ms: 800, texto: "Enviando a OpenAI…" },
        { ms: 2500, texto: "Generando carátula…" },
    ];

    const abrirSelectorCaratula = async () => {
        if (!sku.trim()) return;
        setSelectorCaratulaAbierto(true);
        setCrudasDisp(null);
        try {
            setCrudasDisp(await getCrudasAPI(sku.trim()));
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudieron leer las imágenes crudas");
        }
    };

    const fmtDuracion = (ms: number) => {
        const s = ms / 1000;
        if (s < 60) return `${s.toFixed(1)} s`;
        const m = Math.floor(s / 60);
        return `${m}:${String(Math.round(s % 60)).padStart(2, "0")}`;
    };
```

- [ ] **Step 3: Reescribir `generarCaratula` para recibir la cruda y manejar fases/tiempo**

Reemplazar la función `generarCaratula` existente:

```tsx
    const generarCaratula = async (crudaNombre?: string) => {
        const cruda = crudaNombre ?? crudaElegida;
        if (!sku.trim() || !cruda) return;
        setCrudaElegida(cruda);
        setGenerandoCaratula(true);
        setDuracionCaratula(null);
        const inicio = Date.now();
        const timers = FASES_CARATULA.map(f => setTimeout(() => setFaseCaratula(f.texto), f.ms));
        try {
            const r = await generarCaratulaAPI(sku.trim(), cruda);
            setCaratulaPreview(r.imagenBase64);
            setCaratulaFormato(r.formato);
            setCaratulaCruda(r.crudaBase64);
            setCaratulaCrudaFormato(r.crudaFormato);
            setDuracionCaratula(Date.now() - inicio);
            setSelectorCaratulaAbierto(false);
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo generar la carátula");
        } finally {
            timers.forEach(clearTimeout);
            setFaseCaratula("");
            setGenerandoCaratula(false);
        }
    };
```

- [ ] **Step 4: UI — abrir selector desde el botón, panel selector, progreso, tiempo**

En el bloque de la sección de imágenes (donde hoy está el botón "Mejorar carátula con IA", ~líneas 1901-1935):

1. El botón inicial abre el selector en vez de generar:

```tsx
                                        {!caratulaPreview && !selectorCaratulaAbierto && (
                                            <Button variant="dark" onClick={abrirSelectorCaratula} disabled={generandoCaratula}>
                                                <SparklesIcon className="h-4 w-4" /> Mejorar carátula con IA
                                            </Button>
                                        )}
```

2. Panel selector (debajo del botón). Mostrar diagnóstico + miniaturas + estado de generación:

```tsx
                                        {selectorCaratulaAbierto && !caratulaPreview && (
                                            <div className="mt-3 rounded-2xl border border-slate-200 p-3 dark:border-slate-700">
                                                {!crudasDisp ? (
                                                    <div className="flex items-center gap-2 text-xs text-slate-400"><SpinnerIcon /> Leyendo carpeta cruda…</div>
                                                ) : (
                                                    <>
                                                        <div className="mb-2 text-xs text-slate-500 dark:text-slate-400">
                                                            <div>{crudasDisp.crudaDir.existe && crudasDisp.crudaDir.legible ? "✓" : "⚠"} Carpeta cruda: {crudasDisp.crudaDir.ruta} {crudasDisp.crudaDir.legible ? "(lectura OK)" : "(sin acceso de lectura)"}</div>
                                                            <div>{crudasDisp.destinoDir.escribible ? "✓" : "⚠"} Carpeta destino: {crudasDisp.destinoDir.escribible ? "escritura OK" : "sin acceso de escritura"}</div>
                                                        </div>
                                                        {generandoCaratula ? (
                                                            <div className="flex items-center gap-2 px-1 py-3 text-sm text-slate-500"><SpinnerIcon /> {faseCaratula || "Generando…"}</div>
                                                        ) : crudasDisp.imagenes.length === 0 ? (
                                                            <p className="text-xs text-amber-600 dark:text-amber-400">No hay imágenes crudas para este SKU en la carpeta.</p>
                                                        ) : (
                                                            <div className="flex flex-wrap gap-2">
                                                                {crudasDisp.imagenes.map(nombre => (
                                                                    <button key={nombre} type="button" onClick={() => generarCaratula(nombre)} title={nombre}
                                                                        className="h-20 w-20 overflow-hidden rounded-xl border border-slate-200 hover:border-blue-400 dark:border-slate-700">
                                                                        <img src={crudaMiniaturaURL(nombre)} alt={nombre} loading="lazy" className="h-full w-full bg-white object-contain" />
                                                                    </button>
                                                                ))}
                                                            </div>
                                                        )}
                                                        {!generandoCaratula && (
                                                            <div className="mt-2 flex justify-end">
                                                                <Button variant="light" onClick={() => setSelectorCaratulaAbierto(false)}>Cerrar</Button>
                                                            </div>
                                                        )}
                                                    </>
                                                )}
                                            </div>
                                        )}
```

3. En el preview de resultado (donde está el `figcaption` "Generada con IA"), mostrar el tiempo:

```tsx
                                                        <figcaption className="mb-1 text-center text-xs text-slate-500">Generada con IA{duracionCaratula != null ? ` · ${fmtDuracion(duracionCaratula)}` : ""}</figcaption>
```

4. El botón "Volver a generar" del preview ahora re-genera con la cruda elegida: cambiar su `onClick` a `() => generarCaratula()` (sin args; usa `crudaElegida`).

- [ ] **Step 5: Verificar build**

Run: `npm run build` desde `supermaster-frontend/`
Expected: sin errores.

- [ ] **Step 6: Smoke manual**

Con backend + frontend, abrir un producto con cruda:
- "Mejorar carátula con IA" abre el panel con diagnóstico + miniaturas.
- Click en una miniatura → progreso por fases → preview con "Generada con IA · X s".
- "Volver a generar" usa la misma cruda. "Aceptar" guarda (ver Task 10).

- [ ] **Step 7: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(caratula): selector de cruda, progreso por fases y tiempo de generación"
```

---

## Task 10: Frontend — refrescar tras aceptar (borrado de crudas) y modelo en uso junto a los botones

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `getImagenConfigAPI`, `getSeoConfigAPI` (de `config-ia/seoService` o un service equivalente accesible desde productos).

> Nota: `getImagenConfigAPI`/`getSeoConfigAPI` viven en `config-ia/seoService.ts`. Importarlas desde ahí (`@/app/config-ia/seoService`) o, si se prefiere no cruzar features, agregar dos funciones equivalentes en `productosService.ts` que peguen a `GET /api/imagen-ia/config` y `GET /api/seo/config`. Elegir lo que siga la convención del repo; por defecto, importarlas desde `config-ia/seoService`.

- [ ] **Step 1: Estado de modelos**

Agregar estado:

```tsx
    const [modelImagen, setModelImagen] = useState<string>("");
    const [modelSeo, setModelSeo] = useState<string>("");
```

- [ ] **Step 2: Cargar modelos al abrir el modal**

En el efecto que corre al abrir el modal en modo edición (junto a la carga de estado de canales), agregar:

```tsx
            getImagenConfigAPI().then(c => setModelImagen(c.model)).catch(() => {});
            getSeoConfigAPI().then(c => setModelSeo(c.model)).catch(() => {});
```

- [ ] **Step 3: Tras aceptar la carátula, refrescar el selector**

En `guardarCaratula` (tras `setCaratulaCacheBust`), limpiar lo del selector para reflejar que las crudas se borraron:

```tsx
            setSelectorCaratulaAbierto(false);
            setCrudasDisp(null);
            setCrudaElegida(null);
            setDuracionCaratula(null);
```

- [ ] **Step 4: Texto de modelo + link junto al botón de carátula**

Junto al botón "Mejorar carátula con IA" (Task 9, step 4.1), agregar el texto + link:

```tsx
                                        {modelImagen && (
                                            <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">
                                                Modelo: {modelImagen}{" "}
                                                <a href="/config-ia?tab=caratula" target="_blank" rel="noreferrer" className="text-blue-600 hover:underline dark:text-blue-400">config</a>
                                            </span>
                                        )}
```

- [ ] **Step 5: Texto de modelo + link junto a cada botón "Generar SEO con IA"**

Localizar el/los botón(es) "Generar SEO con IA" (en `renderSeoNube`). Junto a cada uno, agregar:

```tsx
                                        {modelSeo && (
                                            <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">
                                                Modelo: {modelSeo}{" "}
                                                <a href="/config-ia?tab=seo" target="_blank" rel="noreferrer" className="text-blue-600 hover:underline dark:text-blue-400">config</a>
                                            </span>
                                        )}
```

> Si `renderSeoNube` es una función que renderiza ambos canales, basta agregarlo una vez ahí para que aplique a HOGAR y GASTRO.

- [ ] **Step 6: Verificar build**

Run: `npm run build` desde `supermaster-frontend/`
Expected: sin errores.

- [ ] **Step 7: Smoke manual**

- Tras "Aceptar" la carátula, reabrir el selector → la lista de crudas quedó vacía (se borraron).
- Junto a los botones de carátula y SEO aparece "Modelo: …" con link que abre la config en la pestaña correcta (nueva pestaña).

- [ ] **Step 8: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(caratula): refresca selector tras aceptar y muestra modelo en uso + link a config"
```

---

## Self-Review

- **Cobertura del spec A:** selector de cruda (T6/T9), progreso por fases (T9), tiempo (T9), diagnóstico de carpeta (T4/T6/T9), endpoints crudas/miniatura/generar-con-cruda (T5/T6), borrado de crudas al aceptar (T5 backend + T10 refresh), select de modelo (T8), reset de uso SEO+Carátula (T1/T2/T8), modelo en uso + link (T10). ✔
- **Placeholders:** ninguno; todos los pasos tienen código real.
- **Consistencia de tipos:** `CrudasDisponibles`/`EstadoCarpeta` (front) reflejan `CrudasDisponiblesDTO`/`EstadoCarpeta` (back); `generar(sku, cruda)` con param `cruda` coincide entre controller y `generarCaratulaAPI`.
- **Nota de verificación:** confirmar nombres reales de `SeoUso`/`SeoUsoService`/`SeoUsoRepository` en Task 2 antes de implementar (el patrón es el de imagen).
