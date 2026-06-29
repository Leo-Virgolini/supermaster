# Estado de Dux y ML desde el panel (desacoplado de "Activo") — Diseño

**Fecha:** 2026-06-29
**Estado:** aprobado para plan

## Objetivo

Que el estado de publicación de **Dux** (`habilitado` S/N) y de **Mercado Libre** (active/paused) se controle desde el panel **"Estado de publicación"** del modal de producto, en vez de derivarse del checkbox **"Activo"**. Al **crear**, el estado es fijo: Dux `"S"`, ML **pausado**.

## Alcance

- **Frontend:** hacer **editable** la tarjeta **Dux** del panel "Estado de publicación" (hoy es solo-lectura) con un selector **Habilitado / Deshabilitado**. La tarjeta ML ya es editable (Activa/Pausada) y se mantiene. Pasar el `habilitado` elegido al export de Dux.
- **Backend:** dejar **una sola fuente** para el estado de cada canal:
  - **Dux:** el payload del export toma `habilitado` del valor recibido (no de `activo`). En alta, `"S"`.
  - **ML:** el export de **update** deja de setear el status desde `activo`; el status lo aplica el panel (`putEstadoPublicacion` → `updateItemStatus`). El export de **alta** deja la publicación **siempre pausada**.
- **"Activo":** sigue existiendo como flag interno del producto (persistido, sincronizado desde Dux en la importación), pero **ya no** maneja `habilitado` de Dux ni el status de ML.

**Fuera de alcance:** Nube (su visibilidad sigue por el panel sin cambios); agregar estado "Cerrada" para ML; eliminar el campo `activo`.

## Global Constraints

- Backend: Java 25 / Spring Boot 4; `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`). Jackson 3 donde aplique.
- Frontend: Next.js/React/TS; `npx tsc --noEmit` exit 0. Convención de clases (rounded-xl, dark mode, SECTION_TINT).
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos (hay WIP de Dux en el working tree que NO se toca), nunca `-A` ni `.superpowers/`.
- No romper el contrato `/api` ni el frontend existente.

## Estado actual (lo que se reconcilia)

- **Dux export** (`DuxItemBuilder.construir`): `item.put("habilitado", Boolean.TRUE.equals(p.getActivo()) ? "S" : "N")`. El payload de `/item/nuevoItem` es full (siempre incluye `habilitado`), y sirve para alta y update.
- **ML update export** (`MercadoLibreService.actualizarProductoEnMlCore`, ~línea 2038): `estadoTarget = activo ? "active" : "paused"` → `putStatus`.
- **ML alta export**: tras crear, si el producto está inactivo deja la publicación en `paused` (best-effort).
- **Panel-apply** (`ProductoFormModal` ~805-823 → `putEstadoPublicacionAPI` → `EstadoPublicacionService.aplicar`): aplica al guardar **solo lo que cambió**; hoy maneja `ml` (`updateItemStatus`), `hogar`, `gastro` (`actualizarPublished`). **Dux no está**.
- **Panel-leer** (`EstadoPublicacionService.leer`): la tarjeta Dux ya trae `estado` ("habilitado"/"deshabilitado") vía `DuxEstadoParser`.

## Comportamiento

### Dux (vía export)
- El estado de Dux **fluye por el export**, no por `aplicar` (el payload de Dux es full y siempre incluye `habilitado`; mandarlo es idempotente).
- **Alta:** `habilitado: "S"` (fijo).
- **Edición:** el export manda `habilitado` = selección del panel (`Habilitado`→`"S"`, `Deshabilitado`→`"N"`). Se aplica cuando "Sincronizar con Dux" está tildado (si no, no hay update de Dux, igual que hoy).
- `activo` ya no influye en `habilitado`.

### Mercado Libre (alta vía export, status vía panel)
- **Alta:** la publicación queda **siempre pausada** (independiente de `activo`).
- **Edición:** el export de update **no toca** el status (se elimina la línea que lo deriva de `activo`); el status lo cambia el **panel** (`putEstadoPublicacion` → `updateItemStatus`) solo si el usuario lo modificó. Si no lo modifica, queda como esté en ML.

### Panel "Estado de publicación"
- Tarjeta **Dux**: selector `Habilitado / Deshabilitado` (valores internos del estado: `"habilitado"` / `"deshabilitado"`, espejando `DuxEstadoParser`). Al abrir muestra el valor real leído de Dux.
- Tarjeta **ML**: sin cambios (Activa/Pausada).

## Datos

