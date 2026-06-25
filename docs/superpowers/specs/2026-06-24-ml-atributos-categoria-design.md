# Atributos de categoría en la publicación a Mercado Libre

**Fecha:** 2026-06-24
**Estado:** Diseño aprobado

## Problema

Hoy el alta a Mercado Libre ([`MlItemPayloadBuilder`](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java)) manda un set **fijo** de atributos (condición, marca, SKU, dimensiones de paquete, IVA, impuesto de importación) y `shipping.local_pick_up = false`. Faltan:

1. **Retiro en persona** ofrecido (`local_pick_up: true`).
2. **Características principales y secundarias** de la categoría (la "ficha técnica"), que ML trae al fijar la categoría.
3. **EAN/GTIN** (código de barras / código universal de producto).
4. **Formato de venta** (Unidad / Pack) y unidades por pack.

Los puntos 2, 3 y 4 son, en ML, **atributos de categoría** (`GET /categories/{id}/attributes`): `GTIN`, `SALE_FORMAT`/`UNITS_PER_PACK` y el resto de las características. Enviar un atributo que la categoría no declara es un error en ML, así que todo va **gateado por los atributos de la categoría**.

## Decisiones de diseño (confirmadas)

| Tema | Decisión |
|------|----------|
| Retiro en persona | Siempre activado (`shipping.local_pick_up = true`). No configurable. |
| Alcance características | Motor completo: **Principales** (group `MAIN`) + **Secundarias** (resto). |
| Persistencia EAN/GTIN | Columna dedicada **`productos.ean`** (identificador estable, consultable). |
| Persistencia formato + características | Tabla hija **`producto_ml_atributo`** (`id_producto, attribute_id, value_id, value_name`). |
| Cómo se manda el código | Como atributo **`GTIN`** (`value_name` = código). Cae a `EAN` solo si la categoría no declara `GTIN`. |
| Obligatoriedad GTIN | **Opcional, nunca bloquea** ni se valida como required. Sin `EMPTY_GTIN_REASON`. |
| Validación de obligatorios | **Bloquea** el alta si falta algún `required`/`new_required` (excepto GTIN/EAN). `conditional_required` solo avisa. Usa los tags de `/attributes`, sin `/attributes/conditional`. |
| Fuera de alcance | Variaciones, `EMPTY_GTIN_REASON`, `/attributes/conditional`, `technical_specs/input`. |

## Invariante: no romper lo existente

El precio calculado, el cálculo/persistencia de `mlas` (envío/comisión, monitor de precios), el flujo de alta/post-alta y la actualización **no cambian de comportamiento**. Solo se **suman** atributos al payload y se **agrega** `local_pick_up: true`. Los atributos que el builder ya autocompleta siguen igual y **se excluyen** del motor dinámico para no duplicarlos.

## Atributos auto-gestionados (se excluyen del motor dinámico)

El motor dinámico **no muestra ni persiste** estos atributos (los arma el builder o la columna `ean`):

`ITEM_CONDITION`, `BRAND`, `SELLER_SKU`, `SELLER_PACKAGE_HEIGHT`, `SELLER_PACKAGE_WIDTH`, `SELLER_PACKAGE_LENGTH`, `SELLER_PACKAGE_WEIGHT`, `VALUE_ADDED_TAX`, `IMPORT_DUTY`, `GTIN`, `EAN`.

También se excluyen por tags: `fixed` (lo autocompleta ML), `read_only` (el vendedor no puede cargarlo).

## Modelo de datos

### `productos.ean` (columna nueva)
- `VARCHAR(20)`, nullable. Código de barras / código universal. Script SQL manual en `db/` (ddl-auto=validate).
- Entidad `Producto`: campo `String ean`. DTOs (`ProductoDTO`/`ProductoCreateDTO`/`ProductoPatchDTO`/`ProductoResumenDTO` según corresponda) + mapper.

### `producto_ml_atributo` (tabla hija nueva)
```
id            BIGINT PK AUTO_INCREMENT
id_producto   INT  FK -> productos(id_producto)  NOT NULL
attribute_id  VARCHAR(60) NOT NULL
value_id      VARCHAR(60) NULL          -- para value_type list/boolean
value_name    VARCHAR(255) NOT NULL     -- texto/número/“355 mL”/lista; multivalor = comas
UNIQUE (id_producto, attribute_id)
```
- Entidad `ProductoMlAtributo` + `@OneToMany(mappedBy="producto", cascade=ALL, orphanRemoval=true)` en `Producto` (patrón aptos/catálogos).
- Se accede dentro de la tx readOnly del alta (OSIV off), igual que `producto.getMarca()`.

## Backend — metadata de atributos por categoría

Nuevo endpoint **`GET /api/ml/categorias/{categoryId}/atributos`**:
- Proxea `GET /categories/{categoryId}/attributes` vía `MlRetryHandler`.
- **Cachea** en memoria con TTL (los atributos por categoría son estables).
- Devuelve `List<MlAtributoDefDTO>` **ya filtrado y tipado**:

```
record MlAtributoDefDTO(
    String id,
    String name,
    String valueType,            // string | number | number_unit | boolean | list
    List<MlAtributoValorDTO> values,   // {id, name} para list/boolean (vacío si no aplica)
    List<String> allowedUnits,   // number_unit
    String defaultUnit,          // number_unit
    boolean required,            // tag required || new_required
    boolean conditional,         // tag conditional_required
    boolean multivalued,         // tag multivalued
    String grupo                 // "PRINCIPALES" (group MAIN) | "SECUNDARIAS"
)
record MlAtributoValorDTO(String id, String name)
```

