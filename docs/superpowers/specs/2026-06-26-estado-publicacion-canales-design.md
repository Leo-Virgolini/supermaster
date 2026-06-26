# Feature A — Estado de publicación de ML y Nube desde el modal de edición

Fecha: 2026-06-26

## Contexto

Hoy no se puede cambiar el estado de una publicación existente desde el sistema:
- **Mercado Libre:** el item tiene `status` (`active`/`paused`/`closed`). Se puede leer (`MercadoLibreService.obtenerStatusItems`) pero no hay nada que lo cambie.
- **Tienda Nube:** los productos se crean con `published=false` ([NubeProductoPayloadBuilder.java:23](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java#L23)) y no hay forma de cambiar la visibilidad después. Nube tiene **dos tiendas** (KT HOGAR y KT GASTRO); un producto puede estar en una, otra o ambas. El `product_id` de Nube no se persiste: se busca por SKU (`TiendaNubeService.buscarProductoPorSku`).

El flag `activo` del producto hoy solo afecta a Dux (`habilitado` S/N); el tooltip de ML del modal afirma "activa o pausa según Activo", lo cual **no está implementado** (tooltip engañoso).

## Decisión de arquitectura: fetch on demand

El **estado de publicación es propiedad del canal** (cambia fuera del sistema: ML pausa solos sin stock, edición en panel de Nube, ventas, etc.). Por eso **no se persiste** en la BD: se **lee al abrir** la edición y se **aplica al guardar**. Replicarlo requeriría webhooks/polling (mucho mayor) y arriesga mostrar un estado falso. El contenido que el sistema autorea (títulos, descripción, atributos) sigue viviendo en la BD; solo el estado vivo se consulta.

## Alcance

Por cada canal con publicación, en el modal de edición:
1. **Un control de estado editable:** **ML** (Activa/Pausada), **Nube HOGAR** (Visible/Oculta), **Nube GASTRO** (Visible/Oculta).
2. **Un snapshot read-only** de lo que el canal tiene hoy: precio, stock, y dimensiones/peso del paquete (más el estado). Solo visualización — incluye valores que el sistema manda hardcodeados (ej. dimensiones/stock de Nube), para que se vea lo real de cada canal.

- ML: solo `active` ↔ `paused`. `closed` queda afuera (irreversible en ML).
- Solo opera sobre publicaciones **ya existentes**. Publicar por primera vez (checkbox "Sincronizar") sigue igual (ML arranca activa, Nube oculta).
- **Independiente** de los checkboxes de "Sincronizar" y del flag "Activo" (que sigue siendo solo Dux).

### Backend

**Nuevos métodos de canal:**
- `MercadoLibreService.actualizarStatusItem(String mlaCode, String status)` → `PUT /items/{mlaCode}` con `{"status": "active"|"paused"}`. Devuelve el status resultante o lanza con el error de ML.
- `TiendaNubeService.obtenerPublished(String sku, <tienda>)` → busca por SKU en esa tienda; devuelve `{ publicado: bool, productId, published: bool }` (o `publicado=false` si no aparece).
- `TiendaNubeService.actualizarPublished(Long productId, <tienda>, boolean published)` → `PUT /products/{id}` con `{"published": bool}`.

**Nueva porción orquestadora** `EstadoPublicacionService` (un propósito: leer/escribir estado de publicación cruzando ML + Nube) y endpoints en un controller (`EstadoPublicacionController` o método en `ProductoController`):
- `GET /api/productos/{id}/estado-publicacion` → lee y devuelve por canal `{ publicado, estado, + snapshot read-only }`:
  - `ml`: `{ publicado, status, precio, stock, dimensiones }` — del `GET /items/{mla}` (status, `price`, `available_quantity`, y dimensiones desde los atributos `SELLER_PACKAGE_*`). `publicado=false` si no tiene MLA.
  - `hogar`, `gastro`: `{ publicado, visible, precio, stock, peso, dimensiones }` — del producto en esa tienda (`buscarProductoPorSku`): `published`, y del variant `price`, `stock`, `weight`, `depth/width/height`.
  - El **estado** (`status`/`visible`) alimenta el control editable; el resto (precio, stock, dimensiones/peso) es read-only.
  - Las lecturas se hacen en paralelo; si una falla, ese canal vuelve con `error: true` sin romper las otras.
- `PUT /api/productos/{id}/estado-publicacion` → body con los canales que cambiaron, p.ej. `{ "ml": "paused", "hogar": true }`. Aplica solo los presentes. Devuelve por canal `{ ok: bool, detalle }`.

**DTOs:** `EstadoPublicacionDTO` (respuesta del GET; por canal: `publicado`, `estado`, `precio`, `stock`, `peso`, `dimensiones`, `error`) y `EstadoPublicacionUpdateDTO` (body del PUT; solo el estado de los canales que cambiaron). Records.

### Frontend (modal de edición, solo modo edición)

Sección nueva **"Estado de publicación"**:
- Al abrir, se llama async a `GET /api/productos/{id}/estado-publicacion` (igual patrón que la carga de catálogos/aptos/clientes). Mientras carga → controles deshabilitados ("…").
- Por cada canal, un bloque con:
  - El **control de estado** (editable): si `publicado=false` → deshabilitado "No publicado"; si `error` → deshabilitado "No se pudo leer el estado"; si publicado → habilitado mostrando el estado real.
  - El **snapshot read-only**: precio, stock, dimensiones/peso que devolvió el canal (texto, no editable). Si el canal no está publicado o falló, el snapshot no se muestra.
- El usuario cambia el estado deseado (se guarda el valor cargado para diff).
- **Al apretar "Guardar Cambios"**: tras el PATCH del producto y los exports actuales, si algún control difiere de su valor cargado, se llama a `PUT /api/productos/{id}/estado-publicacion` con esos diffs. Los resultados (ok/error por canal) se suman al panel de resultado por canal que ya muestra la subida.
- Si la sesión expiró durante la lectura/escritura, no se muestra toast (reusar `esSesionExpirada`, como el resto del modal).

### Limpieza incluida

Corregir el tooltip de ML del modal que hoy promete "activa o pausa según el flag Activo" (no implementado) para que describa el comportamiento real.

## Error handling

- Lectura: falla por canal aislada (un canal con error no tumba a los otros).
- Escritura: cada canal reporta ok/detalle; se muestran en el panel de resultados. Una falla en un canal no impide aplicar los otros.

## Fuera de alcance

- Persistir estado/ids de ML/Nube (decisión: fetch on demand).
- Estado `closed`/finalizar en ML.
- Cambiar el estado inicial al publicar por primera vez.
- Detección de drift de contenido (comparar lo autoreado vs lo vivo) — feature separada.

## Testing

- **Backend:** tests de `EstadoPublicacionService` con mocks de ML/Nube — mapeo de estados (active/paused; published true/false), extracción del snapshot read-only (precio/stock/dimensiones desde el JSON de ML y desde el variant de Nube), lectura por las dos tiendas, aplicación parcial (solo canales presentes), aislamiento de fallas por canal. Tests de los nuevos métodos de `MercadoLibreService`/`TiendaNubeService` (formato del PUT). Correr con `mvn -o test`.
- **Frontend:** verificación manual — abrir un producto publicado (ver estados reales), cambiar y guardar, verificar en ML/Nube; producto no publicado en un canal (control deshabilitado); error de lectura.
