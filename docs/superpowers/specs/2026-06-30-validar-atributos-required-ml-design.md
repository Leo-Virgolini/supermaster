# Validar atributos `required` de ML en el modal (bloquear + resaltar) — Diseño

**Fecha:** 2026-06-30
**Estado:** aprobado para plan

## Objetivo

Evitar publicar/actualizar en Mercado Libre con atributos **obligatorios faltantes**: cuando "Sincronizar con Mercado Libre" está tildado y hay atributos de la ficha de la categoría marcados como **`required: true`** sin valor, el modal **bloquea el guardado** y **resalta** esos atributos, en vez de dejar que el backend/ML devuelvan el error después.

## Alcance

**Solo frontend** (`supermaster-frontend/src/app/productos/ProductoFormModal.tsx`). El backend ya valida `faltantesRequeridos` como red de seguridad; esto **adelanta** la validación con feedback claro. **Sin cambios de backend ni de DTOs.**

**Fuera de alcance:** atributos `conditional`/`conditional_required` (no se validan en el front; ML los maneja); defaults automáticos (descartado en favor de esta validación); otras categorías/canales.

## Global Constraints

- Frontend Next.js/React/TS; `npx tsc --noEmit` exit 0.
- Trabajar en `main`. Commit termina con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de `ProductoFormModal.tsx` (hay WIP en el working tree que NO se toca), nunca `-A` ni `.superpowers/`.
- Reutilizar los patrones existentes del modal: `validateForm`/`formErrors`, `inputErrorClassName`, `mlFicha`, `mlAtributosVal`, `setAtributo`/`setNoAplica`, `fichaAttrIds`.

## Contexto (estado actual)

- `MlAtributoDef` ya expone `required: boolean` y `conditional: boolean` (de `getMlCategoriaFichaAPI`).
- `mlAtributosVal: Record<string, ProductoMlAtributo>` con `{ attributeId, valueId, valueName, noAplica }`.
- La ficha se renderiza por secciones → componentes → atributos (`renderMlAtributoInput(d, c)`), con un checkbox "No aplica" por atributo (`setNoAplica`).
- El modal ya tiene validaciones bloqueantes de ML (Título ML, categoría) vía `validateForm` + `formErrors` + `inputErrorClassName`.
- En esta categoría de ejemplo (Vasos y Copas, MLA1594) los `required: true` de la ficha son: **BRAND (Marca)**, **MODEL (Modelo)**, **DRINKING_GLASS_PRODUCT_TYPE (Tipo de producto)**.

## Comportamiento

### Detección de faltantes
- Un atributo de la ficha cuenta como **requerido** si `required === true`.
- Está **satisfecho** si `mlAtributosVal[id]?.valueName` es no vacío **y** `noAplica !== true`.
- **Faltantes** = requeridos no satisfechos.
- Helper derivado (p. ej. `mlRequiredFaltantes()`): recorre `mlFicha.secciones[].componentes[].atributos[]`, filtra `required`, devuelve los faltantes (con `id` y `name`).

### "No aplica" en atributos `required` (no puede coexistir)
- Para los atributos `required`, **no se muestra el checkbox "No aplica"** (en `renderMlAtributoInput`/su fila).
- Si un `required` llega con `noAplica: true` (pre-carga vieja), se **fuerza a `false`** al cargar la ficha (así el input queda **habilitado y editable**; si quedara en `noAplica` estaría grisado y no se podría completar). Mismo patrón/momento que el resto de los efectos sobre `mlAtributosVal` al cargar la ficha (`[mlFicha, fichaAttrIds]`).

### Bloqueo al guardar
- En `validateForm`: si **`subirMl`** está tildado **y** `mlRequiredFaltantes()` no está vacío → setear un error (p. ej. `formErrors.mlRequeridos`) → **no guarda** (igual que las demás validaciones).
- Mensaje con los nombres faltantes, p. ej.: *"Faltan atributos obligatorios de Mercado Libre: Modelo, Tipo de producto"*.
- Solo aplica con ficha/categoría cargada (`mlFicha` presente). Si no hay ficha o `subirMl` está destildado, no valida.

### Resaltado
- En `renderMlAtributoInput`, si el atributo es `required` y está faltante: aplicar **`inputErrorClassName`** (borde rojo) al input/select, y un **asterisco** (`*`) en el label del componente.
- El aviso/lista de faltantes se muestra cerca del bloque de atributos ML (con `formErrors.mlRequeridos`), con el estilo de error ya usado (`text-red-500`).

## Manejo de errores / bordes

- **Marca (BRAND)**: es `required` y se autollena desde la Marca maestra (efecto existente). Si la Marca está vacía → BRAND vacío → se marca como faltante (correcto); se resuelve cargando la Marca, no tipeando en BRAND.
- **Alta vs edición**: en edición, la pre-carga del canal corre primero; la detección de faltantes opera sobre el estado ya cargado. En alta, al elegir la categoría y cargar la ficha, los required vacíos se marcan.
- **Sin subir a ML**: si `subirMl` está destildado, no se valida ni se bloquea (podés guardar el producto sin completar atributos ML).
- **`conditional`**: NO se valida en el front (ej.: UNITS_PER_PACK); queda a cargo de ML.

## Testing

- **`npx tsc --noEmit` exit 0.**
- Verificación manual:
  - Producto con "Sincronizar con Mercado Libre" tildado y **Modelo** vacío → al Guardar, **no guarda**: Modelo en rojo + aviso "Faltan atributos obligatorios de Mercado Libre: Modelo".
  - Completar Modelo → guarda normal.
  - En los atributos `required` **no aparece** el checkbox "No aplica"; en los no-required sigue apareciendo.
  - Un `required` que viniera con "No aplica" de antes → aparece editable (no grisado) y marcado como faltante hasta completarlo.
  - Destildar "Sincronizar con Mercado Libre" → guarda sin validar atributos ML.
