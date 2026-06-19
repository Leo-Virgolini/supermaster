# Spec B — Dux: payload enriquecido + datos de mapeo + UI de canales

**Fecha:** 2026-06-19
**Estado:** Diseño aprobado en sus decisiones, pendiente de revisión del spec y plan.
**Relacionado:** [Spec A — Nube/ML actualizar al editar](2026-06-19-actualizar-al-editar-canales-d1-design.md)

## Objetivo

Hoy la exportación a Dux (`/item/nuevoItem`, que hace *upsert* por `cod_item`)
manda un payload mínimo. Se quiere **enriquecer ese payload** con rubro,
subrubro, marca, proveedor, unidad de medida y varios flags, tanto al **crear**
como al **editar** un producto. Para varios de esos campos hace falta **modelar
datos de mapeo a Dux** (unidad de medida, código de marca, id de Dux de las
clasificaciones). Incluye además un ajuste visual de los checkboxes de canal.

## Params reales del endpoint Dux (`/item/nuevoItem`)

Cada objeto del array `productos` acepta (tipos según la doc oficial):

- `cod_item` (string, **required**)
- `item` (string; required en INSERT, opcional en UPDATE)
- `porc_iva` (double)
- `stockeable` (string "S"/"N"/null)
- `stock_matricial` (string "S"/"N"/null) — **fuera de alcance**
- `acepta_stock_negativo` (string "S"/"N"/null)
- `id_moneda` (int32)
- `id_unidad_medida` (int32) — **id** de unidad de medida (no lista, no string)
- `tipo_producto` (string)
- `habilitado` (string "S"/"N"/null)
- `trazable` (string "S"/"N"/null)
- `costo` (double)
- `id_proveedor` (int32)
- `id_rubro` (int32)
- `id_sub_rubro` (int32)
- `codigo_marca` (string)
- `disponible_para` (string)
- `cod_barra` (string) — **fuera de alcance**
- `descripcion` (string)
- `fecha_vencimiento` (string) — **fuera de alcance**
- `indica_ctd_bultos` (string "S"/"N"/null)
- `precios` (array) — **fuera de alcance**

## Mapeo final del payload

| Campo Dux | Origen en supermaster | Notas |
|---|---|---|
| `cod_item` | `producto.sku` | required |
| `item` | `producto.tituloDux` | |
| `tipo_producto` | `producto.esCombo` → `"COMBO"` / `"SIMPLE"` | |
| `id_moneda` | `1` | fijo (ARS) |
| `porc_iva` | `producto.iva` (double) | se mantiene del payload actual |
| `costo` | `producto.costo` (double) | |
| `id_proveedor` | `producto.proveedor.id` | id alineado con Dux (responsabilidad del usuario) |
| `id_rubro` | `idDux` de la clasif **nivel 1** | ver "Rubro / subrubro" |
| `id_sub_rubro` | `idDux` de la clasif **nivel 2** | omitir si no hay nivel 2 |
| `codigo_marca` | `producto.marca.codigoDux` | campo nuevo en `Marca` |
| `id_unidad_medida` | `producto.unidadMedida.id` | entidad/tabla nueva |
| `stockeable` | `"S"` | fijo |
| `acepta_stock_negativo` | `"S"` | fijo |
| `habilitado` | `producto.activo` → `"S"` / `"N"` | se mantiene la lógica actual |
| `trazable` | `"S"` | fijo |
| `disponible_para` | `"todos"` | fijo |
| `indica_ctd_bultos` | `"S"` | fijo |
| `descripcion` | `producto.tituloNube` | solo si no es blank |

**Se quitan** del payload actual: `codigo_externo` y `ctd_unidades_por_bulto`
(no existen en la API de Dux).

**Regla de omisión:** todo campo cuyo origen sea `null` (ej. `id_unidad_medida`
sin unidad asignada, `codigo_marca` sin código, `id_rubro`/`id_sub_rubro` sin
`idDux`, `descripcion` blank) **no se incluye** en el objeto JSON (se deja que
Dux use su default). Esto preserva el comportamiento "campos opcionales si no
nulos" del código actual.

