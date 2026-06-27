# Carátula de producto con OpenAI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Desde el modal de edición, generar con OpenAI (gpt-image-1) una carátula con fondo blanco a partir de la foto cruda del SKU, previsualizarla y, al confirmar, guardarla como `{SKU}.jpg` (1200×1200) en la carpeta de imágenes.

**Architecture:** Réplica del patrón OpenAI del SEO con credencial y cliente aparte (apis/openai): properties + bean RestClient de imágenes, `OpenAiImagenService` (multipart a `/images/edits`), prompt y uso en BD (`imagen_prompt`/`imagen_uso`) editables en la pantalla "SEO IA". Procesamiento de imagen puro (PNG→JPG 1200×1200, `javax.imageio`). `ImagenService` gana leer la cruda desde una carpeta nueva y escribir la carátula. Un `CaratulaService` orquesta; endpoints generar/guardar. Frontend: botón + preview en el modal.

**Tech Stack:** Java 25 / Spring Boot 4 / Maven / JUnit 5 / AssertJ / Jackson 3 / `javax.imageio`+`java.awt` (built-in); Next.js / React / TypeScript.

## Global Constraints

- Backend: tests con `mvn -o test` (offline) desde `supermaster-backend`; `mvn` del PATH (NO `mvnw`).
- API key de imágenes **separada** del SEO: archivo `secrets/openai_image_tokens.json` (formato `{"api_key": "..."}`), cargado en `app.secrets-dir`. NO va en properties.
- Modelo `gpt-image-1`, endpoint `/images/edits`, `size=1024x1024`; salida reescalada a **1200×1200 JPG**.
- Carpeta cruda de entrada: propiedad nueva `app.imagenes-raw-dir` (configurable, separada de `app.imagenes-dir`); archivo crudo `{SKU}.{ext}`. Salida: `{SKU}.jpg` en `app.imagenes-dir`.
- Generar NO guarda (devuelve base64 para preview); guardar escribe el archivo tras confirmación.
- Jackson 3: `tools.jackson.databind` (idiom `.asString(null)`/`.asLong(0)`).
- Permisos: `Permisos.INTEGRACIONES_VER` (lecturas) / `Permisos.INTEGRACIONES_EDITAR` (acciones), import `ar.com.leo.super_master_backend.config.Permisos`.
- Commits en español, estilo `tipo(scope): ...`, cerrando con:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- Frontend: `npm run lint` + `npx tsc --noEmit -p tsconfig.json`; sin errores nuevos.

## Estructura de archivos

- Crear `apis/openai/service/ImagenProcesador.java` — puro: PNG→JPG cuadrado.
- Crear `apis/openai/entity/ImagenPrompt.java`, `ImagenUso.java`.
- Crear `apis/openai/repository/ImagenPromptRepository.java`, `ImagenUsoRepository.java`.
- Crear `apis/openai/service/ImagenIaConfigService.java`, `ImagenUsoService.java`.
- Crear `apis/openai/config/OpenAiImageProperties.java`; modificar `OpenAiConfig.java` (bean nuevo).
- Crear `apis/openai/service/OpenAiImagenService.java` + `OpenAiImagenParser.java` (puro).
- Crear DTOs en `apis/openai/dto/`: `ImagenPromptDTO`, `ImagenPromptUpdateDTO`, `ImagenUsoDTO`, `CaratulaGeneradaDTO`, `CaratulaGuardarDTO`.
- Modificar `dominio/imagen/service/ImagenService.java` (leer cruda + escribir + invalidar) y `ImagenController.java` (endpoints) ; crear `dominio/imagen/service/CaratulaService.java`.
- Crear migración `src/main/resources/db/2026-06-26-imagen-ia.sql`; modificar `application.properties` (openai.image.*) y `application-dev.properties` (app.imagenes-raw-dir).
- Frontend: `productos/productosService.ts` (+2 llamadas), `productos/ProductoFormModal.tsx` (botón+preview), `seo-ia/*` (prompt de carátula + uso).

---

### Task 1: Procesamiento de imagen (PNG → JPG 1200×1200, puro)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/ImagenProcesador.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/ImagenProcesadorTest.java`

**Interfaces:**
- Produces: `static byte[] ImagenProcesador.aJpgCuadrada(byte[] imagenBytes, int lado)` — decodifica, aplana sobre blanco, reescala a `lado×lado`, devuelve JPG.

