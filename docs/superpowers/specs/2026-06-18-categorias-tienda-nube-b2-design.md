# Categorías en el alta de Tienda Nube — Fase B2 — Diseño

**Fecha:** 2026-06-18
**Estado:** Aprobado
**Sub-proyecto B, Fase B2.** Construye sobre la Fase B1 (alta básica): agrega categorías al payload del alta.

## Objetivo

Al dar de alta un producto en Tienda Nube (KT HOGAR / KT GASTRO), asignarlo a la jerarquía de
categorías que corresponde a su clasificación y tipo, **creando en TN las categorías que falten**.
B1 daba de alta el producto sin categorías; B2 agrega el campo `categories` al payload, resolviendo
(y creando si hace falta) el árbol de categorías de la tienda.

## Decisiones tomadas (brainstorming)

| Tema | Decisión |
|---|---|
| Origen de la ruta | **KT HOGAR → clasif gral + tipo** · **KT GASTRO → clasif gastro + tipo**. El tipo cuelga debajo de la hoja de la clasif. |
| Categorías faltantes | **find-or-create**: por cada nivel de la ruta, buscar en TN por nombre + padre; si no existe, crearlo (`POST /categories`) y seguir. |
| Profundidad | **Ruta completa** de cada jerarquía: todos los niveles de la clasif (desde la raíz hasta el nodo asignado al producto) y luego todos los del tipo, anidados. |
| Asignación al producto | El producto se asigna a **todas** las categorías de la ruta (cada nivel), no solo a la hoja. |
| Datos faltantes | Clasif del canal **y** tipo son **obligatorios**: si falta cualquiera de los dos → error para ese SKU/tienda, no se sube. |

### Ejemplo

Producto con `clasifGral = "Cocina › Ollas"` y `tipo = "Acero › Inoxidable"`, subido a KT HOGAR:
- Ruta de nombres (raíz→hoja): `["Cocina", "Ollas", "Acero", "Inoxidable"]`.
- En TN se busca/crea el árbol anidado: `Cocina` (raíz) → `Ollas` (hijo de Cocina) → `Acero`
  (hijo de Ollas) → `Inoxidable` (hijo de Acero).
- El producto se asigna a las **4** categorías: `categories: [idCocina, idOllas, idAcero, idInoxidable]`.

El mismo tipo (`Acero › Inoxidable`) bajo otra clasif genera categorías TN distintas (distinto padre).
Es consecuencia esperada de la "ruta completa anidada".

## Contexto del sistema (verificado)

- **Jerarquías** (`ClasifGral`, `ClasifGastro`, `Tipo`): self-reference padre→hijos. Campos:
  `id`, `nombre`, `padre` (ManyToOne, `null` en la raíz), `subclasificaciones`/`subTipos`.
  No hay `getNivel()` ni ancestro prefabricado; se recorre `getPadre()` recursivamente. Existe
  `buildNombreCompleto()` en cada mapper que arma `"RAÍZ > ... > HOJA"`.
- **Producto** referencia las tres: `getClasifGral()`, `getClasifGastro()`, `getTipo()` (ManyToOne LAZY).
- **TN categorías** (`TiendaNubeService`): hoy solo lectura — `listarCategorias(storeName)` →
  `Map<Long,String>` (id→nombre), `mapearCategoriasASkus(storeName)` → `Map<Long,List<String>>`.
  **No traen `parent`** y **no hay creación** (`POST /categories`) ni asignación de categorías en el alta.
- **API TN**: `GET /categories?fields=id,name,parent` (parent = id del padre, `0`/ausente si raíz).
  `POST /categories` con `{ "name": {"es": "..."}, "parent": <idPadre o ausente si raíz> }` → devuelve
  la categoría creada con su `id`. `POST /products` acepta `categories: [ids]` (los ids deben existir
  previamente). `name` es multilingüe (`{es:...}`).
- **`NubeRetryHandler`**: ya tiene `postJson(uri, accessToken, jsonBody)` (creado en B1). Se reutiliza
  para `POST /categories`.
- **`NubeProductoPayloadBuilder.construir(producto, pvp, pvpInflado)`**: arma el payload del producto
  (B1) sin `categories`.
- **`NubeExportService.exportar(...)`**: itera productos × tiendas y llama a `crearProductoEnNube`.

## Alcance (Fase B2 — todo backend; el frontend de B1 no cambia)

### 1. Helper de ruta de nombres

Dado un nodo de jerarquía (`ClasifGral`/`ClasifGastro`/`Tipo`), recorrer `getPadre()` hasta la raíz y
devolver `List<String>` ordenada **raíz→hoja** de los `nombre`. Reutilizable para las tres entidades
(reciben el nodo hoja y el accessor de padre). La ruta de categorías completa de un producto+tienda =
nombres de la clasif del canal **seguidos de** los nombres del tipo.

### 2. Árbol de categorías cacheado (`NubeCategoriaArbol`)

Estructura en memoria que mapea `(parentId, nombreNormalizado) → categoriaId` (nombre normalizado =
`trim().toLowerCase()`, comparación **case-insensitive**, consistente con el match de imágenes por SKU).
`parentId = null` (o `0`) para las raíces. Métodos:
- `Long buscarHijo(Long parentId, String nombre)` → id o `null`.
- `void registrar(Long id, Long parentId, String nombre)` → agrega una categoría (al cargar el árbol
  inicial y al crear nuevas).

