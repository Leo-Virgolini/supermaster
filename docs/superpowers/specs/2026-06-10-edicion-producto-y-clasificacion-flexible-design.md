# Diseño: Edición de producto y clasificación flexible

Fecha: 2026-06-10

## Objetivo

Tres mejoras sobre el dominio de productos:

1. **Clasificación flexible**: exigir al menos una de las dos clasificaciones
   (general *o* gastronómica) en vez de exigir la general siempre. `tipo` sigue
   siendo obligatorio.
2. **Permitir null** en Clasif. Gral y Clasif. Gastro desde la tabla inline
   (hoy la general no se puede desasignar), respetando la regla de (1).
3. **Panel de edición de producto**: un botón "Editar" por fila que abre el
   mismo panel rico del alta, en modo edición (todos los campos, incluidas
   relaciones N-a-N y márgenes). SKU en solo lectura.

## Estado actual (resumen del código)

- Front alta (`supermaster-frontend/src/app/productos/page.tsx`): form inline con
  ~30 estados. `validateForm` (≈507-508) exige `clasifGralId` y `tipoId`.
  `handleCreate` arma `ProductoCreateDTO` y llama `createProducto`.
- Tabla (`columns.tsx`): celda **rubro/clasifGral** sin `nullable`; **subrubro/clasifGastro**
  con `nullable`; **tipo** sin `nullable`. Edición inline → `handleUpdate` → PATCH.
- `ProductoDetalleModal.tsx`: edita relaciones N-a-N (aptos/catálogos/clientes),
  márgenes, precios inflados e historial. NO edita campos del producto.
- Backend:
  - `ProductoCreateDTO`: `@NotNull` en `clasifGralId` y `tipoId`.
  - `ProductoMapper.toEntity`: `clasifGral` y `tipo` se crean sin check de null;
    `clasifGastro` con check.
  - `aplicarPatch`: `clasifGral`/`tipo` usan `leerIdRequerido` (rechazan null);
    `clasifGastro` usa `leerIdOpcional`.
  - DB: las tres FK (`id_clasif_gral`, `id_clasif_gastro`, `id_tipo`) son
    **nullable** a nivel de schema. El bloqueo es solo de validación de aplicación.

## Parte 1 — Validación "al menos una clasificación"

Regla de negocio: `clasifGral != null || clasifGastro != null`. `tipo` obligatorio.

### Backend (fuente de verdad)

- `ProductoCreateDTO`: quitar `@NotNull` de `clasifGralId` (queda `@Positive`).
- `ProductoMapper.toEntity`: `clasifGral` con check de null (igual que `clasifGastro`).
- `aplicarPatch` (PATCH): `clasifGral` pasa de `leerIdRequerido` a `leerIdOpcional`.
- Validación cross-field reutilizable, p. ej. en `ProductoServiceImpl`:
  `validarAlMenosUnaClasificacion(Producto)` que lanza `BadRequest`/`ConflictException`
  con el mensaje: *"El producto debe tener al menos una clasificación: general o gastronómica."*
  Se invoca al final de `crear`, `patch` y `actualizar` (PUT), sobre el estado
  resultante de la entidad (no sobre el DTO), para cubrir todos los caminos.

### Frontend (alta y edición)

- `validateForm`: reemplazar `if (!clasifGralId)` por
  `if (!clasifGralId && !clasifGastroId)` → setear un error que marque ambos campos.
- Marcado visual: en vez del `*` rojo en Clasif. Gral, **ambos** campos llevan un
  badge "al menos una" (ámbar/azul) junto al label. Si la validación falla, ambos
  campos se marcan en rojo y se muestra un único mensaje:
  *"Seleccioná al menos una clasificación (general o gastronómica)."*

## Parte 2 — Null en Clasif. Gral / Gastro (tabla inline)

- `columns.tsx`: la celda de **rubro (clasifGral)** pasa a `nullable` (gastro ya lo es).
- No se agrega lógica cross-field en el front: al desasignar una clasificación, si
  el PATCH dejaría ambas en null, el backend (Parte 1) responde error y el toast
  existente lo muestra; la celda revierte al valor previo.

