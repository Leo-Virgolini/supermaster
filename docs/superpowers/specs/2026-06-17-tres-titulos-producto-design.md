# Tres títulos por plataforma (Dux / ML / Nube) — Diseño

**Fecha:** 2026-06-17
**Estado:** Aprobado
**Sub-proyecto A** de la iniciativa "títulos + alta en Tienda Nube". El Sub-proyecto B
(alta de producto en Tienda Nube) se diseñará después, sobre esta base.

## Objetivo

Reemplazar el par de títulos actuales del producto (`descripcion`, `titulo_web`) por **tres
títulos**, uno por plataforma de venta: **Título Dux**, **Título ML** y **Título Nube**. Esto
prepara el terreno para publicar cada producto con un título específico en cada canal.

## Decisiones tomadas (brainstorming)

| Tema | Decisión |
|---|---|
| `descripcion` → | **`titulo_dux`**, sigue **NOT NULL** (título base, obligatorio siempre) |
| `titulo_web` → | **`titulo_nube`**, pasa a **NULL** (opcional por ahora) |
| nuevo | **`titulo_ml`**, **NULL** (opcional; vacío en productos existentes) |
| Datos de `titulo_ml` en productos viejos | quedan **vacíos (NULL)** |
| Búsqueda de texto de productos | **solo por `titulo_dux`** (se quita `titulo_web` de la búsqueda global) |
| Export a DUX | `item` = `titulo_dux`; campo descripción de DUX = `titulo_nube` |
| Obligatoriedad de Nube/ML | **opcionales por ahora**; la regla "obligatorio si se sube a ese canal" se hará al implementar cada canal |

## Contexto del sistema (relevante)

- `Producto` (`dominio/producto/entity/Producto.java`): `descripcion` (col `descripcion`, NOT NULL,
  len 100) y `tituloWeb` (col `titulo_web`, NOT NULL, len 100).
- Impacto del rename mapeado: ~16 archivos backend, ~60 puntos de contacto. **Cuidado:**
  `descripcion` también existe en OTRAS entidades (ConfigAutomatizacion, ReglaDescuento, Rol,
  Permiso, CanalConceptoCuota, ConceptoCalculo) — **esas NO se tocan**.
- Consumidores frontend del título: `productos/` (tabla + modal), `producto-canal-precios/`
  (Monitor), `calculadora-precios/` (simulador usa `producto.descripcion`/`tituloWeb`).
- Búsqueda de texto: `ProductoSpecifications` y `PrecioSpecifications` buscan por
  sku/descripcion/tituloWeb/codExt/mla/mlau.
- Export DUX: `DuxService` mapea `item` ← `descripcion`, campo descripción DUX ← `tituloWeb`.
- Excel (`ExcelServiceImpl`): headers `DESCRIPCION`/`TITULO_WEB`, import y export.
- Schema: `ddl-auto=validate` → cambios de columnas requieren script SQL manual en
  `src/main/resources/db/`.

## Alcance

### Base de datos

Script manual `src/main/resources/db/tres-titulos-producto.sql`:
```sql
ALTER TABLE supermaster.productos
  CHANGE COLUMN descripcion titulo_dux VARCHAR(100) NOT NULL,
  CHANGE COLUMN titulo_web titulo_nube VARCHAR(100) NULL,
  ADD COLUMN titulo_ml VARCHAR(100) NULL AFTER titulo_dux;
```
El `CHANGE COLUMN` preserva los datos existentes. `titulo_ml` arranca NULL en todos.

### Backend

- **Entidad `Producto`**: `descripcion`→`tituloDux` (col `titulo_dux`, NOT NULL); `tituloWeb`→
  `tituloNube` (col `titulo_nube`, nullable); +`tituloMl` (col `titulo_ml`, nullable).
