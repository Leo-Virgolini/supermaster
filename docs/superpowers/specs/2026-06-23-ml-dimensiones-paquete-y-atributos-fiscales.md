# Dimensiones de paquete y atributos fiscales para Mercado Libre

**Fecha:** 2026-06-23
**Estado:** Diseño aprobado, pendiente de plan.

## Objetivo

Al publicar/editar en Mercado Libre, ciertas categorías (y la modalidad ME2 cross-docking / xd_drop_off) **exigen** atributos que hoy no enviamos, y la publicación falla con:

> The attributes [seller_package_height, seller_package_width, seller_package_length, seller_package_weight] are all required; The attributes [VALUE_ADDED_TAX, IMPORT_DUTY] are required for category [MLA30083].

Esta feature agrega esos atributos al payload de ML:
- **Dimensiones del paquete de envío**: alto, ancho, largo (cm) y peso (g).
- **Atributos fiscales**: IVA (`VALUE_ADDED_TAX`) e impuesto de importación (`IMPORT_DUTY`).

Para eso se cargan las dimensiones del paquete en el formulario de producto y se guardan en la BD.

## Decisiones tomadas (brainstorming 2026-06-23)

1. **Las dimensiones se guardan en `productos`, NO en `mlas`.** Un MLA puede tener varios SKUs (variantes con `MLAU`) que pueden tener **tamaños distintos**; si se guardaran en `mlas` se compartirían incorrectamente. Cada producto físico (SKU) tiene su propio paquete. `mlas` queda para lo de la publicación (comisión/envío/tope).
2. **Unidades** (según el proyecto de referencia `pickit-y-etiquetas` y la doc de ML): se cargan **cm** (dimensiones) y **kg** (peso); a ML se envía **cm** y **g** (peso kg×1000), **siempre enteros** (ML rechaza decimales con error 5402).
3. **Se sube tal cual se carga** (sin el +20% que aplica pickit).
4. **`VALUE_ADDED_TAX` = el `iva` propio de cada producto** (campo `productos.iva`), mapeado a la lista cerrada de ML. **`IMPORT_DUTY` = `0 %`** constante.
5. **IVA fuera de rango:** se asume que el `iva` siempre es uno de los 4 válidos de ML (0 / 10.5 / 21 / 27). Si no mapea, se omite el atributo y ML rechazará con su propio error (sin manejo especial).
6. **Validación en el form:** si se marca *Sincronizar con Mercado Libre*, las 4 dimensiones pasan a ser **obligatorias**.

## Formato exacto del payload de ML (de la doc oficial, 08/06/2026)

Todo va en la lista `attributes` del item (a nivel ítem, no por variación).

### Dimensiones del paquete (`value_name`, enteros, solo cm/g)
```json
{ "id": "SELLER_PACKAGE_HEIGHT", "value_name": "6 cm" }
{ "id": "SELLER_PACKAGE_WIDTH",  "value_name": "25 cm" }
{ "id": "SELLER_PACKAGE_LENGTH", "value_name": "31 cm" }
{ "id": "SELLER_PACKAGE_WEIGHT", "value_name": "214 g" }
```
- `height/width/length` = `round(cm)` con `" cm"`. `weight` = `round(kg × 1000)` con `" g"`.
- Solo se agregan si **las 4** están presentes.

### Atributos fiscales (`value_type: list` — valor de lista cerrada, formato con espacio `"21 %"`)
`VALUE_ADDED_TAX` — mapeo `producto.iva` → valor:

| iva | value_id | value_name |
|-----|----------|------------|
| 0    | 48405907 | `0 %` |
| 10.5 | 48405908 | `10.5 %` |
| 21   | 48405909 | `21 %` |
| 27   | 48405910 | `27 %` |

`IMPORT_DUTY` — siempre:

| value_id | value_name |
|----------|------------|
| 49553239 | `0 %` |

Se envían con `value_id` **y** `value_name` (como el ejemplo de `/attributes/conditional` de la doc). Si el iva no mapea a ninguna fila, se omite `VALUE_ADDED_TAX`.

## Alcance

### Incluye
- 4 columnas nuevas en `productos` + propagación a entidad/DTOs/mapper/types.
- Nueva sección en el modal de producto con los 4 inputs + validación al subir a ML.
- Agregado de los 6 atributos (4 dimensiones + VAT + IMPORT_DUTY) al payload de creación/edición de ML.

### NO incluye
- Usar estas dimensiones para Tienda Nube (Nube maneja su propio esquema; fuera de alcance).
- El margen +20% de pickit.
- Soporte de IVAs fuera de {0, 10.5, 21, 27}.
- Consultar `/attributes/conditional` dinámicamente (se mandan siempre; ML los ignora si no aplican).

## Diseño

