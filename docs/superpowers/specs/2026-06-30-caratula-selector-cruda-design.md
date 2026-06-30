# Selector de imagen cruda + progreso + diagnóstico de carpeta + select de modelo

**Fecha:** 2026-06-30
**Estado:** Aprobado (pendiente de review del usuario)

## Contexto

Hoy, en el modal de producto ([ProductoFormModal.tsx](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx)), el botón **"Mejorar carátula con IA"**:

- Toma **automáticamente** la imagen cruda `{sku}.{ext}` de la carpeta cruda (`app.imagenes-raw-dir`, hoy `C:/Users/Leo/Desktop/cruda/`).
- La manda a OpenAI (`/images/edits`) vía `CaratulaService.generar(sku)` → `OpenAiImagenService.generarCaratula(...)`.
- La carátula resultante se guarda como imagen principal `{sku}.{ext}` en la carpeta de imágenes (`app.imagenes-dir`, Google Drive).
- Durante la espera muestra solo un spinner con texto fijo "Generando…".

Las miniaturas de "Imágenes (por SKU)" que se ven en el modal salen de la **carpeta de imágenes** (baseDir / Drive), no de la cruda.

## Objetivos

1. **Selector de cruda:** al apretar "Mejorar carátula con IA", mostrar un panel que liste **todas** las imágenes crudas cuyo nombre matchea el SKU (`{sku}`, `{sku}_1`, `{sku}_2`…) y permitir elegir cuál mejorar. El selector se muestra **siempre**, incluso si hay una sola cruda.
2. **Progreso por fases:** durante la generación, mostrar texto que avanza por fases ("Preparando imagen…" → "Enviando a OpenAI…" → "Generando carátula…"), en lugar del "Generando…" fijo. Al terminar, mostrar el **tiempo que tardó** la generación.
3. **Diagnóstico de carpeta:** mostrar si la carpeta cruda se encuentra y si hay acceso de lectura, y si la carpeta destino (donde se guarda la carátula) tiene acceso de escritura.
4. **Select de modelo:** en la pantalla de config IA, convertir el campo "Modelo" de imagen (hoy texto plano) en un `select` con opciones predefinidas.
5. **Resetear uso acumulado:** botón en la config IA para poner en cero los contadores de consumo (consultas, tokens, costo).
6. **Limpiar crudas al aceptar:** al aceptar (guardar) la carátula generada, eliminar **todas** las imágenes crudas del SKU de la carpeta cruda.
7. **Modelo en uso + acceso a config:** al lado de los botones de IA del modal de producto ("Mejorar carátula con IA" y "Generar SEO con IA"), mostrar qué modelo se está usando y un link a la pantalla de config IA correspondiente.

## Decisiones tomadas (brainstorming)

- Origen de las imágenes a elegir: **carpeta cruda** (`rawDir`), no la carpeta de imágenes publicadas.
- Selector: **siempre visible** (aunque haya una sola cruda).
- Progreso: **fases en el cliente con temporizador** (no streaming/SSE). La única etapa larga real es la llamada a OpenAI, que es opaca.

---

## Diseño

### 1. Flujo de UI (ProductoFormModal)

Estado nuevo en el componente:

- `selectorCaratulaAbierto: boolean`
- `crudas: string[]` — nombres de archivos crudos del SKU.
- `estadoCarpetas: EstadoCarpetasDTO | null` — diagnóstico (ver sección 3).
- `crudaElegida: string | null` — la cruda con la que se generó (para "Volver a generar").
- `faseCaratula: string` — texto de la fase actual de progreso.

Flujo:

1. Click en **"Mejorar carátula con IA"** → llama `getCrudasAPI(sku)`, guarda `crudas` + `estadoCarpetas`, y abre el panel selector. **No** genera todavía.
2. El panel muestra:
   - **Arriba:** estado de carpetas (cruda encontrada / lectura OK / destino escritura OK), con íconos ✓ / ⚠.
   - **Abajo:** grid de miniaturas de las crudas. Si no hay crudas → mensaje "No hay imágenes crudas para este SKU".
