# Variantes de producto en ML — Fase 2a (crear con variantes) — Diseño

Fecha: 2026-07-01
Estado: diseño (pendiente de revisión del usuario)
Antecedentes: [Fase 1 (backend habilitador)](2026-07-01-variantes-ml-fase1-design.md), [modelo nuevo User Products](2026-06-22-ml-family-name-user-products.md)

## Contexto y objetivo

La Fase 1 dejó el backend listo (columnas `family_id`/`family_name` en `mlas`, tags
`allow_variations`/`variation_attribute` por atributo, captura/persistencia de la familia al
publicar). La Fase 2a agrega la **UI de creación de variantes** en el modal de producto.

Objetivo: al **crear** un producto, poder marcar que "tiene variantes", elegir el **eje de
variación** (ej. Color) y cargar **N variantes**; al guardar, se crean **N productos** (uno por
variante) y se publican a sus canales. En ML comparten `family_name` → ML los agrupa en una
familia (modelo nuevo User Products).

**Editar familias existentes es la Fase 2b** (no entra acá).

## Decisiones tomadas (brainstorming)

- **Base = variante #1:** el producto que se está cargando (con su SKU) es la primera variante;
  se le suman hermanos.
- **Cada hermano = Producto propio "normal":** va a **todos** los canales que tenga tildados
  (Dux/Nube/ML), como cualquier producto. En ML se agrupan por familia; en Nube/Dux son
  productos separados (no hay agrupación de variantes en esos canales).
- **Precio por variante:** cada variante puede tener cuotas propias por canal (el modelo nuevo
  de ML lo permite).
- **Un solo eje** de variación (multi-eje, más adelante).
- **Cada variante tiene su propio SKU** (y por ende sus propias imágenes, que se detectan solas
  por SKU).
- **Layout:** cada variante es una **tarjeta expandible** (mini-form), no una fila de tabla.
- **Alcance 2a:** solo **crear**. Detectar modelo y editar/agregar/quitar en familias existentes
  es 2b.

## Qué se comparte vs qué es propio de cada variante

**Compartido (heredado del base, igual en todas):** título ML (→ `family_name`), categoría ML,
atributos de ficha técnica, dimensiones físicas, paquete de envío ML, marca, material,
clasificación, aptos, costo, IVA, márgenes, descripciones (ML/Nube), título Dux/Nube, SEO Nube.

**Propio de cada variante:** SKU, **valor del eje** (ej. Color=Plateado), stock, EAN, imágenes
(por su SKU) y **cuotas por canal** (ML/HOGAR/GASTRO).

El **valor del eje** viaja como **atributo ML** (passthrough, no se persiste): al publicar cada
variante a ML se agrega `{ id: <ejeAtributoId>, value_id/value_name: <valor> }` a sus atributos.
El backend (`MlItemPayloadBuilder.construirAtributos`) ya incluye los `mlAtributos` que estén en
la categoría, así que no requiere cambio.

## UI (modal de creación)

En la sección **MercadoLibre**, arriba de "Título ML":

1. Toggle **"Este producto tiene variantes"** (solo en modo crear en 2a).
2. Al activarlo:
   - Select **"Eje de variación"**: se puebla con los atributos de la categoría ML que tienen
     `allowVariations` (de `mlAtributosDef`, campo nuevo de Fase 1). Requiere categoría elegida.
   - **Valor del eje de la variante #1**: input/select (usa los `values` del atributo si es lista;
     permite valor libre si no). El SKU de la variante #1 es el SKU del producto (arriba).
   - **"Otras variantes"**: lista de **tarjetas expandibles**. Botón "+ Agregar variante".
     Cada tarjeta (colapsada muestra SKU + valor de eje + stock; expandida agrega el resto):
     - SKU (requerido, único; distinto del base y de las otras variantes)
     - Valor del eje (requerido; distinto del de las otras variantes)
     - Stock
     - EAN (opcional)
     - Cuotas por canal: ML / KT HOGAR / KT GASTRO (default = las del base)
     - Aviso de imágenes: se detectan por SKU; si sincroniza a ML y no hay imagen válida para
       ese SKU, se marca (ML exige imagen).
   - Botón "− Quitar" por tarjeta.

## Validaciones (frontend)

