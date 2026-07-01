# Variantes de producto en ML — Fase 1 (modelo de datos + backend de creación)

Fecha: 2026-07-01
Estado: diseño (pendiente de aprobación del usuario)

## Contexto

Hoy un producto es **1 SKU / 1 stock / 1 precio** y se sincroniza a Dux, Tienda Nube
(HOGAR/GASTRO) y Mercado Libre. En ML, cada Producto se publica como **un ítem** y
`Producto.mla` apunta a **un** registro de `mlas`.

Se quiere permitir **crear variantes de un producto** (ej. mismo vaso en varios colores)
y publicarlas a Mercado Libre.

Mercado Libre tiene **dos modelos** de variantes, incompatibles entre sí:

- **Legacy**: una sola publicación con un array `variations` anidado (mismo precio,
  stock por variación).
- **Nuevo — User Products / "Precio por variación"**: **no existe** el array `variations`.
  Cada variante es un **ítem separado** que comparte `family_name`; ML los agrupa solo en
  una **familia** (`family_id`) y un **User Product** (`user_product_id`). Cada ítem tiene
  su propio precio/cuotas/envío. Si la cuenta tiene el tag `user_product_seller`, el modelo
  legacy devuelve **error 400**.

Antecedente: [2026-06-22-ml-family-name-user-products.md](2026-06-22-ml-family-name-user-products.md)
— el payload de alta ML ya envía `family_name` ([MlItemPayloadBuilder.java:30](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java)).

## Objetivo (proyecto completo, faseado)

Permitir crear/gestionar variantes ML desde el modal de producto, conviviendo con
publicaciones viejas (legacy) y nuevas.

Faseo acordado:

- **Fase 1 (esta spec): modelo de datos + backend de creación con el modelo nuevo.**
- Fase 2: UI del modal (definir eje + alta de variantes).
- Fase 3: editar — detectar el modelo de la publicación (legacy vs familia nueva) y actualizar.
- Fase 4 (opcional): migración UPtin de publicaciones viejas al modelo nuevo.

## Decisiones tomadas (brainstorming)

1. **Alcance de canales:** solo Mercado Libre. Dux/Nube siguen viendo productos sueltos.
2. **Modelo ML:** al **crear** → modelo nuevo (User Products). Al **editar** → detectar el
   modelo de la publicación (Fase 3). Hay publicaciones viejas y nuevas conviviendo.
3. **Modelo interno:** **cada variante = un Producto propio** (con su SKU/stock/precio),
   agrupados por una **familia**.
4. **Flujo de alta:** "padre + lista de variantes" en el modal: se define el producto base y
   el eje (ej. Color) con sus valores; **un guardado crea N Productos** (uno por variante).
5. **Persistencia de la familia:** **reusar `mlas`** (sin tabla nueva). Se agregan
   `family_id` y `family_name` a `mlas`.
6. **Un solo eje de variación en Fase 1** (multi-eje queda para después).
7. **Cada variante tiene su propio SKU.**

## Hallazgo clave: `mlas` ya modela el nivel de publicación del modelo nuevo

Verificado en [MercadoLibreService.extraerMlau()](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java) (líneas ~1261-1263):
`extraerMlau()` lee `user_product_id` del ítem y lo guarda como `mlau`. Es decir, en `mlas`:

- **`mla` = `item_id`** (la publicación / condición de venta).
- **`mlau` = `user_product_id`** (el User Product = la variante del modelo nuevo).
- `precio_envio` / `comision_porcentaje` = datos por ítem.

Como **cada variante = un Producto propio → su propio `mla` FK → su propia fila `mlas`**, la
tabla `mlas` ya cubre el vínculo por-variante del modelo nuevo. **No se crea tabla de
variantes.** Lo único que falta es el nivel de **familia**, que se agrega a `mlas`.

## Modelo de datos

### Cambios en `mlas`

Agregar dos columnas:

- `family_id` **VARCHAR(30) NULL** — el `family_id` que devuelve ML. Se guarda como texto
  porque puede exceder `BIGINT` con signo (ej. `18446744000000000615` ≈ 2⁶⁴).
- `family_name` **VARCHAR(255) NULL** — nombre genérico de la familia (≤ `max_title_length`
  del dominio). Denormalizado: todas las variantes de una familia comparten el mismo valor.

La "familia" queda definida como **el conjunto de filas de `mlas` que comparten `family_id`**.
Recuperar las variantes de una familia = `mlas` con ese `family_id` → sus `productos`.

Sin tablas nuevas. Sin cambios en `productos`.

### Valor del eje por variante (sin columna nueva)

El valor del eje de cada variante (ej. `COLOR = Negro`) **no se persiste en columna propia**:
es un atributo ML del producto y viaja como **passthrough** (se lee del canal al editar y se
manda al publicar), igual que el resto de los atributos ML hoy
(ver [datos-canal-en-modal](2026-06-29-datos-canal-en-modal-design.md)).

### Migración SQL (ddl-auto=validate)

Archivo nuevo `supermaster-backend/src/main/resources/db/2026-07-01-mlas-family.sql`:

```sql
-- Familia de User Products (modelo nuevo de variantes ML) a nivel publicacion.
-- ddl-auto=validate: aplicar a mano antes de arrancar el backend.
ALTER TABLE supermaster.mlas
  ADD COLUMN family_id   VARCHAR(30)  NULL AFTER mlau,
  ADD COLUMN family_name VARCHAR(255) NULL AFTER family_id;

CREATE INDEX idx_mlas_family_id ON supermaster.mlas (family_id);
```

## Extraer tags de variación de la categoría (hoy falta)

En [MlCategoriaAtributoService.parsear()](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlCategoriaAtributoService.java) (línea ~139)
hoy se leen los tags `read_only`, `fixed`, `required`, `multivalued`, etc., pero **no**
`allow_variations` ni `variation_attribute`.

Fase 1 agrega:

- Leer `allow_variations` y `variation_attribute` de cada atributo.
- Exponer en `MlAtributoDefDTO` qué atributos pueden ser **eje** (`allow_variations`) y cuáles
  pueden ir **por variante** (`variation_attribute`). El modal (Fase 2) usa esa info para
  ofrecer el selector de eje. En Fase 1 alcanza con exponerlo por API; el consumo visual es
  Fase 2.

## API de creación

Extender el request de alta de producto para aceptar, opcionalmente, una familia de variantes.
Cuando el request trae variantes, el endpoint crea la familia completa.

Shape propuesto (nuevo DTO, no rompe el alta actual — si no hay variantes, todo sigue igual):

```
ProductoConVariantesCreateDTO {
  base: ProductoCreateDTO        // datos compartidos (marca, categoría ML, dimensiones, etc.)
  familyName: String             // requerido; <= max_title_length del dominio
  ejeAtributoId: String          // ej. "COLOR" (debe tener allow_variations en la categoría)
  variantes: [                   // >= 2
    {
      sku: String                // SKU propio de la variante (requerido, único)
      stock: Integer
      precio: BigDecimal?        // opcional; el modelo nuevo permite precio por variante
      ejeValorId: String?        // value_id del eje (ej. id de "Negro"); o
      ejeValorNombre: String     // value_name del eje (para valores libres)
      atributosVariante: [ {id, valueName} ]?   // variation_attribute, ej. EAN/UPC
      imagenes: [...]?           // imágenes propias de la variante (SKU->archivos, como hoy)
    }
  ]
}
```

Notas:
- `title` **no** se envía a ML (lo genera ML en el modelo nuevo).
- Precio por variante es opcional; si no viene, se calcula/toma como el resto de productos.

## Flujo del servicio (crear familia)

En una operación transaccional (crear en BD) + publicación best-effort a ML:

1. Validar: categoría permite variaciones (`allow_variations` en `ejeAtributoId`);
   `familyName` ≤ `max_title_length`; ≥ 2 variantes; SKUs únicos; valores de eje distintos.
2. Crear **N Productos** (uno por variante) heredando `base` y con su SKU/stock/precio propios.
   El valor del eje va como atributo ML (passthrough), no como columna.
3. Publicar a ML (modelo nuevo): por cada variante `POST /items` con `family_name` +
   `attributes` (incluido el valor del eje). **No** se envía array `variations`.
   Reusa [MlItemPayloadBuilder](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java)
   (ya manda `family_name`); el cambio central es **iterar por variante**.
4. Por cada respuesta, guardar en `mlas`: `mla` (item_id), `mlau` (user_product_id),
   `family_id` (devuelto por ML), `family_name`. Asociar cada `mla` a su Producto
   (reusa `MlaService.asegurarYAsociar`).
5. ML agrupa las variantes solas por `family_name` (misma familia → mismo `family_id`).

### Consistencia de familia (PARENT_PK)

ML agrupa por igualdad de atributos **PARENT_PK** (marca, modelo, etc.) y permite variar los
**CHILD_PK/custom** (el eje). El servicio debe garantizar que todas las variantes comparten
los PARENT_PK (heredan de `base`) y difieren solo en el eje; si no, ML las separa en familias
distintas.

## Consideraciones / bordes

- **Falla parcial:** si una variante falla el `POST /items`, reportar advertencia por variante
  (patrón actual de export ML) sin abortar toda la creación; las creadas quedan asociadas.
- **`family_id` como texto:** ver arriba (excede BIGINT con signo).
- **`mlas.mla` no es único** (memoria [mlas-mla-no-unico]): en variantes cada ítem tiene su
  propio `item_id`, así que son filas distintas; igual mantener el find-or-create.
- **Relación `Mla.productos` (1:N):** cada variante apunta a su propia fila `mlas` (uso 1:1);
  no rompe la relación existente.
- **Cuenta sin `user_product_seller`:** si la cuenta aún no está en el modelo nuevo, el
  `POST` con estructura nueva podría comportarse distinto. Fase 1 asume cuenta ya migrada
  (decisión del usuario: crear siempre con modelo nuevo). La detección al editar es Fase 3.

## Fuera de alcance (Fase 1)

- UI del modal (Fase 2).
- Editar / detectar modelo legacy vs nuevo (Fase 3).
- Migración UPtin (Fase 4).
- Variantes en Dux/Nube; multi-eje; precio por variante calculado con reglas de canal.

## Supuestos a confirmar

- La cuenta ML de producción ya tiene `user_product_seller` (crear siempre modelo nuevo).
- Mínimo 2 variantes por familia (crear "familia" de 1 no aplica).
- El eje se elige de los atributos con `allow_variations` de la categoría.
