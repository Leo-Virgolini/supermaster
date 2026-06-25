# SEO IA (config y uso) — Diseño

**Fecha:** 2026-06-25
**Estado:** Aprobado, listo para plan

## Objetivo

Una pantalla nueva "SEO IA" que permita (1) **editar los prompts** de generación de SEO de Tienda Nube sin tocar el código ni reiniciar, y (2) **ver el consumo acumulado** de la API de OpenAI (consultas, tokens, costo en USD), junto con el modelo y los precios vigentes.

## Contexto actual

- `OpenAiSeoService.generar(canal, contexto)` llama a `POST /chat/completions`, parsea `choices[0].message.content` y devuelve `SeoGeneradoDTO`. **Hoy ignora el objeto `usage`** del response (que trae `prompt_tokens`, `completion_tokens`, `total_tokens`).
- Los prompts de sistema están **hardcodeados** en `OpenAiSeoPrompts`: un `SYSTEM_BASE` y una `REGLA_GASTRO` que se concatena solo para `GASTRO`. El `userMessage(contexto)` arma el mensaje de usuario con los datos del producto y **no es editable** (es estructura de datos, no copy).
- `SeoCanal` es un enum con `GASTRO` y `HOGAR`.
- `OpenAiProperties` (record, prefix `openai`) ya expone `baseUrl`, `model`, `connectTimeout`, `readTimeout` con defaults.
- Patrón a seguir: `ConfiguracionMlService` ya persiste configuración editable de una integración.
- Permisos: existe `INTEGRACIONES_VER` / `INTEGRACIONES_EDITAR` (OpenAI es una integración externa, como ML y Nube).

## Decisiones tomadas

1. **Registro de uso:** acumulado, **total único** (una sola fila singleton de contadores). No hay histórico por consulta.
2. **Prompts editables:** **un prompt completo por canal** (HOGAR y GASTRO independientes, cada uno de punta a punta). Reemplaza el modelo "base + regla gastro".
3. **Restaurar default:** no hay botón de reset. El prompt por defecto (el del código) solo **siembra** el valor inicial; después la edición es libre.
4. **Modelo, URL y precios:** viven en `properties` (no se editan desde la UI). La pantalla los muestra **solo lectura**.
5. **Permisos:** se reusan `INTEGRACIONES_VER` (lectura) e `INTEGRACIONES_EDITAR` (guardar prompts). No se crea un permiso nuevo.

## Arquitectura

Dos tablas dedicadas + lectura/registro dentro de `OpenAiSeoService`. Descartado: tabla key-value genérica (menos tipada, sin ventaja) y prompts en archivo/properties (no editable desde la UI sin reiniciar).

### 1. Esquema de BD

Script SQL manual en `supermaster-backend/src/main/resources/db/` (porque `ddl-auto=validate` no crea tablas).

