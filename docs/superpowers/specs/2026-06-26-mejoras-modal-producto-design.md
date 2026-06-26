# Mejoras al modal de creación/edición de producto + fixes ML/login

Fecha: 2026-06-26

## Contexto

Conjunto de mejoras de UX en el modal de producto ([ProductoFormModal.tsx](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx)) más correcciones en la integración con Mercado Libre (payload de items y descripciones) y un fix de ruido de toasts al expirar la sesión.

La descripción del producto es **un único campo compartido** (`Producto.descripcion`) que ambos canales renderizan de forma distinta: Nube en HTML ([NubeDescripcionBuilder](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeDescripcionBuilder.java)), ML en texto plano con bullets ([MlDescripcionBuilder](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionBuilder.java)). El refresco de la tabla tras alta/edición **ya funciona** y queda fuera de alcance.

## Alcance

8 cambios agrupados en tres áreas. Numerados según el pedido original.

### A. Frontend — modal

**A1. Mensaje de validación en el footer.**
Mover "Revisá los campos marcados antes de guardar." desde el banner superior ([ProductoFormModal.tsx:1298-1302](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L1298)) al `footer` del Modal ([línea 1269](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L1269)), alineado a la izquierda de los botones Cancelar/Guardar, en rojo. Se muestra solo cuando `Object.values(formErrors).some(Boolean)`. Se elimina el banner superior.

**A4. Asterisco de requerido por canal.**
Auditar `validateForm` ([líneas 274-318](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L274)) y garantizar que **cada** input requerido condicionalmente muestre el `*` rojo cuando su canal está marcado:
- Título ML → `*` si `subirMl`.
- Título Nube → `*` si `subirKtHogar || subirKtGastro`.
- Atributos requeridos de la ficha ML → `*` si `subirMl` (los que `validateForm` exige).
- Paquete para envío (Alto/Ancho/Largo/Peso) → ya tienen `*` con `subirMl`; verificar consistencia.

Reusar el patrón existente `{cond && <span className="... text-red-600">*</span>}`.

**A8. Selector de unidad en Dimensiones Físicas + persistencia concatenada.**
Hoy los campos de Dimensiones Físicas ([líneas 1816-1855](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L1816)) son texto libre donde el usuario carga solo el número; se sincronizan con los atributos de dimensión de la ficha ML (`ML_DIM_MAP`, [líneas 847-935](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L847)) que sí llevan unidad. Resultado: la BD guarda "25" sin unidad y las descripciones la omiten.

Cambio: replicar el patrón `number_unit` de la ficha ML ([líneas 1168-1186](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L1168)) en cada campo físico → **input numérico + selector de unidad** con las unidades más comunes por campo:

| Campo | Unidades (default primero) |
|-------|----------------------------|
| Largo, Ancho, Alto, Diám. Boca, Diám. Base | `cm`, `mm`, `m` |
| Espesor | `mm`, `cm` |
| Capacidad | `ml`, `L`, `cc` |

Comportamiento:
- Se **persiste número + unidad concatenado** en el campo (ej. `"25 cm"`), igual que ya hace la ficha ML. Los campos en BD son strings (≤45) → no requiere cambio de schema.
- **Sincronización bidireccional** con la ficha ML conserva la unidad en ambos sentidos ("si completo un campo, se llena el otro"): `onFisicoChange` espeja el string completo `"num unit"` al atributo ML mapeado, y `setAtributo` (ficha → físico) deja de aplicar `parseNumero` y guarda el `valueName` completo.
- **Carga (edición):** parsear el string guardado en número + unidad para poblar input y select (número con `parseNumero`; unidad = resto; si no hay unidad, usar el default del campo).

Como el valor persistido ya lleva la unidad, los builders de descripción la muestran sin lógica adicional (el comentario "la unidad la trae el valor cargado" en NubeDescripcionBuilder pasa a ser cierto). **No se toca el backend para unidades.** Datos antiguos sin unidad: al reeditarse y guardarse adquieren la unidad por defecto; no se hace migración.