- [ ] **Step 1: Escribir el test que falla**

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenProcesadorTest {

    private static byte[] pngDe(int w, int h, int tipo) throws Exception {
        BufferedImage img = new BufferedImage(w, h, tipo);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(Color.RED); g.fillRect(0, 0, w, h); g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void aJpgCuadrada_devuelveJpgDelLadoPedido() throws Exception {
        byte[] png = pngDe(1024, 1024, BufferedImage.TYPE_INT_RGB);

        byte[] jpg = ImagenProcesador.aJpgCuadrada(png, 1200);

        BufferedImage res = ImageIO.read(new ByteArrayInputStream(jpg));
        assertThat(res).isNotNull();
        assertThat(res.getWidth()).isEqualTo(1200);
        assertThat(res.getHeight()).isEqualTo(1200);
    }

    @Test
    void aJpgCuadrada_aplanaTransparenciaSobreBlanco() throws Exception {
        // PNG con alfa (transparente) → el JPG no debe tener canal alfa y el fondo queda blanco.
        byte[] pngAlfa = pngDe(64, 64, BufferedImage.TYPE_INT_ARGB);

        byte[] jpg = ImagenProcesador.aJpgCuadrada(pngAlfa, 100);

        BufferedImage res = ImageIO.read(new ByteArrayInputStream(jpg));
        assertThat(res.getColorModel().hasAlpha()).isFalse();
        assertThat(res.getWidth()).isEqualTo(100);
    }
}
```

- [ ] **Step 2: Correr y ver fallar**

Run: `cd supermaster-backend && mvn -o test -Dtest=ImagenProcesadorTest`
Expected: FAIL — `ImagenProcesador` no existe.

- [ ] **Step 3: Implementar**

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Procesa la imagen devuelta por OpenAI: aplana sobre blanco, reescala a cuadrada y exporta JPG. Puro. */
public final class ImagenProcesador {

    private ImagenProcesador() {}

    public static byte[] aJpgCuadrada(byte[] imagenBytes, int lado) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imagenBytes));
            if (src == null) throw new IllegalArgumentException("No se pudo decodificar la imagen");
            BufferedImage canvas = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, lado, lado);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, lado, lado, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(canvas, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Error procesando la imagen", e);
        }
    }
}
```

- [ ] **Step 4: Correr y ver pasar**

Run: `cd supermaster-backend && mvn -o test -Dtest=ImagenProcesadorTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/ImagenProcesador.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/ImagenProcesadorTest.java
git commit -m "feat(imagen-ia): procesador puro PNG→JPG cuadrado (fondo blanco)"
```

---

### Task 2: Prompt y uso en BD (imagen_prompt, imagen_uso)

**Files:**
- Create: `apis/openai/entity/ImagenPrompt.java`, `apis/openai/entity/ImagenUso.java`
- Create: `apis/openai/repository/ImagenPromptRepository.java`, `apis/openai/repository/ImagenUsoRepository.java`
- Create: `apis/openai/dto/ImagenPromptDTO.java`, `apis/openai/dto/ImagenPromptUpdateDTO.java`, `apis/openai/dto/ImagenUsoDTO.java`
- Create: `apis/openai/config/OpenAiImageProperties.java`
- Create: `apis/openai/service/ImagenIaConfigService.java`, `apis/openai/service/ImagenUsoService.java`
- Create: `src/main/resources/db/2026-06-26-imagen-ia.sql`
- Modify: `src/main/resources/application.properties` (sección `openai.image.*`)
- Test: `apis/openai/service/ImagenUsoServiceTest.java`

**Interfaces:**
- Produces: `OpenAiImageProperties` (prefix `openai.image`, campos `baseUrl`/`model`/`connectTimeout`/`readTimeout`/`precioInput1m`/`precioOutput1m`).
- Produces: `ImagenIaConfigService.prompt()` → String; `.actualizar(String)` → ImagenPromptDTO.
- Produces: `ImagenUsoService.registrar(long in, long out)`; `.obtener()` → ImagenUsoDTO; `static calcularCosto(...)`.

- [ ] **Step 1: Crear properties, entities, repos, DTOs**

`OpenAiImageProperties.java` (defaults para gpt-image-1; precios placeholder ajustables):
```java
package ar.com.leo.super_master_backend.apis.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "openai.image")
public record OpenAiImageProperties(
        String baseUrl, String model,
        Duration connectTimeout, Duration readTimeout,
        BigDecimal precioInput1m, BigDecimal precioOutput1m
) {
    public OpenAiImageProperties {
        if (baseUrl == null) baseUrl = "https://api.openai.com/v1";
        if (model == null) model = "gpt-image-1";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(10);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(120);
        if (precioInput1m == null) precioInput1m = new BigDecimal("5.00");
        if (precioOutput1m == null) precioOutput1m = new BigDecimal("40.00");
    }
}
```

`ImagenPrompt.java` (una sola fila; clave `id` singleton):
```java
package ar.com.leo.super_master_backend.apis.openai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "imagen_prompt", schema = "supermaster")
public class ImagenPrompt {
    @Id @Column(name = "id")
    private Long id;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
```

`ImagenUso.java` (espejo de SeoUso):
```java
package ar.com.leo.super_master_backend.apis.openai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "imagen_uso", schema = "supermaster")
public class ImagenUso {
    @Id @Column(name = "id")
    private Long id;

    @Column(name = "consultas", nullable = false)
    private long consultas;

    @Column(name = "tokens_entrada", nullable = false)
    private long tokensEntrada;

    @Column(name = "tokens_salida", nullable = false)
    private long tokensSalida;

    @Column(name = "costo_usd", nullable = false, precision = 14, scale = 6)
    private BigDecimal costoUsd;
}
```

`ImagenPromptRepository.java`:
```java
package ar.com.leo.super_master_backend.apis.openai.repository;

import ar.com.leo.super_master_backend.apis.openai.entity.ImagenPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImagenPromptRepository extends JpaRepository<ImagenPrompt, Long> {}
```

`ImagenUsoRepository.java` (UPDATE atómico singleton id=1):
```java
package ar.com.leo.super_master_backend.apis.openai.repository;

import ar.com.leo.super_master_backend.apis.openai.entity.ImagenUso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ImagenUsoRepository extends JpaRepository<ImagenUso, Long> {
    @Modifying
    @Query("UPDATE ImagenUso s SET s.consultas = s.consultas + 1, " +
           "s.tokensEntrada = s.tokensEntrada + :in, " +
           "s.tokensSalida = s.tokensSalida + :out, " +
           "s.costoUsd = s.costoUsd + :costo WHERE s.id = 1")
    int registrar(@Param("in") long tokensEntrada, @Param("out") long tokensSalida, @Param("costo") BigDecimal costo);
}
```

DTOs:
```java
// ImagenPromptDTO.java
package ar.com.leo.super_master_backend.apis.openai.dto;
import java.time.LocalDateTime;
public record ImagenPromptDTO(String contenido, LocalDateTime fechaModificacion) {}
```
```java
// ImagenPromptUpdateDTO.java
package ar.com.leo.super_master_backend.apis.openai.dto;
import jakarta.validation.constraints.NotBlank;
public record ImagenPromptUpdateDTO(@NotBlank(message = "El contenido del prompt es obligatorio") String contenido) {}
```
```java
// ImagenUsoDTO.java
package ar.com.leo.super_master_backend.apis.openai.dto;
import java.math.BigDecimal;
public record ImagenUsoDTO(long consultas, long tokensEntrada, long tokensSalida, BigDecimal costoUsd,
                           String modelo, BigDecimal precioInput1m, BigDecimal precioOutput1m) {}
```

- [ ] **Step 2: Servicios (config + uso)**

`ImagenIaConfigService.java`:
```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenPromptDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenPrompt;
import ar.com.leo.super_master_backend.apis.openai.repository.ImagenPromptRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Lee/actualiza el prompt único de generación de carátula (fila id=1). */
@Service
@RequiredArgsConstructor
public class ImagenIaConfigService {

    private final ImagenPromptRepository repository;

    @Transactional(readOnly = true)
    public String prompt() {
        return repository.findById(1L)
                .map(ImagenPrompt::getContenido)
                .orElseThrow(() -> new NotFoundException("No hay prompt de carátula configurado (revisar el seed de imagen_prompt)"));
    }

    @Transactional(readOnly = true)
    public ImagenPromptDTO obtener() {
        return repository.findById(1L)
                .map(p -> new ImagenPromptDTO(p.getContenido(), p.getFechaModificacion()))
                .orElseThrow(() -> new NotFoundException("No hay prompt de carátula configurado"));
    }

    @Transactional
    public ImagenPromptDTO actualizar(String contenido) {
        ImagenPrompt p = repository.findById(1L)
                .orElseThrow(() -> new NotFoundException("No hay prompt de carátula configurado"));
        p.setContenido(contenido);
        p.setFechaModificacion(LocalDateTime.now());
        repository.save(p);
        return new ImagenPromptDTO(p.getContenido(), p.getFechaModificacion());
    }
}
```

`ImagenUsoService.java` (espejo de SeoUsoService, usa OpenAiImageProperties):
```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.config.OpenAiImageProperties;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenUso;
import ar.com.leo.super_master_backend.apis.openai.repository.ImagenUsoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImagenUsoService {

    private static final BigDecimal MILLON = new BigDecimal("1000000");

    private final ImagenUsoRepository repository;
    private final OpenAiImageProperties properties;

    public static BigDecimal calcularCosto(long tokensEntrada, long tokensSalida, BigDecimal precioIn1m, BigDecimal precioOut1m) {
        BigDecimal costoIn = BigDecimal.valueOf(tokensEntrada).multiply(precioIn1m).divide(MILLON, 6, RoundingMode.HALF_UP);
        BigDecimal costoOut = BigDecimal.valueOf(tokensSalida).multiply(precioOut1m).divide(MILLON, 6, RoundingMode.HALF_UP);
        return costoIn.add(costoOut);
    }

    @Transactional
    public void registrar(long tokensEntrada, long tokensSalida) {
        BigDecimal costo = calcularCosto(tokensEntrada, tokensSalida, properties.precioInput1m(), properties.precioOutput1m());
        if (repository.registrar(tokensEntrada, tokensSalida, costo) == 0) {
            log.warn("imagen_uso id=1 no existe; el uso no se registró");
        }
    }

    @Transactional(readOnly = true)
    public ImagenUsoDTO obtener() {
        ImagenUso u = repository.findById(1L).orElseThrow(() -> new NotFoundException("Fila de uso de imagen (id=1) no encontrada"));
        return new ImagenUsoDTO(u.getConsultas(), u.getTokensEntrada(), u.getTokensSalida(), u.getCostoUsd(),
                properties.model(), properties.precioInput1m(), properties.precioOutput1m());
    }
}
```

- [ ] **Step 3: Migración SQL + properties**

Crear `src/main/resources/db/2026-06-26-imagen-ia.sql`:
```sql
-- Carátula IA: prompt único editable + consumo acumulado de OpenAI (imágenes).
CREATE TABLE supermaster.imagen_prompt (
  id BIGINT NOT NULL,
  contenido TEXT NOT NULL,
  fecha_modificacion DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supermaster.imagen_uso (
  id BIGINT NOT NULL,
  consultas BIGINT NOT NULL DEFAULT 0,
  tokens_entrada BIGINT NOT NULL DEFAULT 0,
  tokens_salida BIGINT NOT NULL DEFAULT 0,
  costo_usd DECIMAL(14,6) NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO supermaster.imagen_prompt (id, contenido) VALUES (1,
'Recortá el producto de la foto y ponelo centrado sobre un fondo blanco puro, con márgenes chicos, como carátula de producto para venta online (Mercado Libre y Tienda Nube). Conservá el producto sin alterarlo (forma, color y detalles reales); no agregues texto, logos ni sombras marcadas.');

INSERT INTO supermaster.imagen_uso (id, consultas, tokens_entrada, tokens_salida, costo_usd) VALUES (1, 0, 0, 0, 0);
```

En `application.properties`, agregar tras la sección `openai.*`:
```properties
# OpenAI imágenes (carátula): key en openai_image_tokens.json (app.secrets-dir), NO acá.
openai.image.base-url=https://api.openai.com/v1
openai.image.model=gpt-image-1
openai.image.precio-input-1m=5.00
openai.image.precio-output-1m=40.00
```

- [ ] **Step 4: Test de uso (TDD del cálculo)**

`ImagenUsoServiceTest.java`:
```java
package ar.com.leo.super_master_backend.apis.openai.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class ImagenUsoServiceTest {
    @Test
    void calcularCosto_sumaInputYOutputPorMillon() {
        // 1.000.000 in a 5 USD/M = 5; 500.000 out a 40 USD/M = 20; total 25.
        BigDecimal costo = ImagenUsoService.calcularCosto(1_000_000L, 500_000L, new BigDecimal("5.00"), new BigDecimal("40.00"));
        assertThat(costo).isEqualByComparingTo(new BigDecimal("25.000000"));
    }
}
```

- [ ] **Step 5: Correr tests**

Run: `cd supermaster-backend && mvn -o test -Dtest=ImagenUsoServiceTest && mvn -o test-compile`
Expected: test PASS; BUILD SUCCESS (entities/repos/services compilan). El registro de `OpenAiImageProperties` se completa al agregar el bean en la Task 3 (`@EnableConfigurationProperties`).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/ \
        supermaster-backend/src/main/resources/db/2026-06-26-imagen-ia.sql \
        supermaster-backend/src/main/resources/application.properties \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/ImagenUsoServiceTest.java
git commit -m "feat(imagen-ia): prompt y uso de carátula en BD (imagen_prompt/imagen_uso)"
```

---

### Task 3: Cliente OpenAI de imágenes + OpenAiImagenService

**Files:**
- Modify: `apis/openai/config/OpenAiConfig.java` (registrar properties + bean `openaiImageRestClient`)
- Create: `apis/openai/service/OpenAiImagenParser.java` (puro)
- Create: `apis/openai/service/OpenAiImagenService.java`
- Test: `apis/openai/service/OpenAiImagenParserTest.java`

**Interfaces:**
- Consumes: `OpenAiImageProperties` (Task 2); `ImagenIaConfigService.prompt()` (Task 2); `ImagenUsoService.registrar(in,out)` (Task 2); `ImagenProcesador.aJpgCuadrada(bytes,1200)` (Task 1).
- Produces: `OpenAiImagenService.generarCaratula(byte[] cruda, String filename)` → `byte[]` (JPG 1200×1200).
- Produces: `OpenAiImagenParser.b64(JsonNode)` → String; `.tokensEntrada(JsonNode)`/`.tokensSalida(JsonNode)` → long.

- [ ] **Step 1: Registrar properties + bean en OpenAiConfig**

En `OpenAiConfig.java`: agregar `OpenAiImageProperties` a `@EnableConfigurationProperties({OpenAiProperties.class, OpenAiImageProperties.class})` y agregar el bean:
```java
    @Bean
    public RestClient openaiImageRestClient(OpenAiImageProperties properties) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }
```
(No se setea `Content-Type` por defecto: las llamadas son multipart y lo fijan ellas.)

- [ ] **Step 2: Parser puro + test (TDD)**

`OpenAiImagenParserTest.java`:
```java
package ar.com.leo.super_master_backend.apis.openai.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiImagenParserTest {
    private static tools.jackson.databind.JsonNode json(String s) { return new ObjectMapper().readTree(s); }

    @Test
    void extraeB64YTokens() {
        var root = json("""
            {"data":[{"b64_json":"QUJD"}],"usage":{"input_tokens":120,"output_tokens":4000}}""");
        assertThat(OpenAiImagenParser.b64(root)).isEqualTo("QUJD");
        assertThat(OpenAiImagenParser.tokensEntrada(root)).isEqualTo(120);
        assertThat(OpenAiImagenParser.tokensSalida(root)).isEqualTo(4000);
    }

    @Test
    void sinB64_devuelveNull() {
        assertThat(OpenAiImagenParser.b64(json("{\"data\":[]}"))).isNull();
    }
}
```

`OpenAiImagenParser.java`:
```java
package ar.com.leo.super_master_backend.apis.openai.service;

import tools.jackson.databind.JsonNode;

/** Lee la respuesta de POST /images/edits de OpenAI (puro). */
public final class OpenAiImagenParser {
    private OpenAiImagenParser() {}

