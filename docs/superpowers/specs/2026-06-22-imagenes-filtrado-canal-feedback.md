# Filtrado de imágenes por canal + feedback en el formulario

**Fecha:** 2026-06-22
**Estado:** Diseño aprobado, pendiente de plan.

## Objetivo

Hoy, al subir un producto, Nube y ML suben **todas** las imágenes que matchean el SKU (`{SKU}`, `{SKU}_2`, …) sin validar formato ni tamaño. Pero cada canal acepta solo ciertos formatos y un máximo de 10 MB por imagen:

- **Mercado Libre:** `jpg, jpeg, png` (doc ML "Imágenes en publicaciones").
- **Tienda Nube:** `gif, jpg, jpeg, png, webp` (doc Nube "Product Image").
- **Ambos:** 10 MB máximo por imagen.

El `ImagenService` resuelve además `bmp` y `svg`, que ningún canal acepta. Resultado: imágenes que el canal rechaza se mandan igual y fallan (hoy solo queda un `log.warn`, sin aviso claro al usuario).

Esta feature: (1) filtra las imágenes por formato y tamaño según el canal antes de subir, reportando las omitidas; y (2) da feedback en el formulario de producto sobre cuántas imágenes hay y cuáles quedarían afuera.

## Decisiones tomadas (brainstorming 2026-06-22)

1. **Filtrado centralizado** en `ImagenService` (no duplicado en cada canal): cada canal pasa sus formatos permitidos; el límite de 10 MB es común.
2. **Las rechazadas se reportan como advertencia** en el resultado del export (mismo mecanismo que hoy usa "X de Y imágenes subidas"), distinguiendo motivo: formato o tamaño.
3. **Feedback completo en el form:** conteo de imágenes detectadas + avisos por imagen (tamaño y formato), según los canales tildados.
4. **Endpoint genérico** `GET /api/imagenes/detalle/{sku}` que devuelve `[{nombre, extension, bytes}]`; el front aplica las reglas por canal. El backend sigue siendo el filtro autoritativo al subir.
5. **Dux no se toca** (no sube imágenes).

## Alcance

### Incluye
- **Backend `ImagenService`:** detalle por SKU (con tamaño) + filtrado por canal (formato + 10 MB).
- **Backend Nube y ML:** usar el filtro al subir; reportar omitidas como advertencia.
- **Backend endpoint:** `GET /api/imagenes/detalle/{sku}`.
- **Frontend form de producto:** bloque de feedback (conteo + avisos de tamaño/formato según canales tildados).

### NO incluye (fuera de alcance)
- Cambiar la convención de nombres de imágenes (`{SKU}`, `{SKU}_2`…).
- Redimensionar/convertir imágenes automáticamente (ej. webp→jpg). Solo se filtran/avisan.
- Validar otras restricciones de ML/Nube (resolución mínima, etc.).
- Tope de cantidad por categoría de ML (`max_pictures_per_item`): fuera de alcance; el canal rechaza el excedente.
- Dux.

## Contexto del código existente

- `ImagenService` (`dominio/imagen/service/ImagenService.java`): `resolverArchivosPorSku(sku)` (líneas 62-86) devuelve `List<String>` con la principal `{sku}.ext` y adicionales `{sku}_N.ext` (N≥2), ordenadas; `EXTENSIONES = {jpg, jpeg, png, gif, webp, bmp, svg}` (línea 30); `leerBase64`/`leerBytes` leen de `baseDir`. NO expone tamaño hoy.
- `ImagenController` (`dominio/imagen/controller/ImagenController.java`): tiene `/buscar/{sku}`, `/producto/{sku}`, `/listar?search=`. NO tiene un endpoint de detalle por SKU.
- **Nube** (`apis/nube/service/TiendaNubeService.java`): `subirImagenesProducto` (1102) y `sincronizarImagenesNube` (1130) llaman `imagenService.resolverArchivosPorSku(sku)` y luego `subirImagenes` (1111), que itera, lee base64, postea, y devuelve `null` o `"ok de N imágenes subidas"`.
- **ML** (`apis/ml/service/MercadoLibreService.java`): en `crearItemEnMl` (~1779-1786), un lambda `sku -> {...}` itera `resolverArchivosPorSku(sku)`, sube cada una con `subirImagenItem(filename)` y junta los `pictureIds`. El reporte de advertencias del alta ML usa `concatAdv` (helper público estático ya existente).

## Diseño

### Backend — `ImagenService`

Nuevos miembros:

```java
public static final long MAX_BYTES_CANAL = 10L * 1024 * 1024; // 10 MB

/** Una imagen resuelta con su metadata. */
public record ImagenDetalle(String nombre, String extension, long bytes) {}

/** Motivo por el que una imagen no se sube a un canal. */
public enum MotivoRechazo { FORMATO, TAMANO }

public record ImagenRechazada(String nombre, MotivoRechazo motivo) {}

/** Válidas a subir + rechazadas (con motivo), para un canal. */
public record FiltroImagenes(List<String> validas, List<ImagenRechazada> rechazadas) {}
```

Métodos:
- `List<ImagenDetalle> resolverDetallePorSku(String sku)`: como `resolverArchivosPorSku` pero cada elemento incluye `extension` (en minúscula, sin punto) y `bytes` (`Files.size(baseDir.resolve(nombre))`; si falla la lectura, se omite o bytes=-1, ver Manejo de errores).
- `FiltroImagenes filtrarParaCanal(String sku, Set<String> extensionesPermitidas)`: recorre `resolverDetallePorSku(sku)`; una imagen es **válida** si su extensión ∈ `extensionesPermitidas` Y `bytes <= MAX_BYTES_CANAL`; si no, va a `rechazadas` con motivo `FORMATO` (extensión no permitida) o `TAMANO` (extensión OK pero supera el límite). El orden de prioridad del motivo: primero formato, luego tamaño.