### Backend — Schema (`productos`)
4 columnas nuevas, **nullable** (no todo producto va a ML). Script SQL manual en `src/main/resources/db/` (porque `ddl-auto=validate`):
```sql
ALTER TABLE productos
  ADD COLUMN ml_paq_alto  DECIMAL(6,2) NULL,
  ADD COLUMN ml_paq_ancho DECIMAL(6,2) NULL,
  ADD COLUMN ml_paq_largo DECIMAL(6,2) NULL,
  ADD COLUMN ml_paq_peso  DECIMAL(8,3) NULL;
```
(`ml_paq_alto/ancho/largo` en cm; `ml_paq_peso` en kg.)

### Backend — Entidad y DTOs
- `Producto`: 4 campos `BigDecimal` (`mlPaqAlto`, `mlPaqAncho`, `mlPaqLargo`, `mlPaqPeso`).
- DTOs (records): `ProductoDTO`, `ProductoCreateDTO`, `ProductoUpdateDTO`, `ProductoConPreciosDTO`, `ProductoPatchDTO` — agregar los 4 campos. **Cuidado:** `ProductoMapper.toDTO` y el constructor de `ProductoConPreciosDTO` son **posicionales manuales** — hay que alinearlos. Agregar componentes a los records rompe `new XDTO(...)` en los tests (p. ej. `RecalculoAutomaticoIntegrationTest`) → se actualizan.
- `aplicarPatch` en `ProductoServiceImpl`: contemplar los 4 campos en el PATCH.

### Backend — Payload de ML
En [`MlItemPayloadBuilder.construir`](supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java) (recibe `Producto p`, ya tiene acceso a las dimensiones y al `iva`):
- Agregar a `attributes` los 4 `SELLER_PACKAGE_*` si `mlPaqAlto/ancho/largo/peso` están todos presentes, con redondeo a entero y unidades `cm`/`g`.
- Agregar `VALUE_ADDED_TAX` mapeando `p.getIva()` a la tabla (id+name); omitir si no mapea.
- Agregar `IMPORT_DUTY` fijo (`49553239` / `"0 %"`).
- Helper de mapeo de iva → (value_id, value_name) con los 4 casos.

### Frontend — Modal
- Nueva sección **"Paquete para Mercado Libre (envío)"** con 4 inputs numéricos: **Alto (cm)**, **Ancho (cm)**, **Largo (cm)**, **Peso (kg)**, con tooltip ("ML exige las dimensiones del paquete para publicar; se envían en cm y gramos, redondeadas a enteros").
- 4 nuevos `useState` en `ProductoFormModal`; precarga en edición; se incluyen en el payload de alta y de edición.
- **Validación** (`validateForm`): si `subirMl` está marcado, los 4 son obligatorios (mensaje por campo). Sin marcar ML, son opcionales.
- `types.ts`: agregar los 4 campos a los tipos de producto.

## Manejo de errores
- Si faltan dimensiones y se sube a ML: lo previene la validación del form (no llega a ML incompleto).
- Si el iva no mapea: se omite `VALUE_ADDED_TAX` y ML rechaza con su error (decisión 5).
- El resto del flujo de exportación a ML (panel de estado por canal, reintento) se mantiene igual.

## Pruebas
- Backend offline: tests unitarios de `MlItemPayloadBuilder` (con `@TempDir`/Mockito/POJOs) verificando que el payload incluye los 6 atributos con el formato exacto (enteros, `" cm"`/`" g"`, ids de VAT/IMPORT_DUTY, mapeo de iva). **No** llamar a la API real de ML.
- Backend: `./mvnw -o test` (compila + tests; detecta los constructores posicionales rotos).
- Frontend: `npx tsc --noEmit`.
- **Smoke (usuario):** crear/editar un producto con dimensiones, subir a ML real, confirmar que ya no aparece el error de `seller_package_*` ni de `VALUE_ADDED_TAX/IMPORT_DUTY`. Validar el formato de los atributos fiscales (value_id/value_name).

## Archivos afectados
**Backend:**
- `src/main/resources/db/2026-06-23-ml-dimensiones-paquete.sql` (nuevo).
- `Producto.java` (4 campos).
- DTOs de producto (`ProductoDTO/CreateDTO/UpdateDTO/ConPreciosDTO/PatchDTO`).
- `ProductoMapper.java` (toDTO posicional + ctor ConPreciosDTO).
- `ProductoServiceImpl.java` (`aplicarPatch`).
- `MlItemPayloadBuilder.java` (atributos + helper de iva).
- Tests afectados por los constructores posicionales (`RecalculoAutomaticoIntegrationTest`, etc.).

**Frontend:**
- `ProductoFormModal.tsx` (sección + estado + validación + payload + precarga).
- `types.ts` (4 campos).

## Pendiente de validar en smoke (usuario)
- La publicación a ML deja de fallar por `seller_package_*` y por `VALUE_ADDED_TAX/IMPORT_DUTY`.
- El formato de los atributos fiscales (value_id/value_name) es aceptado por ML.
