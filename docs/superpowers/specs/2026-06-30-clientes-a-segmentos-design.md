# Clientes → Segmentos — Diseño

**Fecha:** 2026-06-30
**Estado:** Aprobado (pendiente de review del usuario)
**Grupo:** 2 de 2 (el Grupo 1 — mejoras UI del modal — va en su propio spec)

## Contexto

El maestro `Cliente` del proyecto es genérico (`id_cliente` + `nombre`, igual que marca/tipo/origen) y se asocia a productos vía la relación many-to-many `producto_cliente`. En la práctica funciona como un **segmento/segmento** del producto, no como un cliente real. (Los clientes reales del ERP viven en el dominio Dux — `DeudaClienteDux` etc. — y NO se tocan en este spec.)

Se reconvierte ese maestro en **Segmentos** (panadería, restaurant, etc.): los segmentos a los que pertenece un producto. En el modal, el selector de segmentos se muestra solo cuando el producto tiene **clasificación gastronómica**.

## Decisiones tomadas (brainstorming)

- **Sin datos que migrar:** la tabla `clientes` está vacía → la migración solo renombra estructura.
- **Renombrado completo:** BD + código (entidades, dominio, endpoints, frontend). Sin deuda técnica.
- **Sin seed:** la tabla `segmentos` arranca vacía; los segmentos se cargan por CRUD desde la pantalla de gestión.
- **Multiselect condicional:** el selector de segmentos aparece en el modal solo si el producto tiene clasif gastro.

## Diseño

### A) Backend — renombrado de dominio

**Maestro** (`dominio/cliente/` → `dominio/segmento/`):
- `Cliente` → `Segmento` (tabla `clientes` → `segmentos`, columna `id_cliente` → `id_segmento`, `nombre` igual, `@Size(max=45)`).
- `ClienteController` → `SegmentoController`, `ClienteService(Impl)` → `SegmentoService(Impl)`, `ClienteRepository` → `SegmentoRepository`, `ClienteMapper` → `SegmentoMapper`.
- DTOs: `ClienteDTO`/`ClienteCreateDTO`/`ClienteUpdateDTO`/`ClientePatchDTO` → `Segmento*`.
- `ClienteAuditoriaService(Impl)` → `SegmentoAuditoriaService(Impl)`.

**Relación** (`producto`):
- `ProductoCliente` → `ProductoSegmento` (tabla `producto_cliente` → `producto_segmento`).
- `ProductoClienteId` → `ProductoSegmentoId` (columnas `id_cliente`/`id_producto` → `id_segmento`/`id_producto`).
- `ProductoClienteController`/`Service(Impl)`/`Repository`/`Mapper`/`ProductoClienteDTO` → `ProductoSegmento*`.

**Endpoints:**
- `/api/clientes` → `/api/segmentos` (CRUD del maestro).
- `/api/productos/{id}/clientes/{clienteId}` → `/api/productos/{id}/segmentos/{segmentoId}` (asociación; mantener `201` si existe, `404` si falta un lado, `204` en delete).

**Referencias a actualizar** (renombrar usos, sin cambiar lógica): `ProductoMapper`, `ProductoServiceImpl`, `ProductoDTO`, `ProductoConPreciosDTO`, `ExcelService(Impl)`/`ExcelController`, `ReposicionServiceImpl`, y cualquier otro consumidor del maestro/relación.

### B) Migración SQL

Script nuevo en `src/main/resources/db/` (p. ej. `2026-06-30-clientes-a-segmentos.sql`). Como `ddl-auto=validate`, el schema debe quedar idéntico a las entidades renombradas. Sin datos → seguro:

```sql
-- Renombrar tablas
RENAME TABLE clientes TO segmentos;
RENAME TABLE producto_cliente TO producto_segmento;
-- Renombrar columnas PK/FK (ajustar tipos/constraints exactos al schema real)
ALTER TABLE segmentos CHANGE COLUMN id_cliente id_segmento INT NOT NULL AUTO_INCREMENT;
ALTER TABLE producto_segmento CHANGE COLUMN id_cliente id_segmento INT NOT NULL;
-- Renombrar FKs/índices que referencien las columnas/tablas viejas
```
*(El implementador inspecciona el schema real — nombres de FK/índices — y completa los `ALTER` exactos; verifica con `mvn -o test` que Hibernate valide el schema contra las entidades renombradas.)*

### C) Frontend — renombrado

- **Service** (`productosService.ts` y donde corresponda): `searchClientes`→`searchSegmentos`, `getAllClientesAPI`→`getAllSegmentosAPI`, `getProductoClientesAPI`→`getProductoSegmentosAPI`, `addProductoClienteAPI`→`addProductoSegmentoAPI`, `removeProductoClienteAPI`→`removeProductoSegmentoAPI`, con los endpoints nuevos. Tipos `Cliente*`→`Segmento*`.
- **Modal** (`ProductoFormModal.tsx`): `clientesSel`/`clientesOriginal`→`segmentosSel`/`segmentosOriginal`; el `MultiAsyncSelect` "Clientes" → "Segmentos"; el diff de alta/edición usa los endpoints de segmentos.
- **Pantalla de gestión:** la pantalla/tabla de Clientes → **Segmentos** (mismo CRUD renombrado); entrada de navegación/menú actualizada.

### D) Multiselect condicional por clasif gastro (modal)

- El `MultiAsyncSelect` de **Segmentos** se renderiza **solo si `clasifGastroId != null`**.
- Si el producto no tiene clasif gastro, no se muestra ni se edita.
- **No** se auto-limpian los segmentos ya asociados si el producto deja de ser gastro (la relación queda en BD pero oculta; edge case menor, fuera de alcance limpiar).
- Placeholder/etiqueta orientativa: "Segmentos (panadería, restaurant, …)".

### E) Sin seed

La tabla `segmentos` arranca vacía; se cargan desde la pantalla de gestión.

## Manejo de errores

- Asociación producto↔segmento: `409` si ya existe, `404` si falta el producto o el segmento, `204` en delete (igual que la relación actual de clientes).
- Borrar un segmento asociado a productos: respetar el comportamiento actual del maestro cliente (cascade/orphanRemoval ya definido en la entidad — se preserva al renombrar).

## Testing

- Backend: `mvn -o test` valida que Hibernate (ddl-auto=validate) acepte el schema renombrado contra las entidades `Segmento`/`ProductoSegmento`; los tests del CRUD y de la asociación (renombrados) pasan.
- Frontend (smoke manual): el selector de Segmentos aparece solo con clasif gastro; se pueden asociar/desasociar segmentos; la pantalla de gestión de Segmentos hace CRUD; un producto sin clasif gastro no muestra el selector.

## Fuera de alcance (YAGNI)

- Migración de datos (la tabla está vacía).
- Seed de segmentos (vacío + CRUD).
- Auto-limpiar segmentos cuando un producto deja de ser gastro.
- Tocar los clientes reales del ERP (dominio Dux).
