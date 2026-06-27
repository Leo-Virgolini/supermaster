# Parámetros de OpenAI editables (SEO + Imagen) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persistir en BD y hacer editables desde la pantalla "SEO IA" los parámetros que se le pasan a OpenAI (SEO: model + precios; Imagen: model + size + output_format + quality + precios), unificando prompt + parámetros en una tabla de config por servicio.

**Architecture:** Se reemplazan `seo_prompt`/`imagen_prompt` por `seo_config`/`imagen_config` (singleton id=1) que guardan prompt(s) + parámetros. Generación y cálculo de costo leen de la BD vía los config services; `properties` queda solo con base-url/timeouts. La pantalla SEO IA pasa a un formulario por servicio.

**Tech Stack:** Spring Boot 4, Java 25, Maven, JPA, Lombok, Jackson 3, JUnit 5 + Mockito + AssertJ; frontend Next.js/React/TS.

## Global Constraints

- `ddl-auto=validate` en dev/prod; los cambios de schema requieren script SQL manual en `src/main/resources/db/`. En tests el perfil usa `ddl-auto=none` (no valida schema).
- Tests backend: `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`). Frontend: `npx tsc --noEmit` exit 0 y `npm run lint` sin errores `error` nuevos.
- Las API keys NO van en BD ni properties (se cargan de `openai_tokens.json`: `seo_api_key`/`image_api_key`, sin fallback).
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos, nunca `-A` ni `.superpowers/`.
- Los `.sql` con acentos se cargan con `--default-character-set=utf8mb4` (mojibake si no).
- Paquete base backend: `ar.com.leo.super_master_backend`.

## File Structure

**Backend — crear:**
- `db/2026-06-27-openai-config.sql` — migración (crea seo_config + imagen_config, siembra, dropea seo_prompt + imagen_prompt).
- `apis/openai/entity/SeoConfig.java`, `apis/openai/entity/ImagenConfig.java`
- `apis/openai/repository/SeoConfigRepository.java`, `apis/openai/repository/ImagenConfigRepository.java`
- `apis/openai/dto/SeoConfigDTO.java`, `apis/openai/dto/SeoConfigUpdateDTO.java`
- `apis/openai/dto/ImagenConfigDTO.java`, `apis/openai/dto/ImagenConfigUpdateDTO.java`

**Backend — modificar:**
- `apis/openai/service/SeoConfigService.java`, `apis/openai/service/ImagenIaConfigService.java`
- `apis/openai/service/SeoUsoService.java`, `apis/openai/service/ImagenUsoService.java`
- `apis/openai/service/OpenAiSeoService.java`, `apis/openai/service/OpenAiImagenService.java`
- `apis/openai/controller/SeoController.java`, `apis/openai/controller/ImagenIaController.java`
- `dominio/imagen/service/CaratulaService.java`, `dominio/imagen/service/ImagenService.java`, `dominio/imagen/controller/ImagenController.java`, `dominio/imagen/dto/CaratulaGeneradaDTO.java`
- `apis/openai/config/OpenAiProperties.java`, `apis/openai/config/OpenAiImageProperties.java`, `src/main/resources/application.properties`

**Backend — eliminar:**
- `apis/openai/entity/SeoPrompt.java`, `apis/openai/entity/ImagenPrompt.java`
- `apis/openai/repository/SeoPromptRepository.java`, `apis/openai/repository/ImagenPromptRepository.java`
- `apis/openai/dto/SeoPromptDTO.java`, `apis/openai/dto/SeoPromptUpdateDTO.java`, `apis/openai/dto/ImagenPromptDTO.java`, `apis/openai/dto/ImagenPromptUpdateDTO.java`

**Frontend — modificar:**
- `src/app/seo-ia/seoService.ts`, `src/app/seo-ia/types.ts`, `src/app/seo-ia/useSeoIa.ts`, `src/app/seo-ia/page.tsx`
- `src/app/productos/productosService.ts`, `src/app/productos/ProductoFormModal.tsx`

---

## Task 1: Backend SEO — tabla `seo_config`, migración completa, swap y trim de properties

**Files:**
- Create: `supermaster-backend/src/main/resources/db/2026-06-27-openai-config.sql`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/entity/SeoConfig.java`
- Create: `.../apis/openai/repository/SeoConfigRepository.java`
- Create: `.../apis/openai/dto/SeoConfigDTO.java`, `.../apis/openai/dto/SeoConfigUpdateDTO.java`
- Modify: `.../apis/openai/service/SeoConfigService.java`
- Modify: `.../apis/openai/service/SeoUsoService.java`
- Modify: `.../apis/openai/service/OpenAiSeoService.java`
- Modify: `.../apis/openai/controller/SeoController.java`
- Modify: `.../apis/openai/config/OpenAiProperties.java`, `src/main/resources/application.properties`
- Delete: `.../apis/openai/entity/SeoPrompt.java`, `.../repository/SeoPromptRepository.java`, `.../dto/SeoPromptDTO.java`, `.../dto/SeoPromptUpdateDTO.java`
- Test: `.../apis/openai/service/SeoConfigServiceTest.java` (reescribir), `.../apis/openai/config/OpenAiPropertiesTest.java` (ajustar), `.../apis/openai/service/OpenAiSeoPromptTest.java` (ajustar)

**Interfaces:**
- Produces:
  - `SeoConfig` entity (getters: `getPromptHogar/getPromptGastro/getModel/getPrecioInput1m/getPrecioOutput1m/getFechaModificacion`).
  - `SeoConfigService.cargar(): SeoConfig`, `promptDe(SeoCanal): String`, `obtener(): SeoConfigDTO`, `actualizar(SeoConfigUpdateDTO): SeoConfigDTO`.
  - `SeoConfigDTO(String promptHogar, String promptGastro, String model, BigDecimal precioInput1m, BigDecimal precioOutput1m, LocalDateTime fechaModificacion)`.
  - Endpoints `GET /api/seo/config`, `PUT /api/seo/config`.
- Consumes: `SeoCanal` (enum `ar.com.leo.super_master_backend.apis.openai.SeoCanal`: `HOGAR`, `GASTRO`), `OpenAiCostoUtil.calcular(...)`, `NotFoundException`.

- [ ] **Step 1: Crear la migración SQL (cubre SEO + imagen; Task 2 usa imagen_config).**

Create `supermaster-backend/src/main/resources/db/2026-06-27-openai-config.sql`. Para `prompt_hogar`/`prompt_gastro` copiá **textualmente** el contenido de los INSERT de `db/2026-06-25-seo-ia-config-uso.sql` (canales HOGAR y GASTRO). Para `contenido` de imagen copiá el del INSERT de `db/2026-06-26-imagen-ia.sql`.

```sql
-- Config de OpenAI (SEO + imagen): prompt(s) + parámetros editables, unificados por servicio.
-- IMPORTANTE: cargar forzando UTF-8 para no romper los acentos (mojibake á->├í):
--   mysql --default-character-set=utf8mb4 -u root -p supermaster < 2026-06-27-openai-config.sql