### Backend — Nube

Constante `EXT_NUBE = Set.of("gif", "jpg", "jpeg", "png", "webp")`.
- `subirImagenesProducto` y `sincronizarImagenesNube`: reemplazar `resolverArchivosPorSku(sku)` por `imagenService.filtrarParaCanal(sku, EXT_NUBE)`. Subir solo `validas`. Construir la advertencia combinando el conteo de subidas (como hoy) **y** las `rechazadas` (ej. `"2 omitidas: foto.webp (formato), grande.jpg (10MB)"`).
- Si `validas` está vacío pero había rechazadas → advertencia clara (no "creado sin imagen", sino "creado, todas las imágenes omitidas por formato/tamaño").

### Backend — ML

Constante `EXT_ML = Set.of("jpg", "jpeg", "png")`.
- En el lambda de imágenes de `crearItemEnMl`: usar `filtrarParaCanal(sku, EXT_ML)`, subir solo `validas`, y propagar las `rechazadas` a la advertencia del resultado del alta vía el mecanismo de advertencias existente (`concatAdv`). El detalle de cómo se conecta el reporte de rechazadas al resultado del item se resuelve en el plan (el lambda hoy solo devuelve `pictureIds`; habrá que exponer las rechazadas al flujo que arma la advertencia).

### Backend — Endpoint

`GET /api/imagenes/detalle/{sku}` en `ImagenController`:
- Devuelve `List<ImagenDetalle>` (200, lista vacía si no hay imágenes; nunca 404).
- Mismo `@PreAuthorize` que los otros endpoints de imágenes (o ninguno si los actuales no lo tienen — seguir el patrón del controller).

### Frontend — form de producto (`productos/page.tsx`)

- Servicio: `getImagenDetalleAPI(sku): Promise<{nombre, extension, bytes}[]>` en `productosService.ts`.
- Estado: `imagenesDetectadas` (lista) + carga al cambiar el SKU (alta: debounce ~400 ms al tipear; edición: al abrir el modal con el SKU del producto).
- Constantes espejo del backend:
  - `EXT_ML = {jpg, jpeg, png}`, `EXT_NUBE = {gif, jpg, jpeg, png, webp}`, `MAX_BYTES = 10*1024*1024`.
- Render: bloque cerca de los checkboxes de canales:
  - **"N imágenes detectadas"** (o "Sin imágenes para este SKU").
  - Por cada imagen con problema, según los canales tildados (`subirMl`, `subirKtHogar`, `subirKtGastro`):
    - `bytes > MAX_BYTES` → aviso "`{nombre}` supera 10 MB — no se subirá".
    - extensión ∉ formatos de un canal tildado → aviso "`{nombre}` — {Canal} no acepta .{ext}".
  - Si no hay problemas, no se muestran avisos (solo el conteo).

## Manejo de errores
- `resolverDetallePorSku`: si `Files.size` falla para un archivo (borrado entre el índice y la lectura), se omite ese archivo del detalle (best-effort), con `log.debug`.
- Endpoint: SKU en blanco o sin imágenes → `[]` (200).
- Subida (Nube/ML): el filtrado no cambia la política actual de que un fallo de imagen no revierte el alta; solo agrega que las rechazadas por formato/tamaño se reporten sin intentar subirlas.
- Front: si el endpoint falla, no romper el form (mostrar el bloque vacío / sin avisos); el backend filtra igual al subir.

## Pruebas
- **Backend `ImagenService` (unitario, baseDir temporal):**
  - `filtrarParaCanal` con extensión no permitida → va a `rechazadas` con `FORMATO`.
  - imagen de extensión válida pero > 10 MB → `rechazadas` con `TAMANO`.
  - imagen válida (formato + tamaño) → `validas`.
  - `resolverDetallePorSku` devuelve `bytes` correctos y `extension` en minúscula.
- **Backend endpoint:** `GET /api/imagenes/detalle/{sku}` devuelve el detalle; SKU sin imágenes → `[]`.
- **Backend Nube/ML:** la advertencia incluye las rechazadas (test de la construcción del mensaje; sin red).
- **Smoke (usuario):** crear un producto con una imagen `.webp` y otra `>10MB`; verificar que **ML omite ambas** (no acepta webp ni el exceso de tamaño) y **Nube omite solo la `>10MB`** (sí acepta webp), con las advertencias correspondientes; y que el form muestra los avisos según los canales tildados.

## Archivos afectados (resumen)
**Backend:**
- `dominio/imagen/service/ImagenService.java` — records + `resolverDetallePorSku` + `filtrarParaCanal` + `MAX_BYTES_CANAL`.
- `dominio/imagen/controller/ImagenController.java` — `GET /detalle/{sku}`.
- `apis/nube/service/TiendaNubeService.java` — usar filtro + reportar rechazadas (`EXT_NUBE`).
- `apis/ml/service/MercadoLibreService.java` — usar filtro + reportar rechazadas (`EXT_ML`).
- Tests: `ImagenService` (filtro + detalle), endpoint.

**Frontend:**
- `productos/productosService.ts` — `getImagenDetalleAPI`.
- `productos/page.tsx` — estado + carga + bloque de feedback; constantes de formato/límite.

## Pendiente de validar en smoke (usuario)
- ML omite `.webp/.gif/.bmp/.svg` y `>10MB`; Nube omite `.bmp/.svg` y `>10MB` (acepta webp/gif).
- El form muestra el conteo y los avisos correctos según los canales tildados.
