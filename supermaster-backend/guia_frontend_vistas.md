# Guia de Vistas para el Frontend — super-master-backend

## 1. PRODUCTOS (Vista principal)

**Tabla: `productos`** | Endpoint: `GET /api/productos`

La entidad central del sistema. Cada producto tiene SKU, descripcion, costo, IVA, atributos fisicos y relaciones con clasificaciones.

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Integer | ID unico |
| sku | String | Codigo SKU (unico) |
| codExt | String | Codigo externo |
| descripcion | String | Descripcion del producto |
| tituloWeb | String | Titulo para web |
| esCombo | Boolean | Si es combo |
| imagenUrl | String | URL de imagen |
| stock | Integer | Stock actual |
| activo | Boolean | Si esta activo |
| tagReposicion | Enum | Tag de reposicion (`PRIO`, `LIQ` — ver REFERENCIA DE ENUMS) |
| costo | BigDecimal | Costo del producto |
| fechaUltimoCosto | DateTime | Fecha del ultimo costo |
| iva | BigDecimal | Porcentaje de IVA |
| uxb, moq | Integer | Unidades por bulto, cantidad minima de orden |
| capacidad, largo, ancho, alto, diamboca, diambase, espesor | String/Decimal | Atributos fisicos |
| marcaId, origenId, clasifGralId, clasifGastroId, tipoId, proveedorId, materialId | Integer (FK) | Relaciones con maestros |

**Sub-recursos del producto:**

- **Margenes** (`GET /api/productos/{id}/margen`): margenMinorista, margenMayorista, margenFijoMinorista, margenFijoMayorista, notas
- **Aptos** (`GET /api/productos/{id}/aptos`): Relacion many-to-many con aptitudes
- **Catalogos** (`GET /api/productos/{id}/catalogos`): Relacion many-to-many con catalogos
- **Clientes** (`GET /api/productos/{id}/clientes`): Relacion many-to-many con clientes
- **Precios Inflados** (`GET /api/productos/{id}/canal-precios-inflados`): Asignacion de precio inflado por canal

### Botones sugeridos

| Boton | Endpoint | Tipo | Descripcion |
|-------|----------|------|-------------|
| "Nuevo producto" | `POST /api/productos` | Sincronico | Formulario de creacion. |
| "Editar" | `PUT /api/productos/{id}` | Sincronico | Formulario de edicion (por fila). |
| "Eliminar" | `DELETE /api/productos/{id}` | Sincronico | Confirmar antes de eliminar. |
| "Importar costos (Excel)" | `POST /api/excel/importar-costos` | Sincronico | Sube un archivo Excel con columnas: CODIGO, PRODUCTO, COSTO, CODIGO EXTERNO, PROVEEDOR, TIPO DE PRODUCTO, ULTIMA ACT. COSTO, UNIDADES POR BULTO, PORCENTAJE IVA. Devuelve estadisticas de lo importado. |
| "Importar migracion (Excel)" | `POST /api/excel/importar-migracion` | Sincronico | Sube el archivo SUPER MASTER.xlsm completo. Migracion unica. |

---

## 2. PRECIOS CALCULADOS (Vista de precios)

**Tabla: `producto_canal_precios`** | Endpoint: `GET /api/precios`

Muestra los productos con todos sus precios calculados por canal y cuotas. Esta es la vista mas compleja.

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| pvp | BigDecimal | Precio de venta al publico (base) |
| pvpInflado | BigDecimal | PVP con precio inflado aplicado (null si no aplica) |
| costoProducto | BigDecimal | Costo ajustado del producto |
| costosVenta | BigDecimal | Suma de costos de venta (comisiones, etc.) |
| ingresoNetoVendedor | BigDecimal | Ingreso neto despues de costos |
| ganancia | BigDecimal | Ganancia = ingreso neto - costo producto |
| margenSobreIngresoNeto | BigDecimal | % margen sobre ingreso neto |
| margenSobrePvp | BigDecimal | % margen sobre PVP |
| markupPorcentaje | BigDecimal | % markup sobre costo |
| cuotas | Integer | Cantidad de cuotas (null = contado) |
| fechaUltimoCalculo | DateTime | Cuando se calculo |

**Estructura de respuesta:** Cada producto tiene una lista de `canales`, y cada canal tiene una lista de `precios` (uno por opcion de cuotas).

**Acciones:**
- `POST /api/precios/calcular` para recalcular (por producto, canal, o masivo)
- `GET /api/precios/formula?productoId=X&canalId=Y&cuotas=Z` para ver el paso a paso del calculo

### Botones sugeridos

| Boton | Endpoint | Tipo | Descripcion |
|-------|----------|------|-------------|
| "Recalcular todos" | `POST /api/precios/calcular` (sin params) | Sincronico | Recalcula TODOS los productos en TODOS los canales. Operacion pesada. |
| "Recalcular producto" | `POST /api/precios/calcular?productoId={id}` | Sincronico | Recalcula un producto en todos sus canales y cuotas. Puede estar en la fila del producto. |
| "Recalcular producto en canal" | `POST /api/precios/calcular?productoId={id}&canalId={id}` | Sincronico | Recalcula un producto en un canal especifico (todas las cuotas). |
| "Recalcular producto en canal y cuotas" | `POST /api/precios/calcular?productoId={id}&canalId={id}&cuotas={n}` | Sincronico | Recalcula para una combinacion exacta. |
| "Ver formula" | `GET /api/precios/formula?productoId={id}&canalId={id}&cuotas={n}` | Sincronico | Abre un modal/panel con el desglose paso a paso del calculo. Requiere los 3 parametros. |
| "Exportar precios (completo)" | `GET /api/excel/exportar-precios?formato=completo` | Descarga Excel | Exporta todos los precios en formato completo. Soporta todos los filtros de la tabla. |
| "Exportar precios (MercadoLibre)" | `GET /api/excel/exportar-precios?formato=mercadolibre&cuotas={n}` | Descarga Excel | Exporta precios en formato MercadoLibre. Requiere `cuotas`. |
| "Exportar precios (KT Hogar)" | `GET /api/excel/exportar-precios?formato=kt-hogar&cuotas={n}` | Descarga Excel | Exporta precios en formato KT Hogar. Requiere `cuotas`. |
| "Exportar precios (KT Gastro)" | `GET /api/excel/exportar-precios?formato=kt-gastro&cuotas={n}` | Descarga Excel | Exporta precios en formato KT Gastro. Requiere `cuotas`. |
| "Exportar catalogo" | `GET /api/excel/exportar-catalogo?catalogoId={id}&canalId={id}&cuotas={n}` | Descarga Excel | Exporta un catalogo especifico. Requiere catalogo, canal y cuotas. Filtros opcionales: clasifGralId, clasifGastroId, tipoId, marcaId, esMaquina, ordenarPor. |

> **Nota sobre `cuotas`:** -1 = transferencia, 0 = contado, >0 = cantidad de cuotas.

> **Nota sobre exportar precios:** Los mismos filtros que se usan en la tabla de precios (sku, descripcion, search, marcaId, etc.) se pueden pasar como query params al endpoint de exportacion, para que el Excel refleje lo que el usuario ve filtrado.

---

## 3. CANALES

**Tabla: `canales`** | Endpoint: `GET /api/canales`

