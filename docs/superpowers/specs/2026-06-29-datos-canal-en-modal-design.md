# Datos de canal en el modal (externalizar de la BD) — Diseño

**Fecha:** 2026-06-29
**Estado:** aprobado para plan

## Objetivo

Dejar de duplicar en la BD del sistema datos que ya viven en los canales (Mercado Libre / Tienda Nube) y, en cambio, **leerlos del canal al abrir el modal de producto** y **escribirlos al publicar**. Concretamente se externalizan: **categoría ML**, **atributos de ficha técnica ML** y **descripción** (que pasa a ser una **por canal**: ML, Nube KT HOGAR, Nube KT GASTRO). Además se mejora el panel "Estado de publicación": se suma **Dux**, y se quitan los datos redundantes con los inputs (peso y dimensiones), dejando estado + precio + stock.

## Motivación

Hoy `descripcion`, `ml_category_id`/`ml_category_nombre` y la tabla `producto_ml_atributo` se persisten y, al sincronizar, los builders los leen de la BD. Eso duplica información que el canal ya posee y puede quedar desactualizada. El canal pasa a ser la **fuente de verdad** de estos campos.

## Alcance

**Dentro:**
- Backend: borrar de la BD `productos.descripcion`, `productos.ml_category_id`, `productos.ml_category_nombre` y la tabla `producto_ml_atributo`; quitar esos campos de la entidad `Producto`, DTOs, mapper y validaciones; eliminar `ProductoMlAtributo` (entidad/repo) y `reemplazarMlAtributos`.
- Backend lectura: ampliar el endpoint de estado de publicación para que, en un solo viaje y en paralelo, devuelva **estado** (ML/Hogar/Gastro **+ Dux**) y los **campos editables** del canal (categoría ML, valores de atributos ML, descripción por canal). Nuevo `GET /items/{id}/description` en ML. Nuevo endpoint para "componer descripción sugerida".
- Backend escritura: los builders ML/Nube dejan de leer esos datos del `Producto` y los reciben del request de export; ampliar el request de export de ML y de Nube con los campos transitorios.
- Frontend: 3 inputs de descripción (ML/Hogar/Gastro) con botón "Componer descripción sugerida"; pre-carga de categoría/atributos/descripciones desde el canal al abrir; panel con estado+precio+stock y tarjeta Dux; el guardado manda los transitorios al export.

**Fuera:**
- Traer peso/dimensiones desde el canal a sus inputs (siguen cargándose como hoy, locales).
- Publicar **en lote** productos nuevos con categoría/atributos/descripción (la primera publicación es siempre desde el modal).
- Arreglar la **vinculación de MLA** que puede hacer figurar un producto como "Activa" sin esperarlo (problema de datos, se trata aparte).
- Selector para activar/inactivar Dux (la tarjeta Dux es **solo lectura**).
- SEO de Nube (ya hoy no se persiste; sin cambios).

## Global Constraints

- Backend: Spring Boot 4, Java 25, Maven, JPA con `ddl-auto=validate` (dev/prod); los cambios de schema requieren script SQL manual en `src/main/resources/db/`. Tests con `ddl-auto=none`. Correr tests con `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`).
- Frontend: Next.js/React/TS; `npx tsc --noEmit` exit 0, sin errores `error` de lint nuevos. No hay tests automáticos de frontend.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos, nunca `-A` ni `.superpowers/`.
- Paquete base backend: `ar.com.leo.super_master_backend`.
- Las API keys / credenciales no van a la BD ni a properties (se cargan de `secrets/`).

## Modelo de datos (qué se elimina)

Se dejan de persistir; el canal es la fuente de verdad:

| Dato | Hoy | Después |
|---|---|---|
| `productos.descripcion` | 1 columna TEXT, compartida ML+Nube | **borrada**; 3 descripciones por canal, solo en el modal |
| `productos.ml_category_id` / `ml_category_nombre` | 2 columnas | **borradas**; categoría leída del ítem ML |
| `producto_ml_atributo` (+ `ProductoMlAtributo`) | tabla 1‑N | **borrada**; atributos leídos del ítem ML |

Estos datos **nunca se guardan**: viven en el modal (cargados del canal si está publicado, o del predictor/ficha técnica si es nuevo) y se mandan **al publicar**. Si se cierra el modal sin publicar, se pierden.

**Migración destructiva de una vía.** Para ítems ya publicados el dato persiste en el canal; para no publicados se pierde lo local. Se corre asumiendo eso.

## Lectura al abrir el modal (un solo viaje)

El modal ya hace `GET /api/productos/{id}/estado-publicacion` (lee ML y Nube). Para no duplicar llamadas a los canales, ese endpoint se **amplía** para devolver, además del estado, los **campos editables**:

- Estado por canal (lo de hoy) **+ Dux** (nuevo, vía `DuxService.obtenerProductoPorCodigo(cod_item)` → `habilitado` S/N → Habilitado/Deshabilitado/No publicado).
- `categoryId` ML (de `category_id` del ítem) y los **valores de atributos** (de `attributes[]`: `id`/`value_id`/`value_name`).
- `descripcionMl` (nuevo `GET /items/{id}/description` → `plain_text`).
- `descripcionHogar` y `descripcionGastro` (`description.es` de cada tienda Nube, vía `buscarProductoPorSku`).

El frontend pre-llena los inputs. La **ficha técnica** (form de atributos) se sigue armando desde `technical_specs` de la categoría (como hoy) y se **pre-carga** con los valores recibidos. Para un producto nuevo (sin MLA) la categoría sale del predictor y los atributos arrancan vacíos.

**Resiliencia:** si un canal falla o está lento, su bloque queda igualmente editable; la lectura nunca bloquea el guardado. Cada canal reporta su error de forma independiente (como ya hace el panel).

## Escritura al publicar

El export **desde el modal** (single SKU) lleva los datos transitorios en el request:

- ML: `categoryId` (para alta), lista de atributos, `descripcionMl`.
- Nube: `descripcionHogar`, `descripcionGastro` (cada tienda su descripción).

Los builders dejan de leer del `Producto`:
- `MlItemPayloadBuilder`: ya recibe `categoryId` por parámetro; ahora también recibe la lista de atributos y la descripción ya compuesta.
- `MlDescripcionBuilder` / `NubeDescripcionBuilder` pasan a **passthrough**: mandan la descripción tal cual la del campo (sin volver a anexar el bloque de características, para no duplicar). `NubeDescripcionBuilder` conserva el mínimo formateo necesario (saltos de línea → `<br>`, escape) solo si hace falta para que Nube renderice; ML va en texto plano.

**Export en lote (lista, varios SKUs):** ya no manda categoría/atributos/descripción (no los tiene). Queda para re-sincronizar precio/stock/estado/imágenes. El alta de un ítem nuevo en ML que requiera atributos obligatorios solo es posible desde el modal.

### Componer descripción sugerida

Para no perder la comodidad del armado automático, un endpoint nuevo (p.ej. `GET /api/productos/{id}/descripcion-sugerida`) devuelve la descripción **compuesta** (descripción base + características: material, aptos, marca, SKU) reusando la lógica actual de composición, por canal (ML texto plano / Nube HTML). El botón "Componer descripción sugerida" del modal pre-llena el campo correspondiente; el usuario la edita y al publicar se manda verbatim.

## Panel "Estado de publicación"

- Cada tarjeta (ML / Hogar / Gastro) muestra **estado + precio + stock**; se quitan **peso** y **dimensiones** (redundantes con sus inputs).
- Nueva tarjeta **Dux**: **Habilitado / Deshabilitado / No publicado** (sin precio/stock). **Solo lectura** (sin selector de estado).
- El precio y el stock siguen viniendo reales del canal (no se editan acá).

## Data flow

1. Abrir modal de producto → `GET /api/productos/{id}/estado-publicacion` (ampliado) lee ML, Nube (x2) y Dux en paralelo y devuelve estado + campos editables.
2. El modal pre-llena categoría/atributos/descripciones y pinta el panel (estado+precio+stock, +Dux).
3. El usuario edita; opcional: "Componer descripción sugerida" pre-llena un campo de descripción.
4. Guardar: se persiste el producto (sin los campos externalizados) y, por cada canal tildado, se llama al export **con los transitorios** (categoría/atributos/descripción).
5. Re-sincronización en lote desde la lista: solo precio/stock/estado/imágenes.

## Manejo de errores

- Canal caído/lento al leer: el bloque editable queda vacío pero usable; el panel marca error en esa tarjeta; no bloquea guardar.
- ML sin MLA / Nube sin producto / Dux sin ítem: "No publicado"; los campos editables arrancan vacíos (alta).
- `GET /items/{id}/description` inexistente o 404: descripción ML vacía, editable.
- 401 → `esSesionExpirada` suprime el toast (como hoy).

## Testing

- Backend `mvn -o test` verde. Ajustar tests que construyen `ProductoDTO`/`ProductoCreateDTO`/`UpdateDTO`/`PatchDTO` por **constructor posicional** de records (al quitar componentes se rompen — ver memoria de records). Nuevos tests: parser de descripción ML, parser de estado Dux, builders en modo passthrough, endpoint de descripción sugerida.
- Frontend: `tsc --noEmit` exit 0; verificación manual del modal (pre-carga, 3 descripciones, botón sugerir, panel con Dux).

## Sub-planes sugeridos

Una sola spec, dividida en planes:
1. **Backend lectura + estado + Dux**: ampliar endpoint de estado, `GET /items/{id}/description`, parser Dux, tarjeta de datos editables.
2. **Backend escritura + migración**: borrar columnas/tabla y entidad/DTOs/mapper/validaciones; builders passthrough; ampliar request de export; endpoint descripción sugerida; SQL.
3. **Frontend**: 3 descripciones + botón sugerir; pre-carga desde canal; panel estado+precio+stock + Dux; envío de transitorios al export.
