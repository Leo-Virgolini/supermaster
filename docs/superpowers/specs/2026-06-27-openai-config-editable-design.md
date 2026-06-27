# Parámetros de OpenAI editables (SEO + Imagen) — Diseño

**Fecha:** 2026-06-27
**Estado:** aprobado para plan

## Objetivo

Permitir editar desde la pantalla "SEO IA" los **parámetros** que se le pasan a OpenAI, tanto para la generación de **SEO** (Tienda Nube) como para la **carátula** de imagen, persistidos en la base de datos. Hoy esos parámetros viven en `application.properties` (vía `OpenAiProperties` / `OpenAiImageProperties`) y no se pueden cambiar sin tocar el archivo y reiniciar.

Además, se **unifica** el prompt con sus parámetros en una sola tabla de configuración por servicio (decisión del usuario), quedando una estructura simétrica: **una tabla `*_config` (settings editables) + una tabla `*_uso` (consumo acumulado) por servicio**.

## Alcance

Parámetros editables:

- **SEO:** `model`, `precio_input_1m`, `precio_output_1m`. (La llamada SEO hoy solo manda `model`; **no** se agregan `temperature`/`max_tokens`.)
- **Imagen:** `model`, `size`, `output_format`, `quality`, `precio_input_1m`, `precio_output_1m`.

Los prompts (SEO por canal HOGAR/GASTRO; carátula único) pasan a vivir en la misma fila de config de cada servicio.

**Fuera de alcance:** `base-url` y los timeouts siguen en `properties` (infra, no editables por UI). No se agregan parámetros nuevos a la llamada SEO.

## Global Constraints

- Backend: Spring Boot 4, Java 25, Maven, JPA con `ddl-auto=validate` (los cambios de schema requieren script SQL manual en `src/main/resources/db/`).
- Jackson 3 (`tools.jackson`). MapStruct con `nullValuePropertyMappingStrategy=IGNORE`.
- Tests backend: `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`). Frontend: `npx tsc --noEmit` exit 0, sin errores `error` de lint nuevos.
- Las API keys de OpenAI NO van en BD ni properties: se cargan de `openai_tokens.json` (`seo_api_key` / `image_api_key`, sin fallback).
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos, nunca `-A` ni `.superpowers/`.
- Los archivos SQL con acentos se cargan forzando UTF-8 (`mysql --default-character-set=utf8mb4 ...`) para evitar mojibake.

## Modelo de datos

Dos tablas singleton (id=1), una por servicio. Reemplazan a `seo_prompt` e `imagen_prompt` (que se eliminan).

### `seo_config`
| columna | tipo | notas |
|---|---|---|
| `id` | BIGINT PK | siempre 1 |
| `prompt_hogar` | TEXT NOT NULL | prompt SEO tienda KT HOGAR |
| `prompt_gastro` | TEXT NOT NULL | prompt SEO tienda KT GASTRO |
| `model` | VARCHAR(80) NOT NULL | p.ej. `gpt-5-mini` |
| `precio_input_1m` | DECIMAL(12,4) NOT NULL | USD por millón de tokens de entrada |
| `precio_output_1m` | DECIMAL(12,4) NOT NULL | USD por millón de tokens de salida |
| `fecha_modificacion` | DATETIME NULL | |

### `imagen_config`
| columna | tipo | notas |
|---|---|---|
| `id` | BIGINT PK | siempre 1 |
| `contenido` | TEXT NOT NULL | prompt de la carátula |
| `model` | VARCHAR(80) NOT NULL | `gpt-image-2` |
| `size` | VARCHAR(20) NOT NULL | `1024x1024` \| `1024x1536` \| `1536x1024` \| `auto` |
| `output_format` | VARCHAR(10) NOT NULL | `png` \| `jpeg` \| `webp` |
| `quality` | VARCHAR(10) NOT NULL | `low` \| `medium` \| `high` \| `auto` |
| `precio_input_1m` | DECIMAL(12,4) NOT NULL | |
| `precio_output_1m` | DECIMAL(12,4) NOT NULL | |
| `fecha_modificacion` | DATETIME NULL | |

`seo_uso` e `imagen_uso` quedan **sin cambios** (datos de runtime, no settings).

### Migración

`db/2026-06-27-openai-config.sql` (header con el recordatorio de cargar con `utf8mb4`):

1. `CREATE TABLE seo_config (...) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;`
2. `CREATE TABLE imagen_config (...) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;`
3. `INSERT` fila id=1 en cada una con los valores actuales:
   - `seo_config`: prompts HOGAR/GASTRO con el **texto correcto en UTF-8** (los actuales del seed `2026-06-25`), `model='gpt-5-mini'`, precios `0.25` / `2.00`.
   - `imagen_config`: `contenido` = prompt actual de la carátula, `model='gpt-image-2'`, `size='1024x1024'`, `output_format='jpeg'`, `quality='high'`, precios `8.00` / `30.00`.