## Parte 3 — Panel de edición (reutiliza el form del alta)

### Disparador

- Nueva acción por fila "Editar" (ícono lápiz, `TableActionButton` tono neutro/secundario)
  en la columna de acciones de `columns.tsx`, junto a "Detalle" y "Precios". Llama a
  un callback `onEditarProducto(row.original)` provisto desde `page.tsx`.

### Modo del modal

- Nuevo estado `editandoProductoId: number | null` en `page.tsx`.
  `null` = crear (comportamiento actual); con id = editar.
- Derivados del modo: título ("Nuevo Producto" / "Editar Producto"), label del
  botón ("Crear Producto" / "Guardar Cambios"), SKU `disabled` en edición.
- Al cerrar (`onClose`/cancel) se limpia `editandoProductoId` además del `resetForm`.

### Carga del producto al abrir en edición

- Poblar todos los estados del form desde el `ProductoDTO` de la fila (escalares,
  relaciones simples por id, márgenes, dimensiones, económicos, flags).
- Relaciones simples (marca/origen/clasif/tipo/proveedor/material/mla): hay id en
  el DTO. Para que los `AsyncSelect` muestren el nombre, se setea el `displayValue`
  correspondiente (los componentes ya soportan `displayValue`/`value`).
- Relaciones N-a-N (catálogos/aptos/clientes): el `ProductoDTO` trae **nombres**,
  no ids. Para poblar los `MultiAsyncSelect` (que usan `{id,label}`) se reutiliza el
  mecanismo de carga que ya emplea `ProductoDetalleModal`. Si esa vía no expone ids
  de forma directa, se agrega al backend la lista de ids de esas relaciones en el
  `ProductoDTO` (o un endpoint `GET /productos/{id}/relaciones`); se decide en el
  plan de implementación tras inspeccionar `ProductoDetalleModal`.

### Guardado en edición

- Producto: `PATCH /api/productos/{id}` con los campos editables (escalares,
  relaciones simples, dimensiones, económicos). Reusar `updateProductoAPI`.
- Márgenes: `updateProductoMargen` (misma vía que la edición inline de márgenes).
- N-a-N: calcular diff entre el conjunto inicial (al abrir) y el actual; agregar las
  nuevas (`addProductoCatalogoAPI`/`addProductoAptoAPI`/`addProductoClienteAPI`) y
  quitar las removidas (endpoints DELETE equivalentes). Solo se llama lo que cambió.
- Tras guardar: refrescar la fila/listado, cerrar modal, notificar éxito.

### Reutilización / refactor mínimo

- El armado del payload y el submit se comparten ramificando por modo
  (`editandoProductoId == null ? crear : guardarEdicion`). No se extrae el form a otro
  componente en esta iteración (YAGNI); el cambio se mantiene dentro de `page.tsx`.

## Manejo de errores

- Validación "al menos una": backend responde 400/409 con mensaje; el front lo
  muestra (form: mensaje bajo los campos; inline: toast y revierte).
- SKU duplicado: ya implementado para alta; en edición el SKU es solo lectura, así
  que no aplica.
- Guardado de edición parcialmente fallido (p. ej. producto OK pero una N-a-N falla):
  notificar el error puntual; el producto ya quedó persistido. No se implementa
  rollback transaccional cross-endpoint (fuera de alcance).

## Testing

- Backend: tests de `validarAlMenosUnaClasificacion` en `crear`, `patch` y `update`
  (casos: solo gral, solo gastro, ambas, ninguna → error). Test de PATCH que
  desasigna `clasifGral` dejando `clasifGastro` (OK) y otro que deja ambas null (error).
- Frontend: verificación manual del flujo de edición (abrir, modificar campos,
  N-a-N, guardar) y de la validación visual "al menos una" en alta y edición.

## Fuera de alcance

- SKU editable en edición (queda solo lectura).
- Cambiar `tipo` a opcional.
- Constraints NOT NULL a nivel DB (se mantiene la validación en aplicación).
- Rollback transaccional entre los múltiples endpoints del guardado de edición.