    public static String b64(JsonNode root) {
        return root.path("data").path(0).path("b64_json").asString(null);
    }
    public static long tokensEntrada(JsonNode root) {
        return root.path("usage").path("input_tokens").asLong(0);
    }
    public static long tokensSalida(JsonNode root) {
        return root.path("usage").path("output_tokens").asLong(0);
    }
}
```

Run: `cd supermaster-backend && mvn -o test -Dtest=OpenAiImagenParserTest` → PASS.

- [ ] **Step 3: OpenAiImagenService (llamada multipart)**

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.config.OpenAiImageProperties;
import ar.com.leo.super_master_backend.apis.openai.model.OpenAiCredentials;
import ar.com.leo.super_master_backend.dominio.common.exception.ServiceNotConfiguredException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Paths;
import java.util.Base64;

/** Genera la carátula llamando a gpt-image-1 (/images/edits) con credencial propia. */
@Slf4j
@Service
public class OpenAiImagenService {

    private final RestClient restClient;
    private final OpenAiImageProperties properties;
    private final ImagenIaConfigService configService;
    private final ImagenUsoService usoService;
    private final ObjectMapper objectMapper;
    private final String secretsDir;
    private OpenAiCredentials credentials;

    public OpenAiImagenService(@Qualifier("openaiImageRestClient") RestClient restClient,
                               OpenAiImageProperties properties,
                               ImagenIaConfigService configService,
                               ImagenUsoService usoService,
                               ObjectMapper objectMapper,
                               @Value("${app.secrets-dir}") String secretsDir) {
        this.restClient = restClient;
        this.properties = properties;
        this.configService = configService;
        this.usoService = usoService;
        this.objectMapper = objectMapper;
        this.secretsDir = secretsDir;
    }

    @PostConstruct
    void init() {
        try {
            File file = Paths.get(secretsDir).resolve("openai_image_tokens.json").toFile();
            if (file.exists()) {
                credentials = objectMapper.readValue(file, OpenAiCredentials.class);
                log.info("OpenAI imágenes - credenciales cargadas desde {}", file.getAbsolutePath());
            } else {
                log.warn("OpenAI imágenes - credenciales no encontradas: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("OpenAI imágenes - error cargando credenciales: {}", e.getMessage());
        }
    }

    /** Toma la imagen cruda, la edita con fondo blanco vía OpenAI y devuelve la carátula JPG 1200×1200. */
    public byte[] generarCaratula(byte[] cruda, String filename) {
        if (credentials == null || credentials.getApiKey() == null)
            throw new ServiceNotConfiguredException("Falta la credencial de OpenAI de imágenes (openai_image_tokens.json)");

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("model", properties.model());
        parts.add("prompt", configService.prompt());
        parts.add("size", "1024x1024");
        parts.add("image", new ByteArrayResource(cruda) {
            @Override public String getFilename() { return filename; }
        });

        String resp = restClient.post()
                .uri("/images/edits")
                .header("Authorization", "Bearer " + credentials.getApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(resp);
        String b64 = OpenAiImagenParser.b64(root);
        if (b64 == null) throw new IllegalStateException("OpenAI no devolvió la imagen");
        usoService.registrar(OpenAiImagenParser.tokensEntrada(root), OpenAiImagenParser.tokensSalida(root));
        byte[] png = Base64.getDecoder().decode(b64);
        return ImagenProcesador.aJpgCuadrada(png, 1200);
    }
}
```

