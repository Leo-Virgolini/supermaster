# Super Master — Backend

Backend REST que centraliza la gestión comercial de una PyME: **catálogo de productos**, **publicación y pricing en múltiples canales de venta** (MercadoLibre, Tienda Nube, DUX), **órdenes de compra**, **reposición de stock** basada en ventas históricas y **generación de catálogos en PDF**.

El sistema resuelve un problema real: coordinar precios, stock y promociones entre distintos canales de venta que tienen comisiones, impuestos y reglas diferentes, evitando el trabajo manual de mantener consistencia entre ellos.

> **Stack:** Java 25 · Spring Boot 4.0 · Spring Security (JWT) · JPA/Hibernate · MySQL 8.4 · MapStruct · Apache POI · iText · Docker

---

## El problema que resuelve

Una empresa que vende en varios canales (marketplace, tienda online, catálogo mayorista, locales físicos) necesita que **el mismo producto tenga un precio distinto en cada canal**, calculado a partir del costo y ajustado por las comisiones, impuestos, cuotas y promociones propias de cada uno. Hacerlo en planillas es inviable: cambiar un costo o una comisión obliga a recalcular miles de precios manualmente.

Este backend automatiza ese flujo de punta a punta:

1. **Modela el pricing** de cada canal como una combinación configurable de conceptos (comisiones, impuestos, recargos por cuotas, envío) con reglas condicionales.
2. **Calcula precios** para cada combinación producto × canal × cuotas y los persiste.
3. **Recalcula automáticamente en cascada** cuando cambia cualquier variable del cálculo (costo, margen, comisión, financiación, envío, etc.).
4. **Sincroniza** esos precios con los canales externos (MercadoLibre, Tienda Nube, DUX) de forma masiva y auditada.

---

## Highlights técnicos

- **Motor de pricing configurable** sin hard-code: los canales se componen de "conceptos de cálculo" con reglas `INCLUIR` / `EXCLUIR` según atributos del producto (tipo, clasificación, marca, flag de máquina). Agregar un nuevo canal no requiere código.
- **Recálculo automático en cascada** (`RecalculoPrecioFacade`): 11 entidades distintas disparan recálculos con distinto alcance (producto único, canal completo, todos los productos de un proveedor, etc.).
- **Integraciones con APIs externas** (MercadoLibre, DUX, Tienda Nube) con rate limiters dedicados, retry handlers, refresh token automático para OAuth y logging persistente para troubleshooting.
- **Procesos masivos asíncronos** con estado consultable en tiempo real (iniciar / cancelar / estado / resultado / log streaming) para sincronización de precios y cálculo de reposición.
- **Autorización granular**: 22 permisos (11 módulos × VER/EDITAR) distribuidos en 3 roles, declarados con `@PreAuthorize` en cada controller. Tokens JWT firmados con HMAC-SHA256 y validación de usuario activo en cada request.
- **Generación de catálogos PDF** con iText (configuración global + por catálogo).
- **Virtual Threads** (Project Loom de Java 25) activados para soportar la concurrencia de procesos I/O-bound sin saturar el pool de threads.
- **Auditoría** automática de cambios sobre entidades clave con diff de campos.

---

## Funcionalidades

### Catálogo de productos
Productos con SKU, costo, IVA, atributos físicos, relaciones con marca, tipo, origen, material, clasificaciones jerárquicas (general y gastronómica) y proveedor. Márgenes minorista/mayorista por producto. Relaciones many-to-many con aptos, catálogos y clientes. Import/export Excel con Apache POI.

### Canales y pricing
Los canales (MercadoLibre, Tienda Nube, catálogo gastronómico, etc.) se definen como una composición de:
- **ConceptoCalculo** — porcentajes que afectan el cálculo (`GASTO_SOBRE_COSTO`, `COMISION_SOBRE_PVP`, `FLAG_APLICAR_IVA`, `DESCUENTO_PORCENTUAL`, `COSTO_OCULTO_PVP`, `FLAG_INCLUIR_ENVIO`…).
- **CanalConceptoCuota** — planes de cuotas con recargos.
- **CanalConceptoRegla** — reglas condicionales (AND de atributos) que deciden si un concepto aplica a un producto.

