# SuperMaster Frontend

Sistema de gestion integral para administracion de productos, precios, canales de venta, catalogos PDF, ordenes de compra, reposicion y automatizacion de precios entre DUX, MercadoLibre y TiendaNube.

## Stack

- **Framework:** Next.js 16 (App Router)
- **UI:** Tailwind CSS + TanStack React Table v8
- **Backend:** Spring Boot (repo separado)
- **Base de datos:** MySQL 8.4
- **Deploy:** Docker Compose (con backup automatico de MySQL)

## Desarrollo local

```bash
npm install
npm run dev
```

Abrir [http://localhost:3000](http://localhost:3000). Requiere el backend corriendo en `localhost:8080`.

## Modulos

| Seccion | Modulos |
|---------|---------|
| **Analisis** | Estadisticas (dashboard con metricas y graficos) |
| **Control** | Auditoria (historial global de cambios con filtros por entidad, usuario, accion y origen) |
| **Maestros** | Productos, Proveedores, Clientes, Catalogos |
| **Referencias** | MLAs, Marcas, Tipos, Materiales, Origenes, Clasif. Generales, Clasif. Gastro, Aptos |
| **Canales** | Canales de venta |
| **Precios** | Monitor de Precios, Conceptos de Calculo, Cuotas por Canal, Reglas de Excepcion, Reglas de Descuento, Precios Inflados |
| **Compras** | Ordenes de Compra, Reposicion de Stock |
| **Integraciones** | Operaciones ML, Automatizacion de Precios KT, Herramientas Excel, Catalogos PDF, DUX ERP |
| **Configuracion** | Usuarios y Accesos, Config. Automatizacion, Config. Envios ML |

## Automatizacion de Precios KT

Proceso central de sincronizacion de precios con 9 pasos configurables:

1. **Importar costos de DUX** - Descarga incremental de costos desde DUX ERP
2. **Calcular precios de envio** - Para MLAs sin precio de envio asignado
3. **Quitar promociones en ML** - Remueve items de promociones activas
4. **Subir a DUX (Mercado Libre)** - Lista de precios ML
5. **Subir a DUX (KT Gastro)** - Lista de precios Gastro (configurable con/sin IVA)
6. **Subir a DUX (KT Hogar)** - Lista de precios Hogar
7. **Subir a Tienda Nube (KT Hogar)** - Precios via API directa
8. **Modificar precios en ML** - Precios de publicaciones
9. **Incluir en promociones ML** - DEAL, Seller Campaign, Smart con topes individuales por MLA

Ejecutable manualmente desde la UI o automaticamente via n8n (`POST /api/automatizacion-precios/ejecutar`).

## Estructura del proyecto

```
src/app/
  productos/                # ABM productos con edicion inline
  canales/                  # Canales de venta y conceptos de calculo
  catalogos/                # Catalogos y asignacion de productos
  catalogos-pdf/            # Generacion manual y automatica de catalogos PDF
  producto-canal-precios/   # Monitor de precios por canal
  ordenes-compra/           # Ordenes de compra y recepcion
  reposicion/               # Calculo de reposicion de stock
  operaciones-ml/           # Calculo masivo de envios y comisiones ML
  automatizacion-precios/   # Sincronizacion de precios DUX/ML/Nube
  herramientas-excel/       # Importacion/exportacion Excel
  usuarios/                 # Gestion de usuarios, roles y permisos
  auditoria/                # Historial global de cambios auditados
  estadisticas/             # Dashboard con metricas y graficos
  mlas/                     # MLAs (publicaciones de MercadoLibre)
  proveedores/ clientes/    # Entidades maestras
  marcas/ tipos/ materiales/ origenes/  # Datos de referencia
  clasif-grales/ clasif-gastro/ aptos/
  reglas-descuento/         # Reglas de descuento por canal
  precios-inflados/         # Reglas de inflacion de precios
  config-automatizacion/    # Parametros clave-valor del sistema
  dux/                      # Integracion con DUX ERP
  manual/                   # Manual de usuario integrado
  components/               # Componentes reutilizables (Table, Modal, Button, AsyncSelect, etc.)
  context/                  # AuthContext, ProcesoActivoContext
  config/                   # Runtime config (API_BASE_URL)
  utils/                    # Utilidades (fetchAPI, exportCSV, formatDate, etc.)
public/
  favicon.svg               # Favicon del sitio (SuperMaster)
  logos/                    # Logos optimizados (WebP)
migrations/                 # Scripts SQL de migracion
```

## Permisos

El sistema usa control de acceso basado en permisos (PBAC). Cada usuario tiene un rol, y cada rol tiene permisos asignados. Los permisos siguen el patron `MODULO_VER` / `MODULO_EDITAR`:

`PRODUCTOS`, `MAESTROS`, `MLAS`, `CANALES`, `PRECIOS`, `ORDENES_COMPRA`, `REPOSICION`, `INTEGRACIONES`, `EXCEL`, `CATALOGOS_PDF`, `USUARIOS`, `CONFIGURACION`, `ESTADISTICAS`, `AUDITORIA`
