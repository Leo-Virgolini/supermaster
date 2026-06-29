# Comparar cruda vs carátula generada — Diseño

**Fecha:** 2026-06-28
**Estado:** aprobado para plan

## Objetivo

Mejorar la UX de generación de carátula en el modal de producto: al generar, mostrar **la imagen cruda (original) y la carátula generada lado a lado**, con tres acciones: **Aceptar** (guardar), **Volver a generar** (re-llamar a OpenAI) y **Cancelar** (descartar). Hoy solo se muestra la imagen generada con Guardar/Descartar.

## Alcance

- Backend: el endpoint de generar pasa a devolver **también** la imagen cruda (base64 + formato), reutilizando los bytes que ya lee internamente. Una sola llamada (opción A; sin endpoint nuevo, sin exponer la carpeta de crudas).
- Frontend: el bloque de preview del modal muestra las dos imágenes y los tres botones.

**Fuera de alcance:** servir la carpeta de crudas por HTTP; cambiar el guardado, la config, la migración o la generación en sí.

## Global Constraints

- Backend: Spring Boot 4, Java 25, Maven, JPA. Tests: `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`).
- Frontend: Next.js/React/TS; `npx tsc --noEmit` exit 0, sin errores `error` de lint nuevos. No hay tests automáticos de frontend.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos, nunca `-A` ni `.superpowers/`.
- Paquete base backend: `ar.com.leo.super_master_backend`.

## Backend

### Servicio

`CaratulaService.generar(String sku)` deja de devolver `byte[]` y pasa a devolver un record con cruda + generada:

```
public record GeneracionCaratula(byte[] cruda, String crudaNombre, byte[] generada) {}
```

`generar` resuelve la cruda (`resolverCrudaPorSku` → 404 si no hay), lee sus bytes (`leerCrudaBytes`), llama a `openAiImagenService.generarCaratula(cruda, crudaNombre)` y devuelve `new GeneracionCaratula(cruda, crudaNombre, generada)`. `formato()` y `guardar(sku, datos)` no cambian.

### DTO

`CaratulaGeneradaDTO` se amplía:

```
public record CaratulaGeneradaDTO(String imagenBase64, String formato, String crudaBase64, String crudaFormato) {}
```

- `imagenBase64` / `formato`: la generada (igual que hoy; `formato` = `output_format` del config).
- `crudaBase64`: la cruda en base64.
- `crudaFormato`: subtipo MIME de la cruda derivado de su extensión, usable en `data:image/{crudaFormato}` (mapea `jpg`/`jpeg` → `jpeg`; el resto coincide con la extensión: `png`, `webp`, `gif`, `bmp`). La extensión sale del `crudaNombre`.

### Controller

`ImagenController` POST `/api/imagenes/caratula/generar/{sku}` (permiso `INTEGRACIONES_EDITAR`): toma la `GeneracionCaratula`, codifica ambas imágenes con `Base64.getEncoder()`, deriva `crudaFormato` de la extensión del `crudaNombre`, y devuelve el `CaratulaGeneradaDTO` ampliado. El endpoint `guardar` no cambia.

La derivación de subtipo MIME (jpg→jpeg, else extensión, en minúsculas) vive como método `static` en `ImagenController` (solo se usa ahí). Es lógica pura y testeable.

## Frontend (`ProductoFormModal`)

- `productosService.generarCaratulaAPI` retorna `{ imagenBase64: string; formato: string; crudaBase64: string; crudaFormato: string }`.
- Estados nuevos: `caratulaCruda` (base64) y `caratulaCrudaFormato`; se setean al generar junto con `caratulaPreview`/`caratulaFormato`.
- El bloque de preview (hoy una sola `<img>` + Guardar/Descartar, dentro de `editandoProductoId`) pasa a:
  - **Dos imágenes lado a lado**, con rótulos "Original" y "Generada con IA". Layout responsive (se apilan en pantallas chicas, p.ej. `flex flex-col sm:flex-row`).
    - Original: `data:image/${caratulaCrudaFormato};base64,${caratulaCruda}`.
    - Generada: `data:image/${caratulaFormato};base64,${caratulaPreview}`.
  - **Tres botones:**
    - **Aceptar** → `guardarCaratula` (lo de hoy; guarda, limpia preview, notifica, refresca/cache-bust). Deshabilitado mientras `guardandoCaratula`.
    - **Volver a generar** → llama de nuevo a `generarCaratula` (otra llamada con costo a OpenAI). Muestra spinner (`generandoCaratula`) y reemplaza cruda+generada. Deshabilitado mientras `generandoCaratula` o `guardandoCaratula`.
    - **Cancelar** → limpia el preview (`setCaratulaPreview(null)` y la cruda), igual que el actual "Descartar". Deshabilitado mientras `guardandoCaratula`.
- El botón inicial **"Mejorar carátula con IA"** sigue igual (solo en modo edición). El preview se sigue reseteando al cerrar/cambiar de producto (el modal se monta condicionalmente).

## Data flow

1. Click "Mejorar carátula con IA" → `POST /caratula/generar/{sku}`.
2. Backend: cruda → OpenAI → genera; registra uso. Responde `{ imagenBase64, formato, crudaBase64, crudaFormato }`.
3. Modal muestra Original (cruda) + Generada (preview), con Aceptar / Volver a generar / Cancelar.
4. Aceptar → `POST /caratula/guardar/{sku}` (sin cambios). Volver a generar → repite (2). Cancelar → limpia.

## Manejo de errores

- Sin imagen cruda → 404 "No hay imagen cruda para el SKU…" (igual que hoy); el modal muestra el toast de error y no abre el preview.
- Credencial/OpenAI: igual que hoy (503/500), surfaced via toast; `esSesionExpirada` suprime el toast en 401.

## Testing

- Backend: ajustar los tests que usan `CaratulaService.generar` (ahora devuelve `GeneracionCaratula`) y, si corresponde, los del controller; agregar un test del helper de derivación de subtipo MIME (jpg→jpeg, png→png, webp→webp). Suite `mvn -o test` verde.
- Frontend: `tsc --noEmit` exit 0; verificación manual del layout (dos imágenes + 3 botones).