Canales de venta donde se publican productos (ej: MercadoLibre, KT Gastro, etc.).

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Integer | ID unico |
| canal | String | Nombre del canal |
| canalBaseId | Integer (FK) | Canal padre (para canales derivados) |

Los canales pueden ser jerarquicos (un canal puede tener un canal base del cual hereda configuracion).

---

## 4. CONCEPTOS DE CALCULO

**Tabla: `conceptos_calculo`** | Endpoint: `GET /api/conceptos-calculo`

Los conceptos son los porcentajes/flags que intervienen en el calculo de precios.

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Integer | ID unico |
| concepto | String | Nombre del concepto |
| porcentaje | BigDecimal | Valor del porcentaje |
| aplicaSobre | Enum | Donde aplica (ver REFERENCIA DE ENUMS → AplicaSobre) |
| descripcion | String | Descripcion |

**Tipos de `aplicaSobre`:** 19 valores posibles. Ver tabla completa con descripciones detalladas y etapas del calculo en la seccion **REFERENCIA DE ENUMS → AplicaSobre**.

---

## 5. CANAL - CONCEPTOS (Relacion)

**Tabla: `canal_concepto`** | Endpoint: `GET /api/canales/{canalId}/conceptos`

Asigna que conceptos de calculo aplican a cada canal. Relacion many-to-many entre Canal y ConceptoCalculo.

- `POST /api/canales/{canalId}/conceptos/{conceptoId}` — Asignar concepto
- `DELETE /api/canales/{canalId}/conceptos/{conceptoId}` — Quitar concepto

---

## 6. CANAL - CUOTAS

**Tabla: `canal_concepto_cuota`** | Endpoint: `GET /api/canal-concepto-cuotas`

Opciones de cuotas disponibles por canal con su porcentaje de recargo/descuento.

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Long | ID unico |
| canalId | Integer (FK) | Canal |
| cuotas | Integer | Cantidad de cuotas |
| porcentaje | BigDecimal | % de recargo/descuento |
| descripcion | String | Descripcion (ej: "3 cuotas sin interes") |

Endpoints adicionales:
- `GET /api/canal-concepto-cuotas/canal/{canalId}` — Listar por canal
- `GET /api/canal-concepto-cuotas/canal/{canalId}/cuotas/{cuotas}` — Buscar por canal y cantidad de cuotas

---

## 7. CANAL - REGLAS DE CONCEPTO

**Tabla: `canal_concepto_regla`** | Endpoint: `GET /api/canal-concepto-reglas`

Reglas condicionales que determinan si un concepto aplica o no a un producto especifico, segun sus atributos.

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Long | ID unico |
| canalId | Integer (FK) | Canal |
| conceptoId | Integer (FK) | Concepto de calculo |
| tipoRegla | Enum | `INCLUIR` (solo aplica si cumple) o `EXCLUIR` (no aplica si cumple) |
| tipoId | Integer (FK) | Condicion: tipo de producto |
| clasifGastroId | Integer (FK) | Condicion: clasificacion gastro |
| clasifGralId | Integer (FK) | Condicion: clasificacion general |
| marcaId | Integer (FK) | Condicion: marca |
| esMaquina | Boolean | Condicion: si es maquinaria |

Las condiciones se evaluan con logica AND. Conceptos sin reglas se incluyen por defecto.

Endpoints adicionales:
- `GET /api/canal-concepto-reglas/canal/{canalId}` — Listar por canal
- `GET /api/canal-concepto-reglas/concepto/{conceptoId}` — Listar por concepto

---

## 8. REGLAS DE DESCUENTO

**Tabla: `reglas_descuentos`** | Endpoint: `GET /api/reglas-descuento`

Descuentos **informativos** por monto de compra. No afectan el PVP calculado, solo se muestran al usuario como referencia.

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Integer | ID unico |
| canalId | Integer (FK) | Canal donde aplica |
| catalogoId | Integer (FK) | Catalogo (opcional) |
| clasifGralId | Integer (FK) | Clasificacion general (opcional) |
| clasifGastroId | Integer (FK) | Clasificacion gastro (opcional) |
| montoMinimo | BigDecimal | Monto minimo de compra |
| descuentoPorcentaje | BigDecimal | % de descuento |
| prioridad | Integer | Prioridad (mayor = mas importante) |
| activo | Boolean | Si esta activo |
| descripcion | String | Descripcion |

Endpoint adicional:
- `GET /api/reglas-descuento/canal/{canalId}` — Listar por canal

---

## 9. PRECIOS INFLADOS

**Tabla: `precios_inflados`** | Endpoint: `GET /api/precios-inflados`

Configuraciones de inflacion de precio que se asignan a combinaciones producto+canal.

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Integer | ID unico |
| codigo | String | Codigo unico |
| tipo | Enum | `MULTIPLICADOR`, `DESCUENTO_PORC`, `DIVISOR`, `PRECIO_FIJO` |
| valor | BigDecimal | Valor del multiplicador/descuento/divisor/precio fijo |

Endpoint adicional:
- `GET /api/precios-inflados/codigo/{codigo}` — Buscar por codigo

---

## 10. PROVEEDORES

**Tabla: `proveedores`** | Endpoint: `GET /api/proveedores`

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Integer | ID unico |
| proveedor | String | Nombre del proveedor |
| apodo | String | Nombre corto |
| plazoPago | String | Plazo de pago |
| entrega | Boolean | Si hace entregas |
| financiacionPorcentaje | BigDecimal | % de financiacion (afecta calculo de precios) |
| leadTimeDias | Integer | Tiempo de entrega en dias |

Endpoint adicional:
- `GET /api/proveedores/{id}/productos` — Listar productos del proveedor

---

## 11. MLAs (MercadoLibre)

**Tabla: `mlas`** | Endpoint: `GET /api/mlas`

Datos de publicaciones en MercadoLibre asociados a productos.

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Integer | ID unico |
| mla | String | Codigo MLA (ej: MLA123456) |
| mlau | String | Codigo MLAU (unico) |
| precioEnvio | BigDecimal | Costo de envio calculado |
| fechaCalculoEnvio | DateTime | Ultima fecha de calculo de envio |
| comisionPorcentaje | BigDecimal | % de comision de ML |
| fechaCalculoComision | DateTime | Ultima fecha de calculo de comision |
| topePromocion | Integer | Tope de promocion |

Endpoint adicional:
- `GET /api/mlas/{id}/productos` — Listar productos con este MLA

### Botones sugeridos

| Boton | Endpoint | Tipo | Descripcion |
|-------|----------|------|-------------|
| CRUD estandar | `POST/PUT/DELETE /api/mlas` | Sincronico | Crear, editar, eliminar MLAs. |

> **IMPORTANTE: NO implementar botones que hagan POST a `/api/ml/costo-envio` ni `/api/ml/costo-venta` desde esta vista. Estos endpoints interactuan con la API real de MercadoLibre y solo deben ejecutarse de forma controlada.**

---

## 12. ORDENES DE COMPRA

**Tabla: `ordenes_compra`** | Endpoint: `GET /api/ordenes-compra`

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Integer | ID unico |
| proveedorId | Integer (FK) | Proveedor |
| estado | Enum | Estado de la OC (ver REFERENCIA DE ENUMS → EstadoOrdenCompra) |
| observaciones | String | Notas |
| fechaCreacion | DateTime | Auto-generada |
| fechaModificacion | DateTime | Auto-actualizada |
| lineas | Lista | Lineas de la orden |