- El estado elegido es **transient** (no se agrega columna). Dux: se pasa como parámetro al endpoint de export (junto al/los SKU). ML: el status va por el flujo de `aplicar` ya existente; la alta fuerza pausado en el backend.
- `activo`: sin cambios de schema; sigue persistido y sincronizado desde Dux.

## Implementación

### Backend
1. **`DuxItemBuilder.construir`**: `habilitado` usa un `@Transient String duxHabilitado` de `Producto` (mismo patrón que `descripcionNube`/`equipamientoGastro`) **si está seteado** ("S"/"N"); **si es null, fallback a** `activo ? "S" : "N"` (preserva el comportamiento del export masivo, que no setea el transient). El flujo del modal SIEMPRE setea `duxHabilitado` (alta: "S"; edición: valor del panel), de modo que `activo` queda ignorado ahí.
2. **Dux export endpoint/servicio** (`exportarProductosADux`): aceptar un `habilitado` opcional para el flujo del modal (un único valor; el modal exporta de a un SKU). Si no se pasa (export masivo desde la lista), no se setea `duxHabilitado` → fallback a `activo`.
3. **ML alta** (`crearItemEnMl` / post-alta): dejar **siempre pausado** (quitar la condición por `activo`). Aplica también al export masivo (las altas masivas quedan pausadas).
4. **ML update** (`actualizarProductoEnMlCore` ~2038): **eliminar** el seteo de `estadoTarget` desde `activo` y su `putStatus`. El status pasa a ser responsabilidad **única** del panel (`updateItemStatus` vía `aplicar`). Consecuencia: el export masivo ya **no** activa/pausa ML según `activo` (las altas quedan pausadas; los updates no tocan el status).

### Frontend
5. **`ProductoFormModal`**: la tarjeta **Dux** del panel pasa a tener un `<select>` (Habilitado/Deshabilitado) que actualiza `estadoCanales.dux.estado` (igual patrón que ML/Nube). 
6. **Export de Dux desde el modal** (`exportarProductosADuxAPI`): pasar el `habilitado` elegido (Habilitado→"S"/Deshabilitado→"N"); en alta, "S".
7. El **panel-apply** (`putEstadoPublicacion`) sigue manejando ML/Nube; **Dux NO** se agrega a `aplicar` (va por el export). Mantener el criterio "solo aplica lo que cambió" para ML/Nube.
8. `activo`: el checkbox queda en el form como hoy; ya no condiciona Dux/ML (no requiere cambios de UI más allá de no depender de él).

### `EstadoPublicacionService.aplicar`
- **No** se toca para Dux (Dux va por el export). ML/Nube sin cambios.

## Impacto en el export masivo (lista de productos)

- **Dux:** sin cambios funcionales — al no recibir `habilitado`, cae al fallback `activo ? "S":"N"` (igual que hoy).
- **ML:** cambia — las **altas** masivas quedan **pausadas** (antes seguían `activo`) y los **updates** masivos **no tocan** el status (antes lo derivaban de `activo`). El status de ML pasa a manejarse solo desde el panel del modal. (Si se quisiera mantener el comportamiento masivo viejo para ML, habría que pasar también el estado al export masivo — fuera de alcance.)

## Manejo de errores / bordes

- Edición sin cambiar el estado en el panel: ML no se toca (export ya no fuerza status); Dux se re-manda igual (idempotente).
- Alta: Dux "S", ML pausado, siempre, sin importar `activo`.
- Si "Sincronizar con Dux" está destildado: no hay update de Dux (el estado Dux del panel no se aplica), igual que hoy.
- Sincronización desde Dux (import): sigue mapeando `activo ← habilitado` (sin cambios); `activo` no vuelve a influir en el export.

## Testing

- **Backend `mvn -o test` verde.**
  - `DuxItemBuilderTest`: `habilitado` toma el valor provisto (no `activo`); alta → "S"; un producto con `activo=false` pero `duxHabilitado="S"` manda "S"; `duxHabilitado="N"` manda "N".
  - ML alta: la publicación queda pausada aunque `activo=true` (test del flujo de alta o del armado, según dónde se decida).
  - ML update: `actualizarProductoEnMlCore` ya **no** llama a `putStatus`/no setea status desde `activo` (ajustar/!verificar el test existente si lo cubre).
  - `EstadoPublicacionServiceTest`: sin cambios (aplicar no toca Dux).
- **Frontend `npx tsc --noEmit` exit 0.** Verificación manual: en edición, cambiar Dux a Deshabilitado y guardar → Dux recibe `habilitado:"N"`; cambiar ML a Pausada → se aplica; alta → Dux "S" y ML pausado.
