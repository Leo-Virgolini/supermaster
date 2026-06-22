# Estado de publicación por canal (activar / inactivar en Dux, Nube y ML)

**Fecha:** 2026-06-21
**Estado:** Diseño aprobado, pendiente de plan de implementación.
**Relacionado:** [[actualizar-al-editar-canales-estado]] (upsert al editar). Esta spec extiende el upsert para que el flag `Producto.activo` se refleje como **estado de publicación** en cada canal.

## Objetivo

Hoy el flag `Producto.activo` solo vive en la BD local: desmarcar "Activo" no cambia nada en los canales (salvo Dux, que ya recibe `habilitado=S/N`). Esta spec hace que, **al sincronizar un producto** (flujo actual de los checkboxes "Sincronizar con X"), el **estado de publicación** del canal pase a reflejar `activo`, **sin borrar** la publicación (reversible).

| Canal | Activo = true | Activo = false | Cómo |
|-------|---------------|----------------|------|
| **Dux** | `habilitado: "S"` | `habilitado: "N"` | Ya implementado en `DuxItemBuilder` |
| **Tienda Nube** | `published: true` | `published: false` | Alta + update |
| **Mercado Libre** | `status: "active"` | `status: "paused"` | Update (PUT status) + alta |

## Decisiones tomadas (brainstorming 2026-06-21)

1. **Cambiar estado, no borrar.** La baja es un cambio de estado reversible en cada canal (no `DELETE`, no `closed`). Para ML se usa `paused` (reversible), no `closed` (definitivo, perdería el MLA).
2. **Disparador: atado a la sincronización existente.** Al guardar el producto, el estado se aplica **dentro** de la sincronización a los canales que estén **tildados** en "Sincronizar con X" (igual que hoy). No hay disparador automático ni botón nuevo: tildar un canal y guardar sincroniza el producto con su estado actual. Si el producto se guarda sin tildar canales, no se toca ningún canal.
3. **Nube: `published` = `activo`** en alta y update (esto cambia el comportamiento actual del alta, que hoy crea siempre `published: false`).
4. **Best-effort.** El cambio de estado se trata como las imágenes/precio de hoy: si falla, se acumula una **advertencia** y NO se marca el SKU como ERROR (el resto del upsert ya se aplicó).

## Alcance

### Incluye
- **Nube — alta:** `published = producto.activo` en el payload de creación.
- **Nube — update:** agregar `published = producto.activo` al `PATCH /products/{id}`.
- **ML — update:** `PUT /items/{mla}` con `{"status": "active"|"paused"}` según `activo`.
- **ML — alta:** el item nace con `status: "paused"` si el producto está inactivo (si está activo, comportamiento actual).
- **Reactivación simétrica:** marcar Activo + sincronizar → Dux `S`, Nube `published:true`, ML `status:active`.

### NO incluye (fuera de alcance)
- **Dux:** sin cambios (ya manda `habilitado` según `activo` vía `DuxItemBuilder`).
- **Borrado/cierre definitivo** (ML `closed` + `deleted`, Nube `DELETE`): no se implementa. La baja es siempre reversible.
- **Disparador automático** al guardar sin tildar canales, o un botón "Dar de baja" aparte.
- **Cambios de schema** o de la estructura de la UI (el checkbox "Activo" ya existe).

## Contexto del código existente

### Dux — ya funciona
- `apis/dux/service/DuxItemBuilder.java`: `item.put("habilitado", Boolean.TRUE.equals(p.getActivo()) ? "S" : "N")`. Como `/item/nuevoItem` es upsert, sincronizar un producto inactivo lo deshabilita. **Sin cambios.**

### Tienda Nube
- `apis/nube/service/NubeProductoPayloadBuilder.java`: hoy `payload.put("published", false)` **hardcodeado**. Cambiar a `producto.getActivo()`.
- `apis/nube/service/TiendaNubeService.java` → `actualizarProductoEnNubeCore(...)`: hoy el `PATCH` arma `name`/`description`/`categories`/precio pero **no** toca `published`. Agregar `published`.
- Doc confirmada: el producto Nube tiene el campo booleano `published` y se setea por `PUT/PATCH /products/{id}`.

### Mercado Libre
- `apis/ml/service/MercadoLibreService.java`:
  - `leerStatusItems(...)`: ya lee el `status` de items (lectura). Útil para detectar `closed`.
  - `actualizarItemEnMlCore(...)`: actualiza título/desc/precio/imágenes; **no** toca `status`. Agregar el paso de status.
  - `crearItemEnMlCore(...)`: crea con `stock=0` (queda `out_of_stock`). Para producto inactivo, además enviar `status: "paused"`.
