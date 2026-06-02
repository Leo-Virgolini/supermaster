# Diseño: Imagen de producto por SKU (resolución por defecto)

**Fecha:** 2026-06-02
**Estado:** Aprobado para implementación

## Problema

Hoy la imagen de un producto se muestra **solo si** el campo `Producto.imagenUrl`
(`imagen_url`, varchar 500) fue seteado a mano mediante el selector de imágenes.
Con 18.000+ productos eso es inviable. Las imágenes ya existen en la carpeta de
imágenes (montada vía `app.imagenes-dir`, ~37 GB, sobre Google Drive File Stream),
nombradas por SKU: `SKU.jpg`, `SKU.png`, etc. (mayoritariamente `jpg`).

Se quiere que, **por defecto**, cada producto resuelva su imagen desde la carpeta
por su SKU, sin necesidad de cargar `imagen_url` para cada producto.

## Decisiones tomadas

1. **Resolución dinámica, sin tocar la DB.** No se hace backfill ni se escriben los
   18.000 registros. La imagen se resuelve por SKU al vuelo. Siempre refleja la
   carpeta (agregar/quitar un archivo se ve solo).
2. **El `imagenUrl` manual tiene prioridad (override).** La resolución por SKU solo
   actúa cuando `imagenUrl` es null/vacío.
3. **Placeholder "sin imagen"** cuando no hay ni override ni archivo por SKU
   (mantiene el comportamiento actual).
4. **Alcance:** UI de productos (tabla, modal, form) **y** catálogo PDF.

## Estado actual relevante

- `Producto.imagenUrl` guarda un **nombre de archivo** (ej. `SKU123.jpg`), no una URL completa.
- `WebConfig` mapea `GET /api/imagenes/{archivo}` → `file:{app.imagenes-dir}` como
  recurso estático, con `setCachePeriod(86400)` (cache de 1 día).
- `ImagenService` ya indexa la carpeta con un caché en memoria (TTL `app.imagenes-index-ttl-ms`,
  default 60s) para `/listar`, y tiene `buscarPorSku(sku)` que prueba extensiones con
  stats directos al disco.
  - Extensiones soportadas (en orden de prioridad): `jpg, jpeg, png, gif, webp, bmp, svg`.
- Frontend productos: `<img src={ imagenUrl ? .../api/imagenes/{imagenUrl} : "" }>`;
  si `imagenUrl` es null no muestra imagen.
- Catálogo PDF: `CatalogoPdfServiceImpl` pasa `producto.getImagenUrl()` a `CatalogoPdfItem`;
  `ImagenComponent.resolveImagePath` devuelve null si la url es null/blank → placeholder
  `SINIMAGEN.jpg` y suma el SKU a `productosSinImagen`.

## Enfoque elegido

**Endpoint "por SKU" con redirect 302 + fallback en el frontend.** Reusa el servido
estático existente (y su cache nativo); resolución O(1) usando un mapa en memoria
`sku → archivo` derivado del índice ya cacheado.

Descartados:
- Resolver en el `ProductoDTO` (campo calculado): obliga a tocar todos los DTOs/mappers
  de producto y resolver en cada listado. Más superficie.
- Endpoint que streamea el binario: el backend proxea el archivo, pierde el cache nativo
  del resource handler y carga el backend.

## Diseño

### 1. Backend — `ImagenService`: mapa `sku → archivo`

Agregar un mapa en memoria derivado del índice que ya se escanea (mismo `Files.list(baseDir)`,
mismo TTL). Construcción: por cada nombre del índice, `sku = nombre sin extensión` en
minúsculas → nombre de archivo. Ante colisión (`SKU.jpg` y `SKU.png`), **gana el orden de
prioridad de `EXTENSIONES`** (jpg primero).

Nuevo método:

```java
/** Resuelve el nombre del archivo de imagen para un SKU usando el índice cacheado
 *  (O(1), sin stats a disco). Devuelve null si no hay archivo. Case-insensitive. */
public String resolverArchivoPorSku(String sku)
```

- Se construye el mapa dentro de la misma sección sincronizada que refresca el índice,
  para no escanear dos veces.
- `buscarPorSku` (stats directos) puede quedar como está o reapoyarse en el mapa; no es
  obligatorio cambiarlo. Mantener su firma para no romper `/api/imagenes/buscar/{sku}`.