### B. Backend — descripciones (ambos builders)

**B2. SKU en la descripción.**
Agregar `SKU: <sku>` al final, **debajo del bloque CARACTERÍSTICAS**:
- Nube ([NubeDescripcionBuilder.construir](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeDescripcionBuilder.java#L17)): tras cerrar `</ul>`, un `<p>` con `SKU:` resaltado con el mismo estilo `label(...)`.
- ML ([MlDescripcionBuilder.construir](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionBuilder.java#L14)): tras los bullets, línea `SKU: <sku>`.

`p.getSku()` siempre está presente (campo `NOT NULL`).

### C. Backend — payload ML ([MlItemPayloadBuilder](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java))

**C6. "No aplica" se envía explícito.**
Hoy un atributo con `noAplica=true` se omite del payload ([líneas 101-103](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java#L101)), por lo que ML nunca lo registra como N/A. La API marca N/A enviando `{"id": ATTR, "value_id": "-1", "value_name": null}` ([doc ML](https://developers.mercadolibre.com.ar/en_us/attributes)).

Cambio: en vez de `continue`, agregar el atributo con `value_id = "-1"` y `value_name = null`. Como `Map.of` no admite valores null, usar un `HashMap`/`LinkedHashMap`. Solo aplica a atributos no requeridos (el front solo permite marcar "No aplica" en no-requeridos, [línea 1224](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L1224)), así que no rompe validaciones de requeridos.

**C7. Dimensiones del paquete: mapeo natural.**
El re-mapeo cruzado actual ([líneas 75-80](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java#L75)) hace que las medidas aparezcan swapeadas. Corregir a 1:1:

| Atributo ML | Campo BD |
|-------------|----------|
| `SELLER_PACKAGE_HEIGHT` | `mlPaqAlto` |
| `SELLER_PACKAGE_WIDTH`  | `mlPaqAncho` |
| `SELLER_PACKAGE_LENGTH` | `mlPaqLargo` |
| `SELLER_PACKAGE_WEIGHT` | `mlPaqPeso` |

El formato ya es correcto: enteros redondeados, `cm` para dimensiones y `g` para peso (`mlPaqPeso` en kg × 1000). Sin cambios en `cm()`/`gramos()`.

### D. Login — toast duplicado al expirar sesión

**D9. No emitir el toast genérico ante 401.**
Al expirar la sesión, los GET del modal en modo edición ([líneas 588-604](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L588)) fallan con 401; `fetchAPI` ya dispara `signalAuthExpired()` (redirige a login) y lanza `"Sesión expirada..."` ([fetchAPI.ts:86-88](../../../supermaster-frontend/src/app/utils/fetchAPI.ts#L86)). El `catch` muestra igual "No se pudieron cargar catálogos/aptos/clientes del producto".

Cambio: en el `catch` ([línea 601](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L601)), no emitir el toast cuando el error corresponde a sesión expirada. Detección mediante un helper reutilizable (`esSesionExpirada(error)`) en utils, basado en el mensaje/sentinela que lanza `fetchAPI`. Preferible centralizar el sentinela en `fetchAPI` (constante exportada o tipo de error) para evitar comparar strings frágiles.

## Fuera de alcance

- Refresh de tabla tras alta/edición (ya funciona).
- Campo de descripción separado por canal (ya es compartido).
- Migración de datos físicos antiguos sin unidad.
- Cambios de schema en BD.

## Testing

- **Backend:** tests de `MlItemPayloadBuilder` para C6 (atributo `noAplica` → `value_id:"-1"`, `value_name:null`) y C7 (mapeo HEIGHT/WIDTH/LENGTH ← alto/ancho/largo). Tests de `NubeDescripcionBuilder`/`MlDescripcionBuilder` para B2 (SKU al final). Correr con `mvn -o test` (el wrapper falla por red en sandbox).
- **Frontend:** verificación manual del modal — footer con mensaje, asteriscos por canal, selector de unidad con persistencia concatenada y sync con ficha ML, y ausencia del toast al expirar sesión.