## Rubro / subrubro

El producto puede tener `clasifGral` y/o `clasifGastro`. Selección de la
clasificación fuente:
1. Si tiene **ambas** → usar `clasifGral`.
2. Si tiene **solo una** → usar esa.
3. Si **no tiene ninguna** → no mandar `id_rubro` ni `id_sub_rubro`.

La clasificación es jerárquica (`getPadre()` recursivo, LAZY). Desde el nodo al
que apunta el producto se construye la cadena hasta la raíz:
- **rubro (nivel 1)** = nodo raíz (se sube por `getPadre()` hasta `padre == null`).
- **subrubro (nivel 2)** = el hijo directo de la raíz en el camino hacia el nodo
  del producto (el segundo elemento de la cadena raíz→…→nodo).
  - Si el nodo del producto **es** la raíz (nivel 1) → no hay subrubro.
  - Si es nivel 2 → subrubro = ese nodo; rubro = su padre.
  - Si es nivel 3+ → rubro = raíz; subrubro = el nodo de nivel 2 en la cadena.

Se mandan `id_rubro = nodoNivel1.idDux` y `id_sub_rubro = nodoNivel2.idDux`. Si
algún `idDux` es `null`, ese campo se omite.

## Datos de mapeo a Dux (cambios de modelo)

> **Schema:** `ddl-auto=validate`. Cada cambio requiere su script SQL manual en
> `src/main/resources/db/` (ver memoria del proyecto). Ningún `id` de catálogo de
> Dux es autogenerado: se asigna con el valor exacto de Dux.

### 1. Unidad de medida (entidad/tabla nueva)

- Entidad `UnidadMedida`:
  - `id` (Integer, **PK no autogenerada** — es el `id` de Dux).
  - `codigo` (String, ej. `"T1"`, `"J2-C"`, `"COMBOS"`).
- Tabla `unidad_medida` poblada con los 22 valores y sus IDs de Dux:
  `T1, T3, J2-C, J2-F, J1-D, J3-D, J3-B, J2-H, J2-G, T2, J1-E, J2-D, J2-B,
  J1-B, J2-I, J3-C, J3-A, COMBOS, J2-A, J1-C, J1-A, J2-E`.
  - **Dependencia:** el usuario debe proveer el `id` de Dux de cada código. El
    script de seed los lista; los IDs se completan con los reales de Dux.
- `Producto`: relación `@ManyToOne(fetch = LAZY)` `unidadMedida`, columna
  `unidad_medida_id` (FK, nullable).
- Endpoint de lectura para el form (listar las unidades de medida).

### 2. Código de marca

- `Marca`: campo nuevo `codigoDux` (String, nullable), columna `codigo_dux`.
- Se carga desde el ABM de marcas (input nuevo) o por SQL.

### 3. id de Dux de las clasificaciones

- `ClasifGral` y `ClasifGastro`: campo nuevo `idDux` (Integer, nullable),
  columna `id_dux`.
- Se carga desde los ABM de clasificación (input nuevo) o por SQL.

## Cambios de backend

- `DuxService.exportarProductosADux`: reescribir el armado del `itemDux` con el
  mapeo final. Quitar `codigo_externo` y `ctd_unidades_por_bulto`.
- **Manejo LAZY / transaccionalidad (crítico):** el nuevo mapeo accede a
  `marca`, `proveedor`, `clasifGral/Gastro` + `getPadre()` recursivo, y
  `unidadMedida` — todo LAZY, con `open-in-view=false`. El armado del payload
  debe correr dentro de `@Transactional(readOnly = true)` para resolver esas
  asociaciones (mismo aprendizaje que el bug B2). El **POST HTTP a Dux NO debe
  ejecutarse dentro de la transacción**: se separa el flujo en
  (a) un método `@Transactional(readOnly = true)` que carga los productos y
  arma la lista de objetos JSON (resolviendo todo el LAZY), y
  (b) el POST a Dux fuera de la transacción. Patrón análogo al de
  `MlExportService` (self-proxy + tx acotada a la carga).
