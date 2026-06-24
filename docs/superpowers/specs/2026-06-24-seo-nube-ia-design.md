# Generación de SEO de Tienda Nube con IA (OpenAI)

**Goal:** Generar los atributos SEO de Tienda Nube (`seo_title`, `seo_description`, `tags`) con OpenAI (`gpt-5-mini`) a partir del título Nube y las características del producto, mostrarlos en el modal de producto en campos editables, y enviarlos a Nube al subir el producto. **No se persisten en la BD.** Los prompts difieren por canal (KT Gastro vs KT Hogar).

**Arquitectura:** La generación se dispara desde el modal (botón "Generar SEO con IA") contra un endpoint que llama a OpenAI con el prompt del canal. El resultado se muestra en campos editables. Al subir a Nube, el front manda los SEO (por tienda) al flujo de exportación, que los incluye en el payload. Si para una tienda no llegan SEO, no se mandan (alta sin SEO; en edición el PUT parcial no los toca). Nada se guarda en la BD.

**Tech Stack:** Spring Boot 4 / Java 25 (backend), Next.js 16 / React 19 / TS (frontend). HTTP saliente con `RestClient`. JSON: Jackson 3 + `com.fasterxml.jackson.annotation`.

## Decisiones (cerradas con el usuario)

1. **Modelo:** OpenAI `gpt-5-mini`, Chat Completions, respuesta en JSON.
2. **No persistir en BD:** sin columnas ni cambios en la entidad/DTOs de producto. El SEO solo existe en el modal y en el request de export.
3. **Editable:** se muestran y se pueden ajustar a mano antes de subir.
4. **Por canal:** los prompts difieren (Gastro incluye la regla del rubro gastronómico; Hogar no). Si el producto sube a ambos canales, se generan dos juegos (uno por prompt).
5. **Al crear:** si sube a Nube y un bloque de SEO quedó vacío, el front lo auto-genera antes de exportar.
6. **Al editar:** un botón "Forzar/actualizar SEO" regenera; al guardar actualiza en Nube.
7. **Credenciales:** `openai_tokens.json` en `app.secrets-dir` (junto a `dux_tokens.json`/`nube_tokens.json`).

## Prompts (exactos)

**System (común a ambos canales):**
```
Eres un especialista en SEO para ecommerce.

Genera exclusivamente un JSON válido con las siguientes propiedades:

{
  "seo_title": "",
  "seo_description": "",
  "tags": ""
}

Reglas:
- seo_title: máximo 70 caracteres.
- seo_description: máximo 320 caracteres.
- tags: entre 5 y 10 tags separados por comas.
- No inventar características que no estén presentes en la información.
- Optimizar para búsquedas en español.
- No incluir explicaciones ni texto fuera del JSON.
```
**Solo para KT GASTRO, se agrega como última regla:**
```
- Todo lo que generes, que sea enfocado en el rubro gastronomico, no hogareño. Vendemos productos para restaurantes, cafeterias, pastelerias, panaderias, pizzerias, bares, etc.
```

**User (contexto del producto):** título Nube + título Dux + marca + material + dimensiones + aptos, en texto plano.

## Componentes

### Backend

- **Config OpenAI** (replica el patrón Dux/Nube):
  - `OpenAiCredentials` (POJO, `@JsonProperty("api_key") String apiKey`).
  - `OpenAiProperties` (`baseUrl=https://api.openai.com/v1`, `model=gpt-5-mini`, timeouts) con prefijo `openai.*`.
  - `OpenAiConfig` con `@Bean RestClient openaiRestClient`.
- **`SeoCanal`** enum: `GASTRO`, `HOGAR`.
- **`OpenAiSeoService`**:
  - `@PostConstruct` carga `openai_tokens.json` desde `secretsDir`.
  - `SeoGeneradoDTO generar(SeoCanal canal, SeoContexto contexto)`:
    1. Arma `messages`: system = prompt base + (si `GASTRO`) la regla extra; user = contexto en texto.
    2. POST `/chat/completions` con `{model, messages, response_format:{type:"json_object"}}` (header `Authorization: Bearer <apiKey>`). Nota de implementación: si `gpt-5-mini` requiere `max_completion_tokens` en lugar de `max_tokens`, usar el que acepte la API.
    3. Parsea `choices[0].message.content` (que es un JSON string) a `{seo_title, seo_description, tags}`.
    4. **Trunca** `seo_title` a 70 y `seo_description` a 320; devuelve `SeoGeneradoDTO(seoTitle, seoDescription, tags)`.
  - Si falta el token o la API falla → `IllegalStateException`/propaga, el endpoint responde error.