> Nota: verificar el FQN/existencia de `ServiceNotConfiguredException` (el SEO usa una excepción así; si el nombre difiere, usar la real del proyecto). `OpenAiCredentials` ya existe y mapea `api_key`.

- [ ] **Step 4: Compilar**

Run: `cd supermaster-backend && mvn -o test-compile`
Expected: BUILD SUCCESS. (La llamada HTTP real se valida manualmente; no hay test unitario del POST, igual que el SEO.)

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/config/OpenAiConfig.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/OpenAiImagenParser.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/OpenAiImagenService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/OpenAiImagenParserTest.java
git commit -m "feat(imagen-ia): cliente y servicio OpenAI gpt-image-1 (/images/edits)"
```

---

### Task 4: ImagenService — leer cruda + escribir carátula

**Files:**
- Modify: `dominio/imagen/service/ImagenService.java` (inyectar `app.imagenes-raw-dir`; `leerCrudaPorSku`, `guardarCaratula`, `invalidarIndice`)
- Modify: `src/main/resources/application-dev.properties` (`app.imagenes-raw-dir`)
- Test: `dominio/imagen/service/ImagenServiceCaratulaTest.java`

**Interfaces:**
- Produces: `ImagenService.leerCrudaPorSku(String sku)` → `byte[]` (null si no hay cruda); `ImagenService.guardarCaratula(String sku, byte[] jpg)` (escribe `{SKU}.jpg` en imagenes-dir e invalida el índice).

- [ ] **Step 1: Escribir el test (TDD, @TempDir)**

`ImagenServiceCaratulaTest.java`:
```java
package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenServiceCaratulaTest {

    private ImagenService servicio(Path imagenes, Path crudas) {
        return new ImagenService(imagenes.toString(), 0L, crudas.toString());
    }

    @Test
    void leerCrudaPorSku_leeDeLaCarpetaDeEntrada(@TempDir Path imagenes, @TempDir Path crudas) throws Exception {
        Files.write(crudas.resolve("ABC.png"), new byte[]{1, 2, 3});
        assertThat(servicio(imagenes, crudas).leerCrudaPorSku("ABC")).containsExactly(1, 2, 3);
    }

    @Test
    void leerCrudaPorSku_sinArchivo_devuelveNull(@TempDir Path imagenes, @TempDir Path crudas) {
        assertThat(servicio(imagenes, crudas).leerCrudaPorSku("NOPE")).isNull();
    }

    @Test
    void guardarCaratula_escribeJpgEnImagenesYSeResuelve(@TempDir Path imagenes, @TempDir Path crudas) {
        ImagenService s = servicio(imagenes, crudas);
        s.guardarCaratula("ABC", new byte[]{9, 9, 9});
        assertThat(imagenes.resolve("ABC.jpg")).exists();
        assertThat(s.resolverArchivoPorSku("ABC")).isEqualTo("ABC.jpg");
    }
}
```

- [ ] **Step 2: Correr y ver fallar**

Run: `cd supermaster-backend && mvn -o test -Dtest=ImagenServiceCaratulaTest`
Expected: FAIL — el constructor de 3 args y los métodos no existen.

- [ ] **Step 3: Implementar en ImagenService**

Agregar el 3er parámetro al constructor y guardar el `rawDir`:
```java
    private final Path rawDir;

    public ImagenService(
            @Value("${app.imagenes-dir}") String imagenesDir,
            @Value("${app.imagenes-index-ttl-ms:60000}") long ttlMillis,
            @Value("${app.imagenes-raw-dir}") String imagenesRawDir) {
        this.baseDir = Path.of(imagenesDir).normalize();
        this.ttlMillis = ttlMillis;
        this.rawDir = Path.of(imagenesRawDir).normalize();
    }