### 3. Carga del árbol (`TiendaNubeService`)

Nuevo método `NubeCategoriaArbol cargarArbolCategorias(String storeName)`:
- `GET /{storeId}/categories?per_page=200&fields=id,name,parent` con la misma paginación por header
  `Link` que `listarCategorias`.
- Por cada categoría: resolver `name` (i18n español, con el `extraerNombreProducto` existente), leer
  `parent` (`0`/ausente → `null`), y `registrar(id, parent, nombre)`.
- Se carga **una vez por tienda** durante una corrida de exportación.

### 4. Resolver (`NubeCategoriaResolver`, testeable sin red)

`List<Long> resolver(NubeCategoriaArbol arbol, List<String> rutaNombres, BiFunction<Long,String,Long> creador)`
donde `creador.apply(parentId, nombre)` ejecuta el `POST /categories` y devuelve el id nuevo
(en producción lo provee `TiendaNubeService`; en tests, una lambda que simula ids incrementales).

Lógica:
- `parentId = null` al inicio (raíz). `ids = []`.
- Por cada `nombre` de la ruta:
  - `id = arbol.buscarHijo(parentId, nombre)`.
  - Si `id == null` → `id = creador.apply(parentId, nombre)`; `arbol.registrar(id, parentId, nombre)`.
  - `ids.add(id)`; `parentId = id` (el siguiente nivel cuelga de este).
- Devuelve `ids` (toda la ruta, en orden raíz→hoja).

### 5. Creación en `TiendaNubeService`

Método que arma el body `{ "name": {"es": nombre}, "parent": parentId (omitido si null) }`, llama
`postJson("/{storeId}/categories", token, body)` y devuelve el `id` de la respuesta. Es el `creador`
que se pasa al resolver.

### 6. Validación y armado en el alta (`crearProductoEnNube` / `crearProductoEnNubeCore`)

Antes de armar el payload, además de las validaciones de B1 (sin título → error; sin precio → error):
- Determinar la clasif del canal: KT HOGAR → `producto.getClasifGral()`, KT GASTRO →
  `producto.getClasifGastro()`. Tipo → `producto.getTipo()`.
- Si la clasif del canal es `null` **o** el tipo es `null` → `ResultadoAltaNube.error("falta clasif/tipo
  para categorizar")`, no postea.
- Construir `rutaNombres` (clasif raíz→hoja + tipo raíz→hoja), resolver a `categoriaIds` (find-or-create
  con el árbol de la tienda), y pasar `categoriaIds` al payload.

### 7. Payload (`NubeProductoPayloadBuilder`)

Nueva firma `construir(producto, pvp, pvpInflado, List<Long> categoriaIds)`: si `categoriaIds` es no
nulo y no vacío, agrega `"categories": [ids]` al mapa del producto; si es `null`/vacío, omite la clave.
(En B2 siempre habrá ids, porque clasif+tipo son obligatorios; la lista vacía/`null` se contempla por
robustez.) Los tests existentes del payload (B1) se actualizan a la nueva firma pasando `null` como
`categoriaIds`, verificando que el payload no incluye `categories` (comportamiento idéntico a B1).

## Manejo de errores

- Producto sin clasif del canal o sin tipo → se reporta por SKU/tienda en el resumen, sin abortar el
  resto (igual que B1).
- Fallos de `POST /categories` o `POST /products` pasan por `NubeRetryHandler` (retry + rate limit). Un
  fallo de una tienda no impide la otra; un fallo de un producto no impide los demás.
- El árbol cacheado evita crear la misma categoría dos veces dentro de una corrida.

## Testing

- **`NubeCategoriaResolver`** (sin red, árbol en memoria + lambda creadora):
  - Todos los niveles ya existen → devuelve los ids existentes, no crea nada.
  - Match **case-insensitive** ("Cocina" vs "cocina") → reusa, no duplica.
  - Faltan niveles → crea los faltantes con el `parent` correcto y los registra (el segundo producto
    de la misma ruta los reutiliza).
  - Anidamiento clasif→tipo: el primer nivel del tipo cuelga de la hoja de la clasif.
- **Helper de ruta de nombres**: jerarquía de 1 nivel (solo raíz) y de varios niveles → orden raíz→hoja.
- **`NubeProductoPayloadBuilder`**: con `categoriaIds` no vacío → incluye `categories`; con lista vacía
  → no incluye la clave (mantiene los tests de B1 verdes).
- **Validación** en `crearProductoEnNubeCore`: producto sin clasif del canal o sin tipo → `ERROR`, no
  invoca el poster.
- **No se hace ninguna llamada real a Tienda Nube** (todo con mocks/lambdas), consistente con B1.

## Fuera de alcance (fases siguientes / descartado)

- **B3:** imágenes (base64 desde la carpeta por SKU).
- **B4:** SEO con IA (seo_title/seo_description).
- No re-categoriza productos ya existentes en TN (el alta saltea existentes, igual que B1).
- El frontend no cambia (los checkboxes y el disparo del alta ya existen de B1).