Filtrado: excluye los auto-gestionados (lista de arriba) y los tags `fixed`/`read_only`. `grupo = PRINCIPALES` si `attribute_group_id == "MAIN"`, si no `SECUNDARIAS`. Lo consume el front (render) y el publish (validación de required).

## Backend — payload y validación

### `MlItemPayloadBuilder`
- `construir(...)`: `shipping.local_pick_up = true`.
- `construirAtributos(producto)` se extiende:
  - Mantiene los auto-gestionados actuales.
  - **EAN:** si `producto.ean` no está vacío, agrega `{"id":"GTIN","value_name": ean}` (o `EAN` si la categoría no declara GTIN — el builder recibe el set de ids válidos de la categoría, ver abajo).
  - **Guardados:** por cada `ProductoMlAtributo`, agrega `{"id": attribute_id, "value_id"?, "value_name"}` (incluye `value_id` solo si no es null).
- Para saber si la categoría declara `GTIN` vs `EAN`, `construirAtributos` recibe (además del producto) el **set de ids de atributos válidos de la categoría** que el service ya obtuvo de la metadata.

### Validación de obligatorios (en el service, antes del POST)
- Se obtiene la metadata de la categoría (misma fuente cacheada).
- Por cada atributo `required`/`new_required` (excluyendo `GTIN`/`EAN`): debe estar presente como auto-gestionado o en `producto_ml_atributo`. Si falta alguno → `ResultadoAltaMl.error("faltan atributos obligatorios de la categoría: <ids/nombres>")`, **no se publica**.
- `conditional_required` faltantes → se incluyen en `advertencias` del resultado, **se publica igual**.
- La actualización (`actualizarItemEnMlCore`) aplica la misma extensión de atributos; la validación de required aplica donde se reconstruye el set de attributes.

## Frontend — sección MercadoLibre del form

[`ProductoFormModal.tsx`](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx), sección MercadoLibre:
- **Input EAN** (siempre visible), persistido en `productos.ean`. Aviso suave de formato (8–14 dígitos); no bloquea.
- Al haber **categoría** elegida (`mlCategoryId`), se llama a `GET /api/ml/categorias/{id}/atributos` y se renderiza un **form dinámico** agrupado en **Principales** y **Secundarias**:
  - `list` → `<select>` con `values`.
  - `boolean` → `<select>` Sí/No (envía `value_id`).
  - `number_unit` → input numérico + `<select>` de `allowedUnits` (default `defaultUnit`); se guarda `value_name = "<n> <unit>"`.
  - `number` → input numérico.
  - `string` → input texto (con `values` como `<datalist>` de sugeridos si hay).
  - `required` con asterisco; aviso (no bloqueo en front) si faltan; el bloqueo real lo hace el backend al publicar.
- **Formato de venta** aparece acá automáticamente cuando la categoría declara `SALE_FORMAT`/`UNITS_PER_PACK`.
- Los valores se guardan con el producto (alta y edición) en `producto_ml_atributo`; al editar se precargan (GET de los atributos guardados del producto).
- Servicio front: tipos + funciones para `getMlCategoriaAtributosAPI(categoryId)` y para enviar/leer los `producto_ml_atributo` del producto (en el payload de alta/patch del producto, junto a `ean`).

## Componentes y responsabilidades

| Unidad | Responsabilidad |
|--------|-----------------|
| `productos.ean` + `producto_ml_atributo` (+ entidades/mapper) | Persistir EAN y valores de atributos por producto. |
| `MlCategoriaAtributoService` (backend) | Traer/cachear/filtrar/agrupar la metadata de `/categories/{id}/attributes`. |
| `GET /api/ml/categorias/{id}/atributos` | Exponer la metadata al front. |
| `MlItemPayloadBuilder` (extendido) | Inyectar `local_pick_up`, EAN→GTIN y atributos guardados. |
| Validación de required (service) | Bloquear alta si falta `required`/`new_required` (≠ GTIN/EAN). |
| Form dinámico (front) | Render por `value_type`, agrupado Principales/Secundarias; cargar/guardar valores. |

## Testing

- `MlItemPayloadBuilderTest`: `local_pick_up=true`; EAN→`GTIN` presente/ausente y fallback a `EAN`; atributos guardados inyectados (con/sin `value_id`); exclusión de auto-gestionados.
- Filtrado/agrupado de `MlCategoriaAtributoService`: excluye auto-gestionados/`fixed`/`read_only`; `grupo` MAIN→PRINCIPALES; tipado de `required`/`conditional`/`multivalued`/`allowedUnits`. Mockear la respuesta de red.
- Validación de required: bloquea cuando falta un `required`; no bloquea por `conditional_required` ni por GTIN/EAN.
- Mapper de `ProductoMlAtributo` (DTO↔entity); persistencia (diff add/remove al editar, patrón relaciones N-a-N).
- Regresión: el set de atributos auto-gestionados y el flujo de `mlas` no cambian.

## Riesgos / a validar en implementación

- **OSIV off:** cargar `producto.getMlAtributos()` dentro de la tx del alta (igual que `marca`).
- **`record` posicional:** agregar `ean` a DTOs `record` rompe `new XDTO(...)` en tests — correr `mvnw test` (no solo compile). Ver memoria de DTOs posicionales.
- **Atributo válido por categoría:** mandar GTIN/EAN solo si la categoría lo declara (el builder recibe el set de ids válidos).
- **Caché de metadata:** TTL razonable; invalidar no es crítico (los atributos por categoría cambian poco).
