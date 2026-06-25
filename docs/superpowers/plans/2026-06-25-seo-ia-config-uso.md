# SEO IA (config y uso) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pantalla "SEO IA" que permite editar los prompts de generación de SEO de Tienda Nube (persistidos en BD) y ver el consumo acumulado de OpenAI (consultas, tokens, costo USD) junto al modelo y precios vigentes.

**Architecture:** Dos tablas dedicadas (`seo_prompt` por canal, `seo_uso` singleton acumulado). `OpenAiSeoService` toma el system prompt de la BD (sin fallback al código) y, tras cada generación, lee el objeto `usage` del response y registra el uso de forma atómica y best-effort. Una página frontend (patrón hook+service de `configuracion-ml`) edita los prompts y muestra el uso.

**Tech Stack:** Spring Boot 4 / Java 25, JPA/Hibernate (MySQL, `ddl-auto=validate`), Jackson 3 (`tools.jackson`), Lombok; Next.js 16 / React 19 / TS.

## Global Constraints

- `ddl-auto=validate`: NO se crean tablas solas. Todo cambio de schema va en un script SQL manual en `supermaster-backend/src/main/resources/db/` y se aplica a la BD local ANTES de arrancar/validar.
- Tests backend OFFLINE con el Maven instalado: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o test` (NO `mvnw`, que falla por red). Para acotar: `mvn -o -Dtest=ClaseTest test`.
- BD local MySQL: `"C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe" --host=localhost --port=3306 --user=root --password=admin supermaster < script.sql` (requiere `dangerouslyDisableSandbox`).
- DTOs `record`: agregar un componente rompe los `new XDTO(...)` posicionales; verificar con `mvn -o test` (no solo compile).
- OSIV off (`open-in-view=false`): asociaciones LAZY solo dentro de `@Transactional`.
- Jackson 3: `tools.jackson.databind.*`, los nodos usan `.asString()` / `.asLong(default)`.
- Permisos: `Permisos.INTEGRACIONES_VER` (lectura), `Permisos.INTEGRACIONES_EDITAR` (guardar prompts). NO crear permiso nuevo.
- Los prompts viven SOLO en la BD (sembrados por la migración). NO hay fallback al código: `OpenAiSeoPrompts` pierde `SYSTEM_BASE`/`REGLA_GASTRO`/`systemPrompt`.
- NO ejecutar nada que llame a las APIs reales (ML/Dux/Nube/OpenAI). Solo tests unitarios offline.
- Trabajar en `main`. Cada commit termina con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## File Structure

**Backend (nuevos salvo aclaración):**
- `src/main/resources/db/2026-06-25-seo-ia-config-uso.sql` — schema + seed.
- `apis/openai/entity/SeoPrompt.java`, `apis/openai/entity/SeoUso.java` — entidades.
- `apis/openai/repository/SeoPromptRepository.java`, `apis/openai/repository/SeoUsoRepository.java` — repos.
- `apis/openai/dto/SeoPromptDTO.java`, `SeoPromptUpdateDTO.java`, `SeoUsoDTO.java` — DTOs.
- `apis/openai/service/SeoConfigService.java`, `SeoUsoService.java` — servicios nuevos.
- `apis/openai/controller/SeoController.java` — endpoints.
- `apis/openai/config/OpenAiProperties.java` (MOD) — agregar precios.
- `apis/openai/service/OpenAiSeoService.java` (MOD) — prompt de BD + registro de uso.
- `apis/openai/service/OpenAiSeoPrompts.java` (MOD) — quitar prompts hardcodeados.
- `src/main/resources/application.properties` (MOD) — precios.

**Frontend (nuevos salvo aclaración):**
- `src/app/seo-ia/types.ts`, `seoService.ts`, `useSeoIa.ts`, `page.tsx`.
- `src/app/components/navigation/navigationConfig.tsx` (MOD) — entrada de menú.

---

### Task 1: Schema BD (tablas + seed) y aplicación a la BD local

**Files:**
- Create: `supermaster-backend/src/main/resources/db/2026-06-25-seo-ia-config-uso.sql`

**Interfaces:**
- Produces: tablas `supermaster.seo_prompt(id, canal, contenido, fecha_modificacion)` y `supermaster.seo_uso(id, consultas, tokens_entrada, tokens_salida, costo_usd)`, sembradas. Las usan las entidades de la Task 2.

- [ ] **Step 1: Escribir el script SQL**

Crear `2026-06-25-seo-ia-config-uso.sql` con este contenido EXACTO (el texto de los prompts es el que hoy vive en `OpenAiSeoPrompts.SYSTEM_BASE` y `REGLA_GASTRO`):

```sql
-- SEO IA (config y uso): prompts editables por canal + consumo acumulado de OpenAI.
CREATE TABLE supermaster.seo_prompt (
  id BIGINT NOT NULL AUTO_INCREMENT,
  canal VARCHAR(10) NOT NULL,
  contenido TEXT NOT NULL,
  fecha_modificacion DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_seo_prompt_canal (canal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supermaster.seo_uso (
  id BIGINT NOT NULL AUTO_INCREMENT,
  consultas BIGINT NOT NULL DEFAULT 0,
  tokens_entrada BIGINT NOT NULL DEFAULT 0,
  tokens_salida BIGINT NOT NULL DEFAULT 0,
  costo_usd DECIMAL(14,6) NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed: prompt HOGAR = SYSTEM_BASE actual.
INSERT INTO supermaster.seo_prompt (canal, contenido) VALUES ('HOGAR',
'Eres un especialista en SEO para ecommerce.

Genera exclusivamente un JSON válido con las siguientes propiedades:

{
  "seo_title": "",
  "seo_description": "",
  "tags": ""
}

Reglas:
- seo_title: máximo 70 caracteres.
- seo_description: máximo 320 caracteres.
- tags: entre 4 y 6 tags separados por comas. Usá términos de búsqueda generales (tipo de producto, material, color, marca, uso); NO uses como tags las dimensiones ni medidas (por ejemplo 21x21x9,5 cm), ni códigos, SKU o referencias (ni siquiera dentro de otra palabra, por ejemplo nada de "modelo712B").
- No inventar características que no estén presentes en la información.
- Optimizar para búsquedas en español.
- No incluir explicaciones ni texto fuera del JSON.
- No incluir códigos internos, SKU ni referencias (por ejemplo textos entre paréntesis como 712B) en ningún campo.
- No agregar al seo_title sufijos ni etiquetas de rubro o categoría (por ejemplo nada de "- Gastronómico" al final).');

-- Seed: prompt GASTRO = SYSTEM_BASE + REGLA_GASTRO actual.
INSERT INTO supermaster.seo_prompt (canal, contenido) VALUES ('GASTRO',
'Eres un especialista en SEO para ecommerce.

Genera exclusivamente un JSON válido con las siguientes propiedades:

{
  "seo_title": "",
  "seo_description": "",
  "tags": ""
}

Reglas:
- seo_title: máximo 70 caracteres.
- seo_description: máximo 320 caracteres.
- tags: entre 4 y 6 tags separados por comas. Usá términos de búsqueda generales (tipo de producto, material, color, marca, uso); NO uses como tags las dimensiones ni medidas (por ejemplo 21x21x9,5 cm), ni códigos, SKU o referencias (ni siquiera dentro de otra palabra, por ejemplo nada de "modelo712B").
- No inventar características que no estén presentes en la información.
- Optimizar para búsquedas en español.
- No incluir explicaciones ni texto fuera del JSON.
- No incluir códigos internos, SKU ni referencias (por ejemplo textos entre paréntesis como 712B) en ningún campo.
- No agregar al seo_title sufijos ni etiquetas de rubro o categoría (por ejemplo nada de "- Gastronómico" al final).
- Enfocá el SEO en el rubro gastronómico y profesional. Podés mencionar en la descripción los usos en cocinas de restaurantes, cafeterías, pastelerías, panaderías, pizzerías o bares cuando ayuden al posicionamiento, integrados de forma natural en el texto (no como una lista forzada ni una muletilla al final). No agregues el rubro como sufijo del seo_title.');

-- Seed: fila singleton de uso en 0.
INSERT INTO supermaster.seo_uso (id, consultas, tokens_entrada, tokens_salida, costo_usd) VALUES (1, 0, 0, 0, 0);
```

- [ ] **Step 2: Aplicar el script a la BD local**

Run (requiere `dangerouslyDisableSandbox`):
```bash
"C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe" --host=localhost --port=3306 --user=root --password=admin supermaster < supermaster-backend/src/main/resources/db/2026-06-25-seo-ia-config-uso.sql
```
Expected: sin error.

- [ ] **Step 3: Verificar tablas y seed**

Run:
```bash
"C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe" --host=localhost --port=3306 --user=root --password=admin supermaster -e "SELECT canal, LEFT(contenido,30) FROM seo_prompt; SELECT * FROM seo_uso;"
```
Expected: 2 filas en `seo_prompt` (HOGAR, GASTRO) y 1 fila en `seo_uso` con `id=1` y todo en 0.

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/resources/db/2026-06-25-seo-ia-config-uso.sql
git commit -m "feat(seo): schema seo_prompt + seo_uso con seed de prompts y uso en cero"
```

---

### Task 2: Entidades y repositorios

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/entity/SeoPrompt.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/entity/SeoUso.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/repository/SeoPromptRepository.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/repository/SeoUsoRepository.java`

**Interfaces:**
- Consumes: tablas de la Task 1; el enum `ar.com.leo.super_master_backend.apis.openai.SeoCanal` (valores `GASTRO`, `HOGAR`).
- Produces:
  - `SeoPrompt` (getters: `getId():Long`, `getCanal():SeoCanal`, `getContenido():String`, `getFechaModificacion():LocalDateTime`; setters correspondientes).
  - `SeoUso` (getters `getConsultas():long`, `getTokensEntrada():long`, `getTokensSalida():long`, `getCostoUsd():BigDecimal`).
  - `SeoPromptRepository.findByCanal(SeoCanal):Optional<SeoPrompt>`.
  - `SeoUsoRepository.findById(1L)` (heredado) y `int registrar(long in, long out, BigDecimal costo)` (UPDATE atómico sobre `id=1`).

- [ ] **Step 1: Crear la entidad `SeoPrompt`**

```java
package ar.com.leo.super_master_backend.apis.openai.entity;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "seo_prompt", schema = "supermaster")
public class SeoPrompt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 10)
    private SeoCanal canal;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
```

- [ ] **Step 2: Crear la entidad `SeoUso`**

```java
package ar.com.leo.super_master_backend.apis.openai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "seo_uso", schema = "supermaster")
public class SeoUso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
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

- [ ] **Step 3: Crear `SeoPromptRepository`**

```java
package ar.com.leo.super_master_backend.apis.openai.repository;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeoPromptRepository extends JpaRepository<SeoPrompt, Long> {
    Optional<SeoPrompt> findByCanal(SeoCanal canal);
}
```

- [ ] **Step 4: Crear `SeoUsoRepository` con UPDATE atómico**

```java
package ar.com.leo.super_master_backend.apis.openai.repository;

import ar.com.leo.super_master_backend.apis.openai.entity.SeoUso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface SeoUsoRepository extends JpaRepository<SeoUso, Long> {

    /** Incremento atómico de la fila singleton (id=1): evita lost-updates con generaciones en paralelo. */
    @Modifying
    @Query("UPDATE SeoUso s SET s.consultas = s.consultas + 1, " +
           "s.tokensEntrada = s.tokensEntrada + :in, " +
           "s.tokensSalida = s.tokensSalida + :out, " +
           "s.costoUsd = s.costoUsd + :costo WHERE s.id = 1")
    int registrar(@Param("in") long tokensEntrada, @Param("out") long tokensSalida, @Param("costo") BigDecimal costo);
}
```

- [ ] **Step 5: Compilar**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -q compile`
Expected: BUILD SUCCESS (exit 0).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/entity/SeoPrompt.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/entity/SeoUso.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/repository/SeoPromptRepository.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/repository/SeoUsoRepository.java
git commit -m "feat(seo): entidades SeoPrompt/SeoUso y repos (registro atomico de uso)"
```

---

### Task 3: Precios en OpenAiProperties + application.properties

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/config/OpenAiProperties.java`
- Modify: `supermaster-backend/src/main/resources/application.properties`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/config/OpenAiPropertiesTest.java`

**Interfaces:**
- Produces: `OpenAiProperties.precioInput1m():BigDecimal` y `precioOutput1m():BigDecimal` (defaults `0.25` y `2.00`). Las usa `SeoUsoService` (Task 4).

- [ ] **Step 1: Escribir el test de defaults**

Crear `OpenAiPropertiesTest.java`:
```java
package ar.com.leo.super_master_backend.apis.openai.config;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiPropertiesTest {
    @Test
    void defaults_precios() {
        OpenAiProperties p = new OpenAiProperties(null, null, null, null, null, null);
        assertThat(p.precioInput1m()).isEqualByComparingTo("0.25");
        assertThat(p.precioOutput1m()).isEqualByComparingTo("2.00");
    }

    @Test
    void respeta_precios_provistos() {
        OpenAiProperties p = new OpenAiProperties(null, null, null, null, new BigDecimal("0.15"), new BigDecimal("0.60"));
        assertThat(p.precioInput1m()).isEqualByComparingTo("0.15");
        assertThat(p.precioOutput1m()).isEqualByComparingTo("0.60");
    }
}
```

- [ ] **Step 2: Correr el test (debe fallar a compilar — el record aún no tiene los componentes)**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -Dtest=OpenAiPropertiesTest test`
Expected: FAIL de compilación (constructor no acepta 6 args).

- [ ] **Step 3: Agregar los componentes al record**

Reemplazar el record en `OpenAiProperties.java` por:
```java
package ar.com.leo.super_master_backend.apis.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String baseUrl,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        BigDecimal precioInput1m,
        BigDecimal precioOutput1m
) {
    public OpenAiProperties {
        if (baseUrl == null) {
            baseUrl = "https://api.openai.com/v1";
        }
        if (model == null) {
            model = "gpt-5-mini";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(10);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(60);
        }
        if (precioInput1m == null) {
            precioInput1m = new BigDecimal("0.25");
        }
        if (precioOutput1m == null) {
            precioOutput1m = new BigDecimal("2.00");
        }
    }
}
```

- [ ] **Step 4: Agregar los precios a application.properties**

Bajo la sección `# OpenAI`, después de `openai.model=gpt-5-mini`, agregar:
```properties
# Precios USD por millón de tokens (para estimar el costo del uso). Ajustar si cambia el plan/modelo.
openai.precio-input-1m=0.25
openai.precio-output-1m=2.00
```

- [ ] **Step 5: Correr el test (debe pasar)**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -Dtest=OpenAiPropertiesTest test`
Expected: PASS (Tests run: 2, Failures: 0).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/config/OpenAiProperties.java supermaster-backend/src/main/resources/application.properties supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/config/OpenAiPropertiesTest.java
git commit -m "feat(seo): precios USD por 1M tokens configurables en OpenAiProperties"
```

---

### Task 4: SeoUsoService (cálculo de costo + registro + lectura)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/SeoUsoDTO.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/SeoUsoService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/SeoUsoServiceTest.java`

**Interfaces:**
- Consumes: `SeoUsoRepository` (Task 2), `OpenAiProperties` (Task 3).
- Produces:
  - `SeoUsoDTO(long consultas, long tokensEntrada, long tokensSalida, BigDecimal costoUsd, String modelo, BigDecimal precioInput1m, BigDecimal precioOutput1m)`.
  - `SeoUsoService.calcularCosto(long in, long out, BigDecimal precioIn1m, BigDecimal precioOut1m):BigDecimal` (estático, puro).
  - `SeoUsoService.registrar(long in, long out):void` (`@Transactional`).
  - `SeoUsoService.obtener():SeoUsoDTO` (`@Transactional(readOnly=true)`).

- [ ] **Step 1: Crear el DTO**

```java
package ar.com.leo.super_master_backend.apis.openai.dto;

import java.math.BigDecimal;

public record SeoUsoDTO(
        long consultas,
        long tokensEntrada,
        long tokensSalida,
        BigDecimal costoUsd,
        String modelo,
        BigDecimal precioInput1m,
        BigDecimal precioOutput1m
) {}
```

- [ ] **Step 2: Escribir el test del cálculo de costo**

Crear `SeoUsoServiceTest.java`:
```java
package ar.com.leo.super_master_backend.apis.openai.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class SeoUsoServiceTest {

    private static final BigDecimal P_IN = new BigDecimal("0.25");
    private static final BigDecimal P_OUT = new BigDecimal("2.00");

    @Test
    void costo_unMillonInput_igualPrecioInput() {
        assertThat(SeoUsoService.calcularCosto(1_000_000, 0, P_IN, P_OUT)).isEqualByComparingTo("0.25");
    }

    @Test
    void costo_unMillonOutput_igualPrecioOutput() {
        assertThat(SeoUsoService.calcularCosto(0, 1_000_000, P_IN, P_OUT)).isEqualByComparingTo("2.00");
    }

    @Test
    void costo_combinado_sumaAmbos() {
        // 500k in -> 0.125 ; 250k out -> 0.50 ; total 0.625
        assertThat(SeoUsoService.calcularCosto(500_000, 250_000, P_IN, P_OUT)).isEqualByComparingTo("0.625");
    }

    @Test
    void costo_cero_esCero() {
        assertThat(SeoUsoService.calcularCosto(0, 0, P_IN, P_OUT)).isEqualByComparingTo("0");
    }
}
```

- [ ] **Step 3: Correr el test (debe fallar a compilar)**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -Dtest=SeoUsoServiceTest test`
Expected: FAIL de compilación (`SeoUsoService` no existe).

- [ ] **Step 4: Implementar `SeoUsoService`**

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.config.OpenAiProperties;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoUso;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoUsoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Registra y lee el consumo acumulado de OpenAI (singleton id=1). */
@Service
@RequiredArgsConstructor
public class SeoUsoService {

    private static final BigDecimal MILLON = new BigDecimal("1000000");

    private final SeoUsoRepository repository;
    private final OpenAiProperties properties;

    /** Costo USD = in/1e6·precioIn + out/1e6·precioOut, redondeado a 6 decimales. Puro/testeable. */
    public static BigDecimal calcularCosto(long tokensEntrada, long tokensSalida, BigDecimal precioIn1m, BigDecimal precioOut1m) {
        BigDecimal costoIn = BigDecimal.valueOf(tokensEntrada).multiply(precioIn1m).divide(MILLON, 6, RoundingMode.HALF_UP);
        BigDecimal costoOut = BigDecimal.valueOf(tokensSalida).multiply(precioOut1m).divide(MILLON, 6, RoundingMode.HALF_UP);
        return costoIn.add(costoOut);
    }

    @Transactional
    public void registrar(long tokensEntrada, long tokensSalida) {
        BigDecimal costo = calcularCosto(tokensEntrada, tokensSalida, properties.precioInput1m(), properties.precioOutput1m());
        repository.registrar(tokensEntrada, tokensSalida, costo);
    }

    @Transactional(readOnly = true)
    public SeoUsoDTO obtener() {
        SeoUso u = repository.findById(1L).orElseThrow(
                () -> new IllegalStateException("Fila de uso de SEO (id=1) no encontrada"));
        return new SeoUsoDTO(
                u.getConsultas(), u.getTokensEntrada(), u.getTokensSalida(), u.getCostoUsd(),
                properties.model(), properties.precioInput1m(), properties.precioOutput1m());
    }
}
```

- [ ] **Step 5: Correr el test (debe pasar)**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -Dtest=SeoUsoServiceTest test`
Expected: PASS (Tests run: 4, Failures: 0).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/SeoUsoDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/SeoUsoService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/SeoUsoServiceTest.java
git commit -m "feat(seo): SeoUsoService con calculo de costo USD y registro acumulado"
```

---

### Task 5: SeoConfigService (prompts) + DTOs

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/SeoPromptDTO.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/SeoPromptUpdateDTO.java`
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/SeoConfigService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/SeoConfigServiceTest.java`

**Interfaces:**
- Consumes: `SeoPromptRepository` (Task 2), enum `SeoCanal`.
- Produces:
  - `SeoPromptDTO(SeoCanal canal, String contenido, LocalDateTime fechaModificacion)`.
  - `SeoPromptUpdateDTO(String contenido)` (con `@NotBlank`).
  - `SeoConfigService.promptDe(SeoCanal):String` (lanza `IllegalStateException` si falta).
  - `SeoConfigService.obtenerTodos():List<SeoPromptDTO>`.
  - `SeoConfigService.actualizar(SeoCanal, String):SeoPromptDTO`.

- [ ] **Step 1: Crear los DTOs**

`SeoPromptDTO.java`:
```java
package ar.com.leo.super_master_backend.apis.openai.dto;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import java.time.LocalDateTime;

public record SeoPromptDTO(SeoCanal canal, String contenido, LocalDateTime fechaModificacion) {}
```

`SeoPromptUpdateDTO.java`:
```java
package ar.com.leo.super_master_backend.apis.openai.dto;

import jakarta.validation.constraints.NotBlank;

public record SeoPromptUpdateDTO(
        @NotBlank(message = "El contenido del prompt es obligatorio")
        String contenido
) {}
```

- [ ] **Step 2: Escribir el test (mock del repo)**

Crear `SeoConfigServiceTest.java`:
```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoPrompt;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoPromptRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeoConfigServiceTest {

    private final SeoPromptRepository repo = mock(SeoPromptRepository.class);
    private final SeoConfigService service = new SeoConfigService(repo);

    @Test
    void promptDe_devuelveContenidoDeBD() {
        SeoPrompt p = new SeoPrompt();
        p.setCanal(SeoCanal.HOGAR);
        p.setContenido("PROMPT HOGAR");
        when(repo.findByCanal(SeoCanal.HOGAR)).thenReturn(Optional.of(p));

        assertThat(service.promptDe(SeoCanal.HOGAR)).isEqualTo("PROMPT HOGAR");
    }

    @Test
    void promptDe_sinFila_lanzaExcepcion() {
        when(repo.findByCanal(SeoCanal.GASTRO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.promptDe(SeoCanal.GASTRO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GASTRO");
    }

    @Test
    void actualizar_persisteContenidoYFecha() {
        SeoPrompt p = new SeoPrompt();
        p.setCanal(SeoCanal.HOGAR);
        p.setContenido("viejo");
        when(repo.findByCanal(SeoCanal.HOGAR)).thenReturn(Optional.of(p));
        when(repo.save(any(SeoPrompt.class))).thenAnswer(i -> i.getArgument(0));

        service.actualizar(SeoCanal.HOGAR, "nuevo contenido");

        assertThat(p.getContenido()).isEqualTo("nuevo contenido");
        assertThat(p.getFechaModificacion()).isNotNull();
        verify(repo).save(p);
    }
}
```

- [ ] **Step 3: Correr el test (debe fallar a compilar)**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -Dtest=SeoConfigServiceTest test`
Expected: FAIL de compilación (`SeoConfigService` no existe).

- [ ] **Step 4: Implementar `SeoConfigService`**

```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoPromptDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoPrompt;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoPromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Lee y actualiza los prompts de SEO (uno por canal). Los prompts viven solo en BD. */
@Service
@RequiredArgsConstructor
public class SeoConfigService {

    private final SeoPromptRepository repository;

    /** Contenido del prompt del canal. Sin fallback al código: si falta la fila, error explícito. */
    @Transactional(readOnly = true)
    public String promptDe(SeoCanal canal) {
        return repository.findByCanal(canal)
                .map(SeoPrompt::getContenido)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay prompt de SEO configurado para el canal " + canal + " (revisar el seed de seo_prompt)"));
    }

    @Transactional(readOnly = true)
    public List<SeoPromptDTO> obtenerTodos() {
        return repository.findAll().stream()
                .map(p -> new SeoPromptDTO(p.getCanal(), p.getContenido(), p.getFechaModificacion()))
                .toList();
    }

    @Transactional
    public SeoPromptDTO actualizar(SeoCanal canal, String contenido) {
        SeoPrompt p = repository.findByCanal(canal)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay prompt de SEO para el canal " + canal + " (revisar el seed de seo_prompt)"));
        p.setContenido(contenido);
        p.setFechaModificacion(LocalDateTime.now());
        repository.save(p);
        return new SeoPromptDTO(p.getCanal(), p.getContenido(), p.getFechaModificacion());
    }
}
```

- [ ] **Step 5: Correr el test (debe pasar)**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -Dtest=SeoConfigServiceTest test`
Expected: PASS (Tests run: 3, Failures: 0).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/SeoPromptDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/dto/SeoPromptUpdateDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/SeoConfigService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/openai/service/SeoConfigServiceTest.java
git commit -m "feat(seo): SeoConfigService (prompts por canal, solo BD, sin fallback)"
```

---

### Task 6: Integrar en OpenAiSeoService + limpiar OpenAiSeoPrompts

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/OpenAiSeoService.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/OpenAiSeoPrompts.java`
- (Posible) Delete: tests existentes que cubran `OpenAiSeoPrompts.systemPrompt`/`SYSTEM_BASE`.

**Interfaces:**
- Consumes: `SeoConfigService.promptDe(canal)` (Task 5), `SeoUsoService.registrar(in, out)` (Task 4).
- Produces: `OpenAiSeoService.generar` ahora toma el prompt de BD y registra el uso. `OpenAiSeoPrompts` queda solo con `userMessage` + helpers.

- [ ] **Step 1: Quitar los prompts hardcodeados de `OpenAiSeoPrompts`**

Eliminar de `OpenAiSeoPrompts.java`: las constantes `SYSTEM_BASE`, `REGLA_GASTRO` y el método `systemPrompt(SeoCanal)`. Quitar el import de `SeoCanal` si queda sin uso. El archivo conserva `userMessage(SeoContexto)`, `notBlank` y `sinParentesis`. Resultado:
```java
package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.SeoContexto;

/** Construye el mensaje de usuario (datos del producto) para la generación de SEO. Lógica pura, sin red. */
public final class OpenAiSeoPrompts {

    private OpenAiSeoPrompts() {}

    /** Arma el mensaje de usuario con la info del producto en texto plano. */
    public static String userMessage(SeoContexto c) {
        StringBuilder sb = new StringBuilder();
        String titulo = sinParentesis(c.tituloNube());
        if (notBlank(titulo)) sb.append("Título: ").append(titulo).append("\n");
        if (notBlank(c.marca())) sb.append("Marca: ").append(c.marca().trim()).append("\n");
        if (notBlank(c.material())) sb.append("Material: ").append(c.material().trim()).append("\n");
        if (c.dimensiones() != null) {
            for (String d : c.dimensiones()) {
                if (notBlank(d)) sb.append("- ").append(d.trim()).append("\n");
            }
        }
        if (c.aptos() != null) {
            var aptos = c.aptos().stream().filter(OpenAiSeoPrompts::notBlank).map(String::trim).toList();
            if (!aptos.isEmpty()) sb.append("Apto para: ").append(String.join(", ", aptos)).append("\n");
        }
        return sb.toString().trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Quita el contenido entre paréntesis (códigos internos como "(712B)") y colapsa espacios. */
    private static String sinParentesis(String s) {
        if (s == null) return "";
        return s.replaceAll("\\([^)]*\\)", "").replaceAll("\\s+", " ").trim();
    }
}
```

- [ ] **Step 2: Buscar y limpiar tests del prompt eliminado**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -q test-compile 2>&1 | grep -iE "systemPrompt|SYSTEM_BASE|REGLA_GASTRO" | head`
Si aparecen referencias en archivos de test (p. ej. un `OpenAiSeoPromptsTest`), eliminar SOLO los métodos de test que invocan `systemPrompt`/`SYSTEM_BASE`/`REGLA_GASTRO` (los de `userMessage` se conservan). Si no aparece nada, continuar.

- [ ] **Step 3: Inyectar servicios y usar el prompt de BD en `OpenAiSeoService`**

En `OpenAiSeoService.java`:

(a) Agregar dependencias al constructor. Cambiar los campos/constructor:
```java
    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final SeoConfigService seoConfigService;
    private final SeoUsoService seoUsoService;

    @Value("${app.secrets-dir}")
    private String secretsDir;

    private OpenAiCredentials credentials;

    public OpenAiSeoService(RestClient openaiRestClient, OpenAiProperties properties, ObjectMapper objectMapper,
                            SeoConfigService seoConfigService, SeoUsoService seoUsoService) {
        this.restClient = openaiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.seoConfigService = seoConfigService;
        this.seoUsoService = seoUsoService;
    }
```

(b) En `construirBody`, cambiar el system content para que venga de la BD:
```java
        system.put("content", seoConfigService.promptDe(canal));
```
(eliminar el uso de `OpenAiSeoPrompts.systemPrompt(canal)`; `OpenAiSeoPrompts.userMessage(contexto)` se mantiene).

- [ ] **Step 4: Registrar el uso (best-effort) en `generar`**

En `generar`, después de obtener `root` y antes de devolver el DTO, leer el `usage` (está en la raíz del response, no en el `content`) y registrar. Reemplazar el bloque `try { ... return OpenAiSeoParser.parseContenido(...); }` por:
```java
        try {
            JsonNode root = objectMapper.readTree(respuesta);
            String contenido = root.path("choices").path(0).path("message").path("content").asString();
            SeoGeneradoDTO dto = OpenAiSeoParser.parseContenido(contenido, objectMapper);
            registrarUso(root);
            return dto;
        } catch (Exception e) {
            log.error("OpenAI - Error parseando la respuesta: {}", e.getMessage());
            throw new IllegalStateException("Respuesta de OpenAI no procesable: " + e.getMessage(), e);
        }
```
Y agregar el método privado (best-effort: si falla, loguea y no rompe la generación; sin `usage` cuenta la consulta con tokens 0):
```java
    /** Lee usage.{prompt_tokens, completion_tokens} y registra el consumo. Nunca rompe la generación. */
    private void registrarUso(JsonNode root) {
        try {
            JsonNode usage = root.path("usage");
            long in = usage.path("prompt_tokens").asLong(0);
            long out = usage.path("completion_tokens").asLong(0);
            seoUsoService.registrar(in, out);
        } catch (Exception e) {
            log.warn("OpenAI - no se pudo registrar el uso: {}", e.getMessage());
        }
    }
```

- [ ] **Step 5: Compilar y correr los tests de openai**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -Dtest="OpenAiSeo*,SeoConfigServiceTest,SeoUsoServiceTest" test`
Expected: BUILD SUCCESS, todos verdes (los de `userMessage` y `OpenAiSeoParser` siguen pasando).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/OpenAiSeoService.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/service/OpenAiSeoPrompts.java supermaster-backend/src/test/
git commit -m "feat(seo): OpenAiSeoService toma el prompt de BD y registra el uso (best-effort)"
```

---

### Task 7: SeoController (endpoints REST)

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/controller/SeoController.java`

**Interfaces:**
- Consumes: `SeoConfigService` (Task 5), `SeoUsoService` (Task 4), `SeoCanal`, `Permisos`.
- Produces (endpoints que consume el frontend, Task 8):
  - `GET /api/seo/prompts` → `List<SeoPromptDTO>`.
  - `PUT /api/seo/prompts/{canal}` (body `SeoPromptUpdateDTO`) → `SeoPromptDTO`.
  - `GET /api/seo/uso` → `SeoUsoDTO`.

- [ ] **Step 1: Crear el controller**

```java
package ar.com.leo.super_master_backend.apis.openai.controller;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoPromptDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoPromptUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.service.SeoConfigService;
import ar.com.leo.super_master_backend.apis.openai.service.SeoUsoService;
import ar.com.leo.super_master_backend.config.Permisos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seo")
public class SeoController {

    private final SeoConfigService seoConfigService;
    private final SeoUsoService seoUsoService;

    @GetMapping("/prompts")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<List<SeoPromptDTO>> prompts() {
        return ResponseEntity.ok(seoConfigService.obtenerTodos());
    }

    @PutMapping("/prompts/{canal}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<SeoPromptDTO> actualizarPrompt(
            @PathVariable SeoCanal canal,
            @Valid @RequestBody SeoPromptUpdateDTO body) {
        return ResponseEntity.ok(seoConfigService.actualizar(canal, body.contenido()));
    }

    @GetMapping("/uso")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<SeoUsoDTO> uso() {
        return ResponseEntity.ok(seoUsoService.obtener());
    }
}
```

- [ ] **Step 2: Compilar**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/openai/controller/SeoController.java
git commit -m "feat(seo): endpoints /api/seo (prompts GET/PUT, uso GET)"
```

---

### Task 8: Frontend — types, service y hook

**Files:**
- Create: `supermaster-frontend/src/app/seo-ia/types.ts`
- Create: `supermaster-frontend/src/app/seo-ia/seoService.ts`
- Create: `supermaster-frontend/src/app/seo-ia/useSeoIa.ts`

**Interfaces:**
- Consumes: endpoints de la Task 7; `fetchAPI` (`../utils/fetchAPI`), `API_BASE_URL` (`../config/runtime`), `notificar` (`../utils/notificar`), `getErrorMessage` (`@/lib/errors`).
- Produces: `useSeoIa()` → `{ prompts, uso, isLoading, isSaving, savePrompt }` para la página (Task 9).

- [ ] **Step 1: Crear `types.ts`**

```typescript
export type SeoCanal = "HOGAR" | "GASTRO";

export type SeoPrompt = {
    canal: SeoCanal;
    contenido: string;
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
```

- [ ] **Step 2: Crear `seoService.ts`**

```typescript
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import type { SeoCanal, SeoPrompt, SeoUso } from "./types";

const API_URL = `${API_BASE_URL}/api/seo`;

export const getSeoPromptsAPI = async (): Promise<SeoPrompt[]> => {
    const res = await fetchAPI(`${API_URL}/prompts`);
    if (!res.ok) throw new Error("Error al obtener los prompts de SEO");
    return await res.json();
};

export const updateSeoPromptAPI = async (canal: SeoCanal, contenido: string): Promise<SeoPrompt> => {
    const res = await fetchAPI(`${API_URL}/prompts/${canal}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ contenido }),
    });
    if (!res.ok) throw new Error("Error al guardar el prompt de SEO");
    return await res.json();
};

export const getSeoUsoAPI = async (): Promise<SeoUso> => {
    const res = await fetchAPI(`${API_URL}/uso`);
    if (!res.ok) throw new Error("Error al obtener el uso de SEO");
    return await res.json();
};
```

- [ ] **Step 3: Crear `useSeoIa.ts`**

```typescript
"use client";
import { getErrorMessage } from "@/lib/errors";
import { useCallback, useEffect, useState } from "react";
import { notificar } from "../utils/notificar";
import { getSeoPromptsAPI, getSeoUsoAPI, updateSeoPromptAPI } from "./seoService";
import type { SeoCanal, SeoPrompt, SeoUso } from "./types";

export function useSeoIa() {
    const [prompts, setPrompts] = useState<SeoPrompt[]>([]);
    const [uso, setUso] = useState<SeoUso | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState<SeoCanal | null>(null);

    const fetchData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [p, u] = await Promise.all([getSeoPromptsAPI(), getSeoUsoAPI()]);
            setPrompts(p);
            setUso(u);
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "No se pudo cargar la configuración de SEO"));
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);

    const savePrompt = async (canal: SeoCanal, contenido: string) => {
        setIsSaving(canal);
        try {
            const actualizado = await updateSeoPromptAPI(canal, contenido);
            setPrompts(prev => prev.map(p => p.canal === canal ? actualizado : p));
            notificar.success(`Prompt de ${canal === "HOGAR" ? "Hogar" : "Gastro"} guardado`);
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al guardar el prompt"));
        } finally {
            setIsSaving(null);
        }
    };

    return { prompts, uso, isLoading, isSaving, savePrompt };
}
```

- [ ] **Step 4: Type-check**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/seo-ia/types.ts supermaster-frontend/src/app/seo-ia/seoService.ts supermaster-frontend/src/app/seo-ia/useSeoIa.ts
git commit -m "feat(front/seo): types, service y hook de SEO IA (config y uso)"
```

---

### Task 9: Frontend — página y entrada de menú

**Files:**
- Create: `supermaster-frontend/src/app/seo-ia/page.tsx`
- Modify: `supermaster-frontend/src/app/components/navigation/navigationConfig.tsx`

**Interfaces:**
- Consumes: `useSeoIa()` (Task 8). Reusa `Button` (`../components/Button/Button`) según el patrón de `operaciones-ml/page.tsx`.

- [ ] **Step 1: Crear la página**

```tsx
"use client";

import { useEffect, useState } from "react";
import Button from "../components/Button/Button";
import { useSeoIa } from "./useSeoIa";
import type { SeoCanal } from "./types";

const CANALES: { canal: SeoCanal; titulo: string }[] = [
    { canal: "HOGAR", titulo: "KT Hogar" },
    { canal: "GASTRO", titulo: "KT Gastro" },
];

export default function SeoIaPage() {
    const { prompts, uso, isLoading, isSaving, savePrompt } = useSeoIa();
    const [borradores, setBorradores] = useState<Record<string, string>>({});

    useEffect(() => {
        const inicial: Record<string, string> = {};
        prompts.forEach(p => { inicial[p.canal] = p.contenido; });
        setBorradores(inicial);
    }, [prompts]);

    const fmt = (n: number) => new Intl.NumberFormat("es-AR").format(n);

    return (
        <div className="mx-auto max-w-5xl px-4 py-6">
            <h1 className="mb-6 text-2xl font-bold text-slate-800 dark:text-slate-100">SEO IA — configuración y uso</h1>

            {/* Panel de uso */}
            <div className="mb-8 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60">
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Uso de IA (acumulado)</h2>
                {uso ? (
                    <div className="flex flex-wrap gap-x-8 gap-y-2 text-sm text-slate-700 dark:text-slate-200">
                        <span><b>Consultas:</b> {fmt(uso.consultas)}</span>
                        <span><b>Tokens entrada:</b> {fmt(uso.tokensEntrada)}</span>
                        <span><b>Tokens salida:</b> {fmt(uso.tokensSalida)}</span>
                        <span><b>Costo:</b> US$ {uso.costoUsd.toFixed(4)}</span>
                        <span className="text-slate-500 dark:text-slate-400">
                            Modelo: {uso.modelo} · in US${uso.precioInput1m}/1M · out US${uso.precioOutput1m}/1M
                        </span>
                    </div>
                ) : (
                    <p className="text-sm text-slate-400">{isLoading ? "Cargando…" : "Sin datos de uso"}</p>
                )}
            </div>

            {/* Editores de prompt por canal */}
            <div className="space-y-6">
                {CANALES.map(({ canal, titulo }) => {
                    const p = prompts.find(x => x.canal === canal);
                    const valor = borradores[canal] ?? "";
                    return (
                        <div key={canal} className="rounded-xl border border-slate-200 p-4 dark:border-slate-700">
                            <div className="mb-2 flex items-center justify-between">
                                <h3 className="font-semibold text-slate-700 dark:text-slate-200">Prompt — {titulo}</h3>
                                {p?.fechaModificacion && (
                                    <span className="text-xs text-slate-400">
                                        Modificado: {new Date(p.fechaModificacion).toLocaleString("es-AR")}
                                    </span>
                                )}
                            </div>
                            <textarea
                                className="h-64 w-full rounded-lg border border-slate-300 bg-white p-3 font-mono text-xs text-slate-800 focus:border-primary focus:ring-primary dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
                                value={valor}
                                onChange={e => setBorradores(prev => ({ ...prev, [canal]: e.target.value }))}
                                disabled={isLoading}
                            />
                            <div className="mt-2 flex justify-end">
                                <Button
                                    variant="dark"
                                    onClick={() => savePrompt(canal, valor)}
                                    disabled={isSaving !== null || isLoading || !valor.trim()}
                                >
                                    {isSaving === canal ? "Guardando…" : "Guardar"}
                                </Button>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
```

- [ ] **Step 2: Agregar la entrada de menú**

En `navigationConfig.tsx`, dentro de la sección `"Integraciones"` (el array `items`), agregar una entrada nueva siguiendo el patrón exacto de las existentes (mismo color, `requiredRoles: ["ADMIN"]`). Usar un ícono ya importado en ese archivo (p. ej. `SparklesIcon` si está importado; si no, importar uno existente del set de heroicons ya usado en el archivo):
```tsx
            {
                href: "/seo-ia",
                label: "SEO IA",
                description: "Prompts de SEO de Tienda Nube y consumo de OpenAI",
                icon: SparklesIcon,
                color: "orange",
                requiredPermission: "INTEGRACIONES_VER",
                requiredRoles: ["ADMIN"],
            },
```
Si `SparklesIcon` no estaba importado, agregar el import junto a los demás de `@heroicons/react/24/outline` en ese archivo.

- [ ] **Step 3: Type-check**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/seo-ia/page.tsx supermaster-frontend/src/app/components/navigation/navigationConfig.tsx
git commit -m "feat(front/seo): pagina SEO IA (editores de prompt + panel de uso) y menu"
```

---

## Notas de integración / verificación final

- Tras la Task 6, el backend lee el prompt de la BD. Como `ddl-auto=validate`, el backend solo arranca si la Task 1 se aplicó a la BD local: confirmar antes de cualquier prueba manual.
- El registro de uso es best-effort: una falla de BD nunca debe impedir generar SEO (verificado por el try/catch en `registrarUso`).
- Smoke manual (NO automatizado, requiere arrancar backend + front): entrar a "SEO IA", editar un prompt y guardar; generar un SEO desde un producto y ver que el contador de consultas/tokens sube.
```