3. Click en una miniatura → `crudaElegida = nombre` y se dispara `generarCaratula(nombre)`.
4. Durante la generación → progreso por fases (sección 2). El botón / panel queda en estado "generando".
5. Al terminar → preview "Original vs Generada" (UI ya existente) con **Aceptar / Cancelar / Volver a generar**.
   - "Volver a generar" re-genera con `crudaElegida` (misma cruda).
   - "Cancelar" descarta el preview y vuelve al selector (sin cerrarlo).
   - **"Aceptar"** guarda la carátula y, si el guardado fue exitoso, el backend **elimina todas las crudas del SKU** (ver 3.d). El preview advierte que aceptar borra las imágenes crudas del SKU.

### 2. Progreso por fases (frontend)

`generarCaratula(crudaNombre)` arranca un temporizador que actualiza `faseCaratula`:

- `t = 0 ms`: **"Preparando imagen…"**
- `t ≈ 800 ms`: **"Enviando a OpenAI…"**
- `t ≈ 2500 ms`: **"Generando carátula…"** (permanece hasta que la promesa resuelve o falla)

Implementación: array de `{ ms, texto }` y `setTimeout` encadenados (o un `setInterval` que avanza el índice). Se limpian los timers en el `finally` de la generación (resuelto o error). Los textos/tiempos son ajustables en una constante.

**Tiempo de generación:** se marca `inicio = Date.now()` al disparar la llamada y, al resolver, se calcula `duracionMs = Date.now() - inicio` y se guarda en estado (`duracionCaratula`). En el preview del resultado se muestra junto a "Generada con IA" (p. ej. *"Generada con IA · 42 s"*; formato: segundos con un decimal si < 60 s, `m:ss` si ≥ 60 s). Mide el tiempo total percibido en el cliente (incluye red); no requiere cambios de backend. Se limpia al cancelar.

### 3. Backend

#### 3.a Listar crudas + diagnóstico de carpeta

Nuevo endpoint en [ImagenController](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/controller/ImagenController.java):

```
GET /api/imagenes/caratula/crudas/{sku}
→ 200 {
    crudaDir:   { ruta: string, existe: boolean, esDirectorio: boolean, legible: boolean },
    destinoDir: { ruta: string, existe: boolean, esDirectorio: boolean, escribible: boolean },
    imagenes:   string[]   // crudas del SKU, principal primero, luego _1, _2…
  }
```

Cambios en [ImagenService](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/ImagenService.java):

- **`List<String> resolverCrudasPorSku(String sku)`**: lista las crudas de `rawDir` cuyo nombre matchea el SKU usando la lógica de slots (`{sku}` = slot 0, `{sku}_N` = slot N), reutilizando/adaptando `slotDe(...)` y `prioridadExtension(...)` (hoy solo se aplican a `baseDir`). Ordena principal primero. Valida nombre seguro.
  - `rawDir` se lista directamente con `Files.list(...)` filtrando por slot del SKU (la carpeta cruda es chica y local; no necesita el índice cacheado de `baseDir`).
- **`record EstadoCarpeta(String ruta, boolean existe, boolean esDirectorio, boolean legible, boolean escribible)`** + método `estadoDe(Path dir)` usando `Files.exists / isDirectory / isReadable / isWritable`.
- Exponer estado de `rawDir` (legible) y de `baseDir` (escribible) para el DTO.

Nuevo DTO `CrudasDisponiblesDTO(EstadoCarpeta crudaDir, EstadoCarpeta destinoDir, List<String> imagenes)`.

#### 3.b Servir miniatura de cruda

Nuevo endpoint para que el `<img>` del selector pueda mostrar cada cruda:

