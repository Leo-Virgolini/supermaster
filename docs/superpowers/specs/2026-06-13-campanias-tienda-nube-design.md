# Campañas desde categorías de Tienda Nube — Diseño

**Fecha:** 2026-06-13
**Estado:** Aprobado (pendiente revisión final del usuario)

## Objetivo

El cliente arma campañas comerciales (ej. "Día del Padre") creando una **categoría**
en Tienda Nube (TN) y tageando ahí los productos que quiere hacer participar. Hoy ese
trabajo vive solo en TN. Se quiere una **nueva sección en la app** que importe esas
categorías y su tageo a la base de datos, para poder **asignar un precio manual** a cada
producto participante desde la BD.

## Decisiones tomadas (brainstorming)

| Tema | Decisión |
|---|---|
| Fuente de verdad de categorías/tageo | **Tienda Nube**. La app importa (read-only desde TN). No escribe categorías ni tageo en TN. |
| Tipo de precio | **Precio fijo manual** (override) por producto+campaña. |
| Publicación a TN del precio | **No por ahora.** Solo se guarda en la BD. (Publicar a TN queda fuera de alcance.) |
| Tienda | Solo **KT HOGAR** → canal de precios `NUBE`. KT GASTRO queda para después. |
| Disparo de sincronización | **Botón manual** "Sincronizar". Sin jobs automáticos. |
| Metadata de campaña | `fecha_desde`, `fecha_hasta`, `activa`. |
| Modelo de datos | **Dos tablas nuevas** (Enfoque A). No columna en `productos`, no reuso de `PrecioInflado`. |

## Contexto del sistema existente (relevante)

- **`TiendaNubeService`** (`apis/nube/service/TiendaNubeService.java`) ya consume la API de TN
  (tiendas KT HOGAR y KT GASTRO), con `NubeRetryHandler` (retry + rate limit). Ya lista
  productos vía `/products?per_page=200`. **No persiste categorías hoy.**
- Modelo de precios: `Producto` → `ProductoCanalPrecio` (PVP calculado por canal+cuotas);
  `PrecioInflado` + `ProductoCanalPrecioInflado` (promociones con vigencia). Canal `NUBE` ya existe.
- **Frontend** Next.js 16: patrón de sección `types.ts` / `service.ts` / `useX.ts` /
  `columns.tsx` / `page.tsx` + registro en `navigationConfig.tsx`. Tabla con `EditableCell`.
- **Schema:** `ddl-auto=validate` → los cambios de schema requieren script SQL manual en
  `src/main/resources/db/` (se corre a mano antes de levantar).

## API de categorías de Tienda Nube

(`https://tiendanube.github.io/api-documentation/resources/category`)

- `GET /categories` lista categorías. Campos: `id`, `name` (multilingüe, ej.
  `{"es": "Día del Padre"}`), `handle`, `parent`, `subcategories`, `visibility`, timestamps.
- **Las categorías NO devuelven sus productos.** Es el **producto** el que referencia
  categorías (`product.categories = [ids]`).
- Mapeo categoría → productos: se arma del lado de la app trayendo los productos
  (`GET /products`), leyendo su array `categories`, y agrupando por id de categoría.
- Se toma `name.es` como nombre de la campaña; `id` como `tn_categoria_id`.

## Modelo de datos (Enfoque A)

Dos tablas nuevas en el schema `supermaster`.

### `campania`
Una fila por categoría de TN importada.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | PK local | |
| `tn_categoria_id` | bigint | id de la categoría en TN. Clave de reconciliación (único). |
| `nombre` | varchar | importado de TN (`name.es`). |
| `id_canal` | FK → `canales` | fijo en `NUBE` por ahora. |
| `fecha_desde` | date null | vigencia, manual. |
| `fecha_hasta` | date null | vigencia, manual. |
| `activa` | boolean | default `false` al importar. |
| `fecha_ultima_sync` | datetime null | |
| `observaciones` | varchar null | |

### `campania_producto`
Join producto↔campaña con el precio override.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | PK local | |
| `id_campania` | FK → `campania` | |
| `id_producto` | FK → `productos` | matcheado por SKU al sincronizar. |
| `precio_manual` | decimal null | null hasta que se carga. |
| `fecha_sync` | datetime null | |
| `observaciones` | varchar null | |

Unicidad: par (`id_campania`, `id_producto`).