```

Agregar los métodos (usar `EXTENSIONES` ya existente para resolver la cruda; reusar `invalidar` del índice):
```java
    /** Lee los bytes de la imagen cruda {SKU}.{ext} desde la carpeta de entrada; null si no existe. */
    public byte[] leerCrudaPorSku(String sku) {
        if (sku == null || sku.isBlank()) return null;
        for (String ext : EXTENSIONES) {
            Path p = rawDir.resolve(sku.trim() + "." + ext);
            if (Files.isRegularFile(p)) {
                try { return Files.readAllBytes(p); }
                catch (IOException e) { throw new UncheckedIOException("No se pudo leer la cruda " + p, e); }
            }
        }
        return null;
    }

    /** Escribe la carátula {SKU}.jpg en la carpeta de imágenes (reemplaza si existía) e invalida el índice. */
    public void guardarCaratula(String sku, byte[] jpg) {
        try {
            Files.createDirectories(baseDir);
            Files.write(baseDir.resolve(sku.trim() + ".jpg"), jpg);
            invalidarIndice();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la carátula de " + sku, e);
        }
    }

    /** Fuerza el re-escaneo del índice en la próxima consulta (tras escribir un archivo). */
    public synchronized void invalidarIndice() {
        indiceTimestamp = 0L;
    }