CREATE TABLE supermaster.seo_config (
  id BIGINT NOT NULL,
  prompt_hogar TEXT NOT NULL,
  prompt_gastro TEXT NOT NULL,
  model VARCHAR(80) NOT NULL,
  precio_input_1m DECIMAL(12,4) NOT NULL,
  precio_output_1m DECIMAL(12,4) NOT NULL,
  fecha_modificacion DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supermaster.imagen_config (
  id BIGINT NOT NULL,
  contenido TEXT NOT NULL,
  model VARCHAR(80) NOT NULL,
  size VARCHAR(20) NOT NULL,
  output_format VARCHAR(10) NOT NULL,
  quality VARCHAR(10) NOT NULL,
  precio_input_1m DECIMAL(12,4) NOT NULL,
  precio_output_1m DECIMAL(12,4) NOT NULL,
  fecha_modificacion DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO supermaster.seo_config
  (id, prompt_hogar, prompt_gastro, model, precio_input_1m, precio_output_1m)
VALUES (1,
'<<<COPIAR contenido HOGAR de 2026-06-25-seo-ia-config-uso.sql>>>',
'<<<COPIAR contenido GASTRO de 2026-06-25-seo-ia-config-uso.sql>>>',
'gpt-5-mini', 0.25, 2.00);

INSERT INTO supermaster.imagen_config
  (id, contenido, model, size, output_format, quality, precio_input_1m, precio_output_1m)
VALUES (1,
'<<<COPIAR contenido de 2026-06-26-imagen-ia.sql>>>',
'gpt-image-2', '1024x1024', 'jpeg', 'high', 8.00, 30.00);

DROP TABLE supermaster.seo_prompt;
DROP TABLE supermaster.imagen_prompt;
```

- [ ] **Step 2: Crear la entidad `SeoConfig` y el repo.**

Create `SeoConfig.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "seo_config", schema = "supermaster")
public class SeoConfig {
    @Id @Column(name = "id")
    private Long id;

    @Column(name = "prompt_hogar", nullable = false, columnDefinition = "TEXT")
    private String promptHogar;

    @Column(name = "prompt_gastro", nullable = false, columnDefinition = "TEXT")
    private String promptGastro;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "precio_input_1m", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioInput1m;

    @Column(name = "precio_output_1m", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioOutput1m;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
```

Create `SeoConfigRepository.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.repository;

import ar.com.leo.super_master_backend.apis.openai.entity.SeoConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeoConfigRepository extends JpaRepository<SeoConfig, Long> {
}
```

- [ ] **Step 3: Crear los DTOs.**

Create `SeoConfigDTO.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SeoConfigDTO(
        String promptHogar,
        String promptGastro,
        String model,
        BigDecimal precioInput1m,
        BigDecimal precioOutput1m,
        LocalDateTime fechaModificacion) {}
```

Create `SeoConfigUpdateDTO.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SeoConfigUpdateDTO(
        @NotBlank(message = "El prompt de KT Hogar es obligatorio") String promptHogar,
        @NotBlank(message = "El prompt de KT Gastro es obligatorio") String promptGastro,
        @NotBlank(message = "El modelo es obligatorio") String model,
        @NotNull(message = "El precio de input es obligatorio") @DecimalMin(value = "0.0", message = "El precio de input no puede ser negativo") BigDecimal precioInput1m,
        @NotNull(message = "El precio de output es obligatorio") @DecimalMin(value = "0.0", message = "El precio de output no puede ser negativo") BigDecimal precioOutput1m) {}
```

- [ ] **Step 4: Escribir el test (rojo) de `SeoConfigService`.**

Replace `SeoConfigServiceTest.java` con:

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoConfig;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoConfigRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeoConfigServiceTest {

    @Mock SeoConfigRepository repository;
    @InjectMocks SeoConfigService service;

    private SeoConfig fila() {
        SeoConfig c = new SeoConfig();
        c.setId(1L);
        c.setPromptHogar("prompt hogar");
        c.setPromptGastro("prompt gastro");
        c.setModel("gpt-5-mini");
        c.setPrecioInput1m(new BigDecimal("0.25"));
        c.setPrecioOutput1m(new BigDecimal("2.00"));
        return c;
    }

    @Test
    void promptDe_devuelveColumnaSegunCanal() {
        when(repository.findById(1L)).thenReturn(Optional.of(fila()));
        assertThat(service.promptDe(SeoCanal.HOGAR)).isEqualTo("prompt hogar");
        assertThat(service.promptDe(SeoCanal.GASTRO)).isEqualTo("prompt gastro");
    }

    @Test
    void obtener_mapeaTodosLosCampos() {
        when(repository.findById(1L)).thenReturn(Optional.of(fila()));
        SeoConfigDTO dto = service.obtener();
        assertThat(dto.promptHogar()).isEqualTo("prompt hogar");
        assertThat(dto.model()).isEqualTo("gpt-5-mini");
        assertThat(dto.precioInput1m()).isEqualByComparingTo("0.25");
    }

    @Test
    void actualizar_persisteYDevuelve() {
        SeoConfig c = fila();
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        when(repository.save(c)).thenReturn(c);
        SeoConfigDTO dto = service.actualizar(new SeoConfigUpdateDTO(
                "h2", "g2", "gpt-5", new BigDecimal("1.0"), new BigDecimal("3.0")));
        assertThat(dto.promptHogar()).isEqualTo("h2");
        assertThat(dto.model()).isEqualTo("gpt-5");
        assertThat(c.getFechaModificacion()).isNotNull();
    }

    @Test
    void cargar_sinFila_lanzaNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.obtener()).isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 5: Correr el test y verlo fallar.**

Run: `cd supermaster-backend && mvn -o test -Dtest=SeoConfigServiceTest`
Expected: FAIL de compilación (SeoConfigService aún usa SeoPrompt).

- [ ] **Step 6: Reescribir `SeoConfigService`.**

Replace `SeoConfigService.java` con:

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoConfig;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoConfigRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Lee y actualiza la configuración de SEO (prompts por canal + parámetros). Singleton id=1. */
@Service
@RequiredArgsConstructor
public class SeoConfigService {

    private final SeoConfigRepository repository;

    @Transactional(readOnly = true)
    public SeoConfig cargar() {
        return repository.findById(1L).orElseThrow(() -> new NotFoundException(
                "No hay configuración de SEO (revisar el seed de seo_config)"));
    }

    /** Prompt del canal (HOGAR/GASTRO). */
    @Transactional(readOnly = true)
    public String promptDe(SeoCanal canal) {
        SeoConfig c = cargar();
        return canal == SeoCanal.GASTRO ? c.getPromptGastro() : c.getPromptHogar();
    }

    @Transactional(readOnly = true)
    public SeoConfigDTO obtener() {
        return toDto(cargar());
    }

    @Transactional
    public SeoConfigDTO actualizar(SeoConfigUpdateDTO dto) {
        SeoConfig c = cargar();
        c.setPromptHogar(dto.promptHogar());
        c.setPromptGastro(dto.promptGastro());
        c.setModel(dto.model());
        c.setPrecioInput1m(dto.precioInput1m());
        c.setPrecioOutput1m(dto.precioOutput1m());
        c.setFechaModificacion(LocalDateTime.now());
        repository.save(c);
        return toDto(c);
    }

    private SeoConfigDTO toDto(SeoConfig c) {
        return new SeoConfigDTO(c.getPromptHogar(), c.getPromptGastro(), c.getModel(),
                c.getPrecioInput1m(), c.getPrecioOutput1m(), c.getFechaModificacion());
    }
}
```

- [ ] **Step 7: Actualizar `SeoUsoService` para usar el config.**

Replace `SeoUsoService.java` con:

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.SeoUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoConfig;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoUso;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoUsoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Registra y lee el consumo acumulado de SEO (singleton id=1). Precios/modelo desde seo_config. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeoUsoService {

    private final SeoUsoRepository repository;
    private final SeoConfigService seoConfigService;

    public static BigDecimal calcularCosto(long tokensEntrada, long tokensSalida, BigDecimal precioIn1m, BigDecimal precioOut1m) {
        return OpenAiCostoUtil.calcular(tokensEntrada, tokensSalida, precioIn1m, precioOut1m);
    }

    @Transactional
    public void registrar(long tokensEntrada, long tokensSalida) {
        SeoConfig c = seoConfigService.cargar();
        BigDecimal costo = calcularCosto(tokensEntrada, tokensSalida, c.getPrecioInput1m(), c.getPrecioOutput1m());
        if (repository.registrar(tokensEntrada, tokensSalida, costo) == 0) {
            log.warn("seo_uso id=1 no existe; el uso no se registró");
        }
    }

    @Transactional(readOnly = true)
    public SeoUsoDTO obtener() {
        SeoConfig c = seoConfigService.cargar();
        SeoUso u = repository.findById(1L).orElseThrow(
                () -> new NotFoundException("Fila de uso de SEO (id=1) no encontrada"));
        return new SeoUsoDTO(u.getConsultas(), u.getTokensEntrada(), u.getTokensSalida(), u.getCostoUsd(),
                c.getModel(), c.getPrecioInput1m(), c.getPrecioOutput1m());
    }
}
```

- [ ] **Step 8: Actualizar `OpenAiSeoService.construirBody` para tomar prompt+model del config en una lectura.**

En `OpenAiSeoService.java`, reemplazar el método `construirBody` por (cambian solo las líneas de `model` y `system content`):

```java
    private String construirBody(SeoCanal canal, SeoContexto contexto) {
        ObjectNode body = objectMapper.createObjectNode();
        var config = seoConfigService.cargar();
        body.put("model", config.getModel());

        ArrayNode messages = body.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", canal == SeoCanal.GASTRO ? config.getPromptGastro() : config.getPromptHogar());
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", OpenAiSeoPrompts.userMessage(contexto));

        // response_format json_schema (structured outputs): fuerza la forma exacta del JSON.
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", "seo_nube");
        jsonSchema.put("strict", true);
        ObjectNode schema = jsonSchema.putObject("schema");
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = schema.putObject("properties");
        props.putObject("seo_title").put("type", "string");
        props.putObject("seo_description").put("type", "string");
        props.putObject("tags").put("type", "string");
        ArrayNode required = schema.putArray("required");
        required.add("seo_title");
        required.add("seo_description");
        required.add("tags");

        return objectMapper.writeValueAsString(body);
    }
```

(El campo `private final OpenAiProperties properties;` queda sin uso tras el trim del Step 11; quitar su import/inyección si el compilador lo marca como no usado no es necesario, pero si querés limpiar, sacá el parámetro `properties` del constructor y el campo. El SEO ya no lee nada de `properties`.)

> Nota: dejá el constructor de `OpenAiSeoService` recibiendo `OpenAiProperties` SOLO si algo más lo usa; tras este task no lo usa. Para evitar un campo muerto, **quitá** `properties` del constructor y el campo en este step.

Resultado esperado de `OpenAiSeoService` (constructor sin `properties`):

```java
    public OpenAiSeoService(RestClient openaiRestClient, ObjectMapper objectMapper,
                            SeoConfigService seoConfigService, SeoUsoService seoUsoService) {
        this.restClient = openaiRestClient;
        this.objectMapper = objectMapper;
        this.seoConfigService = seoConfigService;
        this.seoUsoService = seoUsoService;
    }
```

y borrar el campo `private final OpenAiProperties properties;` y su import.

- [ ] **Step 9: Actualizar `SeoController` (endpoints `/config`).**

Replace `SeoController.java` con:

```java
package ar.com.leo.super_master_backend.apis.openai.controller;

import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.service.SeoConfigService;
import ar.com.leo.super_master_backend.apis.openai.service.SeoUsoService;
import ar.com.leo.super_master_backend.config.Permisos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seo")
public class SeoController {

    private final SeoConfigService seoConfigService;
    private final SeoUsoService seoUsoService;

    @GetMapping("/config")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<SeoConfigDTO> config() {
        return ResponseEntity.ok(seoConfigService.obtener());
    }

    @PutMapping("/config")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<SeoConfigDTO> actualizar(@Valid @RequestBody SeoConfigUpdateDTO body) {
        return ResponseEntity.ok(seoConfigService.actualizar(body));
    }

    @GetMapping("/uso")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<SeoUsoDTO> uso() {
        return ResponseEntity.ok(seoUsoService.obtener());
    }
}
```

- [ ] **Step 10: Borrar las clases de prompt SEO ya sin uso.**

Delete: `entity/SeoPrompt.java`, `repository/SeoPromptRepository.java`, `dto/SeoPromptDTO.java`, `dto/SeoPromptUpdateDTO.java`.

- [ ] **Step 11: Trim de `OpenAiProperties` + `application.properties`.**

Replace `OpenAiProperties.java` con (sin `model`/precios):

```java
package ar.com.leo.super_master_backend.apis.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public OpenAiProperties {
        if (baseUrl == null) {
            baseUrl = "https://api.openai.com/v1";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(10);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(60);
        }
    }
}
```

En `src/main/resources/application.properties`, eliminar las líneas `openai.model=...`, `openai.precio-input-1m=...`, `openai.precio-output-1m=...` (dejar `openai.base-url`, `openai.connect-timeout`, `openai.read-timeout` si existen). Actualizar el comentario del bloque para aclarar que model/precios viven en `seo_config`.

- [ ] **Step 12: Ajustar `OpenAiPropertiesTest` y `OpenAiSeoPromptTest`.**

En `OpenAiPropertiesTest.java`: quitar las aserciones sobre `model()`/`precioInput1m()`/`precioOutput1m()` de `OpenAiProperties` (ya no existen). Mantener las de `baseUrl()`/timeouts.

En `OpenAiSeoPromptTest.java`: si construye/mockea `SeoPrompt`/`SeoPromptRepository` o `OpenAiProperties.model()`, adaptarlo a `SeoConfig`/`SeoConfigService.cargar()`. Si el test solo verifica el armado del body (prompt/model), mockear `seoConfigService.cargar()` devolviendo un `SeoConfig` con prompts y `model`.

- [ ] **Step 13: Correr los tests afectados y la suite.**

Run: `cd supermaster-backend && mvn -o test -Dtest='SeoConfigServiceTest,OpenAiPropertiesTest,OpenAiSeoPromptTest'`
Expected: PASS.
Run: `mvn -o test`
Expected: BUILD SUCCESS (suite completa verde).

- [ ] **Step 14: Commit.**

```bash
git add supermaster-backend/src/main/resources/db/2026-06-27-openai-config.sql \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/entity/SeoConfig.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/repository/SeoConfigRepository.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/SeoConfigDTO.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/SeoConfigUpdateDTO.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/SeoConfigService.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/SeoUsoService.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/OpenAiSeoService.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/controller/SeoController.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/config/OpenAiProperties.java \
  supermaster-backend/src/main/resources/application.properties \
  supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/SeoConfigServiceTest.java \
  supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/config/OpenAiPropertiesTest.java \
  supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/OpenAiSeoPromptTest.java
git rm supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/entity/SeoPrompt.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/repository/SeoPromptRepository.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/SeoPromptDTO.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/SeoPromptUpdateDTO.java
git commit -m "feat(seo): config en BD (prompts+params) editable, reemplaza seo_prompt

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Backend Imagen — tabla `imagen_config`, swap, formato↔extensión y trim

**Files:**
- Create: `.../apis/openai/entity/ImagenConfig.java`, `.../repository/ImagenConfigRepository.java`
- Create: `.../apis/openai/dto/ImagenConfigDTO.java`, `.../apis/openai/dto/ImagenConfigUpdateDTO.java`
- Modify: `.../apis/openai/service/ImagenIaConfigService.java`, `.../service/ImagenUsoService.java`, `.../service/OpenAiImagenService.java`
- Modify: `.../apis/openai/controller/ImagenIaController.java`
- Modify: `.../dominio/imagen/service/CaratulaService.java`, `.../dominio/imagen/service/ImagenService.java`, `.../dominio/imagen/controller/ImagenController.java`, `.../dominio/imagen/dto/CaratulaGeneradaDTO.java`
- Modify: `.../apis/openai/config/OpenAiImageProperties.java`, `src/main/resources/application.properties`
- Delete: `.../apis/openai/entity/ImagenPrompt.java`, `.../repository/ImagenPromptRepository.java`, `.../dto/ImagenPromptDTO.java`, `.../dto/ImagenPromptUpdateDTO.java`
- Test: `.../apis/openai/service/ImagenIaConfigServiceTest.java` (crear/reescribir), `.../dominio/imagen/service/ImagenServiceCaratulaTest.java` (ajustar firma `guardarCaratula`), `.../dominio/imagen/controller/ImagenControllerDetalleTest.java` (si referencia tipos), `.../apis/openai/parser/OpenAiImagenParserTest.java` (no cambia).

**Interfaces:**
- Consumes (de Task 1): la tabla `imagen_config` ya creada en la migración.
- Produces:
  - `ImagenConfig` entity (getters `getContenido/getModel/getSize/getOutputFormat/getQuality/getPrecioInput1m/getPrecioOutput1m/getFechaModificacion`).
  - `ImagenIaConfigService.cargar(): ImagenConfig`, `prompt(): String`, `obtener(): ImagenConfigDTO`, `actualizar(ImagenConfigUpdateDTO): ImagenConfigDTO`.
  - `CaratulaService.generar(sku): byte[]` (sin cambios), `generarConFormato(sku): GeneradaResultado` o que `ImagenController` consulte el formato del config (ver Step). `CaratulaService.guardar(sku, datos)` deriva la extensión.
  - `ImagenService.guardarCaratula(String sku, byte[] datos, String ext)` (firma de 2→3 args).
  - `CaratulaGeneradaDTO(String imagenBase64, String formato)`.
  - Endpoints `GET/PUT /api/imagen-ia/config`.

- [ ] **Step 1: Crear `ImagenConfig` + repo.**

Create `ImagenConfig.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "imagen_config", schema = "supermaster")
public class ImagenConfig {
    @Id @Column(name = "id")
    private Long id;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "size", nullable = false)
    private String size;

    @Column(name = "output_format", nullable = false)
    private String outputFormat;

    @Column(name = "quality", nullable = false)
    private String quality;

    @Column(name = "precio_input_1m", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioInput1m;

    @Column(name = "precio_output_1m", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioOutput1m;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
```

Create `ImagenConfigRepository.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.repository;

import ar.com.leo.super_master_backend.apis.openai.entity.ImagenConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImagenConfigRepository extends JpaRepository<ImagenConfig, Long> {
}
```

- [ ] **Step 2: Crear los DTOs (con validación de enums).**

Create `ImagenConfigDTO.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ImagenConfigDTO(
        String contenido,
        String model,
        String size,
        String outputFormat,
        String quality,
        BigDecimal precioInput1m,
        BigDecimal precioOutput1m,
        LocalDateTime fechaModificacion) {}
```

Create `ImagenConfigUpdateDTO.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ImagenConfigUpdateDTO(
        @NotBlank(message = "El prompt es obligatorio") String contenido,
        @NotBlank(message = "El modelo es obligatorio") String model,
        @Pattern(regexp = "1024x1024|1024x1536|1536x1024|auto", message = "Tamaño inválido") String size,
        @Pattern(regexp = "png|jpeg|webp", message = "Formato inválido") String outputFormat,
        @Pattern(regexp = "low|medium|high|auto", message = "Calidad inválida") String quality,
        @NotNull(message = "El precio de input es obligatorio") @DecimalMin(value = "0.0", message = "El precio de input no puede ser negativo") BigDecimal precioInput1m,
        @NotNull(message = "El precio de output es obligatorio") @DecimalMin(value = "0.0", message = "El precio de output no puede ser negativo") BigDecimal precioOutput1m) {}
```

- [ ] **Step 3: Test (rojo) de `ImagenIaConfigService`.**

Create/replace `ImagenIaConfigServiceTest.java`:

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenConfig;
import ar.com.leo.super_master_backend.apis.openai.repository.ImagenConfigRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImagenIaConfigServiceTest {

    @Mock ImagenConfigRepository repository;
    @InjectMocks ImagenIaConfigService service;

    private ImagenConfig fila() {
        ImagenConfig c = new ImagenConfig();
        c.setId(1L);
        c.setContenido("prompt caratula");
        c.setModel("gpt-image-2");
        c.setSize("1024x1024");
        c.setOutputFormat("jpeg");
        c.setQuality("high");
        c.setPrecioInput1m(new BigDecimal("8.00"));
        c.setPrecioOutput1m(new BigDecimal("30.00"));
        return c;
    }

    @Test
    void prompt_devuelveContenido() {
        when(repository.findById(1L)).thenReturn(Optional.of(fila()));
        assertThat(service.prompt()).isEqualTo("prompt caratula");
    }

    @Test
    void obtener_mapea() {
        when(repository.findById(1L)).thenReturn(Optional.of(fila()));
        ImagenConfigDTO dto = service.obtener();
        assertThat(dto.size()).isEqualTo("1024x1024");
        assertThat(dto.outputFormat()).isEqualTo("jpeg");
        assertThat(dto.precioInput1m()).isEqualByComparingTo("8.00");
    }

    @Test
    void actualizar_persiste() {
        ImagenConfig c = fila();
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        when(repository.save(c)).thenReturn(c);
        ImagenConfigDTO dto = service.actualizar(new ImagenConfigUpdateDTO(
                "p2", "gpt-image-2", "auto", "png", "medium",
                new BigDecimal("5.0"), new BigDecimal("20.0")));
        assertThat(dto.outputFormat()).isEqualTo("png");
        assertThat(c.getFechaModificacion()).isNotNull();
    }

    @Test
    void cargar_sinFila_lanzaNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.obtener()).isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 4: Correr el test y verlo fallar.**

Run: `cd supermaster-backend && mvn -o test -Dtest=ImagenIaConfigServiceTest`
Expected: FAIL de compilación (ImagenIaConfigService aún usa ImagenPrompt).

- [ ] **Step 5: Reescribir `ImagenIaConfigService`.**

Replace `ImagenIaConfigService.java` con:

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenConfig;
import ar.com.leo.super_master_backend.apis.openai.repository.ImagenConfigRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Lee y actualiza la configuración de la carátula (prompt + parámetros). Singleton id=1. */
@Service
@RequiredArgsConstructor
public class ImagenIaConfigService {

    private final ImagenConfigRepository repository;

    @Transactional(readOnly = true)
    public ImagenConfig cargar() {
        return repository.findById(1L).orElseThrow(() -> new NotFoundException(
                "No hay configuración de carátula (revisar el seed de imagen_config)"));
    }

    @Transactional(readOnly = true)
    public String prompt() {
        return cargar().getContenido();
    }

    @Transactional(readOnly = true)
    public ImagenConfigDTO obtener() {
        return toDto(cargar());
    }

    @Transactional
    public ImagenConfigDTO actualizar(ImagenConfigUpdateDTO dto) {
        ImagenConfig c = cargar();
        c.setContenido(dto.contenido());
        c.setModel(dto.model());
        c.setSize(dto.size());
        c.setOutputFormat(dto.outputFormat());
        c.setQuality(dto.quality());
        c.setPrecioInput1m(dto.precioInput1m());
        c.setPrecioOutput1m(dto.precioOutput1m());
        c.setFechaModificacion(LocalDateTime.now());
        repository.save(c);
        return toDto(c);
    }

    private ImagenConfigDTO toDto(ImagenConfig c) {
        return new ImagenConfigDTO(c.getContenido(), c.getModel(), c.getSize(), c.getOutputFormat(),
                c.getQuality(), c.getPrecioInput1m(), c.getPrecioOutput1m(), c.getFechaModificacion());
    }
}
```

- [ ] **Step 6: Actualizar `ImagenUsoService` para usar el config.**

Replace `ImagenUsoService.java` con:

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenConfig;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenUso;
import ar.com.leo.super_master_backend.apis.openai.repository.ImagenUsoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImagenUsoService {

    private final ImagenUsoRepository repository;
    private final ImagenIaConfigService configService;

    public static BigDecimal calcularCosto(long tokensEntrada, long tokensSalida, BigDecimal precioIn1m, BigDecimal precioOut1m) {
        return OpenAiCostoUtil.calcular(tokensEntrada, tokensSalida, precioIn1m, precioOut1m);
    }

    @Transactional
    public void registrar(long tokensEntrada, long tokensSalida) {
        ImagenConfig c = configService.cargar();
        BigDecimal costo = calcularCosto(tokensEntrada, tokensSalida, c.getPrecioInput1m(), c.getPrecioOutput1m());
        if (repository.registrar(tokensEntrada, tokensSalida, costo) == 0) {
            log.warn("imagen_uso id=1 no existe; el uso no se registró");
        }
    }

    @Transactional(readOnly = true)
    public ImagenUsoDTO obtener() {
        ImagenConfig c = configService.cargar();
        ImagenUso u = repository.findById(1L).orElseThrow(() -> new NotFoundException("Fila de uso de imagen (id=1) no encontrada"));
        return new ImagenUsoDTO(u.getConsultas(), u.getTokensEntrada(), u.getTokensSalida(), u.getCostoUsd(),
                c.getModel(), c.getPrecioInput1m(), c.getPrecioOutput1m());
    }
}
```

- [ ] **Step 7: `OpenAiImagenService` toma model/size/format/quality del config.**

En `OpenAiImagenService.java`, en `generarCaratula`, reemplazar las líneas del multipart por:

```java
        var cfg = configService.cargar();
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("model", cfg.getModel());
        parts.add("prompt", cfg.getContenido());
        parts.add("size", cfg.getSize());
        parts.add("output_format", cfg.getOutputFormat());
        parts.add("quality", cfg.getQuality());
        parts.add("image", new ByteArrayResource(cruda) {
            @Override
            public String getFilename() { return filename; }
        });
```

Quitar la inyección de `OpenAiImageProperties properties` del constructor/campo (ya no se usa nada de properties en este service). El constructor queda:

```java
    public OpenAiImagenService(@Qualifier("openaiImageRestClient") RestClient restClient,
                               ImagenIaConfigService configService,
                               ImagenUsoService usoService,
                               ObjectMapper objectMapper,
                               @Value("${app.secrets-dir}") String secretsDir) {
        this.restClient = restClient;
        this.configService = configService;
        this.usoService = usoService;
        this.objectMapper = objectMapper;
        this.secretsDir = secretsDir;
    }
```

y borrar el campo `private final OpenAiImageProperties properties;` y su import.

- [ ] **Step 8: Formato→extensión en `ImagenService` y `CaratulaService`.**

En `ImagenService.java`, cambiar la firma de `guardarCaratula` para recibir la extensión:

```java
    /** Guarda la carátula como {sku}.{ext} en baseDir e invalida el índice. */
    public void guardarCaratula(String sku, byte[] datos, String ext) {
        validarNombreSeguro(sku);
        try {
            Files.createDirectories(baseDir);
            Files.write(baseDir.resolve(sku.trim() + "." + ext), datos);
            invalidarIndice();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar la carátula de " + sku, e);
        }
    }
```

(Si el cuerpo actual difiere, conservalo pero parametrizando la extensión `"." + ext` en vez de `".jpg"`.)

En `CaratulaService.java`, `guardar` deriva la extensión del `output_format` del config:

```java
package ar.com.leo.super_master_backend.dominio.imagen.service;

import ar.com.leo.super_master_backend.apis.openai.service.ImagenIaConfigService;
import ar.com.leo.super_master_backend.apis.openai.service.OpenAiImagenService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Orquesta la carátula: cruda → OpenAI → imagen → disco. */
@Service
@RequiredArgsConstructor
public class CaratulaService {

    private final ImagenService imagenService;
    private final OpenAiImagenService openAiImagenService;
    private final ImagenIaConfigService configService;

    /** Genera (sin guardar) la carátula a partir de la cruda del SKU. */
    public byte[] generar(String sku) {
        String crudaNombre = imagenService.resolverCrudaPorSku(sku);
        if (crudaNombre == null) throw new NotFoundException("No hay imagen cruda para el SKU " + sku);
        byte[] cruda = imagenService.leerCrudaBytes(crudaNombre);
        return openAiImagenService.generarCaratula(cruda, crudaNombre);
    }

    /** Formato configurado (output_format), p.ej. "jpeg"/"png"/"webp". */
    public String formato() {
        return configService.cargar().getOutputFormat();
    }

    public void guardar(String sku, byte[] datos) {
        imagenService.guardarCaratula(sku, datos, extDe(formato()));
    }

    /** jpeg → jpg; el resto coincide con la extensión. */
    private static String extDe(String formato) {
        return "jpeg".equals(formato) ? "jpg" : formato;
    }
}
```

- [ ] **Step 9: `CaratulaGeneradaDTO` con formato + `ImagenController`.**

Replace `CaratulaGeneradaDTO.java` con:

```java
package ar.com.leo.super_master_backend.dominio.imagen.dto;

public record CaratulaGeneradaDTO(String imagenBase64, String formato) {}
```

En `ImagenController.java`, el endpoint generar devuelve también el formato (del config) y guardar no cambia su firma de request:

```java
    @PostMapping("/caratula/generar/{sku}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<CaratulaGeneradaDTO> generarCaratula(@PathVariable String sku) {
        byte[] jpg = caratulaService.generar(sku);
        String b64 = Base64.getEncoder().encodeToString(jpg);
        return ResponseEntity.ok(new CaratulaGeneradaDTO(b64, caratulaService.formato()));
    }
```

(El endpoint `guardar` queda igual; `caratulaService.guardar(sku, bytes)` ahora deriva la extensión internamente.)

- [ ] **Step 10: Ajustar tests de imagen.**

En `ImagenServiceCaratulaTest.java`: las llamadas a `guardarCaratula(sku, bytes)` pasan a `guardarCaratula(sku, bytes, "jpg")`; agregar (si querés) un caso con `"png"` que verifique que escribe `{sku}.png`.

En `ImagenControllerDetalleTest.java`: si construye `ImagenController` con `CaratulaService`, ahora `CaratulaService` necesita `ImagenIaConfigService` en su constructor — pasar un mock o `null` si ese test no ejercita generar/guardar/formato.

- [ ] **Step 11: Actualizar `ImagenIaController` (endpoints `/config`).**

Replace `ImagenIaController.java` con:

```java
package ar.com.leo.super_master_backend.apis.openai.controller;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigUpdateDTO;
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

    @GetMapping("/config")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ImagenConfigDTO> config() { return ResponseEntity.ok(configService.obtener()); }

    @PutMapping("/config")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<ImagenConfigDTO> actualizar(@Valid @RequestBody ImagenConfigUpdateDTO body) {
        return ResponseEntity.ok(configService.actualizar(body));
    }

    @GetMapping("/uso")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ImagenUsoDTO> uso() { return ResponseEntity.ok(usoService.obtener()); }
}
```

- [ ] **Step 12: Borrar clases de prompt de imagen + trim de `OpenAiImageProperties`.**

Delete: `entity/ImagenPrompt.java`, `repository/ImagenPromptRepository.java`, `dto/ImagenPromptDTO.java`, `dto/ImagenPromptUpdateDTO.java`.

Replace `OpenAiImageProperties.java` con (solo infra):

```java
package ar.com.leo.super_master_backend.apis.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "openai.image")
public record OpenAiImageProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public OpenAiImageProperties {
        if (baseUrl == null) baseUrl = "https://api.openai.com/v1";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(10);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(120);
    }
}
```

En `application.properties`, eliminar `openai.image.model`, `openai.image.precio-input-1m`, `openai.image.precio-output-1m`, `openai.image.size`, `openai.image.output-format`, `openai.image.quality` (si están). Dejar `openai.image.connect-timeout`/`read-timeout` si existen. Actualizar el comentario para indicar que model/size/format/quality/precios viven en `imagen_config`.

- [ ] **Step 13: Compilar y correr la suite.**

Run: `cd supermaster-backend && mvn -o test`
Expected: BUILD SUCCESS, 0 fallos/errores.

- [ ] **Step 14: Commit.**

```bash
git add <archivos creados/modificados de imagen> supermaster-backend/src/main/resources/application.properties
git rm <las 4 clases ImagenPrompt*>
git commit -m "feat(imagen-ia): config en BD (prompt+params) editable + formato/extensión configurable

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Frontend — pantalla SEO IA con formularios de config

**Files:**
- Modify: `supermaster-frontend/src/app/seo-ia/types.ts`, `seoService.ts`, `useSeoIa.ts`, `page.tsx`

**Interfaces:**
- Consumes (de Tasks 1-2): `GET/PUT /api/seo/config` → `SeoConfig`; `GET/PUT /api/imagen-ia/config` → `ImagenConfig`; `GET /api/seo/uso`, `GET /api/imagen-ia/uso` (sin cambios).
- Produces: tipos `SeoConfig`, `ImagenConfig`; APIs `getSeoConfigAPI/updateSeoConfigAPI/getImagenConfigAPI/updateImagenConfigAPI`.

- [ ] **Step 1: Tipos en `types.ts`.**

Replace `types.ts` con:

```ts
export type SeoCanal = "HOGAR" | "GASTRO";

export type SeoConfig = {
    promptHogar: string;
    promptGastro: string;
    model: string;
    precioInput1m: number;
    precioOutput1m: number;
    fechaModificacion: string | null;
};

export type ImagenConfig = {
    contenido: string;
    model: string;
    size: string;
    outputFormat: string;
    quality: string;
    precioInput1m: number;
    precioOutput1m: number;
    fechaModificacion: string | null;
};

export type SeoUso = {
    consultas: number;
    tokensEntrada: number;
    tokensSalida: number;
    costoUsd: number;
    modelo: string;
    precioInput1m: number;
    precioOutput1m: number;
};

export type ImagenUso = SeoUso;

export const CANAL_LABEL: Record<SeoCanal, string> = {
    HOGAR: "KT Hogar",
    GASTRO: "KT Gastro",
};

export const SIZE_OPCIONES = ["1024x1024", "1024x1536", "1536x1024", "auto"] as const;
export const QUALITY_OPCIONES = ["low", "medium", "high", "auto"] as const;
export const FORMATO_OPCIONES = ["png", "jpeg", "webp"] as const;
```

- [ ] **Step 2: APIs en `seoService.ts`.**

Replace `seoService.ts` con:

```ts
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import type { SeoConfig, ImagenConfig, SeoUso, ImagenUso } from "./types";

const API_URL = `${API_BASE_URL}/api/seo`;
const IMAGEN_URL = `${API_BASE_URL}/api/imagen-ia`;

export const getSeoConfigAPI = async (): Promise<SeoConfig> => {
    const res = await fetchAPI(`${API_URL}/config`);
    if (!res.ok) throw new Error("Error al obtener la configuración de SEO");
    return await res.json();
};

export const updateSeoConfigAPI = async (config: Omit<SeoConfig, "fechaModificacion">): Promise<SeoConfig> => {
    const res = await fetchAPI(`${API_URL}/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
    });
    if (!res.ok) throw new Error("Error al guardar la configuración de SEO");
    return await res.json();
};

export const getSeoUsoAPI = async (): Promise<SeoUso> => {
    const res = await fetchAPI(`${API_URL}/uso`);
    if (!res.ok) throw new Error("Error al obtener el uso de SEO");
    return await res.json();
};

export const getImagenConfigAPI = async (): Promise<ImagenConfig> => {
    const res = await fetchAPI(`${IMAGEN_URL}/config`);
    if (!res.ok) throw new Error("Error al obtener la configuración de carátula");
    return await res.json();
};

export const updateImagenConfigAPI = async (config: Omit<ImagenConfig, "fechaModificacion">): Promise<ImagenConfig> => {
    const res = await fetchAPI(`${IMAGEN_URL}/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
    });
    if (!res.ok) throw new Error("Error al guardar la configuración de carátula");
    return await res.json();
};

export const getImagenUsoAPI = async (): Promise<ImagenUso> => {
    const res = await fetchAPI(`${IMAGEN_URL}/uso`);
    if (!res.ok) throw new Error("Error al obtener el uso de carátula");
    return await res.json();
};
```

- [ ] **Step 3: Hook `useSeoIa.ts`.**

Replace `useSeoIa.ts` con:

```ts
"use client";
import { getErrorMessage } from "@/lib/errors";
import { useCallback, useEffect, useState } from "react";
import { notificar } from "../utils/notificar";
import {
    getImagenConfigAPI,
    getImagenUsoAPI,
    getSeoConfigAPI,
    getSeoUsoAPI,
    updateImagenConfigAPI,
    updateSeoConfigAPI,
} from "./seoService";
import type { ImagenConfig, ImagenUso, SeoConfig, SeoUso } from "./types";

export function useSeoIa() {
    const [seoConfig, setSeoConfig] = useState<SeoConfig | null>(null);
    const [imagenConfig, setImagenConfig] = useState<ImagenConfig | null>(null);
    const [uso, setUso] = useState<SeoUso | null>(null);
    const [imagenUso, setImagenUso] = useState<ImagenUso | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSavingSeo, setIsSavingSeo] = useState(false);
    const [isSavingImagen, setIsSavingImagen] = useState(false);

    const fetchData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [sc, ic, u, iu] = await Promise.all([
                getSeoConfigAPI(),
                getImagenConfigAPI(),
                getSeoUsoAPI(),
                getImagenUsoAPI(),
            ]);
            setSeoConfig(sc);
            setImagenConfig(ic);
            setUso(u);
            setImagenUso(iu);
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "No se pudo cargar la configuración de SEO IA"));
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);

    const saveSeoConfig = async (config: Omit<SeoConfig, "fechaModificacion">) => {
        setIsSavingSeo(true);
        try {
            const actualizado = await updateSeoConfigAPI(config);
            setSeoConfig(actualizado);
            notificar.success("Configuración de SEO guardada");
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al guardar la configuración de SEO"));
        } finally {
            setIsSavingSeo(false);
        }
    };

    const saveImagenConfig = async (config: Omit<ImagenConfig, "fechaModificacion">) => {
        setIsSavingImagen(true);
        try {
            const actualizado = await updateImagenConfigAPI(config);
            setImagenConfig(actualizado);
            notificar.success("Configuración de carátula guardada");
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al guardar la configuración de carátula"));
        } finally {
            setIsSavingImagen(false);
        }
    };

    return { seoConfig, imagenConfig, uso, imagenUso, isLoading, isSavingSeo, isSavingImagen, saveSeoConfig, saveImagenConfig };
}
```

- [ ] **Step 4: Página `page.tsx` (un formulario por servicio).**

Replace `page.tsx` con:

```tsx
"use client";