### Por qué tablas y no columna
- Un producto puede pertenecer a varias campañas a la vez → relación N:M.
- El precio override es propio del par producto+campaña, no del producto.
- Aísla la feature del modelo de `Producto` (ya grande) y es extensible (publicar a TN,
  % descuento, segunda tienda) sin tocar `productos`.

### Alternativas descartadas
- **B — reusar `PrecioInflado`/`ProductoCanalPrecioInflado`:** `PrecioInflado` tiene un único
  `valor` por código (mismo precio para todos los productos), y "inflado" es conceptualmente
  lo opuesto (sube precios para cupones). Forzarlo mezcla dos cosas distintas.
- **C — columna(s) en `productos`:** no soporta N campañas ni un precio por par.

## Backend

Nuevo dominio `dominio/campania/` siguiendo el patrón existente:

- **Entidades JPA** `Campania`, `CampaniaProducto` (relaciones `@ManyToOne` a `Canal` / `Producto`).
- **Repositorios** `CampaniaRepository`, `CampaniaProductoRepository`.
- **`CampaniaService`**: CRUD de campañas, edición de `precio_manual` por producto, y
  `sincronizarDesdeTiendaNube()`.
- **`CampaniaController`** REST en `/api/campanias`: lista paginada, detalle con productos,
  PATCH precio, POST `/sincronizar`.
- **Extensión de `TiendaNubeService`** (única parte que toca código existente): agregar
  método de lectura `GET /{storeId}/categories` y armar el mapeo categoría→SKUs a partir
  de los productos ya listados. No se reescribe lo existente.
- **Schema:** script SQL manual `src/main/resources/db/crear-tablas-campania.sql` con los
  `CREATE TABLE`. Se corre a mano antes de levantar (flujo habitual de cambios de schema).

## Flujo de sincronización (botón "Sincronizar")

Al apretar el botón, el backend (solo KT HOGAR):

1. `GET /categories` → **upsert** en `campania` por `tn_categoria_id` (crea nuevas,
   actualiza `nombre`). Campañas nuevas: `activa = false`, sin fechas.
2. `GET /products` → por cada producto y cada id en su `categories`, arma pares (categoría, SKU).
3. **Reconciliación** de `campania_producto` por campaña: agrega productos nuevos
   (match por SKU contra `productos`), quita los que ya no están tageados en TN.
   **Preserva `precio_manual`** de los productos que siguen en la campaña.
4. Devuelve **resumen**: `X categorías importadas, Y productos vinculados, Z SKUs sin match`.

### Puntos de diseño confirmados
- **Producto destageado en TN:** en el próximo sync se quita de la campaña y **se pierde su
  `precio_manual`** (TN es la fuente). Aceptado.
- **SKU sin match en la BD:** se reporta en el resumen y se omite (no rompe el sync). Aceptado.

## Frontend

Nueva sección `src/app/campanias/` (patrón estándar) + entrada en `navigationConfig.tsx`
bajo **Integraciones**.

- **Lista de campañas:** tabla con nombre, vigencia (`desde`/`hasta`), `activa`, nº de
  productos, última sync. Botón **"Sincronizar"** que muestra el resumen X/Y/Z al terminar.
  `fecha_desde`, `fecha_hasta`, `activa` editables.
- **Detalle de campaña:** tabla de sus productos (SKU, descripción, costo/PVP de referencia,
  `precio_manual` **editable inline**), reusando `Table` + `EditableCell`. Permisos vía
  `hasPermiso()`.

## Errores y testing

- **Errores:** las llamadas a TN pasan por `NubeRetryHandler` (retry + rate limit). Si el
  sync falla, no se aplica un estado parcial inconsistente; se devuelve error claro al front.
  SKUs sin match → en el resumen, no rompen.
- **Testing:** tests de `CampaniaService.sincronizarDesdeTiendaNube()` con `TiendaNubeService`
  mockeado (categoría nueva; producto nuevo; producto destageado quita la fila;
  precio_manual preservado en productos que siguen; SKU sin match reportado), tests del CRUD
  y del matching por SKU.

## Fuera de alcance (YAGNI por ahora)

- Publicar el precio a Tienda Nube.
- KT GASTRO / segunda tienda.
- Descuento por % (solo precio fijo).
- Sincronización automática periódica.
- Crear/editar categorías o tageo desde la app hacia TN.