```

> Nota: el case-insensitive del nombre crudo (ej. SKU en otra capitalización) no se cubre; se asume `{SKU}.{ext}` con el SKU tal cual. Si hace falta, ajustar a un escaneo case-insensitive como `obtenerIndice`.

- [ ] **Step 4: Agregar la propiedad en dev**

En `application-dev.properties`, agregar (ruta a definir por el usuario; placeholder en dev):
```properties
app.imagenes-raw-dir=C:/ProgramData/SuperMaster/imagenes-crudas/
```

- [ ] **Step 5: Correr tests**

Run: `cd supermaster-backend && mvn -o test -Dtest=ImagenServiceCaratulaTest && mvn -o test-compile`
Expected: 3 tests PASS; BUILD SUCCESS. (Si otros tests construyen `ImagenService` con 2 args, actualizarlos al nuevo constructor de 3 args — buscar `new ImagenService(` en tests y agregar un tercer arg de carpeta cruda; los tests existentes pueden pasar el mismo `@TempDir` o un dummy.)

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java \
        supermaster-backend/src/main/resources/application-dev.properties \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenServiceCaratulaTest.java
git commit -m "feat(imagen): lee imagen cruda por SKU y guarda la carátula {SKU}.jpg"
```

---

### Task 5: Orquestador + endpoints (generar/guardar)

**Files:**
- Create: `dominio/imagen/service/CaratulaService.java`
- Create: `apis/openai/dto/CaratulaGeneradaDTO.java`, `apis/openai/dto/CaratulaGuardarDTO.java`
- Modify: `dominio/imagen/controller/ImagenController.java` (2 endpoints) + `apis/openai/controller/SeoController.java` o nuevo `ImagenIaController` (prompt/uso de carátula)

**Interfaces:**
- Consumes: `ImagenService.leerCrudaPorSku/guardarCaratula` (Task 4); `OpenAiImagenService.generarCaratula` (Task 3); `ImagenIaConfigService`/`ImagenUsoService` (Task 2).
- Produces: `CaratulaService.generar(sku)` → `byte[]` JPG; `CaratulaService.guardar(sku, byte[] jpg)`.

- [ ] **Step 1: DTOs**
```java
// CaratulaGeneradaDTO.java
package ar.com.leo.super_master_backend.apis.openai.dto;
public record CaratulaGeneradaDTO(String imagenBase64) {}
```
```java
// CaratulaGuardarDTO.java
package ar.com.leo.super_master_backend.apis.openai.dto;
import jakarta.validation.constraints.NotBlank;
public record CaratulaGuardarDTO(@NotBlank String imagenBase64) {}
```

- [ ] **Step 2: CaratulaService**
```java
package ar.com.leo.super_master_backend.dominio.imagen.service;

import ar.com.leo.super_master_backend.apis.openai.service.OpenAiImagenService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Orquesta la generación/guardado de la carátula: cruda → OpenAI → JPG → disco. */
@Service
@RequiredArgsConstructor
public class CaratulaService {

    private final ImagenService imagenService;
    private final OpenAiImagenService openAiImagenService;

    /** Genera (sin guardar) la carátula JPG a partir de la cruda del SKU. */
    public byte[] generar(String sku) {
        byte[] cruda = imagenService.leerCrudaPorSku(sku);
        if (cruda == null) throw new NotFoundException("No hay imagen cruda para el SKU " + sku);
        return openAiImagenService.generarCaratula(cruda, sku + ".png");
    }

    public void guardar(String sku, byte[] jpg) {
        imagenService.guardarCaratula(sku, jpg);
    }
}
```

- [ ] **Step 3: Endpoints de carátula en ImagenController**

Agregar (inyectar `CaratulaService` por constructor; importar `Base64`, los DTOs, `Permisos`, `@PreAuthorize`, `@Valid`):
```java
    @PostMapping("/caratula/generar/{sku}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<CaratulaGeneradaDTO> generarCaratula(@PathVariable String sku) {
        byte[] jpg = caratulaService.generar(sku);
        return ResponseEntity.ok(new CaratulaGeneradaDTO(Base64.getEncoder().encodeToString(jpg)));
    }

    @PostMapping("/caratula/guardar/{sku}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Void> guardarCaratula(@PathVariable String sku, @Valid @RequestBody CaratulaGuardarDTO body) {
        caratulaService.guardar(sku, Base64.getDecoder().decode(body.imagenBase64()));
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 4: Endpoints de prompt/uso de carátula**

Agregar en `SeoController` (o un `ImagenIaController` nuevo bajo `apis/openai/controller`; preferir el controller nuevo para no mezclar): `GET /api/imagen-ia/prompt`, `PUT /api/imagen-ia/prompt`, `GET /api/imagen-ia/uso`, delegando en `ImagenIaConfigService`/`ImagenUsoService`. Patrón idéntico a `SeoController` (permisos `INTEGRACIONES_VER`/`INTEGRACIONES_EDITAR`, `@Valid` con `ImagenPromptUpdateDTO`):
```java
package ar.com.leo.super_master_backend.apis.openai.controller;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenPromptDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenPromptUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.service.ImagenIaConfigService;
import ar.com.leo.super_master_backend.apis.openai.service.ImagenUsoService;
import ar.com.leo.super_master_backend.config.Permisos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/imagen-ia")
public class ImagenIaController {

    private final ImagenIaConfigService configService;
    private final ImagenUsoService usoService;

    @GetMapping("/prompt")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ImagenPromptDTO> prompt() { return ResponseEntity.ok(configService.obtener()); }

    @PutMapping("/prompt")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<ImagenPromptDTO> actualizar(@Valid @RequestBody ImagenPromptUpdateDTO body) {
        return ResponseEntity.ok(configService.actualizar(body.contenido()));
    }

    @GetMapping("/uso")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ImagenUsoDTO> uso() { return ResponseEntity.ok(usoService.obtener()); }
}
```

- [ ] **Step 5: Compilar + suite del paquete**

Run: `cd supermaster-backend && mvn -o test-compile && mvn -o test -Dtest='*Imagen*'`
Expected: BUILD SUCCESS; tests de imagen verdes.

- [ ] **Step 6: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/ \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/
git commit -m "feat(imagen-ia): orquestador + endpoints generar/guardar carátula y prompt/uso"
```

---

### Task 6: Frontend — botón "Mejorar carátula con IA" + preview

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` (2 llamadas)
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (botón + preview + guardar)

**Interfaces:**
- Produces: `generarCaratulaAPI(sku) → { imagenBase64 }`; `guardarCaratulaAPI(sku, imagenBase64) → void`.

- [ ] **Step 1: Llamadas en productosService.ts**
```ts
export async function generarCaratulaAPI(sku: string): Promise<{ imagenBase64: string }> {
    const r = await fetchAPI(`${API_BASE_URL}/api/imagenes/caratula/generar/${encodeURIComponent(sku)}`, { method: "POST" });
    return r.json();
}
export async function guardarCaratulaAPI(sku: string, imagenBase64: string): Promise<void> {
    await fetchAPI(`${API_BASE_URL}/api/imagenes/caratula/guardar/${encodeURIComponent(sku)}`,
        { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ imagenBase64 }) });
}
```
(Replicar el patrón exacto de `fetchAPI` del archivo; verificar `API_BASE_URL`/import.)

- [ ] **Step 2: Estado + handlers en el modal (modo edición)**
```tsx
    const [caratulaPreview, setCaratulaPreview] = useState<string | null>(null);
    const [generandoCaratula, setGenerandoCaratula] = useState(false);

    const generarCaratula = async () => {
        if (!sku.trim()) return;
        setGenerandoCaratula(true);
        try {
            const r = await generarCaratulaAPI(sku.trim());
            setCaratulaPreview(r.imagenBase64);
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo generar la carátula");
        } finally { setGenerandoCaratula(false); }
    };

    const guardarCaratula = async () => {
        if (!caratulaPreview) return;
        try {
            await guardarCaratulaAPI(sku.trim(), caratulaPreview);
            setCaratulaPreview(null);
            notificar.success("Carátula guardada");
            // refrescar el detalle/carrusel de imágenes
            getImagenDetalleAPI(sku.trim()).then(setImagenesDetectadas).catch(() => {});
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo guardar la carátula");
        }
    };