```
GET /api/imagenes/cruda/{nombre}
→ 200 (bytes de la imagen, Content-Type por extensión)
→ 404 si no existe
```

- Valida path traversal con `validarNombreSeguro(nombre)` (ya existe) y que sea archivo de imagen dentro de `rawDir`.
- Content-Type derivado de la extensión (jpg→image/jpeg, etc.).
- Lee con `imagenService.leerCrudaBytes(nombre)` (ya existe).

#### 3.c Generar con cruda elegida

Modificar el endpoint actual para aceptar la cruda elegida (compatible hacia atrás):

```
POST /api/imagenes/caratula/generar/{sku}?cruda={nombre}
```

- `cruda` **opcional**: si viene, se valida que pertenezca al SKU (slot match) y exista; si no viene, se mantiene la resolución automática actual (`resolverCrudaPorSku`).
- `CaratulaService.generar(String sku, String crudaNombre)`:
  - Si `crudaNombre != null`: validar pertenencia al SKU y existencia → usar ese archivo.
  - Si `crudaNombre == null`: `resolverCrudaPorSku(sku)` (comportamiento actual).
  - El resto igual: leer bytes → `OpenAiImagenService.generarCaratula(bytes, nombre)` → devolver `GeneracionCaratula`.
- Sobrecarga: mantener `generar(String sku)` delegando a `generar(sku, null)` para no romper otros llamadores.

#### 3.d Eliminar crudas al guardar

Al aceptar/guardar la carátula, tras escribirla en `baseDir`, eliminar todas las crudas del SKU de `rawDir`:

- Nuevo `ImagenService.eliminarCrudasPorSku(String sku) : int` — resuelve las crudas con `resolverCrudasPorSku(sku)` y borra cada una con `Files.deleteIfExists(rawDir.resolve(nombre))`. Valida nombre seguro. Devuelve cuántas borró. **Best-effort:** si el borrado de alguna falla, loguea warn y continúa (no rompe el guardado, que ya fue exitoso).
- En `CaratulaService.guardar(String sku, byte[] datos)`: **primero** guarda la carátula (`imagenService.guardarCaratula(...)`); **solo si eso no lanzó**, llama `imagenService.eliminarCrudasPorSku(sku)`. Orden importa: nunca borrar la cruda si la carátula no se guardó.
- `rawDir` (Desktop) y `baseDir` (Drive) son carpetas distintas: borrar las crudas no afecta la carátula recién guardada.

### 4. Frontend — service y estado

En [productosService.ts](../../../supermaster-frontend/src/app/productos/productosService.ts):

- **`getCrudasAPI(sku): Promise<CrudasDisponibles>`** → `GET /api/imagenes/caratula/crudas/{sku}`.
- **`generarCaratulaAPI(sku, crudaNombre?)`** → agrega `?cruda=` cuando se pasa `crudaNombre` (param opcional para no romper otros usos).
- Helper de URL de miniatura: `` `${API_BASE_URL}/api/imagenes/cruda/${encodeURIComponent(nombre)}` `` (con `?v=cacheBust` si hace falta).
- Tipos: `EstadoCarpeta`, `CrudasDisponibles`.

### 5. Select de modelo (config IA)

En [config-ia/page.tsx](../../../supermaster-frontend/src/app/config-ia/page.tsx), el campo "Modelo" de imagen (línea ~139, hoy un `<input>`) pasa a `<select>` con el mismo estilo que los selects de `size`/`quality` contiguos:

| Label (visible) | Value (model ID) |
|---|---|
| GPT Image 2 | `gpt-image-2` |
| GPT Image 1.5 | `gpt-image-1.5` |
| GPT Image 1 | `gpt-image-1` |
| GPT Image 1 Mini | `gpt-image-1-mini` |

