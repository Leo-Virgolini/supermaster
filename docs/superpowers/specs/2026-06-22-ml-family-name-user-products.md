# ML: adaptar al nuevo modelo User Products (family_name)

**Fecha:** 2026-06-22
**Estado:** Diseño aprobado, pendiente de plan.

## Objetivo

El seller de Mercado Libre ya está **migrado al nuevo modelo "User Products"** (tag `user_product_seller`). En ese modelo, al publicar un ítem (`POST /items`):

- **`family_name` es obligatorio** (campo de nivel raíz, descripción genérica del producto).
- **`title` NO debe enviarse** — ML lo genera automáticamente a partir del `family_name` + atributos.
- **`variations` ya no se envía** en el alta (cada variación sería un User Product distinto).

El código actual manda el modelo **legacy** (con `title`, sin `family_name`), por lo que ML rechaza el alta con un error de `family_name`. Esta feature adapta el payload de alta y de actualización al nuevo modelo, desbloqueando la publicación en ML.

## Decisiones tomadas (brainstorming 2026-06-22)

1. **`family_name` = `tituloMl`** del producto (el "Título ML" que ya se carga en el form). En el nuevo modelo ML genera el `title` a partir del `family_name`, así que el Título ML pasa a cumplir ese rol.
2. **Recorte por categoría:** el `family_name` se recorta a `settings.max_title_length` del **dominio/categoría real** del producto (no un valor fijo). Se obtiene con `GET /categories/{categoryId}` → `settings.max_title_length`. (No es un número global; el histórico de MLA es 60, pero varía por dominio.)
3. **No enviar `title`** en el alta. En la **actualización**, el PUT que hoy cambia el `title` pasa a cambiar el `family_name` (con la misma condición "solo si la publicación no tuvo ventas", que coincide con la regla de ML para editar el family_name).
4. **No enviar `variations`** en el alta (ya no se manda).
5. **Seller migrado, sin detección de tag:** se adapta directo al nuevo modelo. Es el único seller del proyecto y ya está migrado; si en el futuro hubiera uno no migrado, se agregaría la detección del tag `user_product_seller`.
6. **Reutilización con el #4:** el método para consultar `GET /categories/{id}` se comparte con la feature del predictor con herencia completa (#4), que también lee la jerarquía (`path_from_root`) de la categoría.

## Alcance