- **`SeoContexto`** (record / DTO de entrada): `tituloNube, tituloDux, marca, material, List<String> aptos, List<String> dimensiones`. Un helper `SeoContextoBuilder.texto(SeoContexto)` arma el string plano para el user message.
- **Endpoint** `POST /api/productos/generar-seo` → body `GenerarSeoRequestDTO(String canal, SeoContexto contexto)` (o campos planos + canal) → `SeoGeneradoDTO`. `@PreAuthorize` con el permiso de productos correspondiente.
- **Export a Nube acepta SEO por tienda:**
  - El front, al exportar a Nube, manda por cada tienda un `seo` opcional `{ seoTitle, seoDescription, tags }`.
  - `NubeProductoPayloadBuilder.construir(...)` y el body del PUT en `TiendaNubeService.actualizarProductoEnNubeCore` reciben un `SeoGeneradoDTO seo` opcional y, si no es null y tiene valores, agregan: `seo_title` = `{es: seoTitle}`, `seo_description` = `{es: seoDescription}`, `tags` = `seoTags.split(",")` (trim por elemento, array de strings). Si `seo` es null → no se agregan (PUT no toca el SEO existente en Nube).
  - El endpoint/servicio de export (`NubeExportService` + su controller) propaga el `seo` recibido por tienda hasta el payload.

### Frontend (`ProductoFormModal.tsx`)

- **Estados**: `seoGastro` y `seoHogar`, cada uno `{ title, description, tags }` (no persistidos), + `generandoSeo`.
- **Sección "SEO de Tienda Nube"**: visible si `subirKtGastro || subirKtHogar`. Un sub-bloque por canal activo:
  - Botón "Generar SEO con IA" (spinner mientras `generandoSeo`).
  - 3 campos editables: SEO Title (`maxLength 70` + contador), SEO Description (`textarea`, `maxLength 320` + contador), Tags (texto, CSV).
- **`generarSeo(canal)`**: arma el `SeoContexto` con los datos del form (tituloNube, tituloDux, marcaDisplay, materialDisplay, aptosSel, dimensiones) → `POST /api/productos/generar-seo` → setea el bloque del canal.
- **Al crear/guardar con Nube activo**: por cada canal activo cuyo bloque esté vacío, auto-generar (await) antes de exportar; luego pasar los SEO (por tienda) al flujo de export a Nube.
- **Service**: `generarSeoAPI(canal, contexto)` en `productosService.ts`.

## Data flow

```
[Botón Generar / auto al crear]
  → POST /api/productos/generar-seo {canal, contexto}
      → OpenAiSeoService.generar(canal, contexto) → OpenAI gpt-5-mini → SeoGeneradoDTO
  → rellena campos editables del canal

[Guardar/Subir a Nube]
  → export Nube con seo por tienda (los del modal)
      → NubeProductoPayloadBuilder / PUT body incluye seo_title/seo_description/tags
      → POST/PUT a Nube
```

## Error handling

- Falta `openai_tokens.json` o API key → el endpoint responde error claro ("OpenAI no configurado"); el botón muestra toast de error y no rellena campos.
- Falla de OpenAI (red/cuota/parse) → toast de error; el producto se puede guardar igual (el SEO es opcional).
- Auto-generación al crear que falla → no aborta la creación; se sube a Nube sin SEO y se avisa (toast).

## Testing (offline — NO se llama a OpenAI real)

- **`OpenAiSeoPromptTest`**: que el system prompt para `GASTRO` contenga la línea del rubro gastronómico y para `HOGAR` NO; que el user message incluya título/marca/material/dimensiones/aptos.
- **`OpenAiSeoParseTest`**: dado un JSON de respuesta de OpenAI de ejemplo (`choices[0].message.content` con `{seo_title, seo_description, tags}`), parsear a `SeoGeneradoDTO` y verificar el **truncado** a 70/320.
- **Frontend**: `npx tsc --noEmit`.

## Constraints

- Trabajar en `main`. Backend `mvn -o` instalado. Frontend `npx tsc --noEmit`.
- **NO ejecutar contra la API real de OpenAI/Nube.** El smoke real lo hace el usuario.
- Sin cambios de schema (no se persiste).