- Doc confirmada: `PUT /items/{id}` con `{"status":"paused"}` pausa (reversible vía `{"status":"active"}`); `closed` es final. El valor va en minúscula.

## Diseño

### Tienda Nube
- **Alta** (`NubeProductoPayloadBuilder.construir`): reemplazar `published: false` por `published: Boolean.TRUE.equals(producto.getActivo())`.
- **Update** (`actualizarProductoEnNubeCore`): agregar al body del `PATCH` la clave `published` con `Boolean.TRUE.equals(producto.getActivo())`. Siempre se envía (es un booleano determinístico del producto), junto a `name`/`description`/`categories`.
- No requiere endpoint nuevo: el `published` viaja en el mismo `PATCH`/`POST` que ya se hace.

### Mercado Libre
- Extender `actualizarItemEnMlCore` con un paso de **status best-effort**, en su propio try/catch (como las pictures):
  - Determinar el target: `activo ? "active" : "paused"`.
  - `PUT /items/{mla}` con `{"status": "<target>"}` (minúscula).
  - Si el `PUT` falla (p. ej. el item está `closed` y no se puede reactivar), acumular advertencia `"estado no actualizado: <motivo>"`. NO marcar ERROR.
  - Optimización opcional: leer el status actual antes (`leerStatusItems`) y solo enviar el `PUT` si difiere; si el item está `closed` y `activo=true`, advertir "publicación cerrada, no se puede reactivar" sin intentar el PUT.
- **Alta** (`crearItemEnMl`/`crearItemEnMlCore`): si el producto está inactivo, tras crear el item exitosamente hacer el **mismo `PUT` de status best-effort** (`{"status":"paused"}`) reutilizando el paso del update. No se cambia el payload del POST de creación. Si está activo, se mantiene el comportamiento actual (el `stock=0` ya lo deja `out_of_stock`).

### Reactivación
Es el camino simétrico del mismo flujo: con `activo=true`, el upsert manda Dux `S` / Nube `published:true` / ML `status:active`. No requiere lógica aparte.

## Manejo de errores
- El cambio de estado (Nube `published`, ML `status`) es **best-effort**: un fallo agrega una advertencia al resultado del SKU pero no lo marca como ERROR (el resto del upsert —título, descripción, precio, imágenes— ya se aplicó).
- ML: caso `closed` (no reactivable) → advertencia explícita ("publicación cerrada, no se puede reactivar"); no intentar republicar.
- Se mantiene la transaccionalidad de la spec previa (Nube `@Transactional readOnly` con I/O dentro; ML `procesarConProductoCargado` readOnly + asociación de MLA fuera de la tx). El PUT de status de ML es I/O HTTP, igual que el resto del update.

## Pruebas
- **Nube (núcleo testeable):**
  - Payload de alta (`NubeProductoPayloadBuilder.construir`): con `activo=true` → `published=true`; con `activo=false` → `published=false`.
  - Update: el body del `PATCH` incluye `published` con el valor de `activo`.
- **ML (núcleo testeable):**
  - `actualizarItemEnMlCore` con `activo=true` → llama al cambio de status con `"active"`; con `activo=false` → `"paused"`.
  - Un fallo del cambio de status → resultado sigue `ACTUALIZADO` con advertencia (no ERROR). Mismo patrón que el test `fallaImagenes_siguenActualizadoConAdvertencia`.
- **Red (smoke):** el `PUT` real de status en ML y el `published` real en Nube se validan en el smoke del usuario, no en unit test.

## Archivos afectados (resumen)
- `apis/nube/service/NubeProductoPayloadBuilder.java` — `published = activo` en el alta.
- `apis/nube/service/TiendaNubeService.java` — `published` en `actualizarProductoEnNubeCore`.
- `apis/ml/service/MercadoLibreService.java` — paso de status en `actualizarItemEnMlCore` (+ `crearItemEnMlCore` para alta inactiva); reutilizar `leerStatusItems` para el caso `closed`.
- **Sin cambios:** Dux (`DuxItemBuilder` ya manda `habilitado`), frontend (el checkbox "Activo" y los de canal ya existen), schema.
- **Tests:** `ActualizarItemEnMlTest` (status), `CrearItemEnMlTest`/payload Nube (published en alta), `ActualizarProductoEnNubeTest` (published en PATCH).

## Pendiente de validar en smoke (usuario)
- Nube: que `published:true` publique efectivamente el producto activo (cambia el comportamiento de alta, que hoy crea oculto).
- ML: que `paused`/`active` funcione sobre items existentes; verificar el caso `closed`.