- Lógica de jerarquía rubro/subrubro como método auxiliar testeable.
- Entidades nuevas/modificadas: `UnidadMedida` (+ repositorio), `Producto`
  (relación), `Marca` (`codigoDux`), `ClasifGral`/`ClasifGastro` (`idDux`).
- DTOs: exponer `unidadMedida` (id + código) en el DTO de producto; `codigoDux`
  en el DTO de marca; `idDux` en los DTOs de clasificación, donde el front los
  necesite.

## Cambios de frontend

- **Form de producto (creación/edición):** `select` de **unidad de medida**
  (carga el catálogo desde el backend). El producto elige una.
- **ABM de marcas:** input para `codigoDux`.
- **ABM de clasificaciones (General y Gastro):** input para `idDux`.
- **Iconos/colores de los checkboxes de canal** (transversal, aplica a todos los
  "Subir a …" / "Actualizar en …"): cada canal con su icono y color
  distintivo —
  - **Dux** (ERP): tono índigo/azul, icono de cubo/ERP.
  - **KT HOGAR (Nube)** y **KT GASTRO (Nube)**: identidad Tienda Nube, con icono
    diferenciado entre hogar y gastro (p. ej. casa vs. utensilio) y color
    distinto entre ambos.
  - **Mercado Libre**: amarillo ML, icono de bolsa/tienda.
  Los iconos/colores concretos se eligen en implementación (heroicons +
  paleta Tailwind), respetando la identidad de cada canal.

## Manejo de errores

- Se mantiene el patrón actual de `exportarProductosADux`: acumular errores por
  SKU en `ExportDuxResultDTO` sin frenar el resto.
- Campos con origen `null` se omiten (no son error).
- Si un `idDux`/`codigoDux`/`unidadMedida` falta, simplemente no se manda ese
  campo (Dux usa su default); no se bloquea la exportación.

## Pruebas

- **Mapeo del payload:** test del armado de `itemDux` para un producto completo
  (todos los campos) y para uno con relaciones nulas (verifica la omisión de
  campos null y que no se mande `codigo_externo`/`ctd_unidades_por_bulto`).
- **Rubro/subrubro:** tests de la lógica de jerarquía — nodo en nivel 1 (sin
  subrubro), nivel 2, nivel 3 (rubro=raíz, subrubro=nivel 2), selección
  gral-vs-gastro (ambas → gral; solo una → esa; ninguna → omitir), e `idDux`
  nulo → omitir.
- **Unidad de medida:** test de que `id_unidad_medida` sale de
  `producto.unidadMedida.id`.

## Archivos afectados (resumen)

**Backend:**
- `apis/dux/service/DuxService.java` — payload + separación tx/HTTP + jerarquía.
- `dominio/.../UnidadMedida.java` (nuevo) + repositorio + controller de lectura.
- `dominio/producto/entity/Producto.java` — relación `unidadMedida`.
- `dominio/marca/entity/Marca.java` — `codigoDux`.
- `dominio/clasif_gral/entity/ClasifGral.java` — `idDux`.
- `dominio/clasif_gastro/entity/ClasifGastro.java` — `idDux`.
- DTOs de producto/marca/clasif afectados.
- `src/main/resources/db/` — scripts SQL: crear `unidad_medida` + seed,
  `producto.unidad_medida_id`, `marca.codigo_dux`, `clasif_gral.id_dux`,
  `clasif_gastro.id_dux`.

**Frontend:**
- Form de producto — select de unidad de medida.
- ABM de marcas — input `codigoDux`.
- ABM de clasificaciones — input `idDux`.
- Checkboxes de canal — iconos/colores por canal.

## Dependencias del usuario

- IDs de Dux de las 22 unidades de medida (para el seed).
- Alinear `proveedor.id` con el `id_proveedor` de Dux.
- Cargar `codigoDux` de las marcas y `idDux` de las clasificaciones.

## Fuera de alcance

- `stock_matricial`, `cod_barra`, `fecha_vencimiento`, `precios`.
- La actualización en Nube/ML (cubierta por el Spec A).