import { useEffect, useState } from "react";
import Button from "../components/Button/Button";
import { useSeoIa } from "./useSeoIa";
import { FORMATO_OPCIONES, QUALITY_OPCIONES, SIZE_OPCIONES } from "./types";
import { SparklesIcon } from "@heroicons/react/24/outline";

export default function SeoIaPage() {
    const { seoConfig, imagenConfig, uso, imagenUso, isLoading, isSavingSeo, isSavingImagen, saveSeoConfig, saveImagenConfig } = useSeoIa();

    // Borradores SEO
    const [promptHogar, setPromptHogar] = useState("");
    const [promptGastro, setPromptGastro] = useState("");
    const [seoModel, setSeoModel] = useState("");
    const [seoIn, setSeoIn] = useState("");
    const [seoOut, setSeoOut] = useState("");

    // Borradores Imagen
    const [imgPrompt, setImgPrompt] = useState("");
    const [imgModel, setImgModel] = useState("");
    const [imgSize, setImgSize] = useState("1024x1024");
    const [imgQuality, setImgQuality] = useState("high");
    const [imgFormato, setImgFormato] = useState("jpeg");
    const [imgIn, setImgIn] = useState("");
    const [imgOut, setImgOut] = useState("");

    useEffect(() => {
        if (seoConfig) {
            setPromptHogar(seoConfig.promptHogar);
            setPromptGastro(seoConfig.promptGastro);
            setSeoModel(seoConfig.model);
            setSeoIn(String(seoConfig.precioInput1m));
            setSeoOut(String(seoConfig.precioOutput1m));
        }
    }, [seoConfig]);

    useEffect(() => {
        if (imagenConfig) {
            setImgPrompt(imagenConfig.contenido);
            setImgModel(imagenConfig.model);
            setImgSize(imagenConfig.size);
            setImgQuality(imagenConfig.quality);
            setImgFormato(imagenConfig.outputFormat);
            setImgIn(String(imagenConfig.precioInput1m));
            setImgOut(String(imagenConfig.precioOutput1m));
        }
    }, [imagenConfig]);

    const fmt = (n: number) => new Intl.NumberFormat("es-AR").format(n);
    const inputCls = "w-full rounded-lg border border-slate-300 bg-white p-2 text-sm text-slate-800 focus:border-primary focus:ring-primary dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100";
    const textareaCls = "h-64 w-full rounded-lg border border-slate-300 bg-white p-3 font-mono text-xs text-slate-800 focus:border-primary focus:ring-primary dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100";

    const guardarSeo = () => saveSeoConfig({
        promptHogar, promptGastro, model: seoModel,
        precioInput1m: Number(seoIn), precioOutput1m: Number(seoOut),
    });
    const guardarImagen = () => saveImagenConfig({
        contenido: imgPrompt, model: imgModel, size: imgSize, quality: imgQuality, outputFormat: imgFormato,
        precioInput1m: Number(imgIn), precioOutput1m: Number(imgOut),
    });

    const usoBox = (titulo: string, u: typeof uso) => (
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60">
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">{titulo}</h2>
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

    return (
        <main className="p-4 md:p-5 bg-gray-50 dark:bg-slate-900 flex flex-col gap-4">
            <div>
                <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100 flex items-center gap-3">
                    <SparklesIcon className="w-8 h-8 text-purple-600 dark:text-purple-400" />
                    SEO IA
                </h1>
                <p className="text-gray-500 dark:text-slate-400 text-sm mt-1">Configuración de OpenAI (prompts + parámetros) y consumo</p>
            </div>

            {usoBox("Uso de IA — SEO (acumulado)", uso)}
            {usoBox("Uso de IA — Carátula (acumulado)", imagenUso)}

            {/* Config SEO */}
            <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-700 space-y-3">
                <h3 className="font-semibold text-slate-700 dark:text-slate-200">Configuración SEO</h3>
                <div>
                    <label className="text-xs text-slate-500">Prompt — KT Hogar</label>
                    <textarea className={textareaCls} value={promptHogar} onChange={e => setPromptHogar(e.target.value)} disabled={isLoading} />
                </div>
                <div>
                    <label className="text-xs text-slate-500">Prompt — KT Gastro</label>
                    <textarea className={textareaCls} value={promptGastro} onChange={e => setPromptGastro(e.target.value)} disabled={isLoading} />
                </div>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                    <div><label className="text-xs text-slate-500">Modelo</label><input className={inputCls} value={seoModel} onChange={e => setSeoModel(e.target.value)} disabled={isLoading} /></div>
                    <div><label className="text-xs text-slate-500">US$ input / 1M</label><input type="number" step="0.0001" className={inputCls} value={seoIn} onChange={e => setSeoIn(e.target.value)} disabled={isLoading} /></div>
                    <div><label className="text-xs text-slate-500">US$ output / 1M</label><input type="number" step="0.0001" className={inputCls} value={seoOut} onChange={e => setSeoOut(e.target.value)} disabled={isLoading} /></div>
                </div>
                <div className="flex justify-end">
                    <Button variant="dark" onClick={guardarSeo} disabled={isSavingSeo || isLoading || !promptHogar.trim() || !promptGastro.trim() || !seoModel.trim()}>
                        {isSavingSeo ? "Guardando…" : "Guardar"}
                    </Button>
                </div>
            </div>

            {/* Config Imagen */}
            <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-700 space-y-3">
                <h3 className="font-semibold text-slate-700 dark:text-slate-200">Configuración Carátula</h3>
                <div>
                    <label className="text-xs text-slate-500">Prompt</label>
                    <textarea className={textareaCls} value={imgPrompt} onChange={e => setImgPrompt(e.target.value)} disabled={isLoading} />
                </div>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                    <div><label className="text-xs text-slate-500">Modelo</label><input className={inputCls} value={imgModel} onChange={e => setImgModel(e.target.value)} disabled={isLoading} /></div>
                    <div><label className="text-xs text-slate-500">Tamaño</label>
                        <select className={inputCls} value={imgSize} onChange={e => setImgSize(e.target.value)} disabled={isLoading}>
                            {SIZE_OPCIONES.map(o => <option key={o} value={o}>{o}</option>)}
                        </select>
                    </div>
                    <div><label className="text-xs text-slate-500">Calidad</label>
                        <select className={inputCls} value={imgQuality} onChange={e => setImgQuality(e.target.value)} disabled={isLoading}>
                            {QUALITY_OPCIONES.map(o => <option key={o} value={o}>{o}</option>)}
                        </select>
                    </div>
                    <div><label className="text-xs text-slate-500">Formato</label>
                        <select className={inputCls} value={imgFormato} onChange={e => setImgFormato(e.target.value)} disabled={isLoading}>
                            {FORMATO_OPCIONES.map(o => <option key={o} value={o}>{o}</option>)}
                        </select>
                    </div>
                    <div><label className="text-xs text-slate-500">US$ input / 1M</label><input type="number" step="0.0001" className={inputCls} value={imgIn} onChange={e => setImgIn(e.target.value)} disabled={isLoading} /></div>
                    <div><label className="text-xs text-slate-500">US$ output / 1M</label><input type="number" step="0.0001" className={inputCls} value={imgOut} onChange={e => setImgOut(e.target.value)} disabled={isLoading} /></div>
                </div>
                <div className="flex justify-end">
                    <Button variant="dark" onClick={guardarImagen} disabled={isSavingImagen || isLoading || !imgPrompt.trim() || !imgModel.trim()}>
                        {isSavingImagen ? "Guardando…" : "Guardar"}
                    </Button>
                </div>
            </div>
        </main>
    );
}
```

- [ ] **Step 5: Verificar tsc/lint.**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0.
Run: `npm run lint`
Expected: sin errores `error` nuevos en los archivos tocados.

- [ ] **Step 6: Commit.**

```bash
git add supermaster-frontend/src/app/seo-ia/types.ts supermaster-frontend/src/app/seo-ia/seoService.ts supermaster-frontend/src/app/seo-ia/useSeoIa.ts supermaster-frontend/src/app/seo-ia/page.tsx
git commit -m "feat(seo-ia): pantalla con config (prompts+parámetros) editable por servicio

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Frontend — preview de carátula respeta el formato

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`, `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes (de Task 2): `POST /api/imagenes/caratula/generar/{sku}` → `{ imagenBase64, formato }`.

- [ ] **Step 1: Tipar el retorno de `generarCaratulaAPI` con `formato`.**

En `productosService.ts`, cambiar la firma:

```ts
export async function generarCaratulaAPI(sku: string): Promise<{ imagenBase64: string; formato: string }> {
```

(El resto del cuerpo no cambia: hace el POST y devuelve `res.json()`, que ahora incluye `formato`.)

- [ ] **Step 2: Guardar el formato y usarlo en el preview.**

En `ProductoFormModal.tsx`:

- Agregar estado: `const [caratulaFormato, setCaratulaFormato] = useState<string>("jpeg");`
- En `generarCaratula`, tras `const r = await generarCaratulaAPI(sku.trim());`:
  ```ts
  setCaratulaPreview(r.imagenBase64);
  setCaratulaFormato(r.formato);
  ```
- En el `<img>` del preview, reemplazar el MIME fijo:
  ```tsx
  <img src={`data:image/${caratulaFormato};base64,${caratulaPreview}`} alt="Preview carátula" className="mx-auto max-h-64" />
  ```

- [ ] **Step 3: Verificar tsc/lint.**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0.

- [ ] **Step 4: Commit.**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "fix(modal): el preview de carátula usa el formato configurado (no fijo a jpeg)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Cierre (tras las 4 tareas)

- Suite completa: `cd supermaster-backend && mvn -o test` → verde. Frontend: `npx tsc --noEmit` → 0.
- Review final de toda la rama.
- **Pendiente operativo del usuario:** correr `db/2026-06-27-openai-config.sql` con `--default-character-set=utf8mb4` (crea seo_config/imagen_config, resiembra prompts en UTF-8 correcto, dropea seo_prompt/imagen_prompt).

## Notas de auto-revisión (cobertura del spec)

- Modelo de datos (seo_config 2 columnas de prompt + params; imagen_config prompt+params): Task 1 + Task 2 + migración.
- Generación y costo leen de BD: Task 1 (SEO), Task 2 (imagen).
- Formato↔extensión: Task 2 (backend) + Task 4 (preview).
- Endpoints `/config`: Task 1 + Task 2.
- Validación (enum `@Pattern`, precios ≥ 0): Task 2 DTO + Task 1 DTO.
- Properties solo infra: trim en Task 1 (OpenAiProperties) y Task 2 (OpenAiImageProperties).
- UI un formulario por servicio: Task 3.
- Migración que arregla mojibake: Task 1 Step 1.
