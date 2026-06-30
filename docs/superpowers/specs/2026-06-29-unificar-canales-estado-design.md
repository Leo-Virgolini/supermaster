# Unificar "Estado de publicación" + "Canales de venta" en una sola sección — Diseño

**Fecha:** 2026-06-29
**Estado:** aprobado para plan

## Objetivo

Unir las dos secciones del modal de producto que hoy están separadas pero son **por-canal y sobre los mismos 4 canales** (Dux, KT HOGAR, KT GASTRO, Mercado Libre) en **una sola sección con una tarjeta por canal**, que combine: el checkbox "Sincronizar", el estado de publicación, la cuota del precio y el precio/stock de referencia.

## Alcance

**Solo frontend** (`supermaster-frontend/src/app/productos/ProductoFormModal.tsx`). Se reemplazan las dos secciones (`Estado de publicación` y `Canales de venta`) por una sola. Se **reutiliza la lógica existente** (estados `estadoCanales`/`cargandoEstado`, `renderEstadoCanal`, checkboxes `subirADux`/`subirKtHogar`/`subirKtGastro`/`subirMl`, selects de cuota, `mlaVerif`, etc.). **El backend NO cambia**, ni el flujo de guardado/export/aplicar, ni los DTOs.

**Fuera de alcance:** cambiar cómo se calcula/aplica el estado o el precio; tocar backend; cambiar el orden de export; agregar/quitar canales.

## Global Constraints

- Frontend Next.js/React/TS; `npx tsc --noEmit` exit 0.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de `ProductoFormModal.tsx` (hay WIP en el working tree que NO se toca), nunca `-A` ni `.superpowers/`.
- No romper el comportamiento actual: el estado se lee en vivo del canal y "se aplica al guardar" solo lo que cambió; la cuota decide el precio a publicar; el checkbox "Sincronizar" decide qué se exporta.

## Estado actual (lo que se unifica)

- **Sección "Estado de publicación"**: subtítulo "Estado real de cada publicación (se aplica al guardar)". 4 tarjetas vía `renderEstadoCanal(label, canalState, control, icon, estadoParaIcono)`:
  - **Dux**: `<select>` Habilitado/Deshabilitado (`estadoCanales.dux.estado` = "habilitado"/"deshabilitado").
  - **KT HOGAR / KT GASTRO**: `<select>` Visible/Oculta (`estado` = "visible"/"oculta") + muestra **Precio**.
  - **Mercado Libre**: `<select>` Activa/Pausada (`estado` = "active"/"paused") + **Precio** + **Stock**.
  - Mientras carga: "Leyendo estado…" (`cargandoEstado`).
- **Sección "Canales de venta"**: subtítulo "Dónde publicar/subir el producto". 4 checkboxes (`subirADux`, `subirKtHogar`, `subirKtGastro`, `subirMl`), y para HOGAR/GASTRO/ML un select de **cuota del precio**. Dux no tiene cuota.

## Diseño de la sección unificada

Una sección (una `<fieldset>`) titulada **"Canales de venta"**, subtítulo combinado (p. ej. *"Dónde publicar el producto y su estado en cada canal (se aplica al guardar)."*). Dentro, **una tarjeta por canal**, en el orden **Dux → KT HOGAR → KT GASTRO → Mercado Libre**.

Estructura de cada tarjeta:
1. **Encabezado**: el checkbox `☑ Sincronizar con [canal]` (con su icono y el tooltip ⓘ que ya exista).
2. **Cuerpo** (estado + cuota + precio/stock), que se **deshabilita/grisa cuando el checkbox está destildado**:
   - **Estado**: el `<select>` que hoy vive en "Estado de publicación" (mismos valores y `onChange` por canal). Mientras `cargandoEstado` → "Leyendo estado…".
   - **Cuota del precio**: el `<select>` de cuota actual (HOGAR/GASTRO/ML). Dux no tiene.
   - **Precio / Stock**: lo que muestra hoy el panel (Precio en HOGAR/GASTRO/ML; Stock solo ML). Se muestran como **referencia** (lectura) y **permanecen visibles aunque el canal esté destildado** (solo se grisa el resto).

Detalle por canal:
- **Dux**: encabezado + Estado (Habilitado/Deshabilitado). Sin cuota, sin precio/stock.
- **KT HOGAR / KT GASTRO**: encabezado + Estado (Visible/Oculta) + Cuota + Precio.
- **Mercado Libre**: encabezado + Estado (Activa/Pausada) + Cuota + Precio + Stock. (Si ya se muestra `mlaVerif` u otro aviso de ML cerca, se mantiene donde está; no se mueve a esta tarjeta.)

## Comportamiento (se conserva)

- **Lectura del estado**: igual que hoy (`getEstadoPublicacionAPI` en edición; en alta `estadoCanales` es null → la tarjeta muestra solo Sincronizar + Cuota, sin estado/precio).
- **Aplicar al guardar**: sin cambios (el mismo bloque que arma `EstadoPublicacionUpdate` y llama `putEstadoPublicacionAPI`, y el export por checkbox).
- **Destildado → grisar**: estado y cuota quedan deshabilitados (atributo `disabled` + estilo gris) cuando el checkbox del canal está en `false`; el precio/stock/estado-real se siguen mostrando como referencia.
- **Cuota / export / precios**: sin cambios de semántica; solo se reubican los controles.

## Manejo de errores / bordes

- **Alta** (producto nuevo): `estadoCanales` null y `cargandoEstado` false → la tarjeta no muestra estado ni precio; solo Sincronizar (+ Cuota donde aplica). El estado queda fijo por el alta (no editable), como hoy.
- **Cargando estado** (edición): muestra "Leyendo estado…" en el lugar del `<select>` de estado, igual que hoy.
- **Canal sin publicación** (p. ej. ML "no publicado"): el `<select>` de estado se muestra con su default; "se aplica al guardar" sigue ignorando los no publicados (lógica de backend intacta).

## Testing

- **Frontend `npx tsc --noEmit` exit 0.**
- Verificación manual:
  - En edición de un producto publicado: aparece **una** sección con 4 tarjetas; cada una muestra Sincronizar + Estado + (Cuota) + (Precio/Stock).
  - Destildar "Sincronizar [canal]" → estado y cuota de esa tarjeta quedan **grisados**; el precio/estado real siguen visibles.
  - Cambiar un estado y guardar → se aplica igual que antes (mismo toast de "Estado de publicación").
  - En alta: las tarjetas muestran solo Sincronizar (+ Cuota), sin estado/precio.
  - No quedan referencias a las dos secciones viejas por separado.
