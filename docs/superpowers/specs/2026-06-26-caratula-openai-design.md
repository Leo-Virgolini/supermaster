# Feature B — Carátula de producto con OpenAI (fondo blanco)

Fecha: 2026-06-26

## Contexto

Al editar un producto, poder generar con OpenAI una **carátula** (primera imagen) con fondo blanco y márgenes chicos a partir de una foto cruda, y guardarla en la carpeta de imágenes que ya usa el sistema, nombrada por SKU.

Reutiliza el patrón de la integración OpenAI existente (SEO): cliente `RestClient`, credencial desde `secrets/`, prompt en BD editable, registro de uso/costo, pantalla de configuración. Hoy el sistema **solo lee** imágenes (`ImagenService` desde `app.imagenes-dir`, archivos `{SKU}.{ext}` carátula y `{SKU}_N.{ext}` adicionales); esta feature agrega **escribir** un archivo.

## Decisiones (del brainstorming)

- **Entrada/salida:** la foto cruda vive en una **carpeta de entrada separada** (config nueva `app.imagenes-raw-dir`), archivo `{SKU}.{ext}`. La procesada se guarda como **`{SKU}.jpg`** en `app.imagenes-dir`. La cruda queda intacta; en la carpeta de imágenes se reemplaza la carátula `{SKU}` si ya existía (es la intención).
- **Preview y confirmación:** generar NO guarda; el modal muestra el preview y el usuario confirma **Guardar** o **Descartar**.
- **Salida:** una sola imagen **cuadrada 1024×1024 JPG**, fondo blanco, para ML y Nube por igual. Es el único tamaño **cuadrado** que ofrece el modelo (los mayores —2K/4K— son 16:9, no sirven para carátula). gpt-image-2 devuelve el **JPG final directo** (`output_format=jpeg`, `quality=high`): **sin reescalado ni post-proceso**; se guardan los bytes tal cual.
- **Modelo:** `gpt-image-2` (configurable vía `openai.image.model`), endpoint `/images/edits`, `size=1024x1024`, `output_format=jpeg`, `quality=high` (toma la imagen + prompt y devuelve la editada con fondo blanco).
- **API key distinta:** segundo credential y segundo `RestClient`, separados del SEO.
- **Prompt y uso:** prompt único en BD (editable) y tabla de uso/costo, ambos mostrados/editables en la pantalla "SEO IA".

## Arquitectura

### Backend

**Cliente y credencial (apis/openai/config):**
- Reusar el patrón de `OpenAiConfig`/`OpenAiProperties`. Agregar `OpenAiImageProperties` (prefix `openai.image`: `model=gpt-image-1`, base-url, timeouts, precios por token) y un bean `openaiImageRestClient`.
- Credencial: archivo `secrets/openai_image_tokens.json` (mismo formato que `openai_tokens.json`), cargado en el servicio nuevo (no mezclar con la key de SEO).

**`OpenAiImagenService` (apis/openai/service):**
- `byte[] generarCaratula(byte[] imagenCruda, String filename)`: arma el multipart a `/images/edits` con `model=<de properties>`, `image=<cruda>`, `prompt=<de BD>`, `size=1024x1024`, `output_format=jpeg`, `quality=high`; recibe el **JPG** (b64), lo decodifica y devuelve los bytes **tal cual** (sin reescalar ni reconvertir). Registra el uso (tokens/costo) al estilo `SeoUsoService`.
- Lanza excepción clara si falta credencial / falla OpenAI.

**Prompt en BD (apis/openai):**
- Tabla `imagen_prompt` (una fila; `contenido` TEXT, `fecha_modificacion`). Servicio de lectura/actualización (espejo de `SeoConfigService`). Seed inicial con el texto: recortar el producto y ponerlo sobre fondo blanco puro, centrado, con márgenes chicos, para carátula de e-commerce, sin alterar el producto.

**Uso/costo:**
- Tabla `imagen_uso` (fila singleton: `consultas`, `tokens_entrada`, `tokens_salida`, `costo_usd`). Servicio espejo de `SeoUsoService` con UPDATE atómico. gpt-image-1 factura por tokens (texto + imagen entrada + imagen salida).