Soporta herencia con `canalBase` para derivar canales a partir de uno existente.

### Motor de cálculo de precios

**Fórmula general:**
```
PVP = (COSTO_AJUSTADO × (1 + MARGEN/100) × FACTOR_IMP) / (1 − COMISIONES_PVP/100)
```

**Métricas calculadas y persistidas** (`producto_canal_precios`, PK compuesta producto + canal + cuotas):

| Métrica | Fórmula |
|---------|---------|
| `costoProducto` | costo × (1 + financiaciónProveedor%) |
| `costosVenta` | Σ conceptos COMISION_SOBRE_PVP, DESCUENTO_PORCENTUAL, COSTO_OCULTO_PVP, FLAG_INCLUIR_ENVIO, GASTO_SIN_INFLAR_PVP + cuotas |
| `ingresoNetoVendedor` | PVP − IVA − impuestos − costosVenta |
| `ganancia` | ingresoNetoVendedor − costoProducto |
| `margenSobreIngresoNeto` | (ganancia / ingresoNetoVendedor) × 100 |
| `margenSobrePvp` | (ganancia / pvp) × 100 |
| `markupPorcentaje` | (ganancia / costoProducto) × 100 |

### Recálculo automático en cascada

`RecalculoPrecioFacade` centraliza los disparadores y acota el scope del recálculo al mínimo necesario:

| Cambio en… | Alcance del recálculo |
|------------|------------------------|
| Producto (costo, iva, clasifGastro) | Ese producto en todos sus canales |
| ProductoMargen | Ese producto en todos sus canales |
| ConceptoCalculo | Productos de los canales que usan ese concepto |
| CanalConcepto / CanalConceptoCuota | Todos los productos del canal |
| Canal (canalBase) | Todos los productos del canal |
| Proveedor (% financiación) | Productos de ese proveedor |
| ReglaDescuento | Productos del canal |
| PrecioInflado | Producto × canal afectado |
| MLA (precioEnvio) | Productos con ese MLA |
| ClasifGastro (esMaquina) | Productos de esa clasificación |

### Integraciones externas
- **MercadoLibre** — OAuth 2.0 con refresh automático de tokens, cálculo iterativo del precio de envío, publicación de precios. Rate limit: 5 req/s.
- **DUX Software** — importación de productos y stock. Rate limit: 1 req cada 7 s.
- **Tienda Nube** — publicación de precios y stock. Header `Authentication: bearer`.
- **n8n / webhooks** — endpoint sincrónico (`POST /api/automatizacion-precios/ejecutar`) para disparar la sincronización completa desde un orquestador externo.

### Automatización de precios
Proceso masivo configurable que encadena: import DUX → cálculo de envíos → publicación en ML, Nube y Gastro → aplicación de promociones. API de control completo: iniciar, cancelar, estado en vivo, log streaming y log histórico persistente.

### Órdenes de compra y reposición
- **Órdenes de compra** con encabezado + líneas y máquina de estados (BORRADOR → CONFIRMADA → RECIBIDA).
- **Reposición automática**: cálculo asíncrono de sugerencias basado en ventas diarias cacheadas, tags por producto y configuración global. Permite ajustes manuales antes de generar la orden de compra.

### Catálogo PDF
Generación de catálogos comerciales con iText, con configuración global + configuración por catálogo.

### Autenticación y autorización
- JWT firmado con HMAC-SHA256 (Nimbus JOSE+JWT), secret gestionado fuera del repo.
- 22 permisos × 3 roles (ADMIN, OPERADOR, VISOR).
- `@PreAuthorize` declarativo en cada endpoint.
- Revocación inmediata: el filtro JWT verifica `activo = true` en DB en cada request.

### Auditoría y observabilidad
- Log de cambios sobre entidades clave con diff de campos.
- Spring Boot Actuator en puerto separado `8081` (no expuesto al frontend): `health`, `metrics`, `loggers`, `mappings`.
- Healthcheck configurado para Docker (`/actuator/health/liveness`).

---

## Arquitectura

Organización por dominio (DDD-light), no por capa técnica:

