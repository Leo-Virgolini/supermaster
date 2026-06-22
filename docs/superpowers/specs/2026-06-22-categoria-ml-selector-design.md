# Selector de categoría de Mercado Libre (elegir entre predicciones)

**Fecha:** 2026-06-22
**Estado:** Diseño aprobado, pendiente de plan.

## Objetivo

Hoy, al publicar un producto en Mercado Libre, la categoría se resuelve automáticamente: `predecirCategoria(titulo)` llama al predictor con `limit=1` y toma la primera predicción, sin que el usuario vea ni elija. Esta feature permite **elegir la categoría ML** entre las predicciones del predictor (top-3) desde el form del producto, y **persistir** la elección para que se use al publicar.

## Decisiones tomadas (brainstorming 2026-06-22)

1. **Dónde se elige y se guarda:** en el **form del producto**, persistida. La categoría queda asociada al producto (editable, reutilizable en re-exportaciones).
2. **Alcance del selector:** solo las **top-3 predicciones** del predictor según el Título ML (no se navega el árbol completo de ML). Si ninguna sirve, el usuario ajusta el título y vuelve a predecir.
3. **Persistencia:** dos columnas en `productos` — `ml_category_id` (el ID que va a ML) y `ml_category_nombre` (denormalizado, solo para mostrar en la UI sin re-llamar a ML).
4. **Disparo:** un **botón explícito "Predecir categorías"** (no auto-predict mientras se tipea), para evitar llamadas en cada tecla.
5. **Fallback:** si el producto no tiene `ml_category_id` guardada, el alta sigue usando el **predictor automático** (la primera, como hoy).

## Alcance

### Incluye
- **Backend:** columna `ml_category_id` + `ml_category_nombre` en `productos`; endpoint `GET /api/ml/predecir-categorias?titulo=...` (top-3); `crearItemEnMl` usa la categoría guardada si existe, sino el predictor automático.
- **Frontend:** botón "Predecir categorías" + dropdown de las 3 opciones junto al Título ML; persistir id+nombre en el producto; mostrar la categoría guardada al editar.

### NO incluye (fuera de alcance)
- Navegar/buscar en el árbol completo de categorías de ML (solo las predicciones del título).
- Cambiar la categoría de publicaciones **existentes** (la API de ML no lo permite; la categoría solo aplica al alta).
- Override de la categoría en el momento de exportar (se usa siempre la guardada en el producto).
- Persistir la categoría en `Mla` (va en `Producto`, porque se elige antes de que exista el MLA).

## Contexto del código existente

- `apis/ml/service/MercadoLibreService.java`:
  - `predecirCategoria(String titulo)` (privado): `GET /sites/MLA/domain_discovery/search?limit=1&q=...` → toma `arr.get(0).category_id`. **Generalizar** para devolver top-N `[{categoryId, categoryName}]`.
  - `crearItemEnMlCore(...)` recibe la categoría por una lambda `Function<String,String> predictor` (hoy `titulo -> predecirCategoria(titulo)`). La arquitectura ya acepta una categoría resuelta.
  - `MlItemPayloadBuilder.construir(producto, categoryId, price, qty, pictureIds)` pone `category_id` en el payload. **No cambia.**
- `apis/ml/controller/MercadoLibreController.java`: endpoints existentes (`costo-envio`, `costo-venta`, `configuracion`). Agregar el GET del predictor.
- `dominio/producto/entity/Producto.java`: tiene `tituloMl`; **no** tiene campo de categoría ML. Agregar `mlCategoryId` (String) + `mlCategoryNombre` (String).
- DTOs de producto (`ProductoDTO`, `ProductoCreateDTO`, `ProductoUpdateDTO`, `ProductoPatchDTO`) + `ProductoMapper`: agregar los dos campos. **Atención:** son records → agregar componentes rompe constructores posicionales en TESTS (`mvnw compile` no lo detecta; correr `mvnw test`).
- Frontend `productos/page.tsx`: estado `tituloMl` e input "Título ML" (~línea 1262). Agregar el botón + dropdown + estado `mlCategoryId`/`mlCategoryNombre`. `productos/types.ts` y `productosService.ts`: agregar los campos y la función `predecirCategoriasMlAPI`.

## Diseño

