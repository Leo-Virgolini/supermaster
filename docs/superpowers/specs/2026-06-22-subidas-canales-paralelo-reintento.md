# Subidas a canales: paralelo + estado por canal + reintento

**Fecha:** 2026-06-22
**Estado:** Diseño aprobado, pendiente de plan.

## Objetivo

Hoy, al crear/editar un producto, las subidas a los canales (Dux, Tienda Nube, Mercado Libre) se hacen **secuencialmente** y se reportan con **toasts efímeros**. Si fallan varios, los toasts se apilan y se pierden, no queda claro **qué canal y qué dato** falló, y no hay forma de **reintentar** sin rehacer todo. Además, el modal se cierra aunque una subida haya fallado.

Esta feature: ejecuta las subidas **en paralelo**, muestra un **estado por canal** dentro del modal, **mantiene el modal abierto si algo falla** (limpia y cierra solo si todo salió bien), y permite **reintentar los canales que fallaron** sin volver a crear el producto.

## Decisiones tomadas (brainstorming 2026-06-22)

1. **Paralelo:** las 3 subidas independientes (Dux, Nube, ML) se ejecutan con `Promise.allSettled`. **KT HOGAR + KT GASTRO** siguen yendo **en una sola** llamada a Nube (el backend las procesa juntas).
2. **Recálculo de precio una sola vez, antes** de las subidas (lo necesita Nube; ya implementado en #1).
3. **Estado por canal** visible en el modal (no toasts sueltos): cada canal con ✅/❌ y el **motivo exacto** del fallo.
4. **Cierre condicional:** si **todo** salió bien → `resetForm()` + cerrar modal. Si **algo falló** → el modal **queda abierto** con el panel de estado.
5. **Reintento:** un único botón **"Reintentar los que fallaron"** que re-dispara los exports de los canales con error, **sobre el producto ya creado** (no re-crea ni duplica).
6. **El producto queda creado** en supermaster pase lo que pase con los canales; el panel lo deja claro ("el producto se creó; faltó publicar en …").
7. Aplica a **alta y edición**.

## Alcance

### Incluye
- **Frontend (`productos/page.tsx`):** separar "crear/guardar producto" de "exportar a canales"; ejecutar exports en paralelo; estado por canal; panel en el modal; botón "Reintentar los que fallaron"; cierre condicional.

### NO incluye (fuera de alcance)
- Cambios en los endpoints de export del backend (se siguen usando `exportarProductosADuxAPI`, `exportarProductosANubeAPI`, `exportarProductosAMlAPI` tal cual).
- Reintento por canal individual (se eligió un solo botón global para los fallidos).
- Reintentar desde la tabla de productos (fuera de este modal; ya existe el flujo de editar→guardar para re-exportar).
- Cambios en la lógica de qué se sube a cada canal.

## Contexto del código existente

- `handleCreate` (`page.tsx`): hoy hace `await createProducto(...)` → recalcular → `await exportarProductosADuxAPI` → `await exportarProductosANubeAPI` → `await exportarProductosAMlAPI`, cada uno con su `try/catch` que notifica con toast; al final `resetForm()` + cerrar (siempre).
- `handleUpdate` (edición): mismo patrón.
- `exportarProductosADuxAPI(skus)` → `{ productosEnviados, errores }`.
- `exportarProductosANubeAPI(skus, tiendas)` y `exportarProductosAMlAPI(skus)` → `ExportCanalResultDTO { creados, actualizados, yaExistian, errores, advertencias }`.
- `reportarExportToast(plataforma, r)` (`page.tsx:43`): arma el texto del resultado de un canal. Se reutiliza la lógica de armado del detalle.

## Diseño

### Modelo de resultado por canal
Un tipo en el front:
```ts
type ResultadoCanal = {
    canal: "Dux" | "Tienda Nube" | "Mercado Libre";
    estado: "ok" | "error";
    detalle: string; // ej. "subido", o "KT HOGAR: sin precio para esa cuota"
    // qué hace falta para reintentar este canal:
    reintentar: () => Promise<ResultadoCanal>;
};
```
Cada función de subida se envuelve en un wrapper que devuelve `ResultadoCanal` (capturando éxito/fallo + el detalle), de modo que un fallo **no** corte a los demás (`Promise.allSettled` / wrappers que nunca rechazan).

- **Dux:** `error` si la llamada lanza o `productosEnviados === 0`. Detalle = mensaje de error / `errores`.
- **Tienda Nube:** `error` si lanza o `r.errores.length > 0`. Detalle = `r.errores.join("; ")` (ya vienen etiquetados por tienda, ej. "SKU / KT HOGAR: sin precio…"). El reintento re-manda las tiendas pedidas (o, si es simple, todas las marcadas).
- **Mercado Libre:** `error` si lanza o `r.errores.length > 0`. Detalle = `r.errores.join("; ")`.

### Flujo de `handleCreate` (reestructurado)
1. `validateForm()`.
2. Crear producto + asociaciones (`createProducto` con `asociarMargenYRelaciones`).
3. Si hay MLA: `await calcularEnvioMlaAPI` (ya en #1).
4. Si se exporta a Nube: `await recalcularProductoAPI` (ya en #1).
5. **Exportar en paralelo** los canales marcados (Dux, Nube, ML) con `Promise.allSettled`, recolectando `ResultadoCanal[]`.
6. Si **todos** `ok` → `resetForm()` + cerrar modal (+ refetch).
7. Si **alguno** `error` → guardar `ResultadoCanal[]` en estado, **mantener el modal abierto** y mostrar el panel. El producto ya está creado (guardar `creado.id`/sku para el reintento).
8. Si **el paso 2 falla** (no se pudo crear el producto) → comportamiento actual: modal abierto, error (no se llega a exportar).

### Reintento
- Botón **"Reintentar los que fallaron"** (visible solo si hay canales en `error`).
- Al click: ejecuta en paralelo las `reintentar()` de los canales con `error` (sobre el producto ya creado), actualiza el panel. Si tras el reintento **todos** quedan `ok` → `resetForm()` + cerrar; si no, el panel se actualiza con los que siguen fallando.

### Panel de estado (UI en el modal)
- Aparece debajo del formulario / al pie del modal cuando hay resultados con `error`.
- Una fila por canal: ✅/❌ + nombre + detalle del motivo.
- Texto aclaratorio: "El producto se creó correctamente. Faltó publicar en los canales marcados con ❌."
- Botón "Reintentar los que fallaron".
- El usuario puede cerrar el modal manualmente (el producto queda creado; el canal se puede reintentar luego editando el producto).

### Edición (`handleUpdate`)
Mismo patrón: tras guardar los cambios + recalcular, exportar en paralelo, panel + reintento si algo falla, cierre condicional.

## Manejo de errores
- Cada wrapper de canal **captura** su error y lo convierte en `ResultadoCanal{estado:"error", detalle}` — nunca propaga para no cortar a los demás.
- Si la **creación** del producto falla, no se exporta nada (igual que hoy) y el modal queda abierto con el error.
- El reintento es **idempotente**: los exports son upsert (Nube por SKU, ML por MLA, Dux por cod_item), así que reintentar no duplica.

## Pruebas
- El grueso es UI/orquestación (sin tests automáticos en el front del repo); se valida con **typecheck** + smoke.
- Si se extrae una función pura de "clasificar resultado de canal" (`ExportCanalResultDTO → ok|error + detalle`), conviene un test unitario de esa función (errores → error con detalle; sin errores → ok).
- **Smoke (usuario):** crear un producto marcando los 3 canales; forzar un fallo en uno (ej. ML sin family_name, o Nube sin precio) → el modal queda abierto, el panel muestra el canal y el motivo, "Reintentar los que fallaron" re-dispara solo ese; cuando todos quedan ✅, el modal limpia y cierra.

## Archivos afectados (resumen)
**Frontend:**
- `productos/page.tsx` — reestructurar `handleCreate`/`handleUpdate`; estado `resultadosCanal` + `productoCreadoId`/sku; wrappers de canal; panel + botón reintentar; cierre condicional.
- (Opcional) extraer la función pura de clasificación de resultado por canal a un módulo testeable.

**Backend:** sin cambios.

## Pendiente de validar en smoke (usuario)
- Las 3 subidas corren en paralelo (más rápido).
- Si una falla, el modal no se cierra y el panel muestra canal + motivo.
- "Reintentar los que fallaron" sube solo los pendientes, sin duplicar.
- Si todo sale bien (o tras un reintento exitoso), el modal limpia y cierra.