```
ar.com.leo.super_master_backend
├── config/                              # Security, Permisos, MapStruct config
└── dominio/
    ├── auth/ · usuario/                 # Login, JWT, roles, permisos
    ├── auditoria/                       # Diff-based change log
    ├── producto/                        # Catálogo + márgenes + estadísticas
    │   ├── calculo/                     # Motor de pricing + RecalculoPrecioFacade
    │   └── mla/                         # Códigos de MercadoLibre
    ├── canal/                           # Canales, conceptos, cuotas, reglas
    ├── concepto_calculo/                # Conceptos (% de costo/PVP, flags)
    ├── regla_descuento/                 # Descuentos informativos
    ├── precio_inflado/                  # Ajustes por producto × canal
    ├── proveedor/ · marca/ · tipo/ ·    # Maestros
    │   origen/ · material/ · apto/
    │   catalogo/ · cliente/
    ├── clasif_gral/ · clasif_gastro/    # Clasificaciones jerárquicas
    ├── orden_compra/                    # OC con líneas y estados
    ├── reposicion/                      # Cálculo de reposición asíncrono
    ├── catalogo_pdf_config/             # Config PDF por catálogo
    ├── catalogo_pdf_global_config/      # Config PDF global
    ├── imagen/                          # Servido de imágenes
    ├── automatizacion_precios/          # Orquestador masivo (DUX/ML/Nube)
    ├── config_automatizacion/           # Config persistida de automatización
    └── common/                          # DTOs y excepciones compartidas
```

**Decisiones de diseño:**

- **Services transaccionales** en operaciones de escritura; controllers sin lógica de negocio.
- **MapStruct con `nullValuePropertyMappingStrategy=IGNORE`** — los PUT parciales no pisan campos con `null`.
- **DTOs separados** `Create` / `Update` / `Response` con Bean Validation.
- **JPA batch inserts** habilitados (`batch_size=100`, `order_inserts=true`) para las operaciones masivas.
- **Virtual Threads** (Java 25) para soportar concurrencia I/O-bound sin saturar el thread pool.
- **`ddl-auto=none`** — el esquema se controla con migraciones manuales, no se deja en manos de Hibernate.

---

## Stack

| Categoría | Tecnologías |
|-----------|-------------|
| Lenguaje / runtime | Java 25, Virtual Threads |
| Framework | Spring Boot 4.0.5, Spring Web, Spring Data JPA, Spring Security, Spring Actuator |
| Persistencia | MySQL 8.4, Hibernate (batch + ordering), HikariCP (pool 20) |
| Mapeo | MapStruct 1.6.3 |
| Auth | Nimbus JOSE+JWT 10.8 (HMAC-SHA256) |
| Reporting | Apache POI 5.5.1 (Excel), iText 9.5 (PDF) |
| Utilidades | Guava (RateLimiter), Lombok, Jackson + JsonNullable |
| Contenedores | Docker multi-stage (Temurin 25), Docker Compose |

---

## Ejecución

### Desarrollo local

```bash
# Requiere Java 25 y MySQL 8.4 con schema `supermaster`
./mvnw spring-boot:run
```

### Docker Compose (stack completo: backend + MySQL + frontend)

```bash
docker compose up -d --build
```

- API: `http://localhost:8080/api`
- Actuator: `http://localhost:8081/actuator`
- Frontend: `http://localhost:3000`

### Tests

```bash
./mvnw test
```

Los tests de integración usan `@Transactional` para rollback automático. Destacan:

- `RecalculoAutomaticoIntegrationTest` — verifica el cascade del `RecalculoPrecioFacade` para cada uno de los triggers.
- Tests de cálculo de pricing con casos borde (IVA 0, costo `null`, sin márgenes, cuotas variables).

---

## Documentación adicional

| Archivo | Contenido |
|---------|-----------|
| `frontend_api.md` | Endpoints completos y tipos TypeScript para el frontend |
| `CLAUDE.md` | Guía de dominio y convenciones de desarrollo |
| `guia_frontend_vistas.md` | Guía de las vistas del frontend que consume esta API |

---

## Autor

**Leo Virgolini** — desarrollo backend e integración con canales de venta.