- **Preservar valor existente:** si `imgModel` cargado de BD no coincide con ninguno de los 4 IDs, agregar dinámicamente una `<option>` extra con ese valor (seleccionada) para no perderlo ni romper el guardado.
- Backend **sin cambios**: `model` sigue siendo `String` libre en `ImagenConfigUpdateDTO` / entidad. El select solo guía la elección en la UI (YAGNI: no se restringe en backend).

> ⚠️ **Advertencia (documentada, no bloqueante):** según la doc de OpenAI, el endpoint `/images/edits` (el que usa la carátula) soporta `gpt-image-1.5`, `gpt-image-1`, `gpt-image-1-mini` y `chatgpt-image-latest`; **`gpt-image-2` figura para generación y podría no aceptar edición**. Si al elegir "GPT Image 2" la generación falla, el error de OpenAI se mostrará vía el manejo de errores ya existente. Se incluye igual en el select porque el usuario lo usa desde el playground.

---

### 6. Resetear uso acumulado (config IA)

En la pantalla de Configuración IA, cada box **"USO DE IA … (ACUMULADO)"** tiene un botón **"Resetear"** que pone en cero los contadores de esa función (consultas, tokens entrada/salida, costo). La fila singleton (id=1) se conserva; solo se zeran sus valores.

**Alcance:** el box de uso aparece en ambas pestañas (SEO y Carátula), así que se agrega el botón en **las dos**, cada una reseteando su propia tabla de uso. *(A confirmar por el usuario; si solo se quiere en Carátula, se omite el de SEO — es independiente.)*

**Backend (simétrico para imagen y SEO):**

- `ImagenUsoRepository.reset()` — `@Modifying @Query("UPDATE ImagenUso s SET s.consultas = 0, s.tokensEntrada = 0, s.tokensSalida = 0, s.costoUsd = 0 WHERE s.id = 1")` → devuelve `int` (filas afectadas).
- `ImagenUsoService.reset()` — `@Transactional`; loguea warn si `reset() == 0` (fila ausente), igual que `registrar`.
- Endpoint: `POST /api/imagen-ia/uso/reset` → `204 No Content`. `@PreAuthorize(Permisos.INTEGRACIONES_EDITAR)`.
- Equivalente SEO: `SeoUsoRepository.reset()`, `SeoUsoService.reset()`, `POST /api/seo/uso/reset` (el controller de SEO usa base `/api/seo`).

**Frontend:**

- [seoService.ts](../../../supermaster-frontend/src/app/config-ia/seoService.ts): `resetImagenUsoAPI()` y `resetSeoUsoAPI()`.
- [useSeoIa.ts](../../../supermaster-frontend/src/app/config-ia/useSeoIa.ts): `resetImagenUso()` / `resetSeoUso()` que llaman al endpoint, refrescan el uso (re-fetch o set a ceros) y notifican éxito. Exponer flags `isResettingSeo` / `isResettingImagen` para deshabilitar el botón mientras corre.
- [page.tsx](../../../supermaster-frontend/src/app/config-ia/page.tsx): botón "Resetear" en `usoBox`, con **confirmación previa** (es destructivo, no se puede deshacer). Usar el patrón de confirmación ya existente en el proyecto; si no hay uno, un `window.confirm` simple.

### 7. Modelo en uso + acceso a config (modal de producto)

Junto a cada botón de IA del modal, mostrar el modelo configurado y un acceso a su pantalla de config:

- **Botón "Mejorar carátula con IA"** → texto "Modelo: `{modelImagen}`" + link a config IA (pestaña Carátula). `modelImagen` se lee de `GET /api/imagen-ia/config` (`.model`).
- **Botón "Generar SEO con IA"** (en cada bloque SEO de Nube) → texto "Modelo: `{modelSeo}`" + link a config IA (pestaña SEO). `modelSeo` se lee de `GET /api/seo/config` (`.model`).

**Carga de los modelos en el modal:** al abrir el modal de producto se hacen los dos GET de config (imagen y SEO) y se guardan en estado (`modelImagen`, `modelSeo`). Reusa los services existentes `getImagenConfigAPI` / `getSeoConfigAPI`. Mientras cargan, el texto puede omitirse o mostrar "Modelo: …".