### Backend — predictor generalizado + endpoint
- Nuevo DTO `PrediccionCategoriaMlDTO(String categoryId, String categoryName)`.
- En `MercadoLibreService`, método público `List<PrediccionCategoriaMlDTO> predecirCategorias(String titulo, int limit)`: `GET /sites/MLA/domain_discovery/search?limit={limit}&q={titulo}` → mapea cada elemento a `{category_id, category_name}` (Jackson 3 `asString`). `predecirCategoria(titulo)` (el privado, para el fallback) se reimplementa sobre éste: `predecirCategorias(titulo, 1)` → primer `categoryId` o null.
- `MercadoLibreController`: `GET /api/ml/predecir-categorias?titulo=...` → `List<PrediccionCategoriaMlDTO>` con `limit=3`. Valida `titulo` no vacío (400 si falta). Mismo `PreAuthorize` que el resto de ML.

### Backend — persistencia + alta
- `Producto`: `@Column("ml_category_id") String mlCategoryId`, `@Column("ml_category_nombre") String mlCategoryNombre`.
- SQL (`src/main/resources/db/`): `ALTER TABLE productos ADD COLUMN ml_category_id VARCHAR(20) NULL, ADD COLUMN ml_category_nombre VARCHAR(255) NULL;`
- DTOs + mapper: agregar los dos campos (MapStruct auto-mapea por nombre).
- `crearItemEnMl`: la lambda de categoría pasa a:
  `titulo -> (producto.getMlCategoryId() != null && !producto.getMlCategoryId().isBlank()) ? producto.getMlCategoryId() : predecirCategoria(titulo)`.
  Así, si hay categoría guardada se usa; si no, el automático.

### Frontend — selector
- Estado nuevo: `mlCategoryId: string | null`, `mlCategoryNombre: string | null`, `prediccionesMl: {categoryId, categoryName}[]`, `cargandoPredicciones: boolean`.
- Junto al input "Título ML": botón "Predecir categorías" (deshabilitado si `tituloMl` vacío). onClick → `predecirCategoriasMlAPI(tituloMl)` → setea `prediccionesMl`. Se muestran como un dropdown/lista; al elegir una, setea `mlCategoryId`+`mlCategoryNombre`.
- Muestra la categoría elegida/guardada (nombre) con opción de limpiarla (volver al automático).
- En `handleCreate`/`handleGuardarEdicion`: incluir `mlCategoryId` y `mlCategoryNombre` en el payload. Al abrir edición: precargar de `producto.mlCategoryId`/`mlCategoryNombre`.
- `types.ts`: `mlCategoryId: string | null`, `mlCategoryNombre: string | null` en los tipos de producto.
- `productosService.ts`: `predecirCategoriasMlAPI(titulo)` → `GET /api/ml/predecir-categorias`.

## Manejo de errores
- Endpoint del predictor: si ML no responde o devuelve vacío → lista vacía (no 500); el front muestra "no se pudieron predecir categorías" / "sin sugerencias".
- Alta ML: si la categoría guardada quedó inválida (raro), ML devolverá error en el alta y se reporta como hoy (`extraerErrorMl`). El fallback al predictor solo aplica cuando NO hay categoría guardada.
- `titulo` vacío en el endpoint → 400.

## Pruebas
- **Backend (núcleo testeable):**
  - Parseo de la respuesta del predictor a top-3 `[{categoryId, categoryName}]` (dado un JSON de ejemplo) — sin red, sobre un método testeable de parseo.
  - `crearItemEnMlCore`: con `producto.mlCategoryId` seteado → se usa esa categoría (la lambda predictor no se llama / se llama con override); sin ella → se usa el predictor. Lambda POJO, sin red.
- **Frontend (red):** la llamada real al predictor y el dropdown se validan en el smoke.

## Archivos afectados (resumen)
**Backend:**
- `apis/ml/dto/PrediccionCategoriaMlDTO.java` (nuevo)
- `apis/ml/service/MercadoLibreService.java` — `predecirCategorias(titulo, limit)`, reusar en `predecirCategoria`, override de categoría en `crearItemEnMl`
- `apis/ml/controller/MercadoLibreController.java` — `GET /predecir-categorias`
- `dominio/producto/entity/Producto.java` — 2 campos
- DTOs de producto + `ProductoMapper`
- `src/main/resources/db/ml-categoria.sql` (nuevo)

**Frontend:**
- `productos/page.tsx` — botón + dropdown + estado + payload + preload
- `productos/types.ts` — 2 campos
- `productos/productosService.ts` — `predecirCategoriasMlAPI`

## Pendiente manual (usuario)
- Aplicar `ml-categoria.sql` (las 2 columnas) antes de arrancar (ddl-auto=validate).
- Smoke: predecir categorías de un título real, elegir una, publicar y verificar que el item queda en esa categoría.
