# Variantes de producto en ML — Fase 2b (editar familia) — Diseño

Fecha: 2026-07-01
Estado: diseño (pendiente de revisión del usuario)
Antecedentes: [Fase 1](2026-07-01-variantes-ml-fase1-design.md), [Fase 2a (crear)](2026-07-01-variantes-ml-fase2a-crear-design.md)

## Objetivo

Al **editar** un producto que es parte de una familia de variantes, poder **ver** los miembros,
**agregar** nuevos, **editar** cada uno y **quitar** variantes. Detectando el modelo de la
publicación (nuevo User Products vs legacy `variations[]`).

## Decisiones tomadas (brainstorming)

- **Modelos:** nuevo **y** legacy.
- **Cargar la familia:** **desde ML** (family → user-products → items para el nuevo;
  `variations[]` del item para el legacy).
- **Editar una variante:** abrir **su propio modal** (edición normal del producto; ya existe).
- **Quitar una variante:** **pausar/cerrar en ML** + **desasociar** el producto de la familia
  (no se borra el producto local).

## Aviso de riesgo / método de verificación

Gran parte de 2b toca la **API de escritura de ML** (cerrar/pausar ítems, y confirmar que un
alta se une a la familia por `family_name`). **No se prueba contra ML real** en esta entrega; se
verifica por compilación (`mvn -o compile`) + typecheck y **la validación funcional la hace el
usuario** contra su cuenta. Por eso 2b se entrega en **incrementos independientes y revisables**.

## Detección de modelo (base para todo)

Al leer el item (`leerItemRaw`):
- **Nuevo (User Products):** `item.family_name != null`. Cada variante es un **ítem separado**
  que comparte `family_id`. Los miembros se obtienen de ML:
  `GET /items/{id}` → `user_product_id` → `GET /user-products/{up}` → `family_id` →
  `GET /sites/{site}/user-products-families/{family_id}` → user_products →
  `GET /users/{seller}/items/search?user_product_id=...` → items.
- **Legacy:** `family_name == null` y hay `variations[]` anidado en el item. Los "miembros" son
  las entradas de `variations[]` (id, `attribute_combinations`, `available_quantity`, `price`,
  `seller_custom_field`/SELLER_SKU).

## Modelo de datos (backend)

Sin columnas nuevas. Se agregan **lecturas** (no persistencia): la familia se lee de ML on-demand.

DTO nuevo (lectura):
```
FamiliaMlDTO {
  modelo: "NUEVO" | "LEGACY";
  familyId: String | null;      // solo nuevo
  familyName: String | null;    // nuevo: family_name; legacy: title
  ejeAtributoId: String | null; // atributo que varía (COLOR, SIZE, …) si se puede inferir
  variantes: [ FamiliaVarianteMlDTO ]
}
FamiliaVarianteMlDTO {
  itemId: String | null;        // nuevo: item_id por variante; legacy: el mismo item
  variationId: Long | null;     // solo legacy
  userProductId: String | null; // solo nuevo
  sku: String | null;           // SELLER_SKU
  ejeValor: String | null;      // value_name del atributo de eje
  stock: Integer | null;
  precio: BigDecimal | null;
  status: String | null;        // active/paused/closed…
  productoIdLocal: Integer | null; // match por SKU contra nuestra BD (si existe)
}
```

## Backend — piezas nuevas

1. **`MercadoLibreService.leerFamilia(String mlaCode): FamiliaMlDTO`** — orquesta:
   - `leerItemRaw(mlaCode)`; detecta modelo por `family_name`.
   - **Nuevo:** recorre user-products-families (endpoints de arriba); por cada item, extrae
     SELLER_SKU, valor del eje (atributo con `allow_variations`), stock, precio, status.
   - **Legacy:** recorre `variations[]` del item.
   - Matchea cada SKU contra `productoRepository.findBySku` para `productoIdLocal`.
   - **Métodos ML nuevos** (GET; sin escritura): `leerUserProduct(up)`, `leerFamilia(familyId)`,
     `buscarItemsPorUserProduct(upIds)`.
2. **`MercadoLibreService.cerrarItem(String mla)` / `pausarItem`** — para "quitar" (reusa
   `updateItemStatus(mla, "closed"|"paused")` que ya existe).
3. **Endpoints** (nuevo `FamiliaController` o dentro de `MercadoLibreController`):
   - `GET /api/productos/{id}/familia` → `FamiliaMlDTO` (lee la familia del producto editado).
   - `POST /api/productos/{id}/familia/variantes` → agrega una variante (crea producto + publica
     con el `family_name` de la familia). *(Increment 2)*
   - `DELETE /api/productos/{id}/familia/variantes/{sku}` → pausa/cierra en ML + desasocia. *(Increment 3)*

## Frontend — piezas nuevas

- En `ProductoFormModal.tsx` (modo edición), un **panel "Familia de variantes"** cuando el
  producto pertenece a una familia (o tiene `variations[]` legacy):
  - Lista de variantes: SKU · valor de eje · stock · status · (link "Editar" que abre el modal
    de ESE producto por su `productoIdLocal`).
  - Botón "Agregar variante" → reusa el flujo de alta con variantes de 2a, fijando el
    `family_name` de la familia. *(Increment 2)*
  - Botón "Quitar" por variante → confirma, pausa/cierra en ML y desasocia. *(Increment 3)*
- Servicio: `getFamiliaAPI(productoId)`, `agregarVarianteFamiliaAPI`, `quitarVarianteFamiliaAPI`.

## Faseo de la implementación (incrementos)

- **2b-1 (lectura/detección):** backend `leerFamilia` + `GET /familia` + DTOs + métodos ML de
  lectura; frontend panel **read-only** con la lista de variantes y el link "Editar" (abre el
  modal de cada producto). **Sin escritura a ML.** Es la base y lo más verificable.
- **2b-2 (agregar):** endpoint + botón "Agregar variante" (reusa 2a con el family_name fijo).
- **2b-3 (quitar):** endpoint + botón "Quitar" (pausa/cierra en ML + desasocia).
- **2b-legacy:** ajustes específicos del modelo legacy (edición de `variations[]` con la API
  vieja) si hiciera falta más que la lectura.

Cada incremento se entrega, se revisa y se testea contra ML antes del siguiente.

## Fuera de alcance (2b)

- Editor de familia de ML (`user-products-families/{id}/tasks`) para cambiar atributos padre.
- Multi-eje.
- UPtin (migración legacy→nuevo).

## Supuestos a confirmar

- Los endpoints de ML de user-products/families están disponibles para la cuenta (según la doc,
  requieren `user_product_seller`; para test hay que ambientar el usuario).
- El "valor del eje" se infiere del atributo con `allow_variations` de la categoría; si hay más
  de uno, se toma el primero (multi-eje queda afuera).
- Al agregar una variante, ML la une a la familia si comparte `family_name` + PARENT_PKs; el
  sistema garantiza el `family_name` compartido.
