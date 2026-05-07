# API Documentation Backend

Este documento describe los endpoints de la API REST para el desarrollo del frontend.

**Base URL:** `http://localhost:8080/api`

---

## Tabla de Contenidos

1. [Visión General del Sistema](#visión-general-del-sistema)
2. [Modelo de Datos](#modelo-de-datos)
3. [Convenciones Generales](#convenciones-generales)
4. [Tipos TypeScript](#tipos-typescript)
5. [Endpoints por Módulo](#endpoints-por-módulo)
   - [Productos](#productos)
   - [Precios](#precios)
   - [Canales](#canales)
   - [Conceptos de Cálculo](#conceptos-de-cálculo)
   - [Reglas de Descuento](#reglas-de-descuento)
   - [Precios Inflados](#precios-inflados)
   - [Catálogos y Clientes](#catálogos-y-clientes)
   - [Atributos Maestros](#atributos-maestros)
   - [Excel](#excel-importexport)
   - [MercadoLibre (ML)](#mercadolibre-ml)
   - [DUX ERP](#dux-erp)
   - [Tienda Nube](#tienda-nube)
   - [Configuración Automatización](#configuración-automatización)
   - [Órdenes de Compra](#órdenes-de-compra)
   - [Reposición](#reposición)

---

## Visión General del Sistema

Este backend gestiona un sistema de **cálculo de precios multicanal** para productos. La idea central es:

1. **Productos** tienen un costo base e IVA
2. **Canales** representan diferentes puntos de venta (ej: Mercado Libre, Tienda Nube, Mayorista, etc.)
3. Cada canal tiene **conceptos de cálculo** asociados que afectan el cálculo del precio
4. Los precios se calculan automáticamente aplicando márgenes, impuestos, comisiones y descuentos
5. Cada canal puede tener diferentes **opciones de cuotas** con recargos o descuentos

### Flujo de Cálculo de Precios

```
┌─────────────┐     ┌─────────────┐     ┌──────────────────┐
│  PRODUCTO   │     │   CANAL     │     │ PRECIO CALCULADO │
│  - costo    │ ──► │  - conceptos│ ──► │  - pvp           │
│  - iva      │     │  - cuotas   │     │  - ganancia      │
│  - margen   │     │  - reglas   │     │  - margen %      │
└─────────────┘     └─────────────┘     └──────────────────┘
```

**Fórmula simplificada:**
```
PVP = (COSTO × (1 + MARGEN/100) × FACTOR_IMP) / (1 - COMISIONES/100)
```

Donde los conceptos de cálculo modifican diferentes partes de la fórmula según su `aplicaSobre`.

---

## Modelo de Datos

### Diagrama de Relaciones

```
                                    ┌─────────────┐
                                    │   MARCA     │
                                    └──────┬──────┘
                                           │
┌─────────────┐    ┌─────────────┐    ┌────▼────────┐    ┌─────────────┐
│  PROVEEDOR  │◄───┤  PRODUCTO   ├───►│    TIPO     │    │   ORIGEN    │
│  -porcentaje│    │  -sku       │    └─────────────┘    └──────▲──────┘
└─────────────┘    │  -costo     │                              │
                   │  -iva       │◄─────────────────────────────┘
                   └──────┬──────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
   │  CATALOGO   │ │   CLIENTE   │ │    APTO     │
   │  (N:M)      │ │   (N:M)     │ │   (N:M)     │
   └─────────────┘ └─────────────┘ └─────────────┘

                   ┌─────────────┐
                   │   PRODUCTO  │
                   │   MARGEN    │
                   │ -minorista  │
                   │ -mayorista  │
                   └──────┬──────┘
                          │
                          ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   CANAL     │◄───┤  PRODUCTO   │    │    MLA      │
│  -nombre    │    │   CANAL     │◄───┤  -mla       │
│  -canalBase │    │   PRECIO    │    │  -precioEnvio│
└──────┬──────┘    │  -pvp       │    └─────────────┘
       │           │  -ganancia  │
       │           └─────────────┘
       │
       ├────────────────┬────────────────┐
       ▼                ▼                ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   CANAL     │  │   CANAL     │  │   REGLA     │
│  CONCEPTO   │  │  CONCEPTO   │  │  DESCUENTO  │
│  (N:M)      │  │   CUOTA     │  │  -monto     │
└──────┬──────┘  │  -cuotas    │  │  -descuento │
       │         │  -porcentaje│  └─────────────┘
       ▼         └─────────────┘
┌─────────────┐
│  CONCEPTO   │
│   GASTO     │
│ -porcentaje │
│ -aplicaSobre│
└─────────────┘
```

### Tablas Principales

#### 1. `productos` - Catálogo de Productos
| Campo | Descripción |
|-------|-------------|
| `sku` | Código único del producto |
| `descripcion` | Nombre interno |
| `titulo_web` | Nombre para mostrar en web |
| `costo` | Precio de compra/costo base |
| `iva` | Porcentaje de IVA (ej: 21) |
| `es_combo` | Si es un pack/combo |
| `moq` | Minimum Order Quantity (cantidad mínima de pedido) |
| `stock` | Cantidad disponible |
| `activo` | Si está activo para venta |
| `tag_reposicion` | Tag de reposición: PRIO (prioritario) o LIQ (liquidación) |

**Relaciones:** marca, tipo, origen, material, proveedor, clasificaciones (gral y gastro), MLA.

#### 2. `producto_margen` - Márgenes por Producto
| Campo | Descripción |
|-------|-------------|
| `margen_minorista` | % ganancia para canales minoristas |
| `margen_mayorista` | % ganancia para canales mayoristas |
| `margen_fijo_*` | Ganancia fija en $ (opcional) |

Cada producto puede tener UN registro de márgenes. El canal determina cuál usar según tenga el concepto `FLAG_USAR_MARGEN_MINORISTA` o `FLAG_USAR_MARGEN_MAYORISTA`.

#### 3. `canales` - Canales de Venta
| Campo | Descripción |
|-------|-------------|
| `canal` | Nombre del canal (ej: "ML", "KT GASTRO") |
| `canal_base_id` | Canal padre para calcular sobre su PVP |

**Ejemplos de canales:**
- ML (Mercado Libre)
- KT HOGAR (Tienda Nube Hogar)
- KT GASTRO (Tienda Nube Gastronómico)
- MAYORISTA

**Canales con canal base (herencia de precios):**

Cuando un canal tiene `canal_base_id` configurado y conceptos del tipo `CALCULO_SOBRE_CANAL_BASE` o `CALCULO_SOBRE_CANAL_BASE_RESELLER` asignados, el precio se calcula en base al PVP del canal padre en lugar de usar el costo del producto.

```
Canal hijo (KT GASTRO)          Canal padre (ML)
      │                              │
      │  ◄─── toma PVP ────────────  │
      │                              │
      ▼                              ▼
PVP_HIJO = PVP_PADRE × ∏(1 + factor% / 100)
```

**Dos variantes de `CALCULO_SOBRE_CANAL_BASE`:**

| Variante | Efecto en PVP final | Efecto en ingreso del dueño |
|----------|---------------------|------------------------------|
| `CALCULO_SOBRE_CANAL_BASE` | multiplica el PVP | sí, escala el ingreso del dueño |
| `CALCULO_SOBRE_CANAL_BASE_RESELLER` | multiplica el PVP | **no**, corta acá el ingreso del dueño |

- **Canal propio** (`CALCULO_SOBRE_CANAL_BASE`): el dueño captura el 100% del PVP del canal hijo. Sus indicadores (ganancia, márgenes) escalan con el factor total.
- **Canal re-vendedor** (`CALCULO_SOBRE_CANAL_BASE_RESELLER`): el dueño solo cobra hasta el "PVP_CORTE". El re-vendedor agrega su markup (factores no-RESELLER) para llegar al PVP final que paga el cliente. La ganancia del dueño se calcula sobre el PVP_CORTE, no sobre el PVP final.

**Ejemplo (canal propio, KT GASTRO sobre ML):**
- Canal "ML" (padre): PVP = $10,000
- Canal "KT GASTRO" (hijo): `canal_base_id = ML`, concepto `CALCULO_SOBRE_CANAL_BASE` con `-12%`
- PVP de KT GASTRO = $10,000 × (1 - 12/100) = **$8,800**
- Ingreso del dueño: escalado al 88% del ingreso de ML

**Ejemplo (canal re-vendedor, LIZZY GASTRO sobre LINEA GE):**
- Canal "LINEA GE" (padre): PVP = $1,000
- Canal "LIZZY GASTRO" (hijo): `canal_base_id = LINEA GE` con dos conceptos:
  - `LZ_GASTRO_DESC` con `CALCULO_SOBRE_CANAL_BASE_RESELLER`, `-28%` (descuento mayorista al re-vendedor)
  - `LZ_GASTRO_MUP` con `CALCULO_SOBRE_CANAL_BASE`, `+50%` (markup del re-vendedor al cliente)
- PVP_CORTE = $1,000 × 0.72 = $720 (lo que el re-vendedor paga al dueño)
- PVP final = $720 × 1.5 = **$1,080** (lo que paga el cliente)
- Ingreso del dueño: escalado al 72% del ingreso de LINEA GE
- `margenSobrePvp` se calcula sobre `$720` (no sobre `$1,080`) cuando hay `CALCULO_SOBRE_CANAL_BASE_RESELLER`

**Notas importantes:**
- El canal padre debe calcularse primero (el sistema lo hace automáticamente en el orden correcto)
- Si el factor tiene porcentaje positivo, incrementa el precio; si es negativo, lo decrementa
- `CALCULO_SOBRE_CANAL_BASE` es ideal para canales propios; `CALCULO_SOBRE_CANAL_BASE_RESELLER` para acuerdos con re-vendedores

#### 4. `conceptos_calculo` - Conceptos de Cálculo
| Campo | Descripción |
|-------|-------------|
| `concepto` | Nombre (ej: "GTML", "IIBB", "IVA") |
| `porcentaje` | Valor del concepto |
| `aplica_sobre` | Cómo se aplica en la fórmula |

**Valores de `aplica_sobre`:**

| Valor | Descripción | Ejemplo |
|-------|-------------|---------|
| `GASTO_SOBRE_COSTO` | Gasto que multiplica costo base | Embalaje +2% |
| `FLAG_FINANCIACION_PROVEEDOR` | Flag: usa % financiación del proveedor | |
| `AJUSTE_MARGEN_PUNTOS` | Suma/resta puntos al margen | +5 puntos al margen |
| `AJUSTE_MARGEN_PROPORCIONAL` | Modifica margen proporcionalmente | -12% del margen |
| `FLAG_USAR_MARGEN_MINORISTA` | Flag: usar margen minorista | |
| `FLAG_USAR_MARGEN_MAYORISTA` | Flag: usar margen mayorista | |
| `GASTO_POST_GANANCIA` | Gasto después de ganancia, antes de IMP | |
| `FLAG_APLICAR_IVA` | Flag: aplicar IVA del producto | |
| `IMPUESTO_ADICIONAL` | Se suma a factor de impuestos | IIBB +3.5% |
| `GASTO_POST_IMPUESTOS` | Gasto después de aplicar impuestos | |
| `FLAG_INCLUIR_ENVIO` | Flag: incluir precio envío del MLA | Envío gratis |
| `COMISION_SOBRE_PVP` | Comisión como divisor sobre PVP | Comisión ML -13% |
| `FLAG_COMISION_ML` | Flag: usa comisión ML del MLA (es costo de venta) | mla.comision_porcentaje |
| `FLAG_INFLACION_ML` | Flag: usa comisión ML del MLA como inflación (no es costo de venta) | mla.comision_porcentaje |
| `CALCULO_SOBRE_CANAL_BASE` | Calcula sobre PVP del canal base (canal propio) | |
| `CALCULO_SOBRE_CANAL_BASE_RESELLER` | Variante reseller: corta el ingreso del dueño en este punto | LIZZY descuento mayorista -28% |
| `RECARGO_CUPON` | Divisor adicional sobre PVP | Cupones +5% |
| `DESCUENTO_PORCENTUAL` | Descuento final sobre PVP | Promo -10% |
| `INFLACION_DIVISOR` | Divisor de inflación | Inflación +8% |
| `FLAG_APLICAR_PRECIO_INFLADO` | Flag: aplicar precios inflados | |

**Detalle de cómo se aplican los conceptos:**

```
FÓRMULA GENERAL SIMPLIFICADA:

PVP = (COSTO_AJUSTADO × (1 + MARGEN/100) × FACTOR_IMP) / (1 - COMISIONES_PVP/100)

Donde:
- COSTO_AJUSTADO = costo × (1 + Σ GASTO_SOBRE_COSTO%) × (1 + FLAG_FINANCIACION_PROVEEDOR%)
- MARGEN = margen_base + Σ AJUSTE_MARGEN_PUNTOS ± (margen_base × AJUSTE_MARGEN_PROPORCIONAL%)
- FACTOR_IMP = (1 + FLAG_APLICAR_IVA%) × (1 + Σ IMPUESTO_ADICIONAL%)
- COMISIONES_PVP = Σ conceptos con COMISION_SOBRE_PVP + FLAG_COMISION_ML + FLAG_INFLACION_ML (mla.comision_porcentaje)
```

| Tipo | Efecto | Fórmula |
|------|--------|---------|
| `GASTO_SOBRE_COSTO` | Incrementa costo base | `costo × (1 + %/100)` |
| `COMISION_SOBRE_PVP` | Comisión sobre precio final | Divide: `PVP / (1 - %/100)` |
| `IMPUESTO_ADICIONAL` | Agrega al factor de impuestos | `factor_imp += %/100` |
| `AJUSTE_MARGEN_PUNTOS` | Suma/resta puntos al margen | `margen += %` |
| `AJUSTE_MARGEN_PROPORCIONAL` | Modifica margen proporcionalmente | `margen × (1 + %/100)` |
| `RECARGO_CUPON` | Divisor adicional | `PVP / (1 - %/100)` |
| `DESCUENTO_PORCENTUAL` | Descuento sobre PVP | `PVP × (1 - %/100)` |
| `INFLACION_DIVISOR` | Calcula PVP_INFLADO | `PVP / (1 - %/100)` |
| `FLAG_INCLUIR_ENVIO` | Agrega precio_envio del MLA | `PVP += mla.precio_envio` |
| `FLAG_COMISION_ML` | Comisión ML del MLA como divisor (es costo de venta) | `PVP / (1 - mla.comision_porcentaje/100)` |
| `FLAG_INFLACION_ML` | Inflación ML del MLA como divisor (no es costo de venta) | `PVP / (1 - mla.comision_porcentaje/100)` |

**Conceptos tipo FLAG (el % se ignora, solo importa si está asignado):**
- `FLAG_APLICAR_IVA`: Habilita aplicar el IVA del producto
- `FLAG_USAR_MARGEN_MINORISTA`: Usa el margen minorista del producto
- `FLAG_USAR_MARGEN_MAYORISTA`: Usa el margen mayorista del producto
- `FLAG_APLICAR_PRECIO_INFLADO`: Habilita precios inflados asignadas al producto-canal
- `CALCULO_SOBRE_CANAL_BASE`: Calcula sobre PVP del canal padre — canal propio (ver sección canales)
- `CALCULO_SOBRE_CANAL_BASE_RESELLER`: Variante para canal re-vendedor (corta el ingreso del dueño en este factor; ver sección canales)
- `FLAG_FINANCIACION_PROVEEDOR`: Usa el % financiación del proveedor del producto
- `FLAG_INCLUIR_ENVIO`: Usa mla.precio_envio como costo de envío
- `FLAG_COMISION_ML`: Usa mla.comision_porcentaje como comisión sobre PVP (se cuenta como costo de venta)
- `FLAG_INFLACION_ML`: Usa mla.comision_porcentaje como inflación sobre PVP (NO se cuenta como costo de venta)

#### 5. `canal_concepto` - Conceptos Asignados a Canales
Tabla intermedia que relaciona qué conceptos aplican a cada canal.

**Ejemplo:**
- Canal "ML" tiene: GTML, IIBB, FLAG_APLICAR_IVA, FLAG_USAR_MARGEN_MINORISTA
- Canal "MAYORISTA" tiene: FLAG_APLICAR_IVA, FLAG_USAR_MARGEN_MAYORISTA

#### 6. `canal_concepto_cuota` - Opciones de Cuotas por Canal
| Campo | Descripción |
|-------|-------------|
| `cuotas` | -1=transferencia, 0=contado, >0=cuotas |
| `porcentaje` | Recargo (+) o descuento (-) |
| `descripcion` | Texto a mostrar |

**Ejemplo para canal ML:**
| cuotas | porcentaje | descripcion |
|--------|------------|-------------|
| -1 | -15 | Transferencia |
| 0 | 0 | Contado |
| 1 | 0 | 1 cuota sin interés |
| 3 | 10 | 3 cuotas |
| 6 | 20 | 6 cuotas |

#### 6.1. `canal_concepto_regla` - Reglas Condicionales de Conceptos
Permite incluir o excluir un concepto del cálculo según atributos del producto.

| Campo | Descripción |
|-------|-------------|
| `canal_id` | Canal donde aplica la regla |
| `concepto_id` | Concepto afectado por la regla |
| `tipo_regla` | INCLUIR o EXCLUIR |
| `tipo_id` | Filtro por tipo de producto (opcional) |
| `clasif_gastro_id` | Filtro por clasificación gastro (opcional) |
| `clasif_gral_id` | Filtro por clasificación general (opcional) |
| `marca_id` | Filtro por marca (opcional) |
| `es_maquina` | Filtro si es máquina (opcional) |

**Tipos de regla:**
| Tipo | Comportamiento |
|------|----------------|
| `INCLUIR` | El concepto SOLO aplica si el producto cumple la condición |
| `EXCLUIR` | El concepto NO aplica si el producto cumple la condición |

**Ejemplo 1 - EXCLUIR:**
```
Canal: ML
Concepto: EMBALAJE (aplica_sobre: GASTO_SOBRE_COSTO, 2%)
Regla: EXCLUIR si es_maquina = true
```
Resultado: Las máquinas no pagan embalaje en ML, los demás productos sí.

**Ejemplo 2 - INCLUIR:**
```
Canal: KT GASTRO
Concepto: DESC_GASTRO (aplica_sobre: DESCUENTO_PORCENTUAL, -5%)
Regla: INCLUIR si clasif_gastro_id = 3 (Cafeteras)
```
Resultado: Solo las cafeteras tienen el descuento gastro, los demás productos no.

**Nota:** Si un concepto tiene múltiples reglas, se evalúan todas. El concepto aplica si pasa todas las condiciones.

#### 7. `producto_canal_precios` - Precios Calculados
| Campo | Descripción |
|-------|-------------|
| `pvp` | Precio de venta al público (precio real de venta) |
| `pvp_inflado` | PVP con inflación (solo para mostrar tachado en UI) |
| `costo_producto` | Costo base × (1 + financiación proveedor) |
| `costos_venta` | Σ conceptos con AplicaSobre: COMISION_SOBRE_PVP, DESCUENTO_PORCENTUAL, RECARGO_CUPON, FLAG_INCLUIR_ENVIO (incluye embalaje, comisiones, cuotas) |
| `ingreso_neto_vendedor` | PVP - IVA - impuestos - costosVenta |
| `ganancia` | Ingreso neto - costo producto |
| `margen_sobre_ingreso_neto` | (ganancia / ingreso neto) × 100 - Rentabilidad real después de gastos |
| `margen_sobre_pvp` | (ganancia / pvp) × 100 - Margen tradicional sobre precio de venta |
| `markup_porcentaje` | (ganancia / costo) × 100 |

**Importante:** Todas las métricas (`costos_venta`, `ingreso_neto_vendedor`, `ganancia`, `margen_sobre_ingreso_neto`, `margen_sobre_pvp`, `markup_porcentaje`) se calculan sobre el **`pvp`**, NO sobre `pvp_inflado`. El campo `pvp_inflado` es solo para mostrar un precio "tachado" en la UI (ej: ~~$12,000~~ $10,000).

**Clave única:** (producto, canal, cuotas)

#### 8. `reglas_descuento` - Descuentos por Monto de Compra (Informativos)
| Campo | Descripción |
|-------|-------------|
| `canal_id` | Canal donde aplica |
| `monto_minimo` | Monto mínimo de compra total para aplicar |
| `descuento_porcentaje` | % de descuento |
| `prioridad` | Orden de evaluación (menor = mayor prioridad) |
| `catalogo_id` | Filtro por catálogo (opcional) |
| `clasif_gral_id` | Filtro por clasificación (opcional) |
| `activo` | Si la regla está activa |

**Importante:** Las reglas de descuento son **informativas**. No se aplican automáticamente al cálculo del PVP. Se usan para mostrar al usuario cómo quedaría el precio y margen si se aplica un descuento según el monto de compra total.

Los descuentos aplicables se muestran automáticamente en `GET /api/precios` y `POST /api/precios/calcular` si el canal tiene reglas de descuento configuradas.

#### 9. `precios_inflados` - Precios Inflados
| Campo | Descripción |
|-------|-------------|
| `codigo` | Código único |
| `tipo` | MULTIPLICADOR, DESCUENTO_PORC, DIVISOR, PRECIO_FIJO |
| `valor` | Valor según el tipo |

**Tipos de precio inflado y cómo afectan el PVP:**

| Tipo | Fórmula | Ejemplo | Resultado |
|------|---------|---------|-----------|
| `MULTIPLICADOR` | PVP × valor | PVP=$1000, valor=1.1 | $1,100 (+10%) |
| `DESCUENTO_PORC` | PVP / (1 - valor/100) | PVP=$1000, valor=30 | $1,428.57 (+42.8%) |
| `DIVISOR` | PVP / valor | PVP=$1000, valor=0.9 | $1,111.11 (+11.1%) |
| `PRECIO_FIJO` | valor | PVP=$1000, valor=500 | $500 (fijo) |

**Notas:**
- Los precios inflados se asignan a producto+canal específico mediante `producto_canal_precio_inflado`
- Solo aplican si el canal tiene el concepto `FLAG_APLICAR_PRECIO_INFLADO` asignado
- `DESCUENTO_PORC` usa la fórmula del Excel original: divide por (1-porcentaje), lo que **incrementa** el precio

#### 10. `mlas` - Datos de Mercado Libre
| Campo | Descripción |
|-------|-------------|
| `mla` | Código MLA (ej: "MLA123456") - NO es único, puede repetirse (un listing tiene múltiples SKUs) |
| `mlau` | Código MLAU (variante) |
| `precio_envio` | Costo de envío sin IVA para concepto ENVIO |
| `fecha_calculo_envio` | Fecha del último cálculo de envío |
| `comision_porcentaje` | Porcentaje de comisión ML (ej: 14.35) |
| `fecha_calculo_comision` | Fecha del último cálculo de comisión |
| `tope_promocion` | Tope para promociones ML (default 0, usado por programa externo) |

### Tablas de Clasificación (Maestros)

| Tabla | Descripción |
|-------|-------------|
| `marcas` | Marcas de productos (jerárquica) |
| `tipos` | Tipos de producto (jerárquica) |
| `origenes` | País/origen del producto |
| `materiales` | Material del producto |
| `aptos` | Certificaciones (apto celíaco, vegano, etc.) |
| `clasif_gral` | Clasificación general (jerárquica) |
| `clasif_gastro` | Clasificación gastronómica (jerárquica, tiene `es_maquina`) |
| `catalogos` | Catálogos/listas de precios |
| `clientes` | Clientes especiales |
| `proveedores` | Proveedores con % financiación |

### Tablas de Operaciones

| Tabla | Descripción |
|-------|-------------|
| `ordenes_compra` | Órdenes de compra a proveedores |
| `orden_compra_lineas` | Líneas/items de cada orden de compra |
| `reposicion_config` | Configuración del módulo de reposición (fila única) |
| `venta_diaria_cache` | Cache de ventas diarias por SKU (obtenido de DUX) |

#### `catalogos` - Detalle
| Campo | Descripción |
|-------|-------------|
| `catalogo` | Nombre del catálogo |
| `exportar_con_iva` | Si el precio exportado incluye IVA (default: true) |
| `recargo_porcentaje` | Recargo % a aplicar sobre el PVP al exportar (default: 0) |

**Uso de catálogos:**
- Los productos se asignan a catálogos mediante la relación `producto_catalogo` (many-to-many)
- Al exportar un catálogo (`/api/excel/exportar-catalogo`), se aplica el recargo configurado
- Los catálogos pueden tener reglas de descuento asociadas (`reglas_descuento.catalogo_id`)
- Útil para generar listas de precios para distintos clientes/canales con márgenes diferentes

**Ejemplo:**
```
Catálogo "LISTA MAYORISTA":
  - exportarConIva: false (precios sin IVA)
  - recargoPorcentaje: 5 (agrega 5% al PVP)
```

---

## Convenciones Generales

### Paginación

Los endpoints de listado soportan paginación con los siguientes parámetros:

| Parámetro | Tipo   | Default | Descripción                          |
|-----------|--------|---------|--------------------------------------|
| `page`    | number | 0       | Número de página (0-indexed)         |
| `size`    | number | 20      | Cantidad de elementos por página     |
| `sort`    | string | -       | Campo y dirección: `campo,asc/desc`  |

**Ejemplo:**
```
GET /api/productos?page=0&size=10&sort=descripcion,asc
```

**Respuesta paginada:**
```typescript
interface PageResponse<T> {
  content: T[];
  page: {
    size: number;           // elementos por página
    number: number;         // página actual (0-indexed)
    totalElements: number;  // total de registros
    totalPages: number;     // total de páginas
  };
}
```

### Búsqueda con `search`

La mayoría de los endpoints de listado soportan un parámetro `search` para filtrado por texto:

| Parámetro | Tipo   | Descripción                                      |
|-----------|--------|--------------------------------------------------|
| `search`  | string | Texto para buscar (case-insensitive, parcial)    |

**Endpoints que soportan `search`:**
- `/api/productos` - busca en descripción, SKU, título web
- `/api/canales` - busca en nombre del canal
- `/api/conceptos-calculo` - busca en concepto y descripción
- `/api/catalogos` - busca en nombre
- `/api/clientes` - busca en nombre
- `/api/proveedores` - busca en nombre
- `/api/marcas` - busca en nombre
- `/api/tipos` - busca en nombre
- `/api/origenes` - busca en nombre
- `/api/materiales` - busca en nombre
- `/api/aptos` - busca en nombre
- `/api/clasif-gral` - busca en nombre
- `/api/clasif-gastro` - busca en nombre
- `/api/precios-inflados` - busca en código y descripción
- `/api/mlas` - busca en MLA y MLAU

**Ejemplo:**
```
GET /api/productos?search=cafetera&page=0&size=10
GET /api/marcas?search=oster
```

**Nota:** El parámetro `search` se puede combinar con paginación y ordenamiento.

### Formato de Errores

Todos los errores (400, 404, 409, 500) devuelven el mismo formato:

```typescript
interface ApiError {
  success: false;
  message: string;     // Descripción del error. En 500 incluye el mensaje de la excepción
  path: string;        // Ruta del endpoint que falló
  timestamp: string;   // ISO datetime del error
}
```

### Códigos HTTP

| Código | Significado                        |
|--------|------------------------------------|
| 200    | GET/PUT exitoso                    |
| 201    | POST exitoso (Created)             |
| 204    | DELETE exitoso (No Content)        |
| 400    | Validación fallida / Bad Request   |
| 404    | Recurso no encontrado              |
| 409    | Conflicto (duplicado / integridad) |
| 500    | Error interno del servidor (incluye mensaje de la excepción) |

---

## Tipos TypeScript

### Producto

```typescript
interface Producto {
  id: number;
  sku: string;
  codExt: string | null;
  descripcion: string;
  tituloWeb: string;
  esCombo: boolean | null;
  uxb: number | null;
  moq: number | null;
  imagenUrl: string | null;
  stock: number | null;
  activo: boolean | null;
  tagReposicion: 'PRIO' | 'LIQ' | null;

  // IDs de relaciones
  marcaId: number | null;
  origenId: number;
  clasifGralId: number;
  clasifGastroId: number | null;
  tipoId: number;
  proveedorId: number | null;
  materialId: number | null;

  // Atributos físicos
  capacidad: string | null;
  largo: number | null;   // BigDecimal
  ancho: number | null;
  alto: number | null;
  diamboca: string | null;
  diambase: string | null;
  espesor: string | null;

  // Precios
  costo: number;          // BigDecimal
  fechaUltimoCosto: string | null;  // ISO DateTime
  iva: number;            // BigDecimal (0-100)

  // Fechas
  fechaCreacion: string;      // ISO DateTime
  fechaModificacion: string;  // ISO DateTime
}

interface ProductoCreate {
  sku: string;                    // @NotNull, max 45
  codExt?: string;                // max 45
  descripcion: string;            // @NotNull, max 100
  tituloWeb: string;              // @NotNull, max 100
  esCombo?: boolean;
  uxb?: number;                   // > 0
  moq?: number;                   // > 0
  imagenUrl?: string;             // max 500
  stock?: number;                 // >= 0
  activo?: boolean;

  marcaId?: number;
  origenId: number;               // @NotNull
  clasifGralId: number;           // @NotNull
  clasifGastroId?: number;
  tipoId: number;                 // @NotNull
  proveedorId?: number;
  materialId?: number;

  capacidad?: string;
  largo?: number;
  ancho?: number;
  alto?: number;
  diamboca?: string;
  diambase?: string;
  espesor?: string;

  costo: number;                  // @NotNull, >= 0
  iva: number;                    // @NotNull, 0-100
  tagReposicion?: 'PRIO' | 'LIQ';
}

// Para PUT (update parcial - solo enviar campos a modificar)
interface ProductoUpdate extends Partial<ProductoCreate> {}
```

### Producto con Precios (para tablas)

```typescript
interface ProductoConPrecios {
  // Identificación
  id: number;
  sku: string;

  // MLA
  mla: string | null;
  mlau: string | null;
  precioEnvio: number | null;
  fechaCalculoEnvio: string | null;   // ISO DateTime
  comisionPorcentaje: number | null;  // porcentaje de comisión ML (ej: 14.35)

  codExt: string | null;
  descripcion: string;
  tituloWeb: string;
  esCombo: boolean | null;
  esMaquina: boolean | null;
  imagenUrl: string | null;
  stock: number | null;
  activo: boolean | null;
  tagReposicion: 'PRIO' | 'LIQ' | null;

  // Nombres de relaciones (no IDs)
  marcaNombre: string | null;
  origenNombre: string | null;
  clasifGralNombre: string | null;
  clasifGastroNombre: string | null;
  tipoNombre: string | null;
  proveedorNombre: string | null;
  materialNombre: string | null;

  // Dimensiones
  uxb: number | null;
  moq: number | null;
  capacidad: string | null;
  largo: number | null;
  ancho: number | null;
  alto: number | null;
  diamboca: string | null;
  diambase: string | null;
  espesor: string | null;

  // Precios y costos
  costo: number | null;
  fechaUltimoCosto: string | null;
  iva: number | null;

  // Márgenes
  margenMinorista: number | null;
  margenMayorista: number | null;

  // Fechas
  fechaCreacion: string;
  fechaModificacion: string;

  // Precios por canal
  canales: CanalPrecios[];
}

interface CanalPrecios {
  canalId: number;
  canalNombre: string;
  precios: Precio[];
}

interface Precio {
  cuotas: number;                  // -1 = transferencia, 0 = contado, >0 = cuotas
  descripcion: string;             // "Transferencia", "Contado", "3 cuotas", etc.
  pvp: number;
  pvpInflado: number | null;
  costoProducto: number;           // Costo base × (1 + financiación proveedor)
  costosVenta: number;             // Σ conceptos PVP + DESCUENTO + RECARGO_CUPON + ENVIO + cuotas
  ingresoNetoVendedor: number;     // PVP - IVA - impuestos - costosVenta
  ganancia: number;                // ingresoNetoVendedor - costoProducto
  margenSobreIngresoNeto: number;  // (ganancia / ingresoNetoVendedor) × 100 - Rentabilidad real
  margenSobrePvp: number;          // (ganancia / pvp) × 100 - Margen tradicional
  markupPorcentaje: number;        // (ganancia / costoProducto) × 100
  fechaUltimoCalculo: string;
  descuentos: DescuentoAplicable[] | null;  // Se incluyen automáticamente si el canal tiene reglas
}

// Descuento aplicable según reglas de descuento del canal
interface DescuentoAplicable {
  montoMinimo: number;           // Monto mínimo de compra para aplicar
  descuentoPorcentaje: number;   // % de descuento
  pvpConDescuento: number;       // PVP después de aplicar descuento
  gananciaConDescuento: number;  // Ganancia si se aplica el descuento
  margenConDescuento: number;    // Margen % si se aplica el descuento
}
```

### Filtros de Producto

```typescript
interface ProductoFilter {
  // Filtro por ID
  productoId?: number;

  // Búsqueda por texto (parcial en SKU, codExt, descripción, títuloWeb, MLA, MLAU)
  search?: string;

  // Filtros de texto dedicados (exacto para sku/codExt, parcial para descripcion/tituloWeb)
  sku?: string;          // SKU exacto (case insensitive)
  codExt?: string;       // Código externo exacto (case insensitive)
  descripcion?: string;  // Descripción parcial (case insensitive)
  tituloWeb?: string;    // Título web parcial (case insensitive)

  // Booleanos / Numéricos
  esCombo?: boolean;
  uxb?: number;
  esMaquina?: boolean;
  tieneMla?: boolean;
  activo?: boolean;
  tagReposicion?: 'PRIO' | 'LIQ';

  // Filtros MLA (requieren que el producto tenga MLA asignado)
  mla?: string;                     // Código MLA exacto (case insensitive)
  mlau?: string;                    // Código MLAU exacto (case insensitive)
  precioEnvioMin?: number;          // Precio de envío mínimo
  precioEnvioMax?: number;          // Precio de envío máximo
  comisionPorcentajeMin?: number;   // Comisión porcentaje mínima
  comisionPorcentajeMax?: number;   // Comisión porcentaje máxima
  tieneComision?: boolean;          // true = tiene comisionPorcentaje != null
  tienePrecioEnvio?: boolean;       // true = tiene precioEnvio != null

  // Many-to-One (multi-valor)
  marcaIds?: number[];
  origenIds?: number[];
  tipoIds?: number[];
  clasifGralIds?: number[];
  clasifGastroIds?: number[];
  proveedorIds?: number[];
  materialIds?: number[];

  // Rangos
  costoMin?: number;
  costoMax?: number;
  ivaMin?: number;
  ivaMax?: number;
  stockMin?: number;
  stockMax?: number;

  // Rangos PVP (requiere pvpCanalId)
  pvpMin?: number;
  pvpMax?: number;
  pvpCanalId?: number;

  // Rangos de fechas (ISO date: YYYY-MM-DD)
  desdeFechaUltimoCosto?: string;
  hastaFechaUltimoCosto?: string;
  desdeFechaCreacion?: string;
  hastaFechaCreacion?: string;
  desdeFechaModificacion?: string;
  hastaFechaModificacion?: string;

  // Many-to-Many (arrays de IDs)
  aptoIds?: number[];
  canalIds?: number[];
  catalogoIds?: number[];
  clienteIds?: number[];
  mlaIds?: number[];

  // Filtrar precios por canal (también usado para ordenamiento por campos de precio)
  canalId?: number;

  // Filtrar precios por cuotas (también usado para ordenamiento por campos de precio)
  cuotas?: number;

  // Ordenamiento especial: campos de precio (pvp, costoProducto, ganancia, etc.)
  // usan canalId/cuotas para filtrar, o MAX de todos si no se especifican
}
```

### Canal

```typescript
interface Canal {
  id: number;
  canal: string;
  canalBaseId: number | null;
}

interface CanalCreate {
  canal: string;
  canalBaseId?: number;
}

interface CanalUpdate {
  canal?: string;
  canalBaseId?: number;
}
```

### Concepto de Cálculo

```typescript
interface ConceptoCalculo {
  id: number;
  concepto: string;
  porcentaje: number;           // -100 a 100 (negativos para MARGEN_PTS/MARGEN_PROP)
  aplicaSobre: AplicaSobre;
  descripcion: string | null;
}

interface ConceptoCalculoCreate {
  concepto: string;             // @NotBlank, max 45
  porcentaje: number;           // @NotNull, -100 a 100
  aplicaSobre: AplicaSobre;     // @NotBlank
  descripcion?: string;         // max 255
}

interface ConceptoCalculoUpdate {
  concepto?: string;            // max 45
  porcentaje?: number;          // -100 a 100
  aplicaSobre?: AplicaSobre;
  descripcion?: string;         // max 255
}

type AplicaSobre =
  // ===== ETAPA: COSTO =====
  | 'GASTO_SOBRE_COSTO'           // Gasto que multiplica el costo base
  | 'FLAG_FINANCIACION_PROVEEDOR' // Flag: usa proveedor.financiacionPorcentaje

  // ===== ETAPA: MARGEN =====
  | 'AJUSTE_MARGEN_PUNTOS'        // Suma puntos al margen (+ aumenta, - reduce)
  | 'AJUSTE_MARGEN_PROPORCIONAL'  // Multiplica el margen (+ aumenta, - reduce)
  | 'FLAG_USAR_MARGEN_MINORISTA'  // Flag: usa margenMinorista
  | 'FLAG_USAR_MARGEN_MAYORISTA'  // Flag: usa margenMayorista
  | 'GASTO_POST_GANANCIA'         // Gasto después de calcular ganancia

  // ===== ETAPA: IMPUESTOS =====
  | 'FLAG_APLICAR_IVA'            // Flag: aplica IVA del producto
  | 'IMPUESTO_ADICIONAL'          // Impuesto que suma al factor IMP
  | 'GASTO_POST_IMPUESTOS'        // Gasto después de impuestos

  // ===== ETAPA: PRECIO =====
  | 'FLAG_INCLUIR_ENVIO'          // Flag: incluye mla.precioEnvio
  | 'COMISION_SOBRE_PVP'          // Comisión que divide el PVP
  | 'FLAG_COMISION_ML'            // Flag: usa mla.comisionPorcentaje como comisión sobre PVP
  | 'FLAG_INFLACION_ML'           // Flag: usa mla.comisionPorcentaje como inflación (no es costo de venta)
  | 'CALCULO_SOBRE_CANAL_BASE'           // Calcula sobre PVP del canal base (canal propio)
  | 'CALCULO_SOBRE_CANAL_BASE_RESELLER'  // Variante reseller: corta el ingreso del dueño en este factor

  // ===== ETAPA: POST_PRECIO =====
  | 'RECARGO_CUPON'               // Recargo como divisor sobre PVP
  | 'DESCUENTO_PORCENTUAL'        // Descuento que reduce PVP
  | 'INFLACION_DIVISOR'           // Inflación como divisor
  | 'FLAG_APLICAR_PRECIO_INFLADO';   // Flag: habilita precios inflados

// NOTA: AJUSTE_MARGEN_PUNTOS y AJUSTE_MARGEN_PROPORCIONAL usan el signo del porcentaje:
//   - Porcentaje positivo (+25): aumenta el margen
//   - Porcentaje negativo (-20): reduce el margen
```

### Canal Concepto (relación canal-concepto)

```typescript
interface CanalConcepto {
  canalId: number;
  conceptoId: number;
  concepto: string;
  porcentaje: number;
  aplicaSobre: string;
  descripcion: string | null;
}
```

### Canal Concepto Cuota

```typescript
interface CanalConceptoCuota {
  id: number;
  canalId: number;
  cuotas: number;         // -1 = transferencia, 0 = contado, >0 = cuotas
  porcentaje: number;     // positivo = recargo, negativo = descuento
  descripcion: string | null;  // "Transferencia", "Contado", "3 cuotas", etc.
}

interface CanalConceptoCuotaCreate {
  canalId: number;        // @NotNull, @Positive
  cuotas: number;         // @NotNull: -1 = transferencia, 0 = contado, >0 = cuotas
  porcentaje: number;     // @NotNull, -100 a 100
  descripcion?: string;   // max 255
}

interface CanalConceptoCuotaUpdate {
  cuotas?: number;        // >= 0
  porcentaje?: number;    // -100 a 100
  descripcion?: string;   // max 255
}
```

### Canal Concepto Regla

```typescript
interface CanalConceptoRegla {
  id: number;
  canalId: number;
  conceptoId: number;
  tipoRegla: 'INCLUIR' | 'EXCLUIR';
  tipoId: number | null;
  clasifGastroId: number | null;
  clasifGralId: number | null;
  marcaId: number | null;
  esMaquina: boolean | null;
}

interface CanalConceptoReglaCreate {
  canalId: number;              // @NotNull, @Positive
  conceptoId: number;           // @NotNull, @Positive
  tipoRegla: 'INCLUIR' | 'EXCLUIR';  // @NotBlank
  tipoId?: number;              // @Positive
  clasifGastroId?: number;      // @Positive
  clasifGralId?: number;        // @Positive
  marcaId?: number;             // @Positive
  esMaquina?: boolean;
}

interface CanalConceptoReglaUpdate {
  canalId?: number;             // @Positive (debe coincidir si se envía)
  conceptoId?: number;          // @Positive (debe coincidir si se envía)
  tipoRegla?: 'INCLUIR' | 'EXCLUIR';
  tipoId?: number;              // @Positive
  clasifGastroId?: number;      // @Positive
  clasifGralId?: number;        // @Positive
  marcaId?: number;             // @Positive
  esMaquina?: boolean;
}
```
```

### Regla de Descuento

```typescript
interface ReglaDescuento {
  id: number;
  canalId: number;
  catalogoId: number | null;
  clasifGralId: number | null;
  clasifGastroId: number | null;
  montoMinimo: number;
  descuentoPorcentaje: number;
  prioridad: number;
  activo: boolean;
  descripcion: string | null;
}

interface ReglaDescuentoCreate {
  canalId: number;              // @NotNull, @Positive
  catalogoId?: number;          // @Positive
  clasifGralId?: number;        // @Positive
  clasifGastroId?: number;      // @Positive
  montoMinimo: number;          // @NotNull, >= 0
  descuentoPorcentaje: number;  // @NotNull, 0-100
  prioridad?: number;           // default 1
  activo?: boolean;             // default true
  descripcion?: string;         // max 200
}

interface ReglaDescuentoUpdate {
  catalogoId?: number;          // @Positive
  clasifGralId?: number;        // @Positive
  clasifGastroId?: number;      // @Positive
  montoMinimo?: number;         // >= 0
  descuentoPorcentaje?: number; // 0-100
  prioridad?: number;
  activo?: boolean;
  descripcion?: string;         // max 200
}
```

### Precio Inflado

```typescript
interface PrecioInflado {
  id: number;
  codigo: string;
  tipo: TipoPrecioInflado;
  valor: number;
}

interface PrecioInfladoCreate {
  codigo: string;               // @NotBlank, max 20
  tipo: TipoPrecioInflado;          // @NotNull
  valor: number;                // @NotNull, >= 0
}

interface PrecioInfladoUpdate {
  codigo?: string;              // max 20
  tipo?: TipoPrecioInflado;
  valor?: number;               // >= 0
}

type TipoPrecioInflado = 'MULTIPLICADOR' | 'DESCUENTO_PORC' | 'DIVISOR' | 'PRECIO_FIJO';
```

### Producto-Canal Precio Inflado

```typescript
interface ProductoCanalPrecioInflado {
  id: number;
  productoId: number;
  canalId: number;
  precioInflado: PrecioInflado;
  activa: boolean;
  fechaDesde: string | null;  // ISO date
  fechaHasta: string | null;
  notas: string | null;
}

interface ProductoCanalPrecioInfladoCreate {
  productoId: number;             // @NotNull, debe coincidir con path
  canalId: number;                // @NotNull, debe coincidir con path
  precioInfladoId: number;        // @NotNull, > 0
  activa?: boolean;
  fechaDesde?: string;            // ISO date
  fechaHasta?: string;
  notas?: string;                 // max 255
}

interface ProductoCanalPrecioInfladoUpdate {
  precioInfladoId?: number;       // > 0
  activa?: boolean;
  fechaDesde?: string;            // ISO date
  fechaHasta?: string;
  notas?: string;                 // max 255
}
```

### Producto Margen

```typescript
interface ProductoMargen {
  id: number | null;
  productoId: number;
  margenMinorista: number;      // 0-99.999 (%)
  margenMayorista: number;      // 0-99.999 (%)
  margenFijoMinorista: number | null;  // valor absoluto
  margenFijoMayorista: number | null;
  notas: string | null;
}
```

### Fórmula de Cálculo

```typescript
interface FormulaCalculo {
  canalNombre: string;
  cuotas: number;
  descripcionCuotas: string;   // "contado", "transferencia", "3 cuotas", etc.
  formulaGeneral: string;
  pasos: PasoCalculo[];
  resultadoFinal: number;
}

interface PasoCalculo {
  numeroPaso: number;
  descripcion: string;
  formula: string;
  valor: number;
  detalle: string;
}
```

### MLA (Mercado Libre)

```typescript
interface Mla {
  id: number;
  mla: string;                          // código MLA
  mlau: string | null;
  precioEnvio: number | null;           // costo de envío SIN IVA
  fechaCalculoEnvio: string | null;     // ISO DateTime
  comisionPorcentaje: number | null;    // porcentaje de comisión ML (ej: 14.35)
  fechaCalculoComision: string | null;  // ISO DateTime
  topePromocion: number;                // tope para promociones ML (default 0)
}

interface MlaCreate {
  mla: string;                    // @NotBlank, max 20 (no es único, puede repetirse)
  mlau?: string;                  // max 20
  precioEnvio?: number;           // >= 0
  topePromocion?: number;         // >= 0, default 0
}

interface MlaUpdate {
  mla?: string;                   // max 20
  mlau?: string;                  // max 20
  precioEnvio?: number;           // >= 0
  comisionPorcentaje?: number;    // >= 0
  topePromocion?: number;         // >= 0
}
```

### Configuración ML

```typescript
interface ConfiguracionMl {
  id: number;
  umbralEnvioGratis: number;    // $33,000 - límite para envío gratis (API ML)

  // Tiers de costo de envío por rangos de PVP
  tier1Hasta: number | null;    // $15,000
  tier1Costo: number | null;    // $1,115
  tier2Hasta: number | null;    // $25,000
  tier2Costo: number | null;    // $2,300
  tier3Costo: number | null;    // $2,810 (tier3Hasta = umbralEnvioGratis)
}

interface ConfiguracionMlUpdate {
  umbralEnvioGratis: number;    // @NotNull, @Positive
  tier1Hasta?: number;          // @Positive
  tier1Costo?: number;          // @Positive
  tier2Hasta?: number;          // @Positive
  tier2Costo?: number;          // @Positive
  tier3Costo?: number;          // @Positive
}
```

### Respuestas de Costo de Envío ML

```typescript
interface CostoEnvioResponse {
  mla: string;
  status: string | null;        // Estado del producto en ML ("active", "paused", etc.)
  precioConsultado: number | null;  // PVP usado para el cálculo
  costoEnvioConIva: number;     // Costo de envío original (con IVA 21%)
  costoEnvioSinIva: number;     // Costo de envío sin IVA (el que se guarda en BD)
  mensaje: string;              // Descripción del resultado
}

interface CostoEnvioMasivoResponse {
  totalProcesados: number;
  exitosos: number;
  errores: number;
  omitidos: number;
  resultados: CostoEnvioResponse[];
}

interface CostoVentaResponse {
  mla: string | null;
  status: string | null;              // Estado del producto en ML ("active", "paused", etc.)
  precioConsultado: number | null;    // Precio actual del producto en ML
  // Totales
  comisionVentaTotal: number | null;  // sale_fee_amount - Comisión total de venta
  // Detalles desglosados de sale_fee_details
  costoFijo: number | null;           // sale_fee_details.fixed_fee
  cargoFinanciacion: number | null;   // sale_fee_details.financing_add_on_fee
  porcentajeMeli: number | null;      // sale_fee_details.meli_percentage_fee
  porcentajeTotal: number | null;     // sale_fee_details.percentage_fee
  // Metadata
  listingTypeId: string | null;       // Tipo de publicación ("gold_pro", "gold_special", etc.)
  listingTypeName: string | null;     // Nombre del tipo ("Premium", "Clásica", etc.)
  mensaje: string;                    // Descripción del resultado o error
}

interface CostoVentaMasivoResponse {
  totalProcesados: number;
  exitosos: number;
  errores: number;
  omitidos: number;
  resultados: CostoVentaResponse[];
}

// Estado del proceso masivo (usado por ML costo-envío, ML costo-venta, DUX importar, DUX obtener-productos)
interface ProcesoMasivoEstado {
  enEjecucion: boolean;
  total: number;                   // Total de items a procesar
  procesados: number;              // Items procesados hasta ahora
  exitosos: number;                // Procesados exitosamente
  errores: number;                 // Procesados con error
  estado: 'idle' | 'ejecutando' | 'completado' | 'cancelado';
  iniciadoEn: string | null;       // ISO DateTime
  finalizadoEn: string | null;     // ISO DateTime
  mensaje: string | null;          // Mensaje descriptivo
}
```

### Configuración Automatización

```typescript
interface ConfigAutomatizacion {
  id: number;
  clave: string;           // Identificador único de la configuración
  valor: string;           // Valor de la configuración
  descripcion: string | null;  // Descripción opcional
}

interface ConfigAutomatizacionCreate {
  clave: string;           // @NotBlank, max 50, unique
  valor: string;           // @NotBlank, max 100
  descripcion?: string;    // max 255
}

interface ConfigAutomatizacionUpdate {
  clave?: string;          // max 50
  valor?: string;          // max 100
  descripcion?: string;    // max 255
}
```

### DUX ERP

```typescript
// Status del servicio
interface DuxStatus {
  configurado: boolean;
  servicio: string;
}

// Producto de DUX
interface DuxItem {
  codItem: string;
  item: string;
  codigosBarra: string[] | null;
  rubro: DuxRubro | null;
  subRubro: DuxSubRubro | null;
  marca: DuxMarca | null;
  proveedor: DuxProveedor | null;
  costo: string;
  porcIva: string;
  precios: DuxPrecio[] | null;
  stock: DuxStock[] | null;
  idDetItem: string;
  talle: string | null;
  color: string | null;
  habilitado: string;
  codigoExterno: string | null;
  fechaCreacion: string | null;
  imagenUrl: string | null;
}

interface DuxRubro {
  id: string;
  nombre: string;
  subRubro: DuxSubRubro | null;
}

interface DuxSubRubro {
  id: string;
  nombre: string;
}

interface DuxMarca {
  codigoMarca: string;
  marca: string;
}

interface DuxProveedor {
  idProveedor: number;
  proveedor: string;
  tipoDoc: string | null;
  nroDoc: string | null;
  // ... otros campos de contacto
}

interface DuxPrecio {
  id: number;
  nombre: string;
  precio: string;
}

interface DuxStock {
  id: number;
  nombre: string;
  ctdDisponible: string;
  stockReal: string;
  stockReservado: string;
  stockDisponible: string;
}

// Request para actualizar precios
// IMPORTANTE: El campo "tipo" es OBLIGATORIO.
// Si no se envía el tipo correcto, DUX desconfigura el producto
// y puede perder componentes (en caso de combos).
interface DuxProductoPrecioRequest {
  sku: string;             // Código del producto (cod_item en DUX)
  tipo: 'SIMPLE' | 'COMBO'; // Tipo de producto - OBLIGATORIO
  precio: number;
}

// Resultado de importación DUX → Local
interface ImportDuxResult {
  productosActualizados: number;
  productosNoEncontrados: number;
  proveedoresCreados: number;
  totalProductosDux: number;
  skusNoEncontrados: string[];
  errores: string[];
}

// Request para exportación Local → DUX (body opcional)
interface ExportDuxRequest {
  skus?: string[];  // null/omitido = exportar todos
}

// Resultado de exportación Local → DUX
interface ExportDuxResult {
  productosEnviados: number;
  idProceso: number;       // 0 si falló, usar GET /procesos/{id}/estado para monitorear
  errores: string[];
}
```

### Proveedor

```typescript
interface Proveedor {
  id: number;
  proveedor: string;
  apodo: string | null;
  plazoPago: string | null;
  entrega: boolean | null;
  financiacionPorcentaje: number | null;  // % financiación
}

interface ProveedorCreate {
  proveedor: string;              // @NotBlank, max 100
  apodo: string;                  // @NotBlank, max 50
  plazoPago?: string;             // max 45
  entrega?: boolean;
  financiacionPorcentaje?: number;  // 0-100
}

interface ProveedorUpdate {
  proveedor?: string;             // max 100
  apodo?: string;                 // max 50
  plazoPago?: string;             // max 45
  entrega?: boolean;
  financiacionPorcentaje?: number;  // 0-100
}
```

### Otros Maestros

```typescript
interface Marca {
  id: number;
  nombre: string;
  padreId: number | null;  // Marca padre (jerárquica)
}

interface MarcaCreate {
  nombre: string;             // @NotBlank, max 45
  padreId?: number;           // @Positive
}

interface MarcaUpdate {
  nombre?: string;            // max 45
  padreId?: number;           // @Positive
}

interface Tipo {
  id: number;
  nombre: string;
  padreId: number | null;  // Tipo padre (jerárquico)
}

interface TipoCreate {
  nombre: string;             // @NotBlank, max 45
  padreId?: number;           // @Positive
}

interface TipoUpdate {
  nombre?: string;            // max 45
  padreId?: number;           // @Positive
}

interface Origen {
  id: number;
  origen: string;
}

interface OrigenCreate {
  origen: string;             // @NotBlank, max 45
}

interface OrigenUpdate {
  origen?: string;            // max 45
}

interface Material {
  id: number;
  material: string;
}

interface MaterialCreate {
  material: string;           // @NotBlank, max 45
}

interface MaterialUpdate {
  material?: string;          // max 45
}

interface Apto {
  id: number;
  apto: string;
}

interface AptoCreate {
  apto: string;               // @NotBlank, max 45
}

interface AptoUpdate {
  apto?: string;              // max 45
}

interface ClasifGral {
  id: number;
  nombre: string;
  padreId: number | null;  // Clasificación padre (jerárquica)
}

interface ClasifGralCreate {
  nombre: string;             // @NotBlank, max 45
  padreId?: number;           // @Positive
}

interface ClasifGralUpdate {
  nombre?: string;            // max 45
  padreId?: number;           // @Positive
}

interface ClasifGastro {
  id: number;
  nombre: string;
  esMaquina: boolean;
  padreId: number | null;  // Clasificación padre (jerárquica)
}

interface ClasifGastroCreate {
  nombre: string;             // @NotBlank, max 45
  esMaquina?: boolean;        // default false
  padreId?: number;           // @Positive
}

interface ClasifGastroUpdate {
  nombre?: string;            // max 45
  esMaquina?: boolean;
  padreId?: number;           // @Positive
}

interface Catalogo {
  id: number;
  catalogo: string;                    // Nombre del catálogo
  exportarConIva: boolean;             // Si exportar con IVA (default: true)
  recargoPorcentaje: number;           // Recargo % al exportar (default: 0)
}

interface CatalogoCreate {
  catalogo: string;           // @NotBlank, max 45
  exportarConIva?: boolean;   // default true
  recargoPorcentaje?: number; // 0-100, default 0
}

interface CatalogoUpdate {
  catalogo?: string;          // max 45
  exportarConIva?: boolean;
  recargoPorcentaje?: number; // 0-100
}

interface Cliente {
  id: number;
  cliente: string;  // Nombre del cliente
}

interface ClienteCreate {
  cliente: string;            // @NotBlank, max 45
}

interface ClienteUpdate {
  cliente?: string;           // max 45
}
```

### Orden de Compra

```typescript
type EstadoOrdenCompra = 'BORRADOR' | 'ENVIADA' | 'RECIBIDA_PARCIAL' | 'COMPLETA' | 'CANCELADA';

interface OrdenCompra {
  id: number;
  proveedorId: number;
  proveedorNombre: string;
  estado: EstadoOrdenCompra;
  observaciones: string | null;
  lineas: OrdenCompraLinea[];
  fechaCreacion: string;          // ISO DateTime
  fechaModificacion: string | null;
}

interface OrdenCompraLinea {
  id: number;
  productoId: number;
  productoSku: string;
  productoDescripcion: string;
  cantidadPedida: number;
  cantidadRecibida: number;
  costoUnitario: number | null;   // BigDecimal
}

interface OrdenCompraCreate {
  proveedorId: number;            // @NotNull, @Positive
  observaciones?: string;         // max 500
  lineas: OrdenCompraLineaCreate[];  // @NotEmpty
}

interface OrdenCompraLineaCreate {
  productoId: number;             // @NotNull, @Positive
  cantidadPedida: number;         // @NotNull, > 0
  costoUnitario?: number;         // >= 0
}

interface OrdenCompraUpdate {
  observaciones?: string;         // max 500
  lineas?: OrdenCompraLineaCreate[];
}

interface Recepcion {
  lineas: LineaRecepcion[];       // @NotEmpty
}

interface LineaRecepcion {
  lineaId: number;                // @NotNull, @Positive
  cantidadRecibida: number;       // @NotNull, >= 0
}
```

### Reposición

```typescript
type TagReposicion = 'PRIO' | 'LIQ';

interface ReposicionResult {
  sugerencias: SugerenciaReposicion[];
  totalProductos: number;
  productosConSugerencia: number;
  advertencias: string[];
}

interface SugerenciaReposicion {
  productoId: number;
  sku: string;
  codExt: string | null;
  descripcion: string;
  proveedorNombre: string | null;
  uxb: number | null;
  moq: number | null;
  tagReposicion: TagReposicion | null;
  stockActual: number;
  pendienteClientes: number;
  pendienteProveedores: number;
  saldoDisponible: number;
  ventasMes1: number;
  ventasMes2: number;
  ventasMes3: number;
  promedioVentas: number;
  promedioDiario: number;
  puntoReorden: number;
  urgente: boolean;
  sugerencia: number;
  pedido: number;
  ultimaCompraFecha: string | null;  // ISO DateTime
  ultimaCompraCantidad: number;
}

interface AjustePedido {
  ajustes: LineaAjuste[];         // @NotEmpty
}

interface LineaAjuste {
  productoId: number;             // @NotNull, @Positive
  pedido: number;                 // @NotNull, >= 0
}

interface ReposicionConfig {
  mesesCobertura?: number;        // @Positive
  pesoMes1?: number;              // 0.0 - 1.0
  pesoMes2?: number;              // 0.0 - 1.0
  pesoMes3?: number;              // 0.0 - 1.0
  idEmpresaDux?: number;          // @Positive
  idsSucursalDux?: number[];      // cada uno @Positive
}
```

---

## Endpoints por Módulo

### Productos

#### Listar productos (básico)
```http
GET /api/productos
```
**Query params:** `page`, `size`, `sort`
**Response:** `PageResponse<Producto>`

#### Obtener producto por ID
```http
GET /api/productos/{id}
```
**Response:** `Producto`

#### Crear producto
```http
POST /api/productos
Content-Type: application/json

{
  "sku": "BOL-001",
  "descripcion": "Bolsa kraft",
  "tituloWeb": "Bolsa de papel kraft",
  "origenId": 1,
  "clasifGralId": 1,
  "tipoId": 1,
  "costo": 150.00,
  "iva": 21
}
```
**Response:** `201 Created` + `Producto`

#### Actualizar producto
```http
PUT /api/productos/{id}
Content-Type: application/json

{
  "descripcion": "Bolsa kraft actualizada",
  "costo": 180.00
}
```
**Response:** `Producto`

#### Eliminar producto
```http
DELETE /api/productos/{id}
```
**Response:** `204 No Content`

#### Filtrar productos (con parámetros)
```http
GET /api/productos?search=texto&marcaIds=1&activo=true
GET /api/productos?sku=ABC123&sort=descripcion,asc
GET /api/productos?tieneMla=true&comisionPorcentajeMin=5
```
**Query params:** Todos los de `ProductoFilter` + `page`, `size`, `sort`
**Response:** `PageResponse<Producto>`

**Ordenamiento:** Solo campos JPA de la entidad Producto (`id`, `sku`, `codExt`, `descripcion`, `tituloWeb`, `costo`, `iva`, `stock`, `uxb`, `activo`, `esCombo`, `fechaCreacion`, `fechaModificacion`, `fechaUltimoCosto`).

**Nota:** Para ordenamiento especial (campos MLA, precios calculados, esMaquina), usar `/api/precios`.

---

### Producto - Márgenes

#### Obtener margen del producto
```http
GET /api/productos/{productoId}/margen
```
**Response:** `ProductoMargen`

#### Guardar/actualizar margen
```http
PUT /api/productos/{productoId}/margen
Content-Type: application/json

{
  "margenMinorista": 45.5,
  "margenMayorista": 30.0,
  "margenFijoMinorista": null,
  "margenFijoMayorista": null,
  "notas": "Margen especial"
}
```
**Response:** `ProductoMargen`

#### Eliminar margen
```http
DELETE /api/productos/{productoId}/margen
```
**Response:** `204 No Content`

---

### Producto - Precios Inflados por Canal

#### Obtener precio inflado
```http
GET /api/productos/{productoId}/canales/{canalId}/precios-inflados
```
**Response:** `ProductoCanalPrecioInflado`

#### Crear precio inflado
```http
POST /api/productos/{productoId}/canales/{canalId}/precios-inflados
Content-Type: application/json

{
  "productoId": 1,
  "canalId": 2,
  "precioInfladoId": 1,
  "activa": true,
  "fechaDesde": "2025-01-01",
  "fechaHasta": "2025-12-31",
  "notas": "Promo verano"
}
```
**Response:** `201 Created` + `ProductoCanalPrecioInflado`

#### Actualizar precio inflado
```http
PUT /api/productos/{productoId}/canales/{canalId}/precios-inflados
```

#### Eliminar precio inflado
```http
DELETE /api/productos/{productoId}/canales/{canalId}/precios-inflados
```
**Response:** `204 No Content`

---

### Producto - Relaciones Many-to-Many

#### Aptos
```http
GET    /api/productos/{productoId}/aptos              # Listar aptos del producto
POST   /api/productos/{productoId}/aptos/{aptoId}     # Agregar apto
DELETE /api/productos/{productoId}/aptos/{aptoId}     # Quitar apto
```

#### Catálogos
```http
GET    /api/productos/{productoId}/catalogos
POST   /api/productos/{productoId}/catalogos/{catalogoId}
DELETE /api/productos/{productoId}/catalogos/{catalogoId}
```

#### Clientes
```http
GET    /api/productos/{productoId}/clientes
POST   /api/productos/{productoId}/clientes/{clienteId}
DELETE /api/productos/{productoId}/clientes/{clienteId}
```

---

### Precios

#### Listar productos con precios (para tablas)
```http
GET /api/precios
```
**Query params:** Todos los de `ProductoFilter` + `page`, `size`, `sort`
**Response:** `PageResponse<ProductoConPrecios>`

**Ordenamiento:** Soporta campos JPA + campos especiales:
- **JPA:** `id`, `sku`, `codExt`, `descripcion`, `tituloWeb`, `costo`, `iva`, `stock`, etc.
- **MLA:** `mla`, `mlau`, `comisionPorcentaje`, `precioEnvio`
- **Relaciones:** `esMaquina`
- **Precios:** `pvp`, `pvpInflado`, `costoProducto`, `costosVenta`, `ganancia`, `margenSobreIngreso`, `margenSobrePvp`, `markup`

**Ejemplo con filtros:**
```
GET /api/precios?search=bol&marcaIds=5&costoMin=100&costoMax=500&canalIds=1,2&page=0&size=20
GET /api/precios?canalId=1&cuotas=0&sort=pvp,desc
GET /api/precios?sku=ABC123&sort=ganancia,desc
```

**Ejemplo respuesta con descuentos:**
```json
{
  "content": [{
    "id": 123,
    "sku": "BOL-001",
    "canales": [{
      "canalId": 1,
      "canalNombre": "ML",
      "precios": [{
        "cuotas": 0,
        "pvp": 5000.00,
        "ganancia": 1200.00,
        "descuentos": [
          {
            "montoMinimo": 10000.00,
            "descuentoPorcentaje": 5.00,
            "pvpConDescuento": 4750.00,
            "gananciaConDescuento": 950.00,
            "margenConDescuento": 18.50
          },
          {
            "montoMinimo": 50000.00,
            "descuentoPorcentaje": 10.00,
            "pvpConDescuento": 4500.00,
            "gananciaConDescuento": 700.00,
            "margenConDescuento": 14.20
          }
        ]
      }]
    }]
  }]
}
```

**Notas sobre descuentos:**
- Los descuentos se incluyen **automáticamente** si el canal tiene reglas de descuento activas
- Canales sin reglas muestran `descuentos: null`
- Los descuentos son **informativos** (no se aplican al cálculo del PVP, solo muestran cómo quedaría)

#### Obtener fórmula del cálculo paso a paso
```http
GET /api/precios/formula?productoId=1&canalId=2&cuotas=0
```
**Query params:**
- `productoId` (required): ID del producto
- `canalId` (required): ID del canal
- `cuotas` (required): Número de cuotas (-1 = transferencia, 0 = contado, >0 = cuotas)

**Response:** `FormulaCalculo`

**Ejemplo respuesta:**
```json
{
  "canalNombre": "KT GASTRO",
  "cuotas": 0,
  "descripcionCuotas": "contado",
  "formulaGeneral": "PVP = ((COSTO * (1 + GANANCIA/100)) * IMP / (1 - GTML[%]/100)",
  "pasos": [
    {
      "numeroPaso": 1,
      "descripcion": "Costo base del producto",
      "formula": "COSTO",
      "valor": 4144.89,
      "detalle": "Costo: $4144.89"
    },
    {
      "numeroPaso": 2,
      "descripcion": "Ganancia ajustada",
      "formula": "GANANCIA = MARGEN * (1 - REL_ML_KTG/100)",
      "valor": 52.80,
      "detalle": "MARGEN: 60.000% * (1 - REL_ML_KTG: 12.00%/100) → GANANCIA = 52.80%"
    }
  ],
  "resultadoFinal": 10165.70
}
```

#### Calcular y guardar precios
```http
POST /api/precios/calcular
```
**Query params (todos opcionales):**
- `productoId`: ID del producto
- `canalId`: ID del canal
- `cuotas`: Cantidad de cuotas (-1=transferencia, 0=contado, >0=cuotas)

**Combinaciones:**
| productoId | canalId | cuotas | Comportamiento |
|------------|---------|--------|----------------|
| - | - | - | Recalcula TODOS los productos en TODOS los canales |
| ✓ | - | - | Recalcula el producto en TODOS sus canales (todas las cuotas) |
| ✓ | - | ✓ | Recalcula el producto en TODOS sus canales (solo esas cuotas) |
| ✓ | ✓ | - | Recalcula el producto en ese canal (todas las cuotas) |
| ✓ | ✓ | ✓ | Recalcula solo para esas cuotas en ese canal |

**Response:** `CalculoResultadoDTO`

```typescript
interface CalculoResultadoDTO {
  totalPreciosCalculados: number;
  fechaCalculo: string;                    // ISO DateTime
  canales: CanalPrecios[] | null;          // null para cálculo masivo
  // Campos adicionales para cálculo masivo:
  productosIgnoradosSinCosto: number | null;
  productosIgnoradosSinMargen: number | null;
  errores: number | null;
  skusSinCosto: string[] | null;
  skusSinMargen: string[] | null;
  skusConErrores: string[] | null;
}
```

**Validaciones:**
- Si el producto no tiene **costo** (null o <= 0): se ignora en cálculo masivo, error 400 en cálculo individual
- Si el producto no tiene **margen** requerido por el canal: se ignora en cálculo masivo, error 400 en cálculo individual
  - *"El producto no tiene margen mayorista cargado"*
  - *"El producto no tiene margen minorista cargado"*

**Ejemplo respuesta (producto + canal):**
```json
{
  "totalPreciosCalculados": 2,
  "fechaCalculo": "2026-01-15T17:00:00",
  "canales": [
    {
      "canalId": 3,
      "canalNombre": "KT GASTRO",
      "precios": [
        {
          "cuotas": -1,
          "descripcion": "Transferencia",
          "pvp": 8640.85,
          "pvpInflado": null,
          "costoProducto": 4500.00,
          "costosVenta": 1833.39,
          "ingresoNetoVendedor": 6807.46,
          "ganancia": 2307.46,
          "margenSobreIngresoNeto": 33.90,
          "margenSobrePvp": 26.70,
          "markupPorcentaje": 51.28,
          "fechaUltimoCalculo": "2026-01-15T17:00:00",
          "descuentos": [
            {
              "montoMinimo": 10000.00,
              "descuentoPorcentaje": 5.00,
              "pvpConDescuento": 8208.81,
              "gananciaConDescuento": 1892.09,
              "margenConDescuento": 28.50
            }
          ]
        }
      ]
    }
  ]
}
```

**Nota:** Los descuentos se incluyen automáticamente si el canal tiene reglas de descuento activas. No es necesario pasar ningún parámetro.

**Ejemplo respuesta (masivo - sin parámetros):**
```json
{
  "totalPreciosCalculados": 1250,
  "fechaCalculo": "2026-01-15T15:30:00",
  "canales": null,
  "productosIgnoradosSinCosto": 5,
  "productosIgnoradosSinMargen": 12,
  "errores": 2,
  "skusSinCosto": ["SKU001", "SKU002", "SKU003", "SKU004", "SKU005"],
  "skusSinMargen": ["SKU100", "SKU101", "SKU102", "..."],
  "skusConErrores": ["SKU500", "SKU501"]
}
```

---

### Canales

#### CRUD básico
```http
GET    /api/canales                    # Listar (paginado)
POST   /api/canales                    # Crear
GET    /api/canales/{id}               # Obtener
PUT    /api/canales/{id}               # Actualizar
DELETE /api/canales/{id}               # Eliminar
```

#### Conceptos asignados al canal
```http
GET    /api/canales/{canalId}/conceptos                   # Listar conceptos
POST   /api/canales/{canalId}/conceptos/{conceptoId}      # Asignar concepto
DELETE /api/canales/{canalId}/conceptos/{conceptoId}      # Quitar concepto
```

---

### Canal - Cuotas

```http
GET  /api/canal-concepto-cuotas                           # Listar todas (paginado)
GET  /api/canal-concepto-cuotas/{id}                      # Obtener por ID
GET  /api/canal-concepto-cuotas/canal/{canalId}           # Listar por canal
GET  /api/canal-concepto-cuotas/canal/{canalId}/cuotas/{cuotas}  # Por canal y cuotas
POST /api/canal-concepto-cuotas                           # Crear
PUT  /api/canal-concepto-cuotas/{id}                      # Actualizar
DELETE /api/canal-concepto-cuotas/{id}                    # Eliminar
```

**Crear cuota:**
```json
{
  "canalId": 1,
  "cuotas": -1,
  "porcentaje": -15,
  "descripcion": "Transferencia bancaria"
}
```

**Ejemplos de configuración:**
| cuotas | porcentaje | descripcion | Significado |
|--------|------------|-------------|-------------|
| -1 | -15 | Transferencia | 15% descuento por transferencia |
| 0 | 0 | Contado | Precio contado sin cambio |
| 1 | 0 | 1 cuota | 1 cuota sin interés |
| 3 | 10 | 3 cuotas | 3 cuotas con 10% recargo |
| 6 | 20 | 6 cuotas | 6 cuotas con 20% recargo |

---

### Canal - Reglas de Concepto

```http
GET  /api/canal-concepto-reglas                           # Listar todas (paginado)
GET  /api/canal-concepto-reglas/{id}                      # Obtener por ID
GET  /api/canal-concepto-reglas/canal/{canalId}           # Listar por canal
GET  /api/canal-concepto-reglas/concepto/{conceptoId}     # Listar por concepto
POST /api/canal-concepto-reglas                           # Crear
PUT  /api/canal-concepto-reglas/{id}                      # Actualizar
DELETE /api/canal-concepto-reglas/{id}                    # Eliminar
```

---

### Conceptos de Cálculo

```http
GET    /api/conceptos-calculo           # Listar (paginado)
POST   /api/conceptos-calculo           # Crear
GET    /api/conceptos-calculo/{id}      # Obtener
PUT    /api/conceptos-calculo/{id}      # Actualizar
DELETE /api/conceptos-calculo/{id}      # Eliminar
```

**Crear concepto:**
```json
{
  "concepto": "IIBB",
  "porcentaje": 3.5,
  "aplicaSobre": "IMPUESTO_ADICIONAL",
  "descripcion": "Ingresos Brutos"
}
```

---

### Reglas de Descuento

```http
GET    /api/reglas-descuento                    # Listar (paginado)
GET    /api/reglas-descuento/canal/{canalId}    # Listar por canal
GET    /api/reglas-descuento/{id}               # Obtener
POST   /api/reglas-descuento                    # Crear
PUT    /api/reglas-descuento/{id}               # Actualizar
DELETE /api/reglas-descuento/{id}               # Eliminar
```

**Crear regla:**
```json
{
  "canalId": 1,
  "catalogoId": null,
  "clasifGralId": 5,
  "clasifGastroId": null,
  "montoMinimo": 10000,
  "descuentoPorcentaje": 5.0,
  "prioridad": 1,
  "activo": true,
  "descripcion": "Descuento por clasificación"
}
```

---

### Precios Inflados

```http
GET    /api/precios-inflados                    # Listar todas
GET    /api/precios-inflados/{id}               # Obtener por ID
GET    /api/precios-inflados/codigo/{codigo}    # Obtener por código
POST   /api/precios-inflados                    # Crear
PUT    /api/precios-inflados/{id}               # Actualizar
DELETE /api/precios-inflados/{id}               # Eliminar
```

**Crear precio inflado:**
```json
{
  "codigo": "VERANO25",
  "tipo": "DESCUENTO_PORC",
  "valor": 10.0
}
```

**Tipos válidos:** `MULTIPLICADOR`, `DESCUENTO_PORC`, `DIVISOR`, `PRECIO_FIJO`

---

### Catálogos y Clientes

#### Catálogos
```http
GET    /api/catalogos
POST   /api/catalogos
GET    /api/catalogos/{id}
PUT    /api/catalogos/{id}
DELETE /api/catalogos/{id}
```

#### Clientes
```http
GET    /api/clientes
POST   /api/clientes
GET    /api/clientes/{id}
PUT    /api/clientes/{id}
DELETE /api/clientes/{id}
```

---

### Atributos Maestros

Todos siguen el patrón CRUD estándar:

| Recurso           | Endpoint              |
|-------------------|-----------------------|
| Marcas            | `/api/marcas`         |
| Tipos             | `/api/tipos`          |
| Orígenes          | `/api/origenes`       |
| Materiales        | `/api/materiales`     |
| Aptos             | `/api/aptos`          |
| Clasif. General   | `/api/clasif-gral`    |
| Clasif. Gastro    | `/api/clasif-gastro`  |
| MLAs              | `/api/mlas`           |
| Proveedores       | `/api/proveedores`    |

---

### Excel (Import/Export)

#### Exportar precios a Excel
```http
GET /api/excel/exportar-precios
GET /api/excel/exportar-precios?formato=completo    # default
GET /api/excel/exportar-precios?formato=mercadolibre&cuotas=0
GET /api/excel/exportar-precios?formato=kt-hogar&cuotas=0
GET /api/excel/exportar-precios?formato=kt-gastro&cuotas=0
```
**Query params:**
- `formato` (optional, default=completo): Formato de exportación (completo, mercadolibre, kt-hogar, kt-gastro)
- Para formato `completo`: acepta todos los params de `ProductoFilter`
- Para formato `mercadolibre`, `kt-hogar` y `kt-gastro`: requiere `cuotas` (-1=transferencia, 0=contado, >0=cuotas)

**Response:** Archivo Excel (application/octet-stream)
- Headers fijos (freeze panes): los headers permanecen visibles al hacer scroll
- Formato `completo`: super headers con bordes gruesos, canales separados visualmente con bordes
- Formatos `mercadolibre`, `kt-hogar` y `kt-gastro` incluyen header `X-Advertencias-Count` si hay advertencias (el detalle se registra en el log del servidor)

**Nombre del archivo descargado:**
- Formato `completo`: `PRECIOS_{CANAL}_{CUOTAS}_{filtros}_{timestamp}.xlsx` (canal y cuotas usan nombres descriptivos, ej: `PRECIOS_ML_CONTADO_20260131_120000.xlsx`)
- Formato `mercadolibre`: `PRECIOS_MERCADOLIBRE_{CUOTAS}_{timestamp}.xlsx`
- Formato `kt-hogar`: `PRECIOS_KT_HOGAR_{CUOTAS}_{timestamp}.xlsx`
- Formato `kt-gastro`: `PRECIOS_KT_GASTRO_{CUOTAS}_{timestamp}.xlsx`

**Columnas del formato `completo`:**

| Grupo | Columnas | Formato |
|-------|----------|---------|
| Producto | ID, SKU, MLA, MLAU, PRECIO_ENVIO, **COMISION_ML**, COD_EXT, DESCRIPCION, TITULO_WEB, ES_COMBO, ES_MAQUINA, IMAGEN_URL, STOCK, ACTIVO, MARCA, ORIGEN, CLASIF_GRAL, CLASIF_GASTRO, TIPO, PROVEEDOR, MATERIAL, UXB, CAPACIDAD, LARGO, ANCHO, ALTO, DIAMBOCA, DIAMBASE, ESPESOR, COSTO, FECHA_ULT_COSTO, IVA, MARGEN_MINORISTA, MARGEN_MAYORISTA, FECHA_CREACION, FECHA_MODIFICACION | PRECIO_ENVIO y COSTO con "$", COMISION_ML/IVA/MARGEN_* con "%" |
| Por canal/cuota | PVP, PVP_INFLADO, COSTO_PRODUCTO, COSTOS_VENTA, INGRESO_NETO, GANANCIA, MARGEN_INGRESO, MARGEN_PVP, MARKUP_PCT, FECHA_CALCULO | Valores monetarios con "$", porcentajes con "%" |
| Descuentos (si el canal tiene reglas) | MARGEN (>$montoMinimo -porcentaje%) | Solo para canales con reglas de descuento activas. Muestra el margen con descuento aplicado (formato %) |

**Formato `mercadolibre`:**
| Configuración | Valor |
|---------------|-------|
| Canal | `ML` (hardcodeado) |
| Columnas | SKU, PRECIO ({descCuotas}) (pvpInflado o pvp como fallback), MLA |
| Nombre hoja | "MercadoLibre ({descCuotas})" |
| Validaciones | Excluye productos sin MLA o con precio <= 0 |

**Formato `kt-hogar`:**
| Configuración | Valor |
|---------------|-------|
| Canal | `KT HOGAR` (hardcodeado) |
| Columnas | SKU, PVP_KT_HOGAR ({descCuotas}) (pvp), PVP_INFLADO ({descCuotas}) |
| Nombre hoja | "KT HOGAR ({descCuotas})" |
| Validaciones | Excluye productos con pvp <= 0 o pvpInflado <= 0 |

**Formato `kt-gastro`:**
| Configuración | Valor |
|---------------|-------|
| Canal | `KT GASTRO` (hardcodeado) |
| Columnas | SKU, PVP_GASTRO_S_IVA ({descCuotas}) (pvp / (1 + iva/100)) |
| Nombre hoja | "KT GASTRO ({descCuotas})" |
| Validaciones | Excluye productos con pvp <= 0 o sin IVA definido |

**Importante - Prioridad de parámetros:**
- Si se pasa `formato=mercadolibre`, `formato=kt-hogar` o `formato=kt-gastro`, el parámetro `canalId` es **ignorado**. Siempre se usa el canal hardcodeado (ML, KT HOGAR o KT GASTRO respectivamente).
- El parámetro `canalId` solo aplica cuando `formato=completo` (o no se especifica formato).

#### Exportar catálogo a Excel
```http
GET /api/excel/exportar-catalogo?catalogoId=4&canalId=1&cuotas=0
```
**Query params:**
- `catalogoId` (required): ID del catálogo
- `canalId` (required): ID del canal
- `cuotas` (required): Cantidad de cuotas (-1=transferencia, 0=contado, >0=cuotas)
- `clasifGralId` (optional): Filtrar por clasificación general
- `clasifGastroId` (optional): Filtrar por clasificación gastro
- `tipoId` (optional): Filtrar por tipo
- `marcaId` (optional): Filtrar por marca
- `esMaquina` (optional): Filtrar por máquina
- `ordenarPor` (optional): Campos de ordenamiento separados por coma

**Response:** Archivo Excel (application/octet-stream)
- Nombre archivo: `CATALOGO_{CANAL}-{CATALOGO}_{CUOTAS}_{timestamp}.xlsx` (nombres descriptivos en mayúsculas)

#### Importar costos (actualizar productos existentes)
```http
POST /api/excel/importar-costos
Content-Type: multipart/form-data

archivo: [COSTOS.xls]
```

**Columnas esperadas en el Excel:**
| Columna Excel        | Campo en BD           | Notas                                    |
|----------------------|-----------------------|------------------------------------------|
| CODIGO               | sku                   | Requerido. Busca producto existente      |
| PRODUCTO             | descripcion           | Max 100 caracteres                       |
| COSTO                | costo                 | Formato: 47.639,88 (punto miles, coma decimal) |
| CODIGO EXTERNO       | cod_ext               | Max 45 caracteres                        |
| PROVEEDOR            | proveedor_id          | Se crea si no existe                     |
| TIPO DE PRODUCTO     | es_combo              | "COMBO" → true, otro → false             |
| ULTIMA ACT. COSTO    | fecha_ult_costo       | Formato: dd/MM/yyyy                      |
| UNIDADES POR BULTO   | uxb                   | Se redondea a entero                     |
| PORCENTAJE IVA       | iva                   | Formato: 21,00                           |

**Response:**
```typescript
interface ImportCostosResult {
  productosActualizados: number;
  productosNoEncontrados: number;
  proveedoresCreados: number;
  skusNoEncontrados: string[];    // Lista de SKUs que no existen
  errores: string[];              // Errores de formato/validación
}
```

**Recálculo automático:** Si la importación modifica `costo`, `iva` o `proveedor`, los precios de esos productos se recalculan automáticamente en todos sus canales.

#### Importar migración completa
```http
POST /api/excel/importar-migracion
Content-Type: multipart/form-data

archivo: [archivo.xlsx]
```
**Response:**
```typescript
interface ImportCompletoResult {
  totalHojas: number;
  hojasProcesadas: number;
  hojasConErrores: number;
  resultadosPorHoja: Record<string, ImportResult>;
  erroresGenerales: string[];
  message: string;
}

interface ImportResult {
  totalRows: number;
  successRows: number;
  errorRows: number;
  errors: string[];
  message: string;
}
```

**Ejemplo respuesta:**
```json
{
  "totalHojas": 5,
  "hojasProcesadas": 5,
  "hojasConErrores": 0,
  "resultadosPorHoja": {
    "PRODUCTOS": {
      "totalRows": 150,
      "successRows": 150,
      "errorRows": 0,
      "errors": [],
      "message": "Importación exitosa: 150 de 150 filas procesadas correctamente"
    }
  },
  "erroresGenerales": [],
  "message": "Importación completa exitosa: 5 de 5 hojas procesadas correctamente"
}
```

---

### MercadoLibre (ML)

Módulo para integración con la **API de MercadoLibre**.

**¿Qué hace?**
- **Calcula costos de envío:** Determina cuánto cuesta el envío gratis que absorbe el vendedor, según el PVP del producto
- **Obtiene comisiones:** Consulta el porcentaje de comisión que cobra ML por cada venta
- **Recalcula precios:** Cuando cambian los costos de envío o comisiones, recalcula automáticamente los PVP

**¿Por qué es necesario?**
- MercadoLibre ofrece "envío gratis" pero el vendedor paga ese costo
- El costo de envío depende del precio del producto (tiers)
- La comisión de ML varía según categoría y tipo de publicación
- Estos costos deben incluirse en el cálculo del PVP para mantener el margen deseado

**Flujo típico:**
1. Se calcula el PVP base del producto
2. Se determina el costo de envío según el tier de precio
3. Se recalcula el PVP incluyendo el costo de envío
4. Si el nuevo PVP cambia de tier, se repite hasta estabilizar
5. Se guarda el costo de envío y se recalculan todos los precios

**Cuotas utilizadas en los cálculos:**
| Cálculo | Cuotas | Descripción |
|---------|--------|-------------|
| Costo de envío | `0` (contado) | Usa el PVP calculado con cuotas=0 para determinar el tier de envío |
| Costo de venta | N/A | Usa el precio actual publicado en MercadoLibre (no el calculado localmente) |

#### Configuración

```http
GET /api/ml/configuracion
```
**Response:** `ConfiguracionMl`

```http
PUT /api/ml/configuracion
Content-Type: application/json

{
  "umbralEnvioGratis": 33000,
  "tier1Hasta": 15000,
  "tier1Costo": 1115,
  "tier2Hasta": 25000,
  "tier2Costo": 2300,
  "tier3Costo": 2810
}
```
**Response:** `ConfiguracionMl`

#### Calcular Costo de Envío

Calcula y guarda el costo de envío para productos de ML. **Modifica datos** (guarda en BD y recalcula precios automáticamente).

```http
POST /api/ml/costo-envio                         # ASYNC - Todos los MLAs en background
POST /api/ml/costo-envio?mla=MLA123456789        # SYNC - Solo ese MLA
POST /api/ml/costo-envio?productoId=123          # SYNC - Solo el MLA del producto
```

**Comportamiento según parámetros:**
| Parámetros | Comportamiento | Response |
|------------|----------------|----------|
| `mla=X` | **Sincrónico** - solo ese MLA | `CostoEnvioResponse` |
| `productoId=X` | **Sincrónico** - busca MLA del producto | `CostoEnvioResponse` |
| Sin parámetros | **Asincrónico** - todos en background | `202 Accepted` + info de endpoints |

**Query params (opcionales, prioridad: mla > productoId > masivo async):**
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `mla` | string | Código MLA (ej: MLA123456789) |
| `productoId` | number | ID del producto (busca su MLA asociado) |

**Lógica de cálculo:**

El cálculo es **iterativo** hasta que el costo de envío se estabilice:

0. **Si el MLA no tiene `comisionPorcentaje`**, primero se obtiene automáticamente llamando a `obtenerCostoVenta` (esto es necesario para calcular el PVP correctamente si el canal usa `FLAG_COMISION_ML`)
1. Calcular PVP con costo de envío actual (inicia en $0)
2. Determinar costo de envío según el PVP:
   - **PVP < $15,000** → `tier1Costo` ($1,115)
   - **$15,000 ≤ PVP < $25,000** → `tier2Costo` ($2,300)
   - **$25,000 ≤ PVP < $33,000** → `tier3Costo` ($2,810)
   - **PVP ≥ $33,000** → Consulta API MercadoLibre (envío gratis)
3. Si el costo cambió, recalcular PVP y repetir
4. Cuando se estabiliza:
   - Calcular costo sin IVA: `costoEnvioSinIva = costoEnvioConIva / 1.21`
   - **Guardar el costo SIN IVA** en la base de datos (`mla.precio_envio`)
   - Recalcular precios del producto

**Manejo del IVA:**

Los costos de envío (tanto de tiers como de API ML) incluyen 21% de IVA. El sistema:
- Muestra ambos valores en la respuesta (`costoEnvioConIva` y `costoEnvioSinIva`)
- **Guarda únicamente el costo sin IVA** en la base de datos
- El cálculo de precios usa el costo sin IVA

**Response (MLA individual):** `CostoEnvioResponse`

```json
{
  "mla": "MLA123456789",
  "status": "active",
  "precioConsultado": 35000.00,
  "costoEnvioConIva": 2810.00,
  "costoEnvioSinIva": 2322.31,
  "mensaje": "Costo envío: $2810.00 (sin IVA: $2322.31) - API ML (iteraciones: 2)"
}
```

**Response (masivo):** `CostoEnvioMasivoResponse`

```json
{
  "totalMlas": 150,
  "exitosos": 140,
  "errores": 5,
  "omitidos": 5,
  "resultados": [
    {
      "mla": "MLA123456789",
      "status": "active",
      "precioConsultado": 35000.00,
      "costoEnvioConIva": 2810.00,
      "costoEnvioSinIva": 2322.31,
      "mensaje": "Costo envío: $2810.00 (sin IVA: $2322.31) - Tier (iteraciones: 1)"
    }
  ]
}
```

**Mensajes posibles:**
| Mensaje | Significado |
|---------|-------------|
| `Costo envío: $X (sin IVA: $Y) - Tier (iteraciones: N)` | Usó tier fijo (PVP < umbral) |
| `Costo envío: $X (sin IVA: $Y) - API ML (iteraciones: N)` | Consultó API ML (PVP >= umbral) |
| `MLA no encontrado en la base de datos` | El código MLA no existe |
| `MLA sin producto asociado` | El MLA no tiene producto vinculado |
| `Tiers de costo de envío no configurados` | Faltan tiers en configuración |
| `Error calculando PVP: ...` | Error al calcular precio |
| `No se pudo calcular el costo de envío` | El cálculo resultó en $0 |

**Ejemplo de uso:**
```typescript
// Calcular para un MLA específico (SYNC)
const response = await fetch(
  'http://localhost:8080/api/ml/costo-envio?mla=MLA123456789',
  { method: 'POST' }
);
const resultado: CostoEnvioResponse = await response.json();

// Calcular para un producto (SYNC - busca su MLA)
const responseProducto = await fetch(
  'http://localhost:8080/api/ml/costo-envio?productoId=123',
  { method: 'POST' }
);
const resultadoProducto: CostoEnvioResponse = await responseProducto.json();
```

#### Cálculo Masivo (Asíncrono)

El cálculo masivo (sin parámetros) **siempre es asíncrono**. No existe parámetro para hacerlo sincrónico.

```http
POST /api/ml/costo-envio                     # Inicia proceso en background (202 Accepted)
GET  /api/ml/costo-envio/estado              # Consulta estado del proceso
POST /api/ml/costo-envio/cancelar            # Cancela el proceso en ejecución
GET  /api/ml/costo-envio/resultado           # Obtiene resultado del último proceso
```

**Flujo de uso:**

1. **Iniciar proceso:** `POST /api/ml/costo-envio` (sin parámetros)
   - Retorna inmediatamente con `202 Accepted`
   - Si ya hay un proceso en ejecución, retorna `400 Bad Request`

2. **Monitorear progreso:** `GET /api/ml/costo-envio/estado`
   - Consultar periódicamente para ver el progreso
   - Response: `ProcesoMasivoEstado`

3. **Cancelar (opcional):** `POST /api/ml/costo-envio/cancelar`
   - Cancela el proceso en ejecución
   - Los MLAs ya procesados mantienen sus cambios

4. **Obtener resultado:** `GET /api/ml/costo-envio/resultado`
   - Disponible solo después de que el proceso termine
   - Response: `CostoEnvioMasivoResponse`

**Response al iniciar (202 Accepted):**
```json
{
  "mensaje": "Proceso masivo iniciado en background",
  "iniciado": true,
  "endpoints": {
    "estado": "GET /api/ml/costo-envio/estado",
    "cancelar": "POST /api/ml/costo-envio/cancelar",
    "resultado": "GET /api/ml/costo-envio/resultado"
  }
}
```

**Response si ya hay proceso en ejecución (400):**
```json
{
  "mensaje": "Ya hay un proceso masivo en ejecución. Use GET /api/ml/costo-envio/estado para ver el progreso.",
  "iniciado": false
}
```

**Response Estado:** `ProcesoMasivoEstado`

```typescript
interface ProcesoMasivoEstado {
  enEjecucion: boolean;
  total: number;           // Total de MLAs a procesar
  procesados: number;      // MLAs procesados hasta ahora
  exitosos: number;        // Procesados exitosamente
  errores: number;         // Procesados con error
  estado: string;          // "IDLE", "EN_EJECUCION", "CANCELADO", "COMPLETADO", "ERROR"
  iniciadoEn: string | null;      // ISO DateTime
  finalizadoEn: string | null;    // ISO DateTime
  mensaje: string | null;         // Mensaje descriptivo
}
```

**Ejemplo de uso (polling):**
```typescript
// 1. Iniciar proceso masivo (ASYNC)
const initResponse = await fetch('http://localhost:8080/api/ml/costo-envio', { method: 'POST' });
if (initResponse.status !== 202) {
  console.log('No se pudo iniciar - ya hay un proceso en ejecución');
  return;
}

// 2. Polling del estado
const checkEstado = async () => {
  const response = await fetch('http://localhost:8080/api/ml/costo-envio/estado');
  const estado: ProcesoMasivoEstado = await response.json();

  console.log(`Progreso: ${estado.procesados}/${estado.total} (${estado.estado})`);

  if (estado.enEjecucion) {
    setTimeout(checkEstado, 2000); // Revisar cada 2 segundos
  } else {
    // Proceso terminado, obtener resultado
    const resultResponse = await fetch('http://localhost:8080/api/ml/costo-envio/resultado');
    const resultado: CostoEnvioMasivoResponse = await resultResponse.json();
    console.log('Resultado:', resultado);
  }
};

checkEstado();

// 3. Para cancelar (opcional)
const cancelar = async () => {
  await fetch('http://localhost:8080/api/ml/costo-envio/cancelar', { method: 'POST' });
};
```

#### Calcular Costo de Venta (Comisiones)

Consulta los costos de venta (comisiones ML) de un producto y **guarda el porcentaje de comisión** en la entidad MLA (`comisionPorcentaje` y `fechaCalculoComision`).

```http
POST /api/ml/costo-venta                          # ASYNC - Todos los MLAs en background
POST /api/ml/costo-venta?mla=MLA123456789         # SYNC - Solo ese MLA
POST /api/ml/costo-venta?productoId=123           # SYNC - Solo el MLA del producto
```

**Comportamiento según parámetros:**
| Parámetros | Comportamiento | Response |
|------------|----------------|----------|
| `mla=X` | **Sincrónico** - solo ese MLA | `CostoVentaResponse` |
| `productoId=X` | **Sincrónico** - busca MLA del producto | `CostoVentaResponse` |
| Sin parámetros | **Asincrónico** - todos en background | `202 Accepted` + info de endpoints |

**Query params (opcionales, prioridad: mla > productoId > masivo async):**
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `mla` | string | Código MLA (ej: MLA123456789) |
| `productoId` | number | ID del producto (busca su MLA asociado) |

**Response:** `CostoVentaResponse`

```json
{
  "mla": "MLA913196815",
  "status": "active",
  "precioConsultado": 15769.00,
  "comisionVentaTotal": 6738.69,
  "costoFijo": 2300.00,
  "cargoFinanciacion": 13.80,
  "porcentajeMeli": 14.35,
  "porcentajeTotal": 28.15,
  "listingTypeId": "gold_pro",
  "listingTypeName": "Premium",
  "mensaje": "Comisión total: $6738.69 (Fijo: $2300.00 + Financiación: $13.80), Porcentaje: 28.15%"
}
```

**Campos de respuesta:**
| Campo | Descripción |
|-------|-------------|
| `mla` | Código MLA consultado |
| `status` | Estado del producto en ML ("active", "paused", etc.) |
| `precioConsultado` | Precio actual del producto en ML |
| `comisionVentaTotal` | Total de comisión de venta (sale_fee_amount) |
| `costoFijo` | Costo fijo dentro de la comisión (sale_fee_details.fixed_fee) |
| `cargoFinanciacion` | Cargo adicional por financiación (sale_fee_details.financing_add_on_fee) |
| `porcentajeMeli` | Porcentaje de comisión de MercadoLibre (sale_fee_details.meli_percentage_fee) |
| `porcentajeTotal` | Porcentaje total aplicado (sale_fee_details.percentage_fee) |
| `listingTypeId` | ID del tipo de publicación ("gold_pro", "gold_special", etc.) |
| `listingTypeName` | Nombre del tipo de publicación ("Premium", "Clásica", etc.) |
| `mensaje` | Descripción del resultado o error |

**Mensajes de error posibles:**
| Mensaje | Significado |
|---------|-------------|
| `Producto no encontrado con ID: X` | El productoId no existe |
| `El producto no tiene MLA asociado` | El producto no tiene MLA vinculado |
| `No se pudo obtener el producto de MercadoLibre` | Error al consultar API ML |
| `Error al consultar costos de venta` | Error en la API de listing_prices |

**Ejemplo de uso (sincrónico):**
```typescript
// Calcular costos de venta por MLA (SYNC) - guarda comisionPorcentaje
const response = await fetch(
  'http://localhost:8080/api/ml/costo-venta?mla=MLA123456789',
  { method: 'POST' }
);
const costos: CostoVentaResponse = await response.json();

// Calcular costos de venta por producto (SYNC) - guarda comisionPorcentaje
const responseProducto = await fetch(
  'http://localhost:8080/api/ml/costo-venta?productoId=123',
  { method: 'POST' }
);
const costosProducto: CostoVentaResponse = await responseProducto.json();
```

#### Costo de Venta Masivo (Asíncrono)

El cálculo masivo (sin parámetros) **siempre es asíncrono**. No existe parámetro para hacerlo sincrónico.

```http
POST /api/ml/costo-venta                     # Inicia proceso en background (202 Accepted)
GET  /api/ml/costo-venta/estado              # Consulta estado del proceso
POST /api/ml/costo-venta/cancelar            # Cancela el proceso en ejecución
GET  /api/ml/costo-venta/resultado           # Obtiene resultado del último proceso
```

**Flujo de uso:**

1. **Iniciar proceso:** `POST /api/ml/costo-venta` (sin parámetros)
   - Retorna inmediatamente con `202 Accepted`
   - Si ya hay un proceso en ejecución, retorna `400 Bad Request`

2. **Monitorear progreso:** `GET /api/ml/costo-venta/estado`
   - Consultar periódicamente para ver el progreso
   - Response: `ProcesoMasivoEstado`

3. **Cancelar (opcional):** `POST /api/ml/costo-venta/cancelar`
   - Cancela el proceso en ejecución
   - Los MLAs ya consultados se mantienen en el resultado

4. **Obtener resultado:** `GET /api/ml/costo-venta/resultado`
   - Disponible solo después de que el proceso termine
   - Response: `CostoVentaMasivoResponse`

**Response al iniciar (202 Accepted):**
```json
{
  "mensaje": "Proceso masivo de costo de venta iniciado en background",
  "iniciado": true,
  "endpoints": {
    "estado": "GET /api/ml/costo-venta/estado",
    "cancelar": "POST /api/ml/costo-venta/cancelar",
    "resultado": "GET /api/ml/costo-venta/resultado"
  }
}
```

**Nota:** El proceso masivo también guarda `comisionPorcentaje` y `fechaCalculoComision` en cada MLA procesado exitosamente.
```

**Response masivo:** `CostoVentaMasivoResponse`

```typescript
interface CostoVentaMasivoResponse {
  totalProcesados: number;
  exitosos: number;
  errores: number;
  omitidos: number;
  resultados: CostoVentaResponse[];
}
```

**Ejemplo de uso (polling):**
```typescript
// 1. Iniciar proceso masivo (ASYNC) - guarda comisionPorcentaje en cada MLA
const initResponse = await fetch('http://localhost:8080/api/ml/costo-venta', { method: 'POST' });
if (initResponse.status !== 202) {
  console.log('No se pudo iniciar - ya hay un proceso en ejecución');
  return;
}

// 2. Polling del estado
const checkEstado = async () => {
  const response = await fetch('http://localhost:8080/api/ml/costo-venta/estado');
  const estado: ProcesoMasivoEstado = await response.json();

  console.log(`Progreso: ${estado.procesados}/${estado.total} (${estado.estado})`);

  if (estado.enEjecucion) {
    setTimeout(checkEstado, 2000); // Revisar cada 2 segundos
  } else {
    // Proceso terminado, obtener resultado
    const resultResponse = await fetch('http://localhost:8080/api/ml/costo-venta/resultado');
    const resultado: CostoVentaMasivoResponse = await resultResponse.json();
    console.log('Resultado:', resultado);
  }
};

checkEstado();

// 3. Para cancelar (opcional)
const cancelar = async () => {
  await fetch('http://localhost:8080/api/ml/costo-venta/cancelar', { method: 'POST' });
};
```

---

### DUX ERP

Módulo para integración con **DUX Software ERP** (sistema de gestión empresarial externo).

**¿Qué hace?**
- **Consulta productos:** Permite consultar el catálogo de productos cargados en DUX
- **Actualiza precios:** Envía los precios calculados por este sistema a las listas de precios de DUX
- **Importa productos (DUX → Local):** Actualiza productos locales con datos de DUX (costo, iva, proveedor, etc.)
- **Exporta productos (Local → DUX):** Envía productos locales a DUX
- **Consulta estado:** Verifica si los procesos de actualización en DUX terminaron correctamente

**Flujos típicos:**
1. **Actualizar precios:** Calcular PVP → `POST /listas-precios/{id}/precios` → monitorear con `GET /procesos/{id}/estado`
2. **Obtener productos DUX:** `POST /obtener-productos` → `GET /obtener-productos/estado` → `GET /obtener-productos/resultado`
3. **Importar datos de DUX:** `POST /importar-productos` → `GET /importar-productos/estado` → `GET /importar-productos/resultado`
4. **Exportar a DUX:** `POST /exportar-productos` → monitorear con `GET /procesos/{id}/estado`

**Límite de rate:** 1 request cada 7 segundos (~0.14 req/seg) - impuesto por DUX

**Nota:** Las operaciones de obtener productos e importar son **asincrónicas** (pueden tardar varios minutos). Se inician con POST, se monitorean con GET /estado, y los resultados se obtienen con GET /resultado. Usan el mismo tipo `ProcesoMasivoEstado` que MercadoLibre.

#### Status

```http
GET /api/dux/status
```
**Response:**
```json
{
  "configurado": true,
  "servicio": "DUX ERP"
}
```

#### Productos

```http
POST /api/dux/obtener-productos
```
Inicia la obtención de todos los productos de DUX en background (puede tardar varios minutos debido al rate limit).

**Response (202):**
```json
{
  "mensaje": "Obtención de productos DUX iniciada en background",
  "iniciado": true,
  "endpoints": {
    "estado": "GET /api/dux/obtener-productos/estado",
    "cancelar": "POST /api/dux/obtener-productos/cancelar",
    "resultado": "GET /api/dux/obtener-productos/resultado"
  }
}
```
**Response (400):** Ya hay una obtención en ejecución.

```http
GET /api/dux/obtener-productos/estado
```
**Response:** `ProcesoMasivoEstado`

```http
POST /api/dux/obtener-productos/cancelar
```
**Response:** `{ "mensaje": "...", "cancelado": true|false }`

```http
GET /api/dux/obtener-productos/resultado
```
**Response:** `DuxItem[]` si el proceso completó, o `{ "mensaje": "...", "disponible": false }` si no hay resultados.

```http
GET /api/dux/productos/{codItem}
```
**Response:** `DuxItem` o `404 Not Found`

#### Listas de Precios

```http
GET /api/dux/listas-precios
```
**Response:** JSON con todas las listas de precios de venta

```http
GET /api/dux/listas-precios/{nombre}/id
```
**Response:**
```json
{
  "nombre": "KT GASTRO",
  "id": 123
}
```

#### Actualizar Precios

```http
POST /api/dux/listas-precios/{idLista}/precios
Content-Type: application/json

[
  { "sku": "SKU001", "tipo": "SIMPLE", "precio": 1500.00 },
  { "sku": "SKU002", "tipo": "COMBO", "precio": 2500.00 }
]
```

**⚠️ IMPORTANTE:** El campo `tipo` es **OBLIGATORIO**. Si no se envía el tipo correcto (`SIMPLE` o `COMBO`), DUX desconfigura el producto y puede perder su configuración de componentes.

**Response (200):**
```json
{
  "idProceso": 12345,
  "mensaje": "Proceso de actualización iniciado"
}
```

**Response (400):** `{ "message": "No se pudo iniciar el proceso de actualización", "path": "/api/dux/listas-precios/{idLista}/precios" }`

#### Estado de Proceso

```http
GET /api/dux/procesos/{idProceso}/estado
```
**Response:**
```json
{
  "idProceso": 12345,
  "estado": "..."
}
```

#### Empresas y Sucursales

Consulta las empresas y sucursales configuradas en DUX. Se usan para configurar el módulo de reposición.

```http
GET /api/dux/empresas
```
**Response:** JSON con todas las empresas de DUX

```http
GET /api/dux/empresas/{idEmpresa}/sucursales
```
**Response:** JSON con las sucursales de la empresa

#### Deudas de Clientes — Async

Consulta comprobantes de venta (facturas, notas de crédito, notas de débito) para un rango de fechas, empresa y sucursales. Proceso asíncrono con polling de estado.

```http
POST /api/dux/deudas-clientes/iniciar?fechaDesde=2025-01-01&fechaHasta=2025-06-13&idEmpresa=3455&idsSucursal=3&idsSucursal=7&conCobro=false
```

| Param | Tipo | Requerido | Descripción |
|---|---|---|---|
| `fechaDesde` | string | Sí | Fecha inicio `yyyy-MM-dd` |
| `fechaHasta` | string | Sí | Fecha fin `yyyy-MM-dd` |
| `idEmpresa` | int | Sí | ID empresa DUX |
| `idsSucursal` | int[] | Sí | IDs sucursales DUX (repetir param para varias) |
| `conCobro` | boolean | No | `false` = pendientes, `true` = cobrados, sin enviar = todos |

**Auth:** requiere permiso `INTEGRACIONES_VER`

**Response (202):**
```json
{
  "mensaje": "Consulta de deudas iniciada en background",
  "iniciado": true,
  "endpoints": {
    "estado": "GET /api/dux/deudas-clientes/estado",
    "cancelar": "POST /api/dux/deudas-clientes/cancelar",
    "resultado": "GET /api/dux/deudas-clientes/resultado"
  }
}
```

```http
GET /api/dux/deudas-clientes/estado
```
**Response:** `ProcesoMasivoEstado`

```http
POST /api/dux/deudas-clientes/cancelar
```
**Response:** `{ "mensaje": "...", "cancelado": true|false }`

```http
GET /api/dux/deudas-clientes/resultado
```

**Response (200):**
```json
{
  "total": 337,
  "resumen": {
    "facturas": 300,
    "notasCredito": 30,
    "notasDebito": 7
  },
  "comprobantes": [
    {
      "id": 69681388,
      "idCliente": 4858966,
      "cliente": "LANTEC IT SA",
      "cuit": "33709363919",
      "tipoComp": "FACTURA",
      "letraComp": "B",
      "nroComp": 1285,
      "nroPtoVta": "11",
      "fecha": "Jun 2, 2025 3:00:00 AM",
      "total": 56452.00,
      "montoGravado": 44183.47,
      "montoIva": 9278.53,
      "montoExento": 2990.00,
      "montoDesc": 0.00,
      "anulada": false,
      "urlFactura": "https://erp.duxsoftware.com.ar/...",
      "detalles": [
        {
          "codItem": "AA05",
          "item": "AA PRODUCTO DE PRUEBA5",
          "cantidad": 1.0,
          "precioUni": 18720.66,
          "porcDesc": 0.0,
          "porcIva": 21.0
        }
      ],
      "cobros": [
        {
          "numeroPuntoDeVenta": "1",
          "numeroComprobante": 768,
          "personal": "DEMO, DEMO",
          "caja": "CENTRAL",
          "movimientos": [
            {
              "tipoDeValor": "EFECTIVO",
              "referencia": "",
              "monto": 56452.00
            }
          ]
        }
      ]
    }
  ]
}
```

**Valores posibles de `tipoComp`:**

| Valor | Categoría |
|---|---|
| `FACTURA`, `FACTURA_FCE_MIPYMES`, `COMPROBANTE_VENTA` | Facturas |
| `NOTA_CREDITO`, `NOTA_CREDITO_FCE_MI_PYMES` | Notas de crédito |
| `NOTA_DEBITO`, `NOTA_DEBITO_FCE_MI_PYMES` | Notas de débito |

**Notas:**
- `cobros` viene vacío (`[]`) si el comprobante no tiene cobro aplicado
- `fecha` viene en formato DUX (`"Jun 2, 2025 3:00:00 AM"`) — el frontend debe parsearlo
- La llamada puede ser lenta porque pagina contra DUX a razón de 1 request cada 7 segundos (rate limit de DUX)
- Las empresas/sucursales disponibles se obtienen de `GET /api/dux/empresas` y `GET /api/dux/empresas/{id}/sucursales`
- **⚠️ La API de DUX no respeta correctamente el filtro `conCobro`.** El backend aplica un filtro local adicional post-fetch para garantizar consistencia: si `conCobro=false` se descartan comprobantes con cobros, y si `conCobro=true` se descartan los que no tienen.

#### Importar Productos (DUX → Local) — Async

```http
POST /api/dux/importar-productos
```

Inicia la importación en background. Obtiene todos los productos de DUX y actualiza los productos locales existentes (match por SKU).
Campos actualizados: `descripcion`, `costo`, `codExt`, `proveedor` (auto-crea si no existe), `iva`, `activo`.
Si cambian `costo`, `iva` o `proveedor`, se recalculan precios automáticamente.

**Response (202):**
```json
{
  "mensaje": "Importación DUX iniciada en background",
  "iniciado": true,
  "endpoints": {
    "estado": "GET /api/dux/importar-productos/estado",
    "cancelar": "POST /api/dux/importar-productos/cancelar",
    "resultado": "GET /api/dux/importar-productos/resultado"
  }
}
```
**Response (400):** Ya hay una importación en ejecución.

```http
GET /api/dux/importar-productos/estado
```
**Response:** `ProcesoMasivoEstado`

```http
POST /api/dux/importar-productos/cancelar
```
**Response:** `{ "mensaje": "...", "cancelado": true|false }`

```http
GET /api/dux/importar-productos/resultado
```
**Response:** `ImportDuxResult` si el proceso completó, o `{ "mensaje": "...", "disponible": false }` si no hay resultados.

```json
{
  "productosActualizados": 150,
  "productosNoEncontrados": 23,
  "proveedoresCreados": 2,
  "totalProductosDux": 5743,
  "skusNoEncontrados": ["SKU999", "SKU888"],
  "errores": []
}
```

#### Exportar Productos (Local → DUX)

```http
POST /api/dux/exportar-productos
Content-Type: application/json

{ "skus": ["SKU001", "SKU002"] }
```

Body opcional. Si se omite o `skus` es null, se exportan todos los productos.
Campos enviados: `cod_item`, `item`, `tipo_producto`, `habilitado`, `costo`, `codigo_externo`, `porc_iva`, `ctd_unidades_por_bulto`.
DUX procesa en segundo plano; usar `GET /procesos/{id}/estado` para monitorear.

**Response (200):** `ExportDuxResult`

```json
{
  "productosEnviados": 2,
  "idProceso": 12345,
  "errores": []
}
```

---

### Tienda Nube

Integración con la API de Tienda Nube para consultar ventas, stock y órdenes.

**Base:** `/api/nube`

#### Tipos

```typescript
interface StockNubeDTO {
  sku: string;
  stock: number | null;
  store: string;
}

interface VentaNubeDTO {
  sku: string;
  cantidad: number | null;
  store: string;
}
```

#### Status

```http
GET /api/nube/status
```

**Response:** `200 OK`
```json
{
  "configurado": true,
  "servicio": "Tienda Nube",
  "stores": ["hogar", "gastro"]
}
```

#### Ventas

```http
GET /api/nube/ventas          # Todas las tiendas
GET /api/nube/ventas/hogar    # Solo KT HOGAR
GET /api/nube/ventas/gastro   # Solo KT GASTRO
```

**Response:** `200 OK` + `List<VentaNubeDTO>`

#### Stock

```http
GET /api/nube/stock/{sku}
```

**Response:**
- `200 OK` + `StockNubeDTO`
- `404 Not Found` si no se encuentra el SKU

#### Órdenes

```http
GET /api/nube/ordenes/{numero}
```

**Response:**
- `200 OK` + JSON de la orden (estructura de Tienda Nube)
- `404 Not Found` si no se encuentra la orden

---

### Configuración Automatización

Módulo para gestionar **configuraciones clave-valor** usadas por procesos externos de automatización.

**¿Qué hace?**
- Almacena parámetros de configuración que controlan cómo se ejecutan los procesos automáticos de actualización de precios
- Permite al usuario modificar estos parámetros desde el frontend sin tocar código
- Los valores son strings genéricos que el proceso de automatización interpreta según su lógica

**Ejemplos de uso:**
| Clave | Valor | Descripción |
|-------|-------|-------------|
| `MARGEN_MINIMO_ML` | `15` | Margen mínimo % para publicar en MercadoLibre |
| `LISTA_DUX_GASTRO` | `KT GASTRO` | Nombre de la lista de precios en DUX |
| `REDONDEO_PVP` | `100` | Redondear PVP al múltiplo más cercano |
| `EJECUTAR_AUTOMATICO` | `true` | Habilitar/deshabilitar ejecución automática |

**Nota:** Esta tabla es independiente de `configuracion_ml`. Aquella tiene campos tipados específicos para ML, mientras que esta es genérica clave-valor.

#### Listar

```http
GET /api/config-automatizacion
GET /api/config-automatizacion?search=texto
GET /api/config-automatizacion?page=0&size=20
```
**Response:** `Page<ConfigAutomatizacion>`

#### Obtener

```http
GET /api/config-automatizacion/{id}
```
**Response:** `ConfigAutomatizacion` o `404`

```http
GET /api/config-automatizacion/clave/{clave}
```
**Response:** `ConfigAutomatizacion` o `404`

#### Crear

```http
POST /api/config-automatizacion
Content-Type: application/json

{
  "clave": "MI_CONFIG",
  "valor": "valor123",
  "descripcion": "Descripción opcional"
}
```
**Response:** `201 Created` + `ConfigAutomatizacion`
**Error:** `409 Conflict` si la clave ya existe

#### Actualizar

```http
PUT /api/config-automatizacion/{id}
Content-Type: application/json

{
  "valor": "nuevo_valor",
  "descripcion": "Nueva descripción"
}
```
**Response:** `ConfigAutomatizacion`

#### Eliminar

```http
DELETE /api/config-automatizacion/{id}
```
**Response:** `204 No Content`

---

### Órdenes de Compra

Módulo para gestionar **órdenes de compra** a proveedores. Se integra con el módulo de reposición para generar órdenes automáticamente.

**Flujo de estados:**
```
BORRADOR → ENVIADA → RECIBIDA_PARCIAL → COMPLETA
                  └──────────────────→ CANCELADA
```

**Base:** `/api/ordenes-compra`

#### Listar

```http
GET /api/ordenes-compra
GET /api/ordenes-compra?proveedorId=5
GET /api/ordenes-compra?estado=BORRADOR
GET /api/ordenes-compra?proveedorId=5&estado=ENVIADA&page=0&size=20
```

**Query params:**
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `proveedorId` | number | Filtrar por proveedor (opcional) |
| `estado` | string | Filtrar por estado: BORRADOR, ENVIADA, RECIBIDA_PARCIAL, COMPLETA, CANCELADA (opcional) |
| `page`, `size`, `sort` | - | Paginación estándar |

**Response:** `PageResponse<OrdenCompra>`

#### Obtener

```http
GET /api/ordenes-compra/{id}
```
**Response:** `OrdenCompra`

#### Crear

```http
POST /api/ordenes-compra
Content-Type: application/json

{
  "proveedorId": 5,
  "observaciones": "Pedido urgente",
  "lineas": [
    { "productoId": 10, "cantidadPedida": 100, "costoUnitario": 1500.00 },
    { "productoId": 20, "cantidadPedida": 50 }
  ]
}
```
**Response:** `201 Created` + `OrdenCompra`

#### Actualizar

```http
PUT /api/ordenes-compra/{id}
Content-Type: application/json

{
  "observaciones": "Pedido actualizado",
  "lineas": [
    { "productoId": 10, "cantidadPedida": 200, "costoUnitario": 1400.00 }
  ]
}
```
**Response:** `OrdenCompra`

#### Eliminar

```http
DELETE /api/ordenes-compra/{id}
```
**Response:** `204 No Content`

#### Enviar orden

Cambia el estado de BORRADOR a ENVIADA.

```http
POST /api/ordenes-compra/{id}/enviar
```
**Response:** `OrdenCompra` (con estado ENVIADA)

#### Registrar recepción

Registra las cantidades recibidas en cada línea. Cambia el estado a RECIBIDA_PARCIAL o COMPLETA según corresponda.

```http
POST /api/ordenes-compra/{id}/recepcion
Content-Type: application/json

{
  "lineas": [
    { "lineaId": 1, "cantidadRecibida": 80 },
    { "lineaId": 2, "cantidadRecibida": 50 }
  ]
}
```
**Response:** `OrdenCompra` (con cantidades recibidas actualizadas)

---

### Reposición

Módulo para calcular **sugerencias de reposición** de stock basándose en ventas históricas (obtenidas de DUX). El cálculo es asíncrono y usa datos de ventas de los últimos 3 meses.

**Flujo típico:**
1. Configurar parámetros (meses de cobertura, pesos por mes, empresa DUX)
2. Iniciar cálculo async → obtiene ventas de DUX, stock, y calcula sugerencias
3. Revisar resultado → ajustar pedidos manualmente si se desea
4. Generar órdenes de compra desde el resultado

**Base:** `/api/reposicion`

#### Configuración

```http
GET /api/reposicion/config
```
**Response:** `ReposicionConfig`

```http
PUT /api/reposicion/config
Content-Type: application/json

{
  "mesesCobertura": 2,
  "pesoMes1": 0.50,
  "pesoMes2": 0.30,
  "pesoMes3": 0.20,
  "idEmpresaDux": 1,
  "idsSucursalDux": [1, 2]
}
```
**Response:** `ReposicionConfig`

#### Cálculo (Asíncrono)

```http
POST /api/reposicion/calcular
```
Inicia el cálculo de reposición en background.
- **`202 Accepted`** si se inició correctamente
- **`409 Conflict`** si ya hay un cálculo en ejecución

```http
GET /api/reposicion/calcular/estado
```
**Response:** `ProcesoMasivoEstado`

```http
POST /api/reposicion/calcular/cancelar
```
- **`200 OK`** si se canceló
- **`409 Conflict`** si no hay cálculo en ejecución

#### Resultado

```http
GET /api/reposicion/resultado
```
**Response:** `ReposicionResult` o `204 No Content` si no hay resultado disponible

#### Ajustar pedidos

Permite modificar manualmente las cantidades sugeridas antes de generar órdenes.

```http
PUT /api/reposicion/resultado/ajustar
Content-Type: application/json

{
  "ajustes": [
    { "productoId": 10, "pedido": 200 },
    { "productoId": 20, "pedido": 0 }
  ]
}
```
**Response:** `ReposicionResult` (actualizado)

#### Generar órdenes de compra

Genera órdenes de compra agrupadas por proveedor desde el resultado del cálculo.

```http
POST /api/reposicion/generar-ordenes
POST /api/reposicion/generar-ordenes?proveedorId=5
```

**Query params:**
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `proveedorId` | number | Generar solo para un proveedor específico (opcional) |

**Response:** `201 Created` + `List<OrdenCompra>`

#### Exportar Excel

```http
GET /api/reposicion/resultado/excel
```
**Response:** Archivo Excel con todas las sugerencias de reposición

```http
GET /api/reposicion/resultado/excel/oc/{id}
```
**Response:** Archivo Excel con los datos de una orden de compra específica

---

## Ejemplos de Uso (fetch)

### Listar productos con precios y filtros
```typescript
const params = new URLSearchParams({
  search: 'bolsa',
  marcaIds: '5',
  costoMin: '100',
  page: '0',
  size: '20',
  sort: 'descripcion,asc'
});

const response = await fetch(`http://localhost:8080/api/precios?${params}`);
const data: PageResponse<ProductoConPrecios> = await response.json();
```

### Crear producto
```typescript
const response = await fetch('http://localhost:8080/api/productos', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    sku: 'BOL-001',
    descripcion: 'Bolsa kraft',
    tituloWeb: 'Bolsa de papel kraft',
    origenId: 1,
    clasifGralId: 1,
    tipoId: 1,
    costo: 150.00,
    iva: 21
  })
});

if (response.status === 201) {
  const producto: Producto = await response.json();
}
```

### Recalcular precios de un producto-canal
```typescript
const params = new URLSearchParams({
  productoId: '123',
  canalId: '1'
});

const response = await fetch(
  `http://localhost:8080/api/precios/calcular?${params}`,
  { method: 'POST' }
);
const precios: CanalPrecios = await response.json();
```

### Obtener fórmula de cálculo
```typescript
const params = new URLSearchParams({
  productoId: '123',
  canalId: '1',
  cuotas: '0'
});

const response = await fetch(`http://localhost:8080/api/precios/formula?${params}`);
const formula: FormulaCalculo = await response.json();
```

### Manejo de errores
```typescript
try {
  const response = await fetch(`http://localhost:8080/api/productos/${id}`);

  if (!response.ok) {
    const error: ApiError = await response.json();
    throw new Error(error.message);
  }

  return await response.json();
} catch (error) {
  console.error('Error:', error);
}
```

---

## Comportamiento de Eliminación (ON DELETE)

Al eliminar entidades, el comportamiento varía según las relaciones configuradas:

### Entidades que se eliminan en cascada (CASCADE)

Al eliminar estas entidades, **se eliminan automáticamente** sus registros relacionados:

| Al eliminar... | Se eliminan automáticamente... |
|----------------|-------------------------------|
| **Producto** | ProductoApto, ProductoCatalogo, ProductoCliente, ProductoCanalPrecio, ProductoCanalPrecioInflado, ProductoMargen |
| **Catálogo** | ProductoCatalogo (relaciones con productos), ReglaDescuento |
| **Canal** | ProductoCanalPrecio, ProductoCanalPrecioInflado, CanalConceptoCuota, CanalConceptoRegla, ReglaDescuento |
| **ConceptoCalculo** | CanalConcepto, CanalConceptoRegla |
| **Apto** | ProductoApto (relaciones con productos) |
| **Cliente** | ProductoCliente (relaciones con productos) |

### Entidades que setean NULL (SET_NULL)

Al eliminar estas entidades, los registros relacionados **mantienen su FK en NULL**:

| Al eliminar... | Se setea NULL en... |
|----------------|---------------------|
| **Marca** (padre) | Submarcas (padre_id) |
| **Tipo** (padre) | Subtipos (padre_id) |
| **ClasifGral** (padre) | Subclasificaciones (padre_id) |
| **ClasifGastro** (padre) | Subclasificaciones (padre_id) |
| **Canal** (base) | Canales hijos (canal_base_id) |
| **Catálogo** | ReglaDescuento (catalogo_id) |
| **ClasifGral** | ReglaDescuento, CanalConceptoRegla |
| **ClasifGastro** | ReglaDescuento, CanalConceptoRegla |
| **Tipo** | CanalConceptoRegla (tipo_id) |
| **Marca** | CanalConceptoRegla (marca_id) |

### ⚠️ Entidades que BLOQUEAN la eliminación (RESTRICT)

**No se puede eliminar** estas entidades si tienen registros relacionados:

| Entidad | Bloqueada por | Mensaje de error |
|---------|---------------|------------------|
| **Marca** | Producto | "No se puede eliminar porque tiene registros relacionados en: Productos" |
| **Origen** | Producto | "No se puede eliminar porque tiene registros relacionados en: Productos" |
| **Tipo** | Producto | "No se puede eliminar porque tiene registros relacionados en: Productos" |
| **ClasifGral** | Producto | "No se puede eliminar porque tiene registros relacionados en: Productos" |
| **ClasifGastro** | Producto | "No se puede eliminar porque tiene registros relacionados en: Productos" |
| **Proveedor** | Producto | "No se puede eliminar porque tiene registros relacionados en: Productos" |
| **Material** | Producto | "No se puede eliminar porque tiene registros relacionados en: Productos" |
| **Mla** | Producto | "No se puede eliminar porque tiene registros relacionados en: Productos" |
| **PrecioInflado** | ProductoCanalPrecioInflado | "No se puede eliminar porque tiene registros relacionados en: Producto canal precio inflado" |

### Formato de error 409 (Conflict)

```typescript
interface ErrorResponse {
  success: false;
  message: string;  // "No se puede eliminar porque tiene registros relacionados en: Producto apto"
  path: string;     // "/api/aptos/5"
  timestamp: string;
}
```

### Recomendación para el Frontend

Antes de eliminar entidades que pueden estar bloqueadas, considerar:

1. **Mostrar confirmación** indicando qué se eliminará en cascada
2. **Verificar relaciones** usando los endpoints GET inversos:
   - `GET /api/catalogos/{id}/productos` - ver productos del catálogo
   - `GET /api/aptos/{id}/productos` - ver productos con ese apto
   - `GET /api/clientes/{id}/productos` - ver productos del cliente
   - `GET /api/marcas/{id}/productos` - ver productos de la marca
   - `GET /api/origenes/{id}/productos` - ver productos del origen
   - `GET /api/tipos/{id}/productos` - ver productos del tipo
   - `GET /api/clasif-gral/{id}/productos` - ver productos de la clasificación general
   - `GET /api/clasif-gastro/{id}/productos` - ver productos de la clasificación gastro
   - `GET /api/proveedores/{id}/productos` - ver productos del proveedor
   - `GET /api/materiales/{id}/productos` - ver productos del material
   - `GET /api/mlas/{id}/productos` - ver productos del MLA
3. **Manejar el error 409** mostrando el mensaje descriptivo al usuario

### Endpoints GET inversos (Productos por entidad)

Todos estos endpoints devuelven `List<ProductoResumenDTO>`:

```typescript
interface ProductoResumenDTO {
  id: number;
  sku: string;
  descripcion: string;
}
```

| Endpoint | Descripción |
|----------|-------------|
| `GET /api/catalogos/{id}/productos` | Productos asignados al catálogo |
| `GET /api/aptos/{id}/productos` | Productos con el apto |
| `GET /api/clientes/{id}/productos` | Productos asignados al cliente |
| `GET /api/marcas/{id}/productos` | Productos de la marca |
| `GET /api/origenes/{id}/productos` | Productos del origen |
| `GET /api/tipos/{id}/productos` | Productos del tipo |
| `GET /api/clasif-gral/{id}/productos` | Productos de la clasificación general |
| `GET /api/clasif-gastro/{id}/productos` | Productos de la clasificación gastro |
| `GET /api/proveedores/{id}/productos` | Productos del proveedor |
| `GET /api/materiales/{id}/productos` | Productos del material |
| `GET /api/mlas/{id}/productos` | Productos del MLA |

**Respuestas:**
- `200 OK` + lista de productos (puede estar vacía)
- `404 Not Found` si la entidad no existe

---

## Notas para el Frontend

1. **Recálculo automático:** El backend recalcula automáticamente los precios cuando se modifican:

   | Dato modificado | Alcance del recálculo |
   |-----------------|----------------------|
   | Producto (costo, IVA, clasifGastro) | Ese producto en todos sus canales |
   | ProductoMargen (márgenes) | Ese producto en todos sus canales |
   | ConceptoCalculo (porcentaje o aplicaSobre) | Todos los productos de canales que usan ese concepto |
   | CanalConcepto (asignar/quitar concepto) | Todos los productos del canal |
   | CanalConceptoCuota (porcentaje cuotas) | Todos los productos del canal |
   | Canal (canalBase) | Todos los productos del canal cuyo canalBase cambió |
   | Proveedor (financiacionPorcentaje) | Todos los productos de ese proveedor |
   | Precio inflado (asignar/desasignar) | Ese producto en ese canal |
   | MLA (precioEnvio) | Todos los productos con ese MLA |
   | ClasifGastro (esMaquina) | Todos los productos de esa clasificación en todos sus canales |
   | **POST /api/ml/costo-envio** | Calcula costo de envío y recalcula precios de productos afectados |

2. **Filtros many-to-many:** Para filtrar por múltiples valores, enviar como array o separados por coma:
   ```
   GET /api/precios?canalIds=1,2,3&aptoIds=1,2
   ```

3. **Ordenamiento especial:** Campos calculados de precio y otros campos especiales usan el parámetro `sort` estándar de Spring:

   **Campos de precio** (usan los filtros `canalId`/`cuotas`, o MAX de todos si no se especifican):
   - `pvp`, `pvpInflado`, `costoProducto`, `costosVenta`
   - `ingresoNetoVendedor`, `ganancia`
   - `margenSobreIngreso`, `margenSobrePvp`, `markup`

   **Campos MLA:** `mla`, `mlau`, `comisionPorcentaje`, `precioEnvio`

   **Campos de relaciones:** `esMaquina`

   ```
   GET /api/precios?sort=ganancia,desc                       # MAX ganancia de todos los canales
   GET /api/precios?sort=pvp,asc&canalId=1                   # PVP del canal 1
   GET /api/precios?sort=margenSobrePvp,desc&canalId=2&cuotas=0  # Margen del canal 2, contado
   GET /api/precios?sort=mla,desc                            # Por código MLA
   GET /api/precios?sort=comisionPorcentaje,asc              # Por comisión ML
   ```

4. **Fechas:** Enviar en formato ISO:
   - Date: `YYYY-MM-DD` (ej: `2025-01-15`)
   - DateTime: `YYYY-MM-DDTHH:mm:ss` (ej: `2025-01-15T14:30:00`)

5. **Decimales:** Se manejan con 2 decimales de precisión para precios y porcentajes.

6. **Conceptos flag:** Algunos conceptos (`FLAG_APLICAR_IVA`, `FLAG_USAR_MARGEN_MINORISTA`, `FLAG_USAR_MARGEN_MAYORISTA`, `FLAG_APLICAR_PRECIO_INFLADO`, `FLAG_FINANCIACION_PROVEEDOR`, `FLAG_INCLUIR_ENVIO`) actúan como flags. Solo importa si están asignados al canal o no, el porcentaje se ignora.

7. **Cuotas:** El sistema de cuotas usa:
   - `cuotas = -1` → Transferencia (generalmente con descuento)
   - `cuotas = 0` → Contado (precio base)
   - `cuotas > 0` → Cantidad de cuotas (1, 3, 6, 12, etc.)

   Solo se calculan las opciones configuradas en `canal_concepto_cuota` para cada canal.

8. **Porcentaje en cuotas:** Valores positivos son recargos (ej: +10% para 3 cuotas), valores negativos son descuentos (ej: -15% para transferencia).

9. **Costo de envío ML e IVA:** El endpoint `POST /api/ml/costo-envio` devuelve dos valores:
   - `costoEnvioConIva`: Costo original (con 21% IVA incluido)
   - `costoEnvioSinIva`: Costo neto (sin IVA) - **este es el que se guarda en BD**

   El frontend puede mostrar ambos valores según necesidad. El cálculo de precios usa el costo sin IVA.

10. **Descuentos automáticos:** Los endpoints `GET /api/precios` y `POST /api/precios/calcular` incluyen automáticamente los descuentos aplicables si el canal tiene reglas de descuento configuradas. No es necesario pasar ningún parámetro adicional.
    - Canales con reglas activas → `descuentos: [...]`
    - Canales sin reglas → `descuentos: null`

11. **Cálculo masivo ML asíncrono:** Para el cálculo masivo de costos de envío/venta, llamar al endpoint sin parámetros:
    - `POST /api/ml/costo-envio` (sin params) → Inicia en segundo plano (`202 Accepted`)
    - `GET /api/ml/costo-envio/estado` → Monitorear progreso
    - `POST /api/ml/costo-envio/cancelar` → Cancelar si es necesario
    - `GET /api/ml/costo-envio/resultado` → Obtener resultado final
    - Misma lógica para `/api/ml/costo-venta` y sus sub-endpoints