**Link a la config:** navega a la pantalla de config IA. Para abrir directo en la pestaña correcta, [config-ia/page.tsx](../../../supermaster-frontend/src/app/config-ia/page.tsx) lee un query param (`?tab=caratula` | `?tab=seo`) y preselecciona esa pestaña; sin el param mantiene su default actual. El link se abre en **nueva pestaña** (`target="_blank"`) para no perder los cambios sin guardar del modal en edición.

**Backend:** sin cambios (los endpoints `GET /config` de imagen y SEO ya existen). El select de modelo de imagen (sección 5) y este texto leen la misma fuente, así que quedan consistentes.

## Manejo de errores

- **Carpeta cruda inexistente / sin lectura:** el endpoint de crudas no falla; devuelve `crudaDir.existe=false` (o `legible=false`) e `imagenes=[]`. El frontend muestra el diagnóstico y el mensaje "No hay imágenes crudas para este SKU".
- **Generar sin cruda válida:** `404` (`NotFoundException`) como hoy, propagado al toast del frontend.
- **OpenAI falla / timeout:** ya cubierto (`IllegalStateException` → 500 con mensaje); el progreso por fases se limpia en el `finally`.
- **Modelo no soportado por edits (p. ej. gpt-image-2):** error de OpenAI propagado al toast.
- **Borrado de crudas al guardar:** best-effort. Si el guardado de la carátula falla, **no** se borra nada. Si el guardado va OK pero el borrado de alguna cruda falla, se loguea warn y el guardado se considera exitoso igual (la carátula ya está).

## Seguridad

- `GET /api/imagenes/cruda/{nombre}` y la resolución de crudas validan con `validarNombreSeguro` (sin `/`, `\`, `..`) y restringen a archivos de imagen dentro de `rawDir`.
- La cruda pasada a `/generar` se valida contra el patrón de slot del SKU antes de leerla.
- `eliminarCrudasPorSku` valida `validarNombreSeguro(sku)` y solo borra archivos resueltos como crudas del SKU dentro de `rawDir` (nunca rutas arbitrarias).

## Testing

- `ImagenService.resolverCrudasPorSku`: SKU con principal + adicionales; orden; case-insensitive; SKU inexistente → vacío; nombre inseguro → excepción.
- `estadoDe(Path)`: carpeta existente legible/escribible; inexistente.
- Endpoint crudas: forma del DTO; carpeta vacía.
- `CaratulaService.generar(sku, cruda)`: con cruda válida; con cruda que no pertenece al SKU (rechazo); con `null` (fallback automático).
- `ImagenService.eliminarCrudasPorSku`: borra todas las crudas del SKU; cuenta correcta; SKU inexistente → 0; nombre inseguro → excepción; no toca archivos de otros SKU.
- `CaratulaService.guardar`: borra crudas solo tras guardar OK; si `guardarCaratula` lanza, no se borra ninguna cruda.
- `ImagenUsoService.reset()` / `SeoUsoService.reset()`: deja la fila en ceros; warn si la fila no existe.
- Endpoint `POST /uso/reset`: `204`; el `GET /uso` posterior devuelve ceros.
- Frontend: smoke manual del selector, progreso por fases, tiempo de generación, select de modelo (incl. valor BD fuera de la lista preservado), botón de reset con confirmación, y el texto "Modelo: …" + link a config (pestaña correcta, nueva pestaña) junto a los botones de carátula y SEO.

## Fuera de alcance (YAGNI)

- Streaming/SSE de progreso real desde OpenAI.
- Elegir crudas desde la carpeta de imágenes publicadas (baseDir).
- Restringir el `model` en backend a una lista cerrada.
- Subir/gestionar crudas desde la UI (siguen poniéndose a mano en la carpeta).