**Lineas de orden** (`orden_compra_lineas`):

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| id | Integer | ID unico |
| productoId | Integer (FK) | Producto |
| cantidadPedida | Integer | Cantidad pedida |
| cantidadRecibida | Integer | Cantidad recibida |
| costoUnitario | BigDecimal | Costo unitario |

Filtros disponibles: `proveedorId`, `estado`

### Botones sugeridos

| Boton | Endpoint | Tipo | Descripcion |
|-------|----------|------|-------------|
| "Nueva orden" | `POST /api/ordenes-compra` | Sincronico | Formulario de creacion con proveedor, observaciones y lineas. |
| "Editar" | `PUT /api/ordenes-compra/{id}` | Sincronico | Solo si esta en estado BORRADOR. |
| "Eliminar" | `DELETE /api/ordenes-compra/{id}` | Sincronico | Solo si esta en estado BORRADOR. |
| "Enviar" (por fila) | `POST /api/ordenes-compra/{id}/enviar` | Sincronico | Cambia el estado de la orden (deja de ser borrador). Mostrar en la fila. |
| "Registrar recepcion" (por fila) | `POST /api/ordenes-compra/{id}/recepcion` | Sincronico | Abre formulario para registrar cantidades recibidas. Envia un `RecepcionDTO` en el body. |

---

> **REPOSICION: esta seccion esta en desarrollo y no debe implementarse todavia.**

---

## 13. TABLAS MAESTRAS (ABM simples)

Todas siguen el mismo patron CRUD: `GET` (listado con busqueda paginada), `POST`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`.

| Tabla | Endpoint | Campo principal | Notas |
|-------|----------|-----------------|-------|
| **Marcas** | `/api/marcas` | nombre | Jerarquica (tiene submarcas) |
| **Tipos** | `/api/tipos` | nombre | Jerarquica (tiene subtipos) |
| **Origenes** | `/api/origenes` | origen | Simple |
| **Materiales** | `/api/materiales` | material | Simple |
| **Aptos** | `/api/aptos` | apto | Se asigna a productos (many-to-many) |
| **Catalogos** | `/api/catalogos` | catalogo | Tiene `exportarConIva` y `recargoPorcentaje` |
| **Clientes** | `/api/clientes` | cliente | Se asigna a productos (many-to-many) |
| **Clasif. General** | `/api/clasif-gral` | nombre | Jerarquica (subclasificaciones) |
| **Clasif. Gastro** | `/api/clasif-gastro` | nombre | Jerarquica, tiene flag `esMaquina` |

---

## 14. INTEGRACIONES EXTERNAS

### MercadoLibre (`/api/ml`)

Vista de configuracion y acciones de calculo de costos de MercadoLibre.

> **IMPORTANTE: NO implementar ningun boton que haga POST a endpoints de MercadoLibre (`/api/ml/costo-envio`, `/api/ml/costo-venta`). Estos endpoints interactuan con la API real de MercadoLibre y solo deben ejecutarse de forma controlada. Usar solo los endpoints GET para consultar datos ya calculados.**

#### Endpoints disponibles (solo lectura)

| Boton | Endpoint | Tipo | Descripcion |
|-------|----------|------|-------------|
| "Configuracion ML" | `GET /api/ml/configuracion` | Sincronico | Muestra formulario con umbral envio gratis y tiers de costo de envio. |
| "Guardar configuracion" | `PUT /api/ml/configuracion` | Sincronico | Guarda cambios de configuracion. |

#### Endpoints restringidos (NO exponer como botones)

Los siguientes endpoints existen pero **NO deben tener botones en el frontend**:

- `POST /api/ml/costo-envio` — Calcula costos de envio (individual o masivo) contra la API de ML
- `POST /api/ml/costo-envio/cancelar` — Cancelar proceso masivo
- `POST /api/ml/costo-venta` — Calcula comisiones (individual o masivo) contra la API de ML
- `POST /api/ml/costo-venta/cancelar` — Cancelar proceso masivo

#### Endpoints de consulta (estos SI se pueden usar)

- `GET /api/ml/costo-envio/estado` — Estado del proceso masivo de envio
- `GET /api/ml/costo-envio/resultado` — Resultados del proceso masivo de envio
- `GET /api/ml/costo-venta/estado` — Estado del proceso masivo de comision
- `GET /api/ml/costo-venta/resultado` — Resultados del proceso masivo de comision

---

### DUX ERP (`/api/dux`)

Vista de integracion con el sistema ERP DUX. Permite consultar datos del ERP.

> **IMPORTANTE: NO implementar ningun boton que haga POST a endpoints de DUX. Estos endpoints interactuan con la API real del ERP DUX y solo deben ejecutarse de forma controlada. Usar solo los endpoints GET para consultar datos.**

- `GET /api/dux/status` — Verificar si la integracion esta configurada

#### Endpoints disponibles (solo lectura)

| Boton | Endpoint | Tipo | Descripcion |
|-------|----------|------|-------------|
| "Buscar producto DUX" | `GET /api/dux/productos/{codItem}` | Sincronico | Busca un producto especifico en DUX por codigo. |
| "Ver listas de precios" | `GET /api/dux/listas-precios` | Sincronico | Muestra las listas de precios disponibles en DUX. |
| "Obtener ID de lista" | `GET /api/dux/listas-precios/{nombre}/id` | Sincronico | Obtiene el ID de una lista de precios por nombre. |
| "Ver empresas" | `GET /api/dux/empresas` | Sincronico | Lista las empresas configuradas en DUX. |
| "Ver sucursales" | `GET /api/dux/empresas/{idEmpresa}/sucursales` | Sincronico | Lista sucursales de una empresa. |

#### Endpoints restringidos (NO exponer como botones)

Los siguientes endpoints existen pero **NO deben tener botones en el frontend**:

- `POST /api/dux/obtener-productos` — Obtener productos desde DUX
- `POST /api/dux/obtener-productos/cancelar` — Cancelar obtencion de productos
- `POST /api/dux/importar-productos` — Importar productos al sistema
- `POST /api/dux/importar-productos/cancelar` — Cancelar importacion
- `POST /api/dux/listas-precios/{idLista}/precios` — Actualizar precios en lista
- `POST /api/dux/exportar-productos` — Exportar productos a DUX

#### Endpoints de consulta de procesos (estos SI se pueden usar)

- `GET /api/dux/obtener-productos/estado` — Progreso de obtencion
- `GET /api/dux/obtener-productos/resultado` — Resultados de obtencion
- `GET /api/dux/importar-productos/estado` — Progreso de importacion
- `GET /api/dux/importar-productos/resultado` — Resultados de importacion
- `GET /api/dux/procesos/{idProceso}/estado` — Estado de un proceso DUX

---

### Tienda Nube (`/api/nube`)

> **IMPORTANTE: NO implementar ningun boton que haga peticiones a endpoints de Tienda Nube. Estos endpoints interactuan con la API real de Tienda Nube y solo deben ejecutarse de forma controlada.**

Endpoints disponibles solo como referencia (no exponer en el frontend por ahora):

- `GET /api/nube/status` — Verificar estado
- `GET /api/nube/ventas` — Todas las ventas
- `GET /api/nube/ventas/hogar` — Ventas Hogar
- `GET /api/nube/ventas/gastro` — Ventas Gastro
- `GET /api/nube/stock/{sku}` — Stock por SKU
- `GET /api/nube/ordenes/{numero}` — Orden por numero

---

## 15. EXCEL (Import/Export) — Resumen de todos los endpoints

Todos los endpoints de Excel agrupados en un solo lugar para referencia rapida.

### Importacion

| Boton | Endpoint | Metodo | Descripcion |
|-------|----------|--------|-------------|
| "Importar costos" | `/api/excel/importar-costos` | POST (multipart) | Sube Excel con costos. Columnas esperadas: CODIGO, PRODUCTO, COSTO, CODIGO EXTERNO, PROVEEDOR, TIPO DE PRODUCTO, ULTIMA ACT. COSTO, UNIDADES POR BULTO, PORCENTAJE IVA. Devuelve estadisticas. |
| "Importar migracion" | `/api/excel/importar-migracion` | POST (multipart) | Sube el archivo SUPER MASTER.xlsm completo. Migracion unica inicial. Devuelve estadisticas por hoja. |

### Exportacion

| Boton | Endpoint | Metodo | Parametros | Descripcion |
|-------|----------|--------|------------|-------------|
| "Exportar precios (completo)" | `/api/excel/exportar-precios` | GET | `formato=completo` + filtros opcionales | Descarga Excel con todos los precios. Acepta los mismos filtros que la tabla de precios. |
| "Exportar precios (MercadoLibre)" | `/api/excel/exportar-precios` | GET | `formato=mercadolibre`, `cuotas` (requerido) + filtros | Descarga Excel en formato MercadoLibre. |
| "Exportar precios (KT Hogar)" | `/api/excel/exportar-precios` | GET | `formato=kt-hogar`, `cuotas` (requerido) + filtros | Descarga Excel en formato KT Hogar. |
| "Exportar precios (KT Gastro)" | `/api/excel/exportar-precios` | GET | `formato=kt-gastro`, `cuotas` (requerido) + filtros | Descarga Excel en formato KT Gastro. |
| "Exportar catalogo" | `/api/excel/exportar-catalogo` | GET | `catalogoId`, `canalId`, `cuotas` (todos requeridos). Opcionales: `clasifGralId`, `clasifGastroId`, `tipoId`, `marcaId`, `esMaquina`, `ordenarPor` | Descarga Excel de un catalogo especifico. |
| ~~"Exportar sugerencias reposicion"~~ | `/api/reposicion/resultado/excel` | GET | ninguno | **EN DESARROLLO** — No implementar todavia. |
| ~~"Exportar orden de compra"~~ | `/api/reposicion/resultado/excel/oc/{id}` | GET | `id` en la URL | **EN DESARROLLO** — No implementar todavia. |

> **Nota sobre `cuotas`:** -1 = transferencia, 0 = contado, >0 = cantidad de cuotas.

> **Nota sobre filtros de exportacion de precios:** Los mismos filtros disponibles en `GET /api/precios` se pueden pasar como query params: search, sku, descripcion, marcaIds, tipoIds, origenIds, clasifGralIds, clasifGastroIds, proveedorIds, materialIds, esMaquina, activo, esCombo, canalId, rangos de costo/iva/stock/pvp, y mas.

---

## Resumen visual de relaciones

```
Producto -----+---- Marca
              |---- Tipo
              |---- Origen
              |---- ClasifGral
              |---- ClasifGastro
              |---- Material
              |---- Proveedor ---------- OrdenCompra ---- OrdenCompraLinea
              |---- MLA
              |---- ProductoMargen
              |---- *Aptos (M:M)
              |---- *Catalogos (M:M)
              |---- *Clientes (M:M)
              +---- ProductoCanalPrecio ---- Canal ----+---- *ConceptoCalculo (M:M)
                                                       |---- CanalConceptoCuota
                                                       |---- CanalConceptoRegla
                                                       |---- ReglaDescuento
                                                       +---- Canal (base/padre)