```sql
CREATE TABLE supermaster.seo_prompt (
  id BIGINT NOT NULL AUTO_INCREMENT,
  canal VARCHAR(10) NOT NULL,                 -- 'HOGAR' | 'GASTRO'
  contenido TEXT NOT NULL,
  fecha_modificacion DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_seo_prompt_canal (canal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supermaster.seo_uso (
  id BIGINT NOT NULL AUTO_INCREMENT,          -- singleton: siempre id = 1
  consultas BIGINT NOT NULL DEFAULT 0,
  tokens_entrada BIGINT NOT NULL DEFAULT 0,
  tokens_salida BIGINT NOT NULL DEFAULT 0,
  costo_usd DECIMAL(14,6) NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**Seed (en el mismo script):**
- `seo_prompt`: dos filas, una por canal, con el contenido actual del código — `HOGAR` = `SYSTEM_BASE`; `GASTRO` = `SYSTEM_BASE + REGLA_GASTRO`.
- `seo_uso`: una fila `id = 1` con todos los contadores en 0.

### 2. Backend

**Entidades + repos**
- `SeoPrompt` (`id`, `canal` enum `SeoCanal`, `contenido`, `fechaModificacion`) + `SeoPromptRepository` (`findByCanal`).
- `SeoUso` (`id`, `consultas`, `tokensEntrada`, `tokensSalida`, `costoUsd`) + `SeoUsoRepository`.

**`SeoConfigService`**
- `String promptDe(SeoCanal canal)`: devuelve el `contenido` de la BD; si no hay fila para el canal (tabla recién creada / sin seed), **fallback** al hardcodeado de `OpenAiSeoPrompts.systemPrompt(canal)`.
- `void actualizar(SeoCanal canal, String contenido)`: upsert del prompt + `fechaModificacion`.
- `List<SeoPromptDTO> obtenerTodos()`: los 2 prompts para la pantalla.

**`SeoUsoService`**
- `void registrar(long tokensEntrada, long tokensSalida)`: incremento **atómico** vía un `@Modifying @Query` UPDATE
  (`SET consultas = consultas + 1, tokens_entrada = tokens_entrada + :in, tokens_salida = tokens_salida + :out, costo_usd = costo_usd + :costo WHERE id = 1`),
  con `:costo = tokensEntrada/1e6 · precioInput1m + tokensSalida/1e6 · precioOutput1m` calculado en `BigDecimal`. El cálculo del costo se hace en el servicio (método puro testeable) y se pasa el resultado al UPDATE.
- `SeoUsoDTO obtener()`: los totales + el `modelo`, `precioInput1m`, `precioOutput1m` vigentes (de `OpenAiProperties`).

**`OpenAiSeoService.generar()` — cambios**
- Tomar el system prompt de `SeoConfigService.promptDe(canal)` en vez de `OpenAiSeoPrompts.systemPrompt(canal)`. El `userMessage(contexto)` sigue saliendo de `OpenAiSeoPrompts` sin cambios.
- Tras parsear la respuesta, leer `usage.prompt_tokens` y `usage.completion_tokens` del JSON del response.
- Llamar a `SeoUsoService.registrar(in, out)` **best-effort**: envuelto en try/catch que loguea y no propaga, para que un fallo al contabilizar nunca rompa la generación de SEO.

**`OpenAiProperties` — agregar**
- `BigDecimal precioInput1m` (default `0.25`) y `BigDecimal precioOutput1m` (default `2.00`) — USD por millón de tokens (valores de referencia de gpt-5-mini; se ajustan por properties). Se setean en el compact constructor si vienen null.
- En `application.properties`: `openai.precio-input-1m=0.25` y `openai.precio-output-1m=2.00`.

**Controller** (`SeoController`, base `/api/seo`)
- `GET /api/seo/prompts` → `List<SeoPromptDTO>` — `@PreAuthorize(INTEGRACIONES_VER)`.
- `PUT /api/seo/prompts/{canal}` (body `{ contenido }`) → actualiza un prompt — `@PreAuthorize(INTEGRACIONES_EDITAR)`.
- `GET /api/seo/uso` → `SeoUsoDTO { consultas, tokensEntrada, tokensSalida, costoUsd, modelo, precioInput1m, precioOutput1m }` — `@PreAuthorize(INTEGRACIONES_VER)`.

**DTOs:** `SeoPromptDTO(canal, contenido, fechaModificacion)`, `SeoPromptUpdateDTO(contenido)`, `SeoUsoDTO(...)`.

### 3. Frontend

Página nueva "SEO IA (config y uso)", accesible desde el menú (sección de integraciones/configuración, gated por `INTEGRACIONES_VER`).

- **Config de prompts:** dos `textarea` (Hogar y Gastro) precargadas con `GET /api/seo/prompts`; botón Guardar por canal → `PUT /api/seo/prompts/{canal}`. Toast de éxito/error. Indicar `fechaModificacion` si existe.
- **Panel de uso:** `GET /api/seo/uso` → Consultas, Tokens entrada, Tokens salida, Costo (US$ con los decimales que correspondan), y **solo lectura** el modelo vigente y los precios (`in US$X/1M · out US$Y/1M`).
- Servicio API (`seoService.ts`) con `getPromptsAPI`, `updatePromptAPI`, `getUsoAPI`.

## Flujo de datos

**Generar SEO** (alta/edición de producto, sin cambios de UX):
1. `OpenAiSeoService.generar(canal, contexto)`.
2. Prompt de sistema ← `SeoConfigService.promptDe(canal)` (BD, o fallback al código).
3. Llamada a OpenAI → parseo del contenido → `SeoGeneradoDTO`.
4. Lee `usage` → `SeoUsoService.registrar(in, out)` (best-effort) → `UPDATE` atómico de la fila singleton. Si el response no trae `usage`, `in = out = 0`: la consulta se cuenta igual (`consultas + 1`) con tokens y costo 0.
5. Devuelve el `SeoGeneradoDTO` (igual que hoy).

**Pantalla SEO IA:**
- Carga: `GET /api/seo/prompts` + `GET /api/seo/uso`.
- Guardar prompt: `PUT /api/seo/prompts/{canal}` → re-fetch.

## Manejo de errores

- **Registro de uso falla** (BD caída, etc.) → se loguea y **no** aborta la generación de SEO.
- **Prompt no encontrado en BD** → fallback al prompt hardcodeado del código.
- **Incremento concurrente** (dos generaciones en paralelo — recordar que Hogar/Gastro pueden correr a la vez) → `UPDATE ... SET col = col + :delta` es atómico en el motor, sin lost updates.
- **OpenAI no configurado** → comportamiento actual sin cambios (excepción "OpenAI no configurado").

## Testing (todo offline, sin llamar a OpenAI ni APIs reales)

- `SeoUsoService`: cálculo de costo (`tokensIn/1e6·precioIn + tokensOut/1e6·precioOut` en `BigDecimal`, casos con 0 tokens y con redondeo) y que `registrar` arma el delta correcto.
- `SeoConfigService`: `promptDe` devuelve el de BD; con la fila ausente cae al fallback del código; `actualizar` persiste contenido + fecha.
- Parseo del `usage`: dado un JSON de response de OpenAI con `usage`, se extraen `prompt_tokens`/`completion_tokens` correctamente; un response sin `usage` no rompe y cuenta la consulta con tokens 0.
- `OpenAiSeoPrompts.userMessage` sigue intacto (los tests existentes deben seguir verdes).
- Migración: el backend arranca con `ddl-auto=validate` contra el schema con las 2 tablas nuevas.

## Fuera de alcance (YAGNI)

- Histórico por consulta / gráficos por período.
- Botón de "restaurar prompt por defecto" y de "reiniciar contadores".
- Edición del modelo/URL/precios desde la UI (quedan en properties).
- Edición del `userMessage` (estructura de datos del producto).