**Escritura de archivo (dominio/imagen):**
- Agregar a `ImagenService` (o un colaborador) la capacidad de **leer la cruda** desde `app.imagenes-raw-dir` por SKU y **escribir** `{SKU}.jpg` en `app.imagenes-dir` (crear/reemplazar). Invalidar el índice cacheado de imágenes tras escribir, para que el carrusel la vea.

**Endpoints (dominio/imagen/controller, o un controller nuevo de carátula):**
- `POST /api/imagenes/caratula/generar/{sku}` → lee la cruda, llama a `OpenAiImagenService`, devuelve `{ imagenBase64 }` (JPG en base64). No guarda.
- `POST /api/imagenes/caratula/guardar/{sku}` (body `{ imagenBase64 }`) → decodifica y escribe `{SKU}.jpg` en `app.imagenes-dir`. Devuelve 204/ok.
- Permisos: acción sobre integraciones/imágenes (reusar el permiso que corresponda, ej. `INTEGRACIONES_EDITAR`).

### Frontend (modal de edición, modo edición)

- Botón **"Mejorar carátula con IA"** (junto a la sección de imágenes / carátula). Al apretarlo: llama a `generar/{sku}`, muestra spinner; con la respuesta, muestra el **preview** (`<img src="data:image/jpeg;base64,...">`) con botones **Guardar** y **Descartar**.
- **Guardar** → `guardar/{sku}` con el base64; al ok, refrescar el carrusel/detalle de imágenes (la nueva `{SKU}.jpg` ya está en disco). **Descartar** → cierra el preview sin escribir.
- Errores (sin imagen cruda, falla OpenAI, sin credencial) → toast claro; nada se escribe. Reusar `esSesionExpirada`.
- **Pantalla "SEO IA":** agregar una tarjeta "Prompt de carátula" (editar el prompt de `imagen_prompt`) y mostrar el uso/costo de `imagen_uso`, con el mismo patrón que el SEO.

## Config

- `app.imagenes-raw-dir`: carpeta de fotos crudas de entrada (nueva propiedad; en dev apuntará a la carpeta real).
- `app.imagenes-dir`: ya existe (salida = carátula).
- `secrets/openai_image_tokens.json`: API key de imágenes.
- `openai.image.*`: modelo (`gpt-image-1`), base-url, timeouts, precios.

## Manejo de errores

- Falta la cruda `{SKU}` en `app.imagenes-raw-dir` → 404/mensaje "No hay imagen cruda para este SKU".
- Falla/timeout de OpenAI o credencial ausente → mensaje claro; no se guarda.
- El preview garantiza que nada se escribe sin confirmación.

## Fuera de alcance

- Generar más de una imagen o variantes por canal (una sola cuadrada para ambos).
- Procesar imágenes adicionales (`{SKU}_N`); solo la carátula.
- Reescalado, recorte o conversión de formato: gpt-image-2 devuelve el JPG cuadrado final (1024×1024); se guarda tal cual.
- Tamaños mayores a 1024 cuadrados (no existen nativos; 2K/4K son 16:9).
- Edición/selección manual de la foto cruda (se toma `{SKU}` de la carpeta de entrada).

## Testing

- **Backend:** tests puros del cálculo/registro de uso (espejo de `SeoUsoService`) y del parseo de la respuesta de OpenAI (b64 + tokens). Lectura cruda / escritura `{SKU}.jpg` con un dir temporal. La llamada real a OpenAI se valida manualmente (sin test unitario del POST, como el SEO). `mvn -o test`.
- **Frontend:** verificación manual — generar (preview), guardar (aparece en el carrusel), descartar (no cambia nada), errores.

## A confirmar en la revisión del spec

- La imagen cruda de entrada se identifica como `{SKU}.{ext}` en `app.imagenes-raw-dir` (misma convención que el resto). Si el archivo crudo se nombra/ubica distinto, ajustar.
