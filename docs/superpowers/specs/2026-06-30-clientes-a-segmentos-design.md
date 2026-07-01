# Clientes → Rubros — Diseño

**Fecha:** 2026-06-30
**Estado:** Aprobado (pendiente de review del usuario)
**Grupo:** 2 de 2 (el Grupo 1 — mejoras UI del modal — va en su propio spec)

## Contexto

El maestro `Cliente` del proyecto es genérico (`id_cliente` + `nombre`, igual que marca/tipo/origen) y se asocia a productos vía la relación many-to-many `producto_cliente`. En la práctica funciona como un **segmento/rubro** del producto, no como un cliente real. (Los clientes reales del ERP viven en el dominio Dux — `DeudaClienteDux` etc. — y NO se tocan en este spec.)

Se reconvierte ese maestro en **Rubros** (panadería, restaurant, etc.): los rubros a los que pertenece un producto. En el modal, el selector de rubros se muestra solo cuando el producto tiene **clasificación gastronómica**.

## Decisiones tomadas (brainstorming)

- **Sin datos que migrar:** la tabla `clientes` está vacía → la migración solo renombra estructura.
- **Renombrado completo:** BD + código (entidades, dominio, endpoints, frontend). Sin deuda técnica.
- **Sin seed:** la tabla `rubros` arranca vacía; los rubros se cargan por CRUD desde la pantalla de gestión.
- **Multiselect condicional:** el selector de rubros aparece en el modal solo si el producto tiene clasif gastro.

## Diseño

### A) Backend — renombrado de dominio

**Maestro** (`dominio/cliente/` → `dominio/rubro/`):
- `Cliente` → `Rubro` (tabla `clientes` → `rubros`, columna `id_cliente` → `id_rubro`, `nombre` igual, `@Size(max=45)`).
- `ClienteController` → `RubroController`, `ClienteService(Impl)` → `RubroService(Impl)`, `ClienteRepository` → `RubroRepository`, `ClienteMapper` → `RubroMapper`.
- DTOs: `ClienteDTO`/`ClienteCreateDTO`/`ClienteUpdateDTO`/`ClientePatchDTO` → `Rubro*`.
- `ClienteAuditoriaService(Impl)` → `RubroAuditoriaService(Impl)`.

**Relación** (`producto`):
- `ProductoCliente` → `ProductoRubro` (tabla `producto_cliente` → `producto_rubro`).
- `ProductoClienteId` → `ProductoRubroId` (columnas `id_cliente`/`id_producto` → `id_rubro`/`id_producto`).
- `ProductoClienteController`/`Service(Impl)`/`Repository`/`Mapper`/`ProductoClienteDTO` → `ProductoRubro*`.

**Endpoints:**
- `/api/clientes` → `/api/rubros` (CRUD del maestro).
- `/api/productos/{id}/clientes/{clienteId}` → `/api/productos/{id}/rubros/{rubroId}` (asociación; mantener `201` si existe, `404` si falta un lado, `204` en delete).

**Referencias a actualizar** (renombrar usos, sin cambiar lógica): `ProductoMapper`, `ProductoServiceImpl`, `ProductoDTO`, `ProductoConPreciosDTO`, `ExcelService(Impl)`/`ExcelController`, `ReposicionServiceImpl`, y cualquier otro consumidor del maestro/relación.

### B) Migración SQL

Script nuevo en `src/main/resources/db/` (p. ej. `2026-06-30-clientes-a-rubros.sql`). Como `ddl-auto=validate`, el schema debe quedar idéntico a las entidades renombradas. Sin datos → seguro:

```sql
-- Renombrar tablas
RENAME TABLE clientes TO rubros;
RENAME TABLE producto_cliente TO producto_rubro;
-- Renombrar columnas PK/FK (ajustar tipos/constraints exactos al schema real)
ALTER TABLE rubros CHANGE COLUMN id_cliente id_rubro INT NOT NULL AUTO_INCREMENT;
ALTER TABLE producto_rubro CHANGE COLUMN id_cliente id_rubro INT NOT NULL;
-- Renombrar FKs/índices que referencien las columnas/tablas viejas
```
*(El implementador inspecciona el schema real — nombres de FK/índices — y completa los `ALTER` exactos; verifica con `mvn -o test` que Hibernate valide el schema contra las entidades renombradas.)*

### C) Frontend — renombrado

- **Service** (`productosService.ts` y donde corresponda): `searchClientes`→`searchRubros`, `getAllClientesAPI`→`getAllRubrosAPI`, `getProductoClientesAPI`→`getProductoRubrosAPI`, `addProductoClienteAPI`→`addProductoRubroAPI`, `removeProductoClienteAPI`→`removeProductoRubroAPI`, con los endpoints nuevos. Tipos `Cliente*`→`Rubro*`.
- **Modal** (`ProductoFormModal.tsx`): `clientesSel`/`clientesOriginal`→`rubrosSel`/`rubrosOriginal`; el `MultiAsyncSelect` "Clientes" → "Rubros"; el diff de alta/edición usa los endpoints de rubros.
- **Pantalla de gestión:** la pantalla/tabla de Clientes → **Rubros** (mismo CRUD renombrado); entrada de navegación/menú actualizada.

### D) Multiselect condicional por clasif gastro (modal)

- El `MultiAsyncSelect` de **Rubros** se renderiza **solo si `clasifGastroId != null`**.
- Si el producto no tiene clasif gastro, no se muestra ni se edita.
- **No** se auto-limpian los rubros ya asociados si el producto deja de ser gastro (la relación queda en BD pero oculta; edge case menor, fuera de alcance limpiar).
- Placeholder/etiqueta orientativa: "Rubros (panadería, restaurant, …)".

### E) Sin seed

La tabla `rubros` arranca vacía; se cargan desde la pantalla de gestión.

## Manejo de errores

- Asociación producto↔rubro: `409` si ya existe, `404` si falta el producto o el rubro, `204` en delete (igual que la relación actual de clientes).
- Borrar un rubro asociado a productos: respetar el comportamiento actual del maestro cliente (cascade/orphanRemoval ya definido en la entidad — se preserva al renombrar).

## Testing

- Backend: `mvn -o test` valida que Hibernate (ddl-auto=validate) acepte el schema renombrado contra las entidades `Rubro`/`ProductoRubro`; los tests del CRUD y de la asociación (renombrados) pasan.
- Frontend (smoke manual): el selector de Rubros aparece solo con clasif gastro; se pueden asociar/desasociar rubros; la pantalla de gestión de Rubros hace CRUD; un producto sin clasif gastro no muestra el selector.

## Fuera de alcance (YAGNI)

- Migración de datos (la tabla está vacía).
- Seed de rubros (vacío + CRUD).
- Auto-limpiar rubros cuando un producto deja de ser gastro.
- Tocar los clientes reales del ERP (dominio Dux).