Al guardar, además de las validaciones actuales:
- Eje elegido (si "tiene variantes").
- Cada variante (incl. la #1) tiene **valor de eje**; los valores son **distintos entre sí**.
- Cada variante tiene **SKU**; los SKU son **únicos entre sí** (y no chocan con productos
  existentes — el backend igual valida duplicado en BD por variante).
- Si sincroniza a ML: cada variante tiene al menos una **imagen válida** para su SKU
  (JPG/PNG ≤10 MB), reusando la regla ya agregada para el producto simple.

## Flujo de guardado (orquestación, frontend)

Al apretar "Crear Producto" con variantes activadas, para **cada** variante (base + hermanos),
en secuencia (para reportar por variante y no saturar):

1. Armar `ProductoCreateDTO` = **clon del base** con override de `sku`, `stock`, `ean`.
   (El valor del eje NO va en el DTO de producto: es atributo ML, se manda en el export.)
2. `createProducto(dto, asociarMargenYRelaciones)` — crea el producto y asocia
   márgenes/aptos/catálogos/segmentos (igual que hoy, con los datos compartidos).
3. Exportar a canales de esa variante (reusando el patrón actual), con:
   - **ML**: `tituloMl` = el título compartido (→ `family_name` **igual para todas**),
     `mlCategoryId` compartido, `mlAtributos` = los compartidos **+ el atributo del eje con el
     valor de esta variante**, `cuotas` = la cuota ML de esta variante, `descripcionMl` compartida.
   - **Nube (HOGAR/GASTRO)**: como producto normal, con las cuotas de esta variante.
   - **Dux**: como producto normal.
4. Acumular el resultado por variante (panel de estado por variante/canal, extendiendo el panel
   de canales actual).

Como todas las variantes se publican a ML con el **mismo `family_name`** + su atributo de eje,
ML las agrupa solas en una familia. La Fase 1 ya persiste `family_id`/`family_name` en `mlas`.

### Manejo de fallas parciales

Si una variante falla (creación o export), se reporta esa variante y se sigue con las demás
(no aborta todo). El modal queda abierto mostrando el detalle por variante, igual que hoy con
los canales.

## Backend

**No requiere cambios nuevos para 2a.** Se reutilizan:
- `POST /api/productos` (crear, uno por variante).
- Export a canales existentes por SKU (`exportarProductosAMlAPI` con `tituloMl` compartido +
  `mlAtributos` con el eje por variante; Nube/Dux normales).
- La captura de `family_id`/`family_name` de la Fase 1.

(Si al implementar apareciera un límite del batch de export que fuerce una sola cuota por lote,
se llama al export **una vez por variante** con su cuota — ya es 1 SKU por llamada.)

## Frontend — piezas nuevas / a tocar

- `ProductoFormModal.tsx`:
  - Estado nuevo: `tieneVariantes`, `ejeAtributoId`, `ejeValorBase` (valor de la variante #1),
    y `variantes: VarianteBorrador[]` (`{ sku, ejeValorId?, ejeValorNombre, stock, ean,
    cuotaMl, cuotaHogar, cuotaGastro, expandida }`).
  - UI del bloque de variantes (toggle + eje + tarjetas).
  - Validaciones nuevas.
  - Orquestación de guardado por variante (extraer de `handleCreate` un helper que cree+exporte
    UNA variante dado su override, y llamarlo en loop).
  - Panel de resultados por variante.
- `productosService.ts`: sin tipos nuevos obligatorios; se reusan `createProductoAPI`,
  `exportarProductosAMlAPI`, `exportarProductosANubeAPI`, `exportarProductosADuxAPI`.
  (Eventual helper `ProductoMlAtributo` para inyectar el eje — ya existe el tipo.)

## Fuera de alcance (2a)

- Editar familias existentes (agregar/editar/quitar variantes, detectar modelo viejo/nuevo) → **2b**.
- Multi-eje.
- Agrupar variantes en Nube/Dux (allí son productos separados).
- UPtin (migración de publicaciones viejas) → fase posterior.

## Supuestos a confirmar

- El valor del eje se elige de los `values` del atributo (con `value_id`) cuando el atributo es
  de tipo lista; si no, valor libre (`value_name`).
- Crear en secuencia (no en paralelo) las N variantes es aceptable en UX (más simple y con mejor
  reporte por variante). Si se necesita, luego se paraleliza.
- Mínimo 2 variantes (la #1 + al menos una hermana) para considerar "tiene variantes".