```

---

## Notas generales para el frontend

- **Paginacion**: Todos los listados soportan `page`, `size`, `sort`
- **Busqueda**: Parametro `search` (case-insensitive, parcial) en la mayoria de endpoints
- **Formato de error**: `{ "message": "...", "path": "/api/..." }`
- **Codigos HTTP**: 200 (OK), 201 (Created), 204 (No Content en DELETE), 400 (validacion), 404 (no encontrado), 409 (conflicto/duplicado), 500 (error interno)

### Botones CRUD estandar (aplica a todas las tablas maestras y la mayoria de vistas)

Todas las vistas tienen como minimo:
- **"Nuevo"** — `POST /api/{recurso}` — Abre formulario de creacion
- **"Editar"** (por fila) — `PUT /api/{recurso}/{id}` — Abre formulario de edicion
- **"Eliminar"** (por fila) — `DELETE /api/{recurso}/{id}` — Confirmar antes de eliminar

### Patron asincrono (referencia tecnica)

Los procesos masivos de ML, DUX y Reposicion siguen este patron internamente:

```
1. POST .../iniciar          → Devuelve 202 Accepted (o 409 si ya hay uno corriendo)
2. GET  .../estado           → Polling para mostrar progreso (porcentaje, mensaje)
3. POST .../cancelar         → Cancela el proceso en curso
4. GET  .../resultado        → Obtiene los resultados cuando finaliza
```

> **IMPORTANTE: Los POST de procesos masivos de ML, DUX, Nube y Reposicion NO deben tener botones en el frontend.** Solo se pueden usar los GET de estado y resultado para consultar procesos que se hayan iniciado desde otro lugar.

---

# SUGERENCIA DE DISEÑO DE VISTAS

A continuacion se detalla como deberia verse cada vista, que columnas mostrar en la tabla, que botones incluir y donde ubicarlos.

---

## VISTA: Productos

**Ruta sugerida:** `/productos`

### Barra superior (toolbar)

| Elemento | Posicion | Descripcion |
|----------|----------|-------------|
| Buscador (`search`) | Izquierda | Input de texto para busqueda general (busca en SKU, descripcion, titulo web) |
| Filtros avanzados | Izquierda (desplegable) | Panel/dropdown con filtros (ver detalle abajo) |
| "Importar costos (Excel)" | Derecha | Boton que abre file picker. `POST /api/excel/importar-costos`. Mostrar resultado con estadisticas al terminar. |
| "Nuevo producto" | Derecha | Boton primario. Abre formulario/modal de creacion. |

### Filtros avanzados disponibles (query params de `GET /api/productos`)

| Categoria | Parametros | Tipo |
|-----------|------------|------|
| Texto | `sku`, `codExt`, `descripcion`, `tituloWeb` | String (parcial, case-insensitive) |
| Booleanos | `activo`, `esCombo`, `esMaquina`, `tieneMla` | Boolean |
| Clasificaciones | `marcaIds`, `tipoIds`, `origenIds`, `clasifGralIds`, `clasifGastroIds`, `proveedorIds`, `materialIds` | List\<Integer\> (multi-valor) |
| Many-to-Many | `aptoIds`, `catalogoIds`, `clienteIds`, `canalIds`, `mlaIds` | List\<Integer\> (multi-valor) |
| Rangos numericos | `costoMin`/`costoMax`, `ivaMin`/`ivaMax`, `stockMin`/`stockMax`, `pvpMin`/`pvpMax` (+ `pvpCanalId`) | BigDecimal / Integer |
| Rangos de fecha | `desdeFechaUltimoCosto`/`hastaFechaUltimoCosto`, `desdeFechaCreacion`/`hastaFechaCreacion`, `desdeFechaModificacion`/`hastaFechaModificacion` | LocalDate (yyyy-MM-dd) |
| MLA | `mla`, `mlau`, `precioEnvioMin`/`precioEnvioMax`, `comisionPorcentajeMin`/`comisionPorcentajeMax`, `tieneComision`, `tienePrecioEnvio` | String / BigDecimal / Boolean |
| Tag | `tagReposicion` | Enum: `PRIO`, `LIQ` |

> **Nota:** Los filtros multi-valor (ej: `marcaIds=1&marcaIds=2`) filtran con logica OR dentro de la misma categoria.

### Columnas de la tabla

> **Nota:** `GET /api/productos` devuelve IDs de relaciones (marcaId, tipoId, etc.), no nombres. El frontend debe resolver los nombres cargando los maestros (ej: `GET /api/marcas`) al inicio y mapeando ID → nombre, o bien usar el endpoint `GET /api/precios` que devuelve los nombres directamente (`marcaNombre`, `tipoNombre`, etc.).

| Columna | Campo | Ordenable | Notas |
|---------|-------|-----------|-------|
| SKU | `sku` | Si | Texto corto, identificador principal |
| Descripcion | `descripcion` | Si | Columna mas ancha |
| Marca | `marcaId` → resolver nombre | Si | Cargar marcas al inicio y mapear |
| Tipo | `tipoId` → resolver nombre | Si | Cargar tipos al inicio y mapear |
| Costo | `costo` | Si | Formato moneda ($X.XX) |
| IVA % | `iva` | Si | Porcentaje |
| Stock | `stock` | Si | Numero |
| Activo | `activo` | Si | Chip o icono (verde/rojo) |
| Proveedor | `proveedorId` → resolver nombre | Si | Cargar proveedores al inicio y mapear |
| Acciones | — | No | Botones por fila (ver abajo) |

### Botones por fila (columna Acciones)

| Boton | Accion |
|-------|--------|
| Editar (icono lapiz) | Abre formulario de edicion con los datos del producto |
| Eliminar (icono papelera) | Modal de confirmacion → `DELETE /api/productos/{id}` |

### Detalle del producto (al hacer click en una fila o boton "ver")

Panel lateral o pagina de detalle con pestañas:

| Pestaña | Contenido | Endpoint |
|---------|-----------|----------|
| **General** | Todos los campos del producto (formulario de edicion) | `GET /api/productos/{id}` |
| **Margenes** | Formulario: margenMinorista, margenMayorista, margenFijoMinorista, margenFijoMayorista, notas. Boton "Guardar". | `GET/PUT /api/productos/{id}/margen` |
| **Aptos** | Lista de aptos asignados + selector para agregar. Boton "X" para quitar. | `GET/POST/DELETE /api/productos/{id}/aptos/{aptoId}` |
| **Catalogos** | Lista de catalogos asignados + selector para agregar. Boton "X" para quitar. | `GET/POST/DELETE /api/productos/{id}/catalogos/{catalogoId}` |
| **Clientes** | Lista de clientes asignados + selector para agregar. Boton "X" para quitar. | `GET/POST/DELETE /api/productos/{id}/clientes/{clienteId}` |
| **Precios inflados** | Lista de asignaciones canal+precioInflado. Boton agregar/quitar. | `GET/POST/DELETE /api/productos/{id}/canal-precios-inflados` |

### Formulario de creacion/edicion del producto

Organizar en secciones:

**Seccion 1 - Datos basicos:**
- SKU (requerido, unico)
- Descripcion (requerido)
- Titulo web
- Codigo externo
- Es combo (checkbox)
- Activo (checkbox)
- Imagen URL

**Seccion 2 - Costos:**
- Costo (decimal)
- IVA % (requerido)

**Seccion 3 - Clasificacion (dropdowns cargados desde las APIs de maestros):**
- Marca (`GET /api/marcas`)
- Tipo (`GET /api/tipos`, requerido)
- Origen (`GET /api/origenes`, requerido)
- Clasif. General (`GET /api/clasif-gral`, requerido)
- Clasif. Gastro (`GET /api/clasif-gastro`)
- Material (`GET /api/materiales`)
- Proveedor (`GET /api/proveedores`)

**Seccion 4 - Atributos fisicos:**
- UxB, MOQ, Capacidad, Largo, Ancho, Alto, Diamboca, Diambase, Espesor

**Seccion 5 - Stock:**
- Stock (numero)

---

## VISTA: Precios calculados

**Ruta sugerida:** `/precios`

### Barra superior (toolbar)

| Elemento | Posicion | Descripcion |
|----------|----------|-------------|
| Buscador (`search`) | Izquierda | Busca en SKU, descripcion |
| Filtro de canal | Izquierda | Dropdown para filtrar por canal (carga desde `GET /api/canales`) |
| Filtro de cuotas | Izquierda | Dropdown: Transferencia (-1), Contado (0), 3, 6, 12, etc. |
| Filtros avanzados | Izquierda (desplegable) | Mismos filtros que la vista de Productos (ver tabla de filtros avanzados arriba) + `canalId` y `cuotas` para filtrar precios especificos |
| "Recalcular todos" | Derecha | Boton con confirmacion ("Esto recalculara todos los productos. Continuar?"). `POST /api/precios/calcular` |
| Dropdown "Exportar" | Derecha | Menu desplegable con opciones de exportacion (ver abajo) |

### Opciones del dropdown "Exportar"

| Opcion | Accion |
|--------|--------|
| "Precios (completo)" | `GET /api/excel/exportar-precios?formato=completo` + filtros activos |
| "Precios (MercadoLibre)" | Pide seleccionar cuotas → `GET /api/excel/exportar-precios?formato=mercadolibre&cuotas={n}` |
| "Precios (KT Hogar)" | Pide seleccionar cuotas → `GET /api/excel/exportar-precios?formato=kt-hogar&cuotas={n}` |
| "Precios (KT Gastro)" | Pide seleccionar cuotas → `GET /api/excel/exportar-precios?formato=kt-gastro&cuotas={n}` |
| "Catalogo" | Pide seleccionar catalogo, canal y cuotas → `GET /api/excel/exportar-catalogo?catalogoId=X&canalId=Y&cuotas=Z` |

### Columnas de la tabla

La tabla tiene una estructura anidada: cada fila es un producto, y al expandir muestra los precios por canal y cuotas.

**Nivel 1 - Fila del producto:**

| Columna | Campo | Ordenable | Notas |
|---------|-------|-----------|-------|
| Expandir | — | No | Icono ">" para expandir y ver precios por canal |
| SKU | `sku` | Si | |
| Descripcion | `descripcion` | Si | |
| Costo | `costo` | Si | Formato moneda |
| IVA % | `iva` | Si | |
| Marca | `marcaNombre` | Si | |
| Acciones | — | No | Boton "Recalcular" por fila |

**Nivel 2 - Sub-tabla por canal (al expandir):**

| Columna | Campo | Notas |
|---------|-------|-------|
| Canal | `canalNombre` | Nombre del canal |
| Cuotas | `cuotas` | null=contado, -1=transferencia |
| PVP | `pvp` | Formato moneda |
| PVP Inflado | `pvpInflado` | Formato moneda (mostrar solo si no es null) |
| Costo Producto | `costoProducto` | Formato moneda |
| Costos Venta | `costosVenta` | Formato moneda |
| Ingreso Neto | `ingresoNetoVendedor` | Formato moneda |
| Ganancia | `ganancia` | Formato moneda. Color verde si > 0, rojo si < 0 |
| Margen s/INV % | `margenSobreIngresoNeto` | Porcentaje |
| Margen s/PVP % | `margenSobrePvp` | Porcentaje |
| Markup % | `markupPorcentaje` | Porcentaje |
| Ultimo calculo | `fechaUltimoCalculo` | Fecha relativa ("hace 2 hs") |
| Descuentos | `descuentos` | Lista de descuentos aplicables (informativo) |
| Acciones | — | Boton "Ver formula" |

### Botones por fila

| Boton | Nivel | Accion |
|-------|-------|--------|
| "Recalcular" | Producto | `POST /api/precios/calcular?productoId={id}` — Recalcula en todos los canales |
| "Ver formula" | Canal+Cuotas | Abre modal con el desglose del calculo. `GET /api/precios/formula?productoId={id}&canalId={id}&cuotas={n}` |

---

## VISTA: Canales

**Ruta sugerida:** `/canales`

### Barra superior

| Elemento | Posicion | Descripcion |
|----------|----------|-------------|
| Buscador (`search`) | Izquierda | Busca por nombre de canal |
| "Nuevo canal" | Derecha | Boton primario |

### Columnas de la tabla

| Columna | Campo | Ordenable | Notas |
|---------|-------|-----------|-------|
| ID | `id` | Si | |
| Canal | `canal` | Si | Nombre |
| Canal base | `canalBaseId` | Si | Mostrar nombre del canal base (o "—" si no tiene) |
| Acciones | — | No | Editar, Eliminar, Ver detalle |

### Detalle del canal (al hacer click en una fila)

Panel lateral o pagina con pestañas:

> **Nota:** Varias pestañas devuelven solo IDs en sus DTOs. Cargar los maestros necesarios al inicio (conceptos, tipos, marcas, clasif. gral, clasif. gastro, catalogos) y resolver nombres en el frontend.

| Pestaña | Contenido | Botones | Endpoint |
|---------|-----------|---------|----------|
| **Conceptos** | Lista de conceptos asignados al canal. Resolver nombre, porcentaje y aplicaSobre desde `GET /api/conceptos-calculo`. | "Agregar concepto" (selector dropdown), "X" para quitar | `GET /api/canales/{canalId}/conceptos` |
| **Cuotas** | Tabla de cuotas: cuotas, porcentaje, descripcion. | "Nueva cuota", Editar, Eliminar por fila | `GET /api/canal-concepto-cuotas/canal/{canalId}` |
| **Reglas** | Tabla de reglas: concepto, tipoRegla, condiciones. Resolver nombres de tipo, marca, clasifGral, clasifGastro desde maestros. | "Nueva regla", Editar, Eliminar por fila | `GET /api/canal-concepto-reglas/canal/{canalId}` |
| **Reglas descuento** | Tabla: montoMinimo, descuento%, prioridad, activo. Resolver nombre de catalogo, clasifGral, clasifGastro. | "Nueva regla", Editar, Eliminar por fila | `GET /api/reglas-descuento/canal/{canalId}` |

---

## VISTA: Conceptos de calculo

**Ruta sugerida:** `/conceptos-calculo`

### Barra superior

| Elemento | Posicion | Descripcion |
|----------|----------|-------------|
| Buscador (`search`) | Izquierda | Busca por nombre del concepto |
| "Nuevo concepto" | Derecha | Boton primario |

### Columnas de la tabla

| Columna | Campo | Ordenable | Notas |
|---------|-------|-----------|-------|
| ID | `id` | Si | |
| Concepto | `concepto` | Si | Nombre |
| Porcentaje | `porcentaje` | Si | Formato porcentaje (X.XXX%) |
| Aplica sobre | `aplicaSobre` | Si | Mostrar como chip/badge legible |
| Descripcion | `descripcion` | No | Texto descriptivo |
| Acciones | — | No | Editar, Eliminar |

### Formulario de creacion/edicion

- Concepto (texto, requerido)
- Porcentaje (decimal)
- Aplica sobre (dropdown con los 19 valores del enum)
- Descripcion (texto)

---

## VISTA: Reglas de descuento

**Ruta sugerida:** `/reglas-descuento`

### Barra superior

| Elemento | Posicion | Descripcion |
|----------|----------|-------------|
| Filtro de canal | Izquierda | Dropdown para filtrar por canal (requerido o "todos"). Usar `GET /api/reglas-descuento/canal/{canalId}` para filtrar. |
| "Nueva regla" | Derecha | Boton primario |

### Columnas de la tabla

> **Nota:** El DTO devuelve IDs de relaciones (canalId, catalogoId, etc.). El frontend debe resolver los nombres cargando los maestros correspondientes.

| Columna | Campo | Ordenable | Notas |
|---------|-------|-----------|-------|
| ID | `id` | Si | |
| Canal | `canalId` → resolver nombre | Si | Mapear desde canales |
| Catalogo | `catalogoId` → resolver nombre | Si | Mostrar nombre (o "—" si null) |
| Clasif. Gral | `clasifGralId` → resolver nombre | Si | Mostrar nombre (o "—" si null) |
| Clasif. Gastro | `clasifGastroId` → resolver nombre | Si | Mostrar nombre (o "—" si null) |
| Monto minimo | `montoMinimo` | Si | Formato moneda |
| Descuento % | `descuentoPorcentaje` | Si | Porcentaje |
| Prioridad | `prioridad` | Si | Numero |
| Activo | `activo` | Si | Chip verde/rojo |
| Descripcion | `descripcion` | No | |
| Acciones | — | No | Editar, Eliminar |

> Nota: Estas reglas son informativas. Se muestran al usuario en la vista de precios pero no afectan el calculo del PVP.

---

## VISTA: Precios inflados

**Ruta sugerida:** `/precios-inflados`

### Barra superior

| Elemento | Posicion | Descripcion |
|----------|----------|-------------|
| Buscador (`search`) | Izquierda | Busca por codigo |
| "Nuevo precio inflado" | Derecha | Boton primario |

### Columnas de la tabla

| Columna | Campo | Ordenable | Notas |
|---------|-------|-----------|-------|
| ID | `id` | Si | |
| Codigo | `codigo` | Si | Identificador unico |
| Tipo | `tipo` | Si | Chip: MULTIPLICADOR, DESCUENTO_PORC, DIVISOR, PRECIO_FIJO |
| Valor | `valor` | Si | Numero decimal. La interpretacion depende del tipo. |
| Acciones | — | No | Editar, Eliminar |

### Formulario de creacion/edicion

- Codigo (texto, requerido, unico)
- Tipo (dropdown: Multiplicador, Descuento %, Divisor, Precio fijo)
- Valor (decimal, requerido)

---

## VISTA: Proveedores

**Ruta sugerida:** `/proveedores`

### Barra superior

| Elemento | Posicion | Descripcion |
|----------|----------|-------------|
| Buscador (`search`) | Izquierda | Busca por nombre/apodo |
| "Nuevo proveedor" | Derecha | Boton primario |

### Columnas de la tabla

| Columna | Campo | Ordenable | Notas |
|---------|-------|-----------|-------|
| ID | `id` | Si | |
| Proveedor | `proveedor` | Si | Nombre completo |
| Apodo | `apodo` | Si | Nombre corto |
| Plazo pago | `plazoPago` | Si | Texto libre |
| Entrega | `entrega` | Si | Chip si/no |
| Financiacion % | `financiacionPorcentaje` | Si | Porcentaje (afecta calculo de precios) |
| Lead time (dias) | `leadTimeDias` | Si | Numero |
| Acciones | — | No | Editar, Eliminar, Ver productos |

### Botones por fila

| Boton | Accion |
|-------|--------|
| Editar | Formulario de edicion |
| Eliminar | Confirmacion → DELETE |
| "Ver productos" | Abre lista filtrada de productos de este proveedor. `GET /api/proveedores/{id}/productos` |

---

## VISTA: MLAs (MercadoLibre)

**Ruta sugerida:** `/mlas`

### Barra superior

| Elemento | Posicion | Descripcion |
|----------|----------|-------------|
| Buscador (`search`) | Izquierda | Busca por codigo MLA/MLAU |
| "Nuevo MLA" | Derecha | Boton primario |

### Columnas de la tabla

| Columna | Campo | Ordenable | Notas |
|---------|-------|-----------|-------|
| ID | `id` | Si | |
| MLA | `mla` | Si | Codigo MLA |
| MLAU | `mlau` | Si | Codigo MLAU (unico) |
| Precio envio | `precioEnvio` | Si | Formato moneda (solo lectura, calculado por el backend) |
| Fecha calculo envio | `fechaCalculoEnvio` | Si | Fecha relativa |
| Comision % | `comisionPorcentaje` | Si | Porcentaje (solo lectura, calculado por el backend) |
| Fecha calculo comision | `fechaCalculoComision` | Si | Fecha relativa |
| Tope promocion | `topePromocion` | Si | Numero |
| Acciones | — | No | Editar, Eliminar, Ver productos |

> **IMPORTANTE: NO agregar botones que hagan POST a `/api/ml/`. Estos endpoints interactuan con la API real de MercadoLibre y solo deben ejecutarse de forma controlada.**

---

## VISTA: Ordenes de compra

**Ruta sugerida:** `/ordenes-compra`

### Barra superior

| Elemento | Posicion | Descripcion |
|----------|----------|-------------|
| Filtro de proveedor | Izquierda | Dropdown |
| Filtro de estado | Izquierda | Dropdown con todos los valores del enum EstadoOrdenCompra (ver REFERENCIA DE ENUMS) |
| "Nueva orden" | Derecha | Boton primario |

### Columnas de la tabla

| Columna | Campo | Ordenable | Notas |
|---------|-------|-----------|-------|
| ID | `id` | Si | |
| Proveedor | `proveedorNombre` | Si | Ya viene resuelto en el DTO |
| Estado | `estado` | Si | Chip con color segun estado (ver colores sugeridos en REFERENCIA DE ENUMS → EstadoOrdenCompra) |
| Cant. lineas | `lineas.length` | No | Cantidad de lineas en la orden |
| Observaciones | `observaciones` | No | Texto truncado |
| Creacion | `fechaCreacion` | Si | Fecha |
| Modificacion | `fechaModificacion` | Si | Fecha |
| Acciones | — | No | Botones segun estado (ver abajo) |

### Botones por fila (dependen del estado)

| Estado | Botones disponibles |
|--------|---------------------|
| BORRADOR | Editar, Eliminar, "Enviar" |
| ENVIADA | "Registrar recepcion" |
| RECIBIDA_PARCIAL | "Registrar recepcion" (para completar la recepcion) |
| COMPLETA | Solo lectura |
| CANCELADA | Solo lectura |

### Detalle de la orden (al hacer click o editar)

Formulario con:
- Proveedor (dropdown, solo lectura si no es borrador)
- Observaciones (textarea)
- **Tabla de lineas:**

| Columna | Campo | Notas |
|---------|-------|-------|
| Producto | `productoId`, `productoSku`, `productoDescripcion` | Selector de producto. El DTO devuelve SKU y descripcion resueltos. |
| Cantidad pedida | `cantidadPedida` | Input numerico |
| Cantidad recibida | `cantidadRecibida` | Input numerico (solo en recepcion) |
| Costo unitario | `costoUnitario` | Input decimal |
| Acciones | — | Eliminar linea |

Boton "Agregar linea" al final de la tabla.

---

## VISTA: Tablas maestras (ABM simples)

Cada tabla maestra sigue el mismo patron. Se pueden hacer como paginas separadas o como tabs dentro de una pagina "Configuracion" o "Maestros".

**Rutas sugeridas:** `/marcas`, `/tipos`, `/origenes`, `/materiales`, `/aptos`, `/catalogos`, `/clientes`, `/clasif-gral`, `/clasif-gastro`

### Patron comun para todas

**Barra superior:**
- Buscador (`search`) a la izquierda
- Boton "Nuevo {recurso}" a la derecha

**Columnas base:**
- ID
- Nombre/campo principal
- Acciones (Editar, Eliminar)

### Variaciones por tabla

| Tabla | Columnas adicionales | Formulario extra | Notas |
|-------|----------------------|------------------|-------|
| **Marcas** | padre (nombre de marca padre) | Dropdown "Marca padre" (opcional) | Arbol jerarquico: mostrar como lista con indentacion o con filtro de padre |
| **Tipos** | padre (nombre de tipo padre) | Dropdown "Tipo padre" (opcional) | Arbol jerarquico |
| **Origenes** | — | — | Tabla simple, campo: `origen` |
| **Materiales** | — | — | Tabla simple, campo: `material` |
| **Aptos** | — | — | Tabla simple, campo: `apto` |
| **Catalogos** | exportarConIva (chip si/no), recargoPorcentaje (%) | Checkbox "Exportar con IVA", Input "Recargo %" | |
| **Clientes** | — | — | Tabla simple, campo: `cliente` |
| **Clasif. General** | padre (nombre de clasif padre) | Dropdown "Clasificacion padre" (opcional) | Arbol jerarquico |
| **Clasif. Gastro** | padre, esMaquina (chip si/no) | Dropdown "Clasificacion padre" (opcional), Checkbox "Es maquina" | `esMaquina` afecta las reglas de concepto en el calculo de precios |

### Sugerencia de navegacion para jerarquicas (Marcas, Tipos, Clasif. Gral, Clasif. Gastro)

Dos opciones:
1. **Vista plana con filtro:** Tabla con todas las entidades + columna "Padre" + filtro dropdown por padre
2. **Vista arbol:** Tree view con expand/collapse de niveles

---

## VISTA: Configuracion MercadoLibre

**Ruta sugerida:** `/configuracion/ml`

Formulario simple (no es una tabla):

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| Umbral envio gratis | Decimal | Monto a partir del cual el envio es gratis |
| Tier 1 - Hasta | Decimal | Limite superior del tier 1 |
| Tier 1 - Costo | Decimal | Costo de envio del tier 1 |
| Tier 2 - Hasta | Decimal | Limite superior del tier 2 |
| Tier 2 - Costo | Decimal | Costo de envio del tier 2 |
| Tier 3 - Costo | Decimal | Costo de envio del tier 3 (para montos mayores al tier 2) |

Boton "Guardar" → `PUT /api/ml/configuracion`

---

## VISTA: DUX (solo consulta)

**Ruta sugerida:** `/configuracion/dux`

| Elemento | Endpoint | Descripcion |
|----------|----------|-------------|
| Estado de integracion | `GET /api/dux/status` | Mostrar si esta configurado (chip verde/rojo) |
| Buscador de producto | `GET /api/dux/productos/{codItem}` | Input + boton "Buscar". Muestra resultado en panel. |
| Listas de precios | `GET /api/dux/listas-precios` | Tabla de solo lectura con las listas disponibles. |
| ID de lista por nombre | `GET /api/dux/listas-precios/{nombre}/id` | Obtiene el ID numerico de una lista por su nombre. |
| Empresas | `GET /api/dux/empresas` | Tabla de solo lectura. Al hacer click en una, mostrar sucursales. |

> **Recordar: NO exponer botones de POST (importar, exportar, actualizar precios). Solo consulta.**

---

## REFERENCIA DE ENUMS

Todos los enums que el frontend necesita para dropdowns, chips, badges y filtros.

### AplicaSobre (19 valores)

Usado en: Conceptos de calculo (`conceptos_calculo.aplica_sobre`)

| Valor | Etapa | Descripcion |
|-------|-------|-------------|
| `GASTO_SOBRE_COSTO` | Costo | Gasto que multiplica el costo base: COSTO x (1 + %/100) |
| `FLAG_FINANCIACION_PROVEEDOR` | Costo | Flag: usa proveedor.porcentaje para financiacion |
| `AJUSTE_MARGEN_PUNTOS` | Margen | Suma/resta puntos porcentuales al margen |
| `AJUSTE_MARGEN_PROPORCIONAL` | Margen | Ajusta el margen proporcionalmente: MARGEN x (1 + %/100) |
| `FLAG_USAR_MARGEN_MINORISTA` | Margen | Flag: usa margenMinorista del producto |
| `FLAG_USAR_MARGEN_MAYORISTA` | Margen | Flag: usa margenMayorista del producto |
| `GASTO_POST_GANANCIA` | Margen | Gasto despues de ganancia, antes de impuestos |
| `FLAG_APLICAR_IVA` | Impuestos | Flag: habilita IVA del producto para el canal |
| `IMPUESTO_ADICIONAL` | Impuestos | Impuesto adicional (ej: IIBB) |
| `GASTO_POST_IMPUESTOS` | Impuestos | Gasto despues de aplicar impuestos |
| `FLAG_INCLUIR_ENVIO` | Precio | Flag: incluye precioEnvio del MLA |
| `COMISION_SOBRE_PVP` | Precio | Comision como divisor: PVP / (1 - %/100) |
| `FLAG_COMISION_ML` | Precio | Flag: usa comisionPorcentaje del MLA como comision |
| `FLAG_INFLACION_ML` | Precio | Flag: usa comisionPorcentaje del MLA como inflacion (no cuenta como costo de venta) |
| `CALCULO_SOBRE_CANAL_BASE` | Precio | Calcula PVP basado en el PVP del canal base (canal propio) |
| `CALCULO_SOBRE_CANAL_BASE_RESELLER` | Precio | Variante reseller: el factor afecta el PVP pero CORTA el ingreso del dueño en este punto (no escala con factores no-RESELLER posteriores) |
| `RECARGO_CUPON` | Post-precio | Recargo cupon: PVP / (1 - %/100) |
| `DESCUENTO_PORCENTUAL` | Post-precio | Descuento: PVP x (1 - %/100) |
| `INFLACION_DIVISOR` | Post-precio | Inflacion como divisor: PVP / (1 - %/100) |
| `FLAG_APLICAR_PRECIO_INFLADO` | Post-precio | Flag: habilita precios inflados para el canal |

> **Nota:** Los valores con prefijo `FLAG_` son habilitadores. Su porcentaje se ignora, solo importa que existan asignados al canal.

### TipoRegla (2 valores)

Usado en: Reglas de concepto (`canal_concepto_regla.tipo_regla`)

| Valor | Descripcion |
|-------|-------------|
| `INCLUIR` | El concepto SOLO aplica si el producto cumple la condicion |
| `EXCLUIR` | El concepto NO aplica si el producto cumple la condicion |

### TipoPrecioInflado (4 valores)

Usado en: Precios inflados (`precios_inflados.tipo`)

| Valor | Formula | Ejemplo |
|-------|---------|---------|
| `MULTIPLICADOR` | PVP x valor | valor=1.1 → multiplica por 1.1 |
| `DESCUENTO_PORC` | PVP / (1 - valor/100) | valor=30 → PVP / 0.70 (incrementa) |
| `DIVISOR` | PVP / valor | valor=0.9 → divide por 0.9 |
| `PRECIO_FIJO` | valor | valor=100 → precio fijo $100 |

### EstadoOrdenCompra (5 valores)

Usado en: Ordenes de compra (`orden_compra.estado`)

| Valor | Descripcion | Color sugerido |
|-------|-------------|----------------|
| `BORRADOR` | OC creada, aun no enviada | Gris |
| `ENVIADA` | OC enviada al proveedor | Azul |
| `RECIBIDA_PARCIAL` | Se recibio parte de la mercaderia | Amarillo |
| `COMPLETA` | Toda la mercaderia fue recibida | Verde |
| `CANCELADA` | OC cancelada | Rojo |

### TagReposicion (2 valores)

Usado en: Productos (`productos.tag_reposicion`) — *Reposicion esta en desarrollo*

| Valor | Descripcion | Color sugerido |
|-------|-------------|----------------|
| `PRIO` | Producto prioritario para reposicion | Naranja |
| `LIQ` | Producto en liquidacion | Rojo |

> **Nota:** Estos tags se asignan manualmente o por el proceso de reposicion (en desarrollo). El frontend deberia mostrarlos como badges/chips en la tabla de productos.

---

## Sugerencia de navegacion principal (sidebar/menu)

```
Productos
Precios
Canales
  └─ (detalle con tabs: Conceptos, Cuotas, Reglas, Descuentos)
Ordenes de compra
Maestros
  ├─ Marcas
  ├─ Tipos
  ├─ Origenes
  ├─ Materiales
  ├─ Aptos
  ├─ Catalogos
  ├─ Clientes
  ├─ Clasif. General
  └─ Clasif. Gastro
Configuracion
  ├─ Conceptos de calculo
  ├─ Precios inflados
  ├─ Reglas de descuento
  ├─ Proveedores
  ├─ MLAs
  ├─ MercadoLibre (config)
  └─ DUX (consulta)
```