### Incluye
- **Alta ML** (`MlItemPayloadBuilder` + `crearItemEnMlCore`/`crearItemEnMl`): payload con `family_name` (recortado), sin `title`.
- **Actualización ML** (`actualizarItemEnMl`): el PUT de "título" pasa a `family_name` (recortado), misma condición sin-ventas.
- **Backend ML:** método para obtener de una categoría su `max_title_length` (y el `path_from_root`, que usará el #4).

### NO incluye (fuera de alcance)
- Atributos de ficha técnica por categoría (otros `attributes` requeridos más allá de los actuales: condición, marca, SKU). Si una categoría exige atributos extra, ese es un trabajo aparte.
- Detección del tag `user_product_seller`.
- Cambios en el cálculo/actualización de precio y la detección de variaciones (queda como está).
- Manejo de la migración UPtin de ítems legacy existentes.
- Validación en el form del largo del Título ML (se recorta en el backend; podría sumarse después un aviso en el form).

## Contexto del código existente

- `MlItemPayloadBuilder.construir(producto, categoryId, price, availableQuantity, pictureIds)` (`apis/ml/service/MlItemPayloadBuilder.java`): hoy hace `payload.put("title", p.getTituloMl())` (línea 19) + category_id, price, attributes (ITEM_CONDITION, BRAND, SELLER_SKU), shipping me2, pictures.
- `crearItemEnMlCore` (`MercadoLibreService.java:~1821`): resuelve `categoryId = predictor.apply(producto.getTituloMl())` y llama a `MlItemPayloadBuilder.construir(producto, categoryId, price, 0, pictureIds)`.
- `actualizarItemEnMl` (`MercadoLibreService.java:~1762`): si `soldQty == 0`, el lambda de título hace `PUT /items/{mla}` con `{"title": title}` (hoy `producto.getTituloMl()`).
- `extraerErrorMl` ya captura y reporta el mensaje de error de ML (incluido el de `family_name`).

## Diseño

### Obtener el límite de la categoría
Nuevo método en `MercadoLibreService`, p. ej.:
```java
// Devuelve el max_title_length del dominio/categoría (o un default si no se puede obtener).
int obtenerMaxTitleLength(String categoryId)
```
Implementado sobre `GET /categories/{categoryId}` → `settings.max_title_length`. Parte testeable (parseo del JSON → int) separada de la llamada de red, igual que `parsePredicciones`. Si la consulta falla o no trae el campo, usar un default conservador (**60**). Este endpoint se comparte con el #4 (que además lee `path_from_root`); el plan puede unificarlo en un método `obtenerCategoria(categoryId)` que devuelva ambos datos.

### Recorte del family_name
Helper testeable (estático), p. ej.:
```java
// Recorta el tituloMl al max permitido (sin cortar a la mitad de una palabra si es simple de evitar; recorte duro está OK).
static String construirFamilyName(String tituloMl, int maxLen)
```
Devuelve `tituloMl` recortado a `maxLen` (trim). El resultado nunca es vacío (el alta ya valida que `tituloMl` no sea blank).

### Alta
- `MlItemPayloadBuilder.construir(...)`: reemplazar `payload.put("title", ...)` por `payload.put("family_name", familyName)`. El `familyName` se pasa como parámetro nuevo (el builder no debería conocer el max length; se calcula en el caller).
- `crearItemEnMlCore`/`crearItemEnMl`: tras resolver `categoryId`, calcular `maxLen = obtenerMaxTitleLength(categoryId)` y `familyName = construirFamilyName(producto.getTituloMl(), maxLen)`, y pasarlo al builder.

### Actualización
- En `actualizarItemEnMl`, el lambda que hoy manda `{"title": ...}` pasa a mandar `{"family_name": familyName}`, donde `familyName` se recorta con el `max_title_length` de la categoría del producto. Fuente del `categoryId` en update: `producto.getMlCategoryId()` si está; si no, default 60 (el plan confirmará si conviene consultar la categoría del ítem existente).
- Se mantiene la condición: solo se actualiza si `soldQty == 0` (coincide con "el family_name se edita solo sin ventas").

## Manejo de errores
- Si `GET /categories/{id}` falla → usar `max_title_length = 60` (default) y seguir (no bloquear el alta por no poder leer el límite).
- El error de ML (si lo hubiera por otra causa, p. ej. un atributo de categoría faltante) se sigue reportando vía `extraerErrorMl` con su mensaje.

## Pruebas
- **`construirFamilyName` (unitario):** recorta a `maxLen`; respeta uno más corto; trim; no devuelve vacío.
- **Parseo de `max_title_length` (unitario):** dado el JSON de `GET /categories/{id}`, extrae el int; default 60 si falta.
- **`MlItemPayloadBuilder` (unitario):** el payload tiene `family_name` y **NO** tiene `title`; el resto de los campos intactos.
- **Smoke (usuario):** crear un producto en ML → publica sin el error de `family_name`; el título en ML se genera a partir del family_name.

## Archivos afectados (resumen)
**Backend:**
- `apis/ml/service/MlItemPayloadBuilder.java` — `family_name` en vez de `title` (parámetro nuevo).
- `apis/ml/service/MercadoLibreService.java` — `obtenerMaxTitleLength`/`obtenerCategoria` + `construirFamilyName`; cablear en alta y update.
- Tests: builder, recorte, parseo de max_title_length.

**Frontend:** sin cambios (el Título ML ya existe; el recorte es backend).

## Pendiente de validar en smoke (usuario)
- El alta en ML entra sin error de `family_name`.
- El title que genera ML es coherente con el Título ML cargado.
- Si el Título ML supera el `max_title_length`, se recorta sin romper el alta.