```
(Reusar el nombre real del estado de imágenes detectadas y de la función de carga — `imagenesDetectadas`/`getImagenDetalleAPI` ya existen en el archivo.)

- [ ] **Step 3: UI (botón + preview)**

En la zona de imágenes/carátula del modal (solo `editandoProductoId`), agregar el botón y, si hay preview, el bloque de confirmación:
```tsx
    <Button variant="dark" onClick={generarCaratula} disabled={generandoCaratula}>
        {generandoCaratula ? <SpinnerIcon /> : <SparklesIcon className="h-4 w-4" />}
        {generandoCaratula ? "Generando..." : "Mejorar carátula con IA"}
    </Button>
    {caratulaPreview && (
        <div className="mt-3 rounded-2xl border border-slate-200 p-3 dark:border-slate-700">
            <img src={`data:image/jpeg;base64,${caratulaPreview}`} alt="Preview carátula" className="mx-auto max-h-64" />
            <div className="mt-2 flex justify-end gap-2">
                <Button variant="light" onClick={() => setCaratulaPreview(null)}>Descartar</Button>
                <Button variant="dark" onClick={guardarCaratula}><CheckIcon className="h-4 w-4" /> Guardar carátula</Button>
            </div>
        </div>
    )}
```
(Usar íconos ya importados — `SparklesIcon`, `CheckIcon`, `SpinnerIcon` — y `Button`/`notificar`/`esSesionExpirada` existentes.)

- [ ] **Step 4: Lint + tsc + manual**

Run: `cd supermaster-frontend && npm run lint && npx tsc --noEmit -p tsconfig.json`
Expected: tsc 0; sin errores de lint nuevos.
Manual: con un producto que tenga cruda → generar (preview), guardar (aparece en el carrusel), descartar (no cambia nada); sin cruda → error claro.

- [ ] **Step 5: Commit**
```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(modal): botón Mejorar carátula con IA con preview y guardado"
```

---

### Task 7: Frontend — pantalla "SEO IA": prompt y uso de carátula

**Files:**
- Modify: `supermaster-frontend/src/app/seo-ia/seoService.ts` (o crear `imagenIaService.ts`) — 3 llamadas
- Modify: `supermaster-frontend/src/app/seo-ia/page.tsx` y `useSeoIa.ts` — tarjeta "Prompt de carátula" + panel de uso

**Interfaces:**
- Produces: `getImagenPromptAPI()`/`updateImagenPromptAPI(contenido)`/`getImagenUsoAPI()` (GET/PUT `/api/imagen-ia/prompt`, GET `/api/imagen-ia/uso`).

- [ ] **Step 1: Llamadas API**

Agregar (mismo patrón que `getSeoPromptsAPI`/`updateSeoPromptAPI`/`getSeoUsoAPI`):
```ts
export type ImagenPrompt = { contenido: string; fechaModificacion: string | null };
export type ImagenUso = { consultas: number; tokensEntrada: number; tokensSalida: number; costoUsd: number; modelo: string; precioInput1m: number; precioOutput1m: number };

export async function getImagenPromptAPI(): Promise<ImagenPrompt> {
    const r = await fetchAPI(`${API_BASE_URL}/api/imagen-ia/prompt`); return r.json();
}
export async function updateImagenPromptAPI(contenido: string): Promise<ImagenPrompt> {
    const r = await fetchAPI(`${API_BASE_URL}/api/imagen-ia/prompt`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ contenido }) });
    return r.json();
}
export async function getImagenUsoAPI(): Promise<ImagenUso> {
    const r = await fetchAPI(`${API_BASE_URL}/api/imagen-ia/uso`); return r.json();
}
```

- [ ] **Step 2: UI en la pantalla SEO IA**

En `page.tsx` (con su hook), agregar una tarjeta "Prompt de carátula" (textarea + botón Guardar que llama `updateImagenPromptAPI`) y un panel con el uso de carátula (`getImagenUsoAPI`: consultas, tokens, costo, modelo), siguiendo el mismo layout que el de SEO. Cargar ambos en el `useEffect` de carga junto a los de SEO.

> Implementar reusando los componentes/estilos ya presentes en `page.tsx` (textarea, botón "Guardar", panel de uso). No introducir librerías nuevas.

- [ ] **Step 3: Lint + tsc + manual**

Run: `cd supermaster-frontend && npm run lint && npx tsc --noEmit -p tsconfig.json`
Expected: tsc 0; sin errores nuevos.
Manual: editar y guardar el prompt de carátula; ver el uso actualizarse tras generar una carátula.

- [ ] **Step 4: Commit**
```bash
git add supermaster-frontend/src/app/seo-ia/
git commit -m "feat(seo-ia): edición del prompt de carátula y panel de uso de imágenes"
```

---

## Notas de cierre

- Tras todas las tareas: `cd supermaster-backend && mvn -o test` (suite completa verde) y `npm run lint && npx tsc --noEmit` en el frontend.
- Verificación manual end-to-end con un SKU que tenga una foto cruda en `app.imagenes-raw-dir`: generar → preview → guardar → confirmar que aparece la `{SKU}.jpg` y que el uso/costo se registró.
- Pendiente operativo (no de código): cargar `secrets/openai_image_tokens.json` con la API key de imágenes y setear `app.imagenes-raw-dir` a la carpeta real.