- **DTOs** (renombrar campos + ajustar validaciones):
  - `ProductoDTO`, `ProductoConPreciosDTO`, `ProductoResumenDTO`, `ProductoFilter`: renombrar
    `descripcion`→`tituloDux`, `tituloWeb`→`tituloNube`, +`tituloMl`.
  - `ProductoCreateDTO`: `tituloDux` con `@NotBlank @Size(max=100)`; `tituloNube`, `tituloMl`
    opcionales `@Size(max=100)`.
  - `ProductoUpdateDTO`, `ProductoPatchDTO`: campos renombrados + `tituloMl`, todos opcionales
    (patch).
- **`ProductoMapper`**: actualizar las construcciones de DTO (toDTO, toProductoConPreciosDTO).
- **`ProductoSpecifications` / `PrecioSpecifications`**: la **búsqueda de texto global usa solo
  `tituloDux`** (+ sku/codExt/mla/mlau). Renombrar los métodos de filtro por columna:
  `descripcion(...)`→`tituloDux(...)`, `tituloWeb(...)`→`tituloNube(...)` (filtros individuales se
  conservan, solo renombrados; el filtro por `tituloNube` queda disponible aunque no esté en la
  búsqueda global).
- **`ProductoController` / `PrecioController`**: renombrar los `@RequestParam` de filtro
  (`descripcion`→`tituloDux`, `tituloWeb`→`tituloNube`, +`tituloMl` si se quiere filtrar; mínimo
  renombrar los dos existentes).
- **`ProductoServiceImpl`**: validación en patch, set en update, paso de filtros.
- **`ProductoAuditoriaServiceImpl`**: snapshot (`descripcion`→`tituloDux`, etc.; +`tituloMl`).
- **`ExcelServiceImpl`**: headers de export (`DESCRIPCION`→`TITULO_DUX`, `TITULO_WEB`→`TITULO_NUBE`,
  +`TITULO_ML`); lógica de import (las columnas "PRODUCTO"/"TITULO WEB" se mapean a los nuevos
  campos); ordenamiento por `tituloDux`. Mantener el orden de columnas coherente.
- **`DuxService`**: `item` ← `tituloDux`; campo descripción de DUX ← `tituloNube`.

### Frontend

- **Tipos** (`productos/types.ts` y consumidores): `ProductoDTO` renombrar `descripcion`→
  `tituloDux`, `tituloWeb`→`tituloNube`, +`tituloMl`.
- **Modal crear/editar** (`productos/page.tsx`): tres inputs — **Título Dux** (obligatorio,
  asterisco + validación), **Título ML** y **Título Nube** (opcionales). Renombrar estados
  (`descripcion`→`tituloDux`, `tituloWeb`→`tituloNube`, +`tituloMl`), validación de `validateForm`,
  payloads de create/edit, carga al editar, reset.
- **Tabla** (`productos/columns.tsx`): columna **Título Dux** (principal, editable inline como hoy
  lo era descripción); agregar columnas **Título ML** y **Título Nube** (editables inline).
- **Buscador**: el placeholder/lógica reflejan que se busca por Título Dux.
- **Monitor de precios** (`producto-canal-precios/`) y **simulador** (`calculadora-precios/`):
  actualizar las referencias a `descripcion`/`tituloWeb` del producto → `tituloDux`/`tituloNube`
  (ej. el label `[sku] descripcion` del simulador usa `tituloDux`).

## Testing

- **Backend:** la suite existente debe seguir verde tras el rename (los tests que referencien
  `descripcion`/`tituloWeb` del producto se adaptan a los nuevos nombres). Verificar arranque con
  `ddl-auto=validate` (entidad ↔ columnas nuevas).
- **Frontend:** typecheck + build sin errores; verificación manual del modal (3 títulos, Dux
  obligatorio), la tabla (3 columnas), la búsqueda por Dux, y que el export a DUX use el Título Dux.

## Fuera de alcance (YAGNI)

- Validación condicional "Título Nube/ML obligatorio si se sube a ese canal" (los checkboxes
  Nube/ML son placeholders; se hará al implementar cada canal).
- El alta de producto en Tienda Nube (Sub-proyecto B).
- Migrar/poblar `titulo_ml` o `titulo_nube` con contenido (quedan como están / vacíos).