### 2. Backend — endpoint nuevo

En `ImagenController`:

```
GET /api/imagenes/producto/{sku}
  → si resolverArchivoPorSku(sku) != null:
       302 Found, Location: /api/imagenes/{archivo}
  → si no:
       404 Not Found
```

El 302 apunta al resource handler existente, que sirve el binario con su cache de 1 día.

### 3. Frontend — regla de resolución del `src`

En los tres puntos que hoy arman el `src` de la imagen del producto
(`productos/columns.tsx`, `productos/page.tsx`, y el modal si corresponde):

```
src = imagenUrl
        ? `${API_BASE_URL}/api/imagenes/${imagenUrl}`      // override manual
        : `${API_BASE_URL}/api/imagenes/producto/${encodeURIComponent(sku)}`  // por SKU
```

- `onError` del `<img>` → placeholder "sin imagen" (patrón ya usado).
- El selector manual de imagen no cambia; su valor (`imagenUrl`) sigue teniendo prioridad.
- Para construir la URL por SKU se necesita el `sku` del producto disponible junto a la
  celda de imagen (ya está en la fila).

### 4. Backend — catálogo PDF

En `CatalogoPdfServiceImpl`, inyectar `ImagenService` y, al construir cada `CatalogoPdfItem`:

```java
String imagenRef = producto.getImagenUrl();
if (imagenRef == null || imagenRef.isBlank()) {
    imagenRef = imagenService.resolverArchivoPorSku(producto.getSku()); // puede ser null
}
// pasar imagenRef como imageUrl del item
```

Así el override gana; si null se resuelve por SKU; si sigue null, `ImagenComponent` cae al
placeholder y cuenta el SKU en `productosSinImagen` (comportamiento actual intacto).

**Supuesto:** la carpeta de `ImagenService` (`app.imagenes-dir`) y la que usa el PDF
(`obtenerImagenesDirGlobal()`) son la misma. `resolverArchivoPorSku` devuelve solo el
nombre del archivo; `ImagenComponent` lo resuelve contra el dir del PDF. Si en el futuro
divergen, habría que alinear ambos.

## Data flow

```
[tabla productos]  <img src=/api/imagenes/producto/{sku}>
        │ (solo si imagenUrl es null)
        ▼
GET /api/imagenes/producto/{sku}
        │ ImagenService.resolverArchivoPorSku (mapa en memoria)
        ▼
302 → /api/imagenes/{archivo}  →  WebConfig resource handler  →  binario (cache 1 día)
```

Override manual: el `<img>` va directo a `/api/imagenes/{imagenUrl}` sin pasar por el endpoint.

## Edge cases

- **SKU sin archivo:** endpoint 404 → `onError` → placeholder. PDF → placeholder + `productosSinImagen`.
- **Imagen recién agregada:** visible tras expirar el TTL del índice (≤ TTL, default 60s).
- **Carpeta inaccesible (Drive caído):** índice vacío → 404 → placeholder. No rompe la UI.
- **Colisión de formatos:** gana la extensión de mayor prioridad (jpg).
- **SKU con caracteres especiales:** `encodeURIComponent` en el front; el backend resuelve
  contra el mapa por clave en minúsculas.

## Testing

- **Unit `ImagenService.resolverArchivoPorSku`** (con dir temporal):
  - resuelve `SKU.jpg`; case-insensitive (`sku` vs `SKU`, `.JPG`).
  - prioridad de extensión cuando coexisten `SKU.jpg` y `SKU.png` → jpg.
  - SKU inexistente → null.
- **Endpoint `/api/imagenes/producto/{sku}`**: 302 con `Location` correcto cuando hay
  archivo; 404 cuando no.
- **PDF (opcional / manual):** un producto sin `imagenUrl` pero con `SKU.jpg` en la carpeta
  aparece con imagen; uno sin archivo cae al placeholder y se reporta en `productosSinImagen`.

## Fuera de alcance

- Backfill / persistencia de `imagen_url`.
- Cambios en el selector manual de imágenes.
- Normalización de SKUs o renombrado de archivos en la carpeta.
- Soporte recursivo de subcarpetas (las imágenes están en la raíz del dir).