4. `DROP TABLE seo_prompt;` y `DROP TABLE imagen_prompt;`

> Nota operativa: como esta migración resiembra los prompts SEO en UTF-8 correcto, **arregla de paso el mojibake** que tenía `seo_prompt` (siempre que se cargue con `--default-character-set=utf8mb4`). `imagen_prompt` todavía no estaba en producción, así que su DROP no afecta datos reales.

## Backend

### Entidades / repos
- Nuevas: `SeoConfig`, `ImagenConfig` (singleton id=1) + `SeoConfigRepository`, `ImagenConfigRepository`.
- Se eliminan: `SeoPrompt`, `ImagenPrompt` + sus repos (`SeoPromptRepository`, `ImagenPromptRepository`). Quitar las entidades es necesario porque `ddl-auto=validate` fallaría si mapean a tablas dropeadas.

### Config services (reuso de los existentes)
- **`SeoConfigService`** (hoy maneja prompts por canal):
  - `String promptDe(SeoCanal canal)` → lee `prompt_hogar`/`prompt_gastro` de la fila id=1 según el canal.
  - `String modelo()`, `BigDecimal precioInput1m()`, `BigDecimal precioOutput1m()` → para generación y costo.
  - `SeoConfigDTO obtener()` y `SeoConfigDTO actualizar(SeoConfigUpdateDTO)` → para la UI.
- **`ImagenIaConfigService`** (hoy maneja el prompt único):
  - `String prompt()` → `contenido`.
  - `String modelo()`, `String size()`, `String outputFormat()`, `String quality()`, `BigDecimal precioInput1m()`, `BigDecimal precioOutput1m()`.
  - `ImagenConfigDTO obtener()` y `ImagenConfigDTO actualizar(ImagenConfigUpdateDTO)`.
- Si falta la fila (no sembrada) → `NotFoundException` con mensaje claro (igual que hoy con el prompt).

### Generación
- `OpenAiSeoService.construirBody`: `body.put("model", seoConfigService.modelo())` (en vez de `properties.model()`).
- `OpenAiImagenService.generarCaratula`: `model`/`size`/`output_format`/`quality` desde `ImagenIaConfigService` (en vez de `properties`).

### Costo / uso
- `SeoUsoService` / `ImagenUsoService`: `registrar(...)` calcula el costo con los precios del **config** (no de `properties`); `obtener()` arma el `*UsoDTO` con `model` y precios del **config**. Pasan a inyectar el config service correspondiente.

### Formato ↔ extensión (carátula)
- `CaratulaGeneradaDTO(String imagenBase64, String formato)` — `formato` = `output_format` del config (`jpeg`/`png`/`webp`).
- `ImagenController` POST `/caratula/generar/{sku}` devuelve el DTO con el formato.
- Guardado: la extensión del archivo se deriva del formato (`jpeg→jpg`, `png→png`, `webp→webp`). `CaratulaService.guardar(sku, datos)` calcula la `ext` desde `output_format` del config y llama a `ImagenService.guardarCaratula(String sku, byte[] datos, String ext)` (la firma actual pasa de 2 a 3 args). `EXTENSIONES` ya incluye `jpg/jpeg/png/webp`.
- La cruda se sigue resolviendo por cualquier extensión soportada (sin cambios).

### Endpoints
Se reemplazan los de prompt por los de config. Permisos: `INTEGRACIONES_VER` (GET) / `INTEGRACIONES_EDITAR` (PUT).

| Método | Ruta | Reemplaza a |
|---|---|---|
| GET | `/api/seo/config` | `GET /api/seo/prompts` |
| PUT | `/api/seo/config` | `PUT /api/seo/prompts/{canal}` |
| GET | `/api/imagen-ia/config` | `GET /api/imagen-ia/prompt` |
| PUT | `/api/imagen-ia/config` | `PUT /api/imagen-ia/prompt` |
| GET | `/api/seo/uso` | (sin cambios) |
| GET | `/api/imagen-ia/uso` | (sin cambios) |

### DTOs y validación
- `SeoConfigDTO(String promptHogar, String promptGastro, String model, BigDecimal precioInput1m, BigDecimal precioOutput1m, LocalDateTime fechaModificacion)`.
- `SeoConfigUpdateDTO`: `@NotBlank promptHogar`, `@NotBlank promptGastro`, `@NotBlank model`, precios `@NotNull @DecimalMin("0.0")`.
- `ImagenConfigDTO(String contenido, String model, String size, String outputFormat, String quality, BigDecimal precioInput1m, BigDecimal precioOutput1m, LocalDateTime fechaModificacion)`.
- `ImagenConfigUpdateDTO`: `@NotBlank contenido`, `@NotBlank model`, `size` `@Pattern(regexp="1024x1024|1024x1536|1536x1024|auto")`, `output_format` `@Pattern(regexp="png|jpeg|webp")`, `quality` `@Pattern(regexp="low|medium|high|auto")`, precios `@NotNull @DecimalMin("0.0")`.
- Se eliminan `SeoPromptDTO`/`SeoPromptUpdateDTO`/`ImagenPromptDTO`/`ImagenPromptUpdateDTO` (ya no hay endpoints de prompt sueltos).

### Properties
- `OpenAiProperties`: quitar `model`, `precioInput1m`, `precioOutput1m`. Quedan `baseUrl`, `connectTimeout`, `readTimeout`.
- `OpenAiImageProperties`: quitar `model`, `size`, `outputFormat`, `quality`, `precioInput1m`, `precioOutput1m`. Quedan `baseUrl`, `connectTimeout`, `readTimeout`.
- `application.properties`: quitar las líneas correspondientes (`openai.model`, `openai.precio-*`, `openai.image.model`, `openai.image.precio-*`, etc.). La BD es la fuente de verdad.

## Frontend (pantalla SEO IA)

- `seoService.ts` / `types.ts`: tipos `SeoConfig`, `ImagenConfig`; `getSeoConfigAPI`/`updateSeoConfigAPI`, `getImagenConfigAPI`/`updateImagenConfigAPI`. Las APIs de uso quedan igual. Se quitan `getSeoPromptsAPI`/`updateSeoPromptAPI`/`getImagenPromptAPI`/`updateImagenPromptAPI`.
- `useSeoIa.ts`: cargar `seoConfig` + `imagenConfig` + ambos usos en `fetchData`; handlers `saveSeoConfig` / `saveImagenConfig`.
- `page.tsx`: consolidar en **un formulario por servicio**:
  - **SEO:** textareas Hogar y Gastro + `model` (texto) + `precio_input_1m` + `precio_output_1m` (number) → **un** botón Guardar (PUT del config completo).
  - **Imagen:** textarea del prompt + `model` (texto) + `size`/`quality`/`output_format` (selectores con valores válidos) + 2 precios → **un** Guardar.
  - Los paneles de **uso** quedan abajo (ahora muestran model/precios del config editable).
- `ProductoFormModal.tsx`: `generarCaratula` usa el `formato` devuelto para el `data:image/{formato};base64,...` del preview. El cache-buster y el guardado siguen igual (la extensión la maneja el backend).

## Data flow (carátula, end-to-end)

1. UI genera → `POST /api/imagenes/caratula/generar/{sku}`.
2. `CaratulaService.generar` → cruda → `OpenAiImagenService.generarCaratula` (model/size/format/quality desde `imagen_config`, prompt desde `imagen_config`) → registra uso con precios de `imagen_config`.
3. Controller devuelve `{ imagenBase64, formato }`. Preview: `data:image/{formato}`.
4. Confirmar → `POST /caratula/guardar/{sku}` → backend deriva extensión del `output_format` del config y escribe `{SKU}.{ext}`, invalida índice.

## Manejo de errores

- Config sin sembrar → 404 con mensaje claro.
- PUT con valores inválidos (enum fuera de set, precio negativo, campos vacíos) → 400 (Bean Validation), mensaje en `ErrorResponse.message`.
- Generación/parseo de OpenAI: igual que hoy (503 si falta credencial, 500 si la API falla).

## Testing

- Unit de `SeoConfigService` / `ImagenIaConfigService`: `obtener`, `actualizar` (persiste y devuelve), getters usados por generación/costo, 404 si falta la fila.
- Validación de los `*UpdateDTO` (enums con `@Pattern`, precios ≥ 0).
- Tests de controller de los nuevos endpoints (`200` y `400`).
- Verificar que el costo se calcula con los precios del config (no de properties).
- Ajustar los tests existentes que construían `OpenAiProperties`/servicios con los campos removidos, y los que ejercían los endpoints/entidades de prompt.
- Suite completa `mvn -o test` verde; frontend `tsc --noEmit` exit 0.

## Pendiente operativo (usuario)

- Correr `db/2026-06-27-openai-config.sql` con `--default-character-set=utf8mb4`. (Sustituye a los seeds de prompt anteriores; resiembra el SEO en UTF-8 correcto.)
