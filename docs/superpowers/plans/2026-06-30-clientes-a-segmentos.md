# Clientes → Segmentos (Grupo 2) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconvertir el maestro "Cliente" (asociado a productos) en "Segmento": renombrado completo de BD + código, sin datos que migrar, y un multiselect de segmentos en el modal condicionado a la clasificación gastronómica.

**Architecture:** Renombrado mecánico atómico (entidad/tablas/dominio/endpoints/frontend) más una migración SQL de estructura (sin datos) y una sola feature nueva (el selector condicional).

**Tech Stack:** Spring Boot 4 / Java 25 (ddl-auto=validate) / MySQL; Next.js / React / TypeScript.

## Global Constraints

- **NO TOCAR los clientes reales del ERP Dux.** Solo se renombra el maestro genérico `dominio/cliente/` (entidad `Cliente`, tabla `clientes`) y la relación `producto_cliente`. NO tocar `apis/dux/` ni nada con `DeudaCliente`/`ClienteDux`/`deuda` — son otro concepto.
- `ddl-auto=validate`: el schema debe quedar idéntico a las entidades renombradas. La migración SQL (Task 1) se aplica a la BD ANTES de verificar el backend.
- Tests backend: `mvn -o test` desde `supermaster-backend/` (con MySQL local arriba para los tests de integración; ver memoria "Arrancar backend local"/"Acceso BD MySQL local"). Frontend: `npx tsc --noEmit` + `npm run build`.
- Commits: `git add` SOLO rutas explícitas; NUNCA `git add -A`/`.`.
- El renombrado backend es ATÓMICO (un commit): el código no compila si se renombra a medias.
- Sin seed: la tabla `segmentos` arranca vacía.

---

## Task 1: Migración SQL (estructura, sin datos)

**Files:**
- Create: `supermaster-backend/src/main/resources/db/2026-06-30-clientes-a-segmentos.sql`

- [ ] **Step 1: Inspeccionar el schema real**

Conectarse a MySQL local (ver memoria "Acceso BD MySQL local") e inspeccionar las tablas y sus FKs/índices:
```sql
SHOW CREATE TABLE supermaster.clientes;
SHOW CREATE TABLE supermaster.producto_cliente;
```
Anotar los nombres reales de columnas, FKs e índices (necesarios para los `ALTER`/`RENAME` exactos).

- [ ] **Step 2: Escribir el script**

Crear `2026-06-30-clientes-a-segmentos.sql` con (ajustar tipos/constraints/nombres de FK a lo que mostró el Step 1):
```sql
-- Sin datos: renombrado de estructura. Idempotencia no requerida (one-shot).
RENAME TABLE supermaster.clientes TO supermaster.segmentos;
RENAME TABLE supermaster.producto_cliente TO supermaster.producto_segmento;

ALTER TABLE supermaster.segmentos CHANGE COLUMN id_cliente id_segmento INT NOT NULL AUTO_INCREMENT;
ALTER TABLE supermaster.producto_segmento CHANGE COLUMN id_cliente id_segmento INT NOT NULL;

-- Recrear la FK de producto_segmento.id_segmento -> segmentos.id_segmento con el nombre real:
-- ALTER TABLE supermaster.producto_segmento DROP FOREIGN KEY <fk_vieja>;
-- ALTER TABLE supermaster.producto_segmento ADD CONSTRAINT fk_producto_segmento_segmento FOREIGN KEY (id_segmento) REFERENCES supermaster.segmentos (id_segmento);
```

- [ ] **Step 3: Aplicar a la BD local**

Aplicar el script a la BD de desarrollo (necesario para que el Step de verificación del backend valide). Confirmar:
```sql
SHOW TABLES LIKE 'segmentos'; SHOW TABLES LIKE 'producto_segmento';
```

- [ ] **Step 4: Commit**
```bash
git add supermaster-backend/src/main/resources/db/2026-06-30-clientes-a-segmentos.sql
git commit -m "feat(segmentos): migracion SQL clientes->segmentos (estructura, sin datos)"
```

> **PENDIENTE OPERATIVO de Leo:** aplicar este script en cada entorno (prod/staging) antes de desplegar el renombrado de código.

---

## Task 2: Backend — renombrado atómico Cliente→Segmento (maestro + relación + referencias)

**Files (renombrar / mover):**
- `dominio/cliente/` → `dominio/segmento/`: `entity/Cliente.java`→`entity/Segmento.java`, `controller/ClienteController.java`→`controller/SegmentoController.java`, `service/ClienteService(Impl).java`→`SegmentoService(Impl).java`, `repository/ClienteRepository.java`→`SegmentoRepository.java`, `mapper/ClienteMapper.java`→`SegmentoMapper.java`, `service/ClienteAuditoriaService(Impl).java`→`SegmentoAuditoriaService(Impl).java`, `dto/Cliente{,Create,Update,Patch}DTO.java`→`Segmento*DTO.java`.
- `dominio/producto/`: `entity/ProductoCliente.java`→`ProductoSegmento.java`, `entity/ProductoClienteId.java`→`ProductoSegmentoId.java`, `controller/ProductoClienteController.java`→`ProductoSegmentoController.java`, `service/ProductoClienteService(Impl).java`→`ProductoSegmentoService(Impl).java`, `repository/ProductoClienteRepository.java`→`ProductoSegmentoRepository.java`, `mapper/ProductoClienteMapper.java`→`ProductoSegmentoMapper.java`, `dto/ProductoClienteDTO.java`→`ProductoSegmentoDTO.java`.
- Modify (referencias): `dominio/producto/mapper/ProductoMapper.java`, `service/ProductoServiceImpl.java`, `dto/ProductoDTO.java`, `dto/ProductoConPreciosDTO.java`.
- Modify (tests): renombrar las clases de test correspondientes.

**Interfaces:**
- Produces: entidad `Segmento` (`@Table(name="segmentos")`, `@Column(name="id_segmento")`, `nombre`); `ProductoSegmento`/`ProductoSegmentoId` (`@Table(name="producto_segmento")`, columnas `id_segmento`/`id_producto`); endpoints `/api/segmentos` y `/api/productos/{id}/segmentos/{segmentoId}`.

> **Renombrado mecánico pero con cuidado quirúrgico.** Reglas:
> - Renombrar identificadores `Cliente`→`Segmento`, `cliente`→`segmento`, `clientes`→`segmentos`, `clienteId`→`segmentoId`, `id_cliente`→`id_segmento` SOLO dentro de los archivos del maestro genérico y la relación producto↔maestro listados arriba.
> - **NO tocar** nada en `apis/dux/`, ni `DeudaClienteDux`, ni métodos/campos relativos a clientes reales del ERP. Si un archivo (p. ej. un service) mezcla ambos conceptos, renombrar SOLO las referencias al maestro `Cliente`/`ProductoCliente`, no las de Dux. Ante duda, abrir el archivo y leer el contexto.

- [ ] **Step 1: Renombrar el maestro (entity/repo/mapper/service/controller/DTOs/auditoría)**

Mover `dominio/cliente/` → `dominio/segmento/` con todos sus archivos renombrados. En la entidad: `@Table(name = "segmentos", schema = "supermaster")`, `@Column(name = "id_segmento")`, la relación `@OneToMany(mappedBy = "segmento") Set<ProductoSegmento> productoSegmentos`. En `SegmentoController`: `@RequestMapping("/api/segmentos")`. Actualizar package declarations a `...dominio.segmento...`.

- [ ] **Step 2: Renombrar la relación (ProductoCliente→ProductoSegmento)**

Renombrar `ProductoCliente`/`ProductoClienteId` y su controller/service/repo/mapper/DTO. En `ProductoSegmento`: `@Table(name="producto_segmento")`, FK a `Segmento` (`@JoinColumn(name="id_segmento")`), `@ManyToOne Segmento segmento`. En `ProductoSegmentoController`: `@PostMapping("/api/productos/{id}/segmentos/{segmentoId}")` etc. (mantener `201/404/409/204`).

- [ ] **Step 3: Actualizar referencias en producto**

En `ProductoMapper`, `ProductoServiceImpl`, `ProductoDTO`, `ProductoConPreciosDTO`: renombrar todo lo que referencie el maestro/relación (campos como `clientes`→`segmentos`, getters, imports). Ajustar los tests que construyan estos DTOs (records posicionales).

- [ ] **Step 4: Compilar + suite (MySQL arriba)**

Run: `mvn -o test` → debe compilar y validar el schema (la tabla ya se renombró en Task 1). Verde. Si Hibernate se queja de un nombre de tabla/columna, revisar que la entidad coincida con el schema migrado. Distinguir fallos ambientales de MySQL apagado de fallos reales de validación.

- [ ] **Step 5: Commit (atómico)**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/segmento/ supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/ supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/
# (verificar con `git status` que NO se borró nada de apis/dux ni clientes reales)
git commit -m "refactor(segmentos): renombra maestro Cliente->Segmento y relacion producto_segmento"
```

---

## Task 3: Backend — referencias en Excel y Reposición

**Files:**
- Modify: `excel/service/ExcelServiceImpl.java`, `excel/service/ExcelService.java`, `excel/controller/ExcelController.java`, `dominio/reposicion/service/ReposicionServiceImpl.java` (y cualquier otro que el compilador marque tras Task 2).

**Interfaces:**
- Consumes: `Segmento`/`ProductoSegmento` (Task 2).

> Si Task 2 ya dejó el backend compilando (porque renombró TODAS las referencias), esta tarea puede haber quedado absorbida. Esta tarea existe por si Excel/Reposición usan el maestro y quedaron pendientes. Si tras Task 2 `mvn -o test` ya pasó verde, confirmar que Excel/Reposición no referencian nombres viejos (grep `Cliente`/`cliente` en esos archivos, excluyendo Dux) y, si está limpio, esta tarea es un no-op (saltearla en el ledger).

- [ ] **Step 1: Renombrar referencias del maestro en Excel/Reposición**

Abrir cada archivo y renombrar SOLO las referencias al maestro `Cliente`/`ProductoCliente` (no las columnas de export con etiqueta "Cliente" si son texto de UI — revisar si "Cliente" es un encabezado de columna Excel que el usuario ve; si es así, cambiar a "Segmento" también, ya que el concepto cambió).

- [ ] **Step 2: Compilar + suite** → `mvn -o test` verde.

- [ ] **Step 3: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/excel/ supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/reposicion/
git commit -m "refactor(segmentos): actualiza referencias de Excel y Reposicion a Segmento"
```

---

## Task 4: Frontend — renombrado (services + pantalla de gestión)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` (funciones/tipos de clientes), la carpeta de la pantalla de clientes (`src/app/clientes/` → `src/app/segmentos/` con sus `page.tsx`/`columns.tsx`/`service`/`types`/`hook`), y la config de navegación/menú.

**Interfaces:**
- Produces: `searchSegmentos`, `getAllSegmentosAPI`, `getProductoSegmentosAPI`, `addProductoSegmentoAPI`, `removeProductoSegmentoAPI` (endpoints `/api/segmentos` y `/api/productos/{id}/segmentos/{rid}`); tipos `Segmento*`.

> Abrir primero el código para ubicar la pantalla de clientes (buscar `app/clientes` o similar) y los nombres exactos de las funciones de service. Renombrar `cliente`→`segmento`/`Cliente`→`Segmento` en los archivos de esa feature y en los usos de `productosService`. NO tocar nada relativo a Dux/deuda.

- [ ] **Step 1: Renombrar services del maestro/relación**

En `productosService.ts`: `searchClientes`→`searchSegmentos`, `getAllClientesAPI`→`getAllSegmentosAPI`, `getProductoClientesAPI`→`getProductoSegmentosAPI`, `addProductoClienteAPI`→`addProductoSegmentoAPI`, `removeProductoClienteAPI`→`removeProductoSegmentoAPI`, con los endpoints `/api/segmentos` y `/api/productos/${id}/segmentos/${rid}`. Renombrar tipos `Cliente*`→`Segmento*`.

- [ ] **Step 2: Renombrar la pantalla de gestión**

Mover `app/clientes/` → `app/segmentos/` (page/columns/service/types/hook), renombrar identificadores y textos visibles ("Clientes"→"Segmentos"). Actualizar la entrada de navegación/menú (buscar el label "Clientes" en la config de nav) a "Segmentos" con su ruta nueva.

- [ ] **Step 3: tsc + build**

Run: `npx tsc --noEmit` && `npm run build` → 0 errores. (Habrá errores en `ProductoFormModal.tsx` por `clientesSel` hasta Task 5; commitear Task 4 + Task 5 juntas, o renombrar las refs del modal acá y dejar el multiselect condicional para Task 5.)

> Para mantener el build verde por commit: en esta tarea renombrar TAMBIÉN en `ProductoFormModal.tsx` los identificadores `clientesSel`→`segmentosSel`, `clientesOriginal`→`segmentosOriginal` y los usos de los services, SIN cambiar todavía la condición de visibilidad (eso es Task 5). Así el build queda verde.

- [ ] **Step 4: Commit**
```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/segmentos/ supermaster-frontend/src/app/productos/ProductoFormModal.tsx <archivo-de-nav>
git commit -m "refactor(segmentos): renombra clientes->segmentos en frontend (services + pantalla)"
```

---

## Task 5: Frontend — multiselect de segmentos condicional a clasif gastro

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `segmentosSel`/`setSegmentosSel`, `searchSegmentos` (Task 4), `clasifGastroId` (estado existente).

- [ ] **Step 1: Condicionar el MultiAsyncSelect de Segmentos**

Ubicar el `MultiAsyncSelect` de Segmentos (antes "Clientes") en el modal. Envolverlo para que se renderice solo si el producto tiene clasif gastro:
```tsx
{clasifGastroId != null && (
    <MultiAsyncSelect label="Segmentos" loadOptions={(q) => searchSegmentos(q)} value={segmentosSel} onChange={setSegmentosSel}
        placeholder="Segmentos (panadería, restaurant, …)" inputClassName={inputBaseClassName}
        chipClassName="..." />
)}
```
(Conservar las clases/props del MultiAsyncSelect existente.) El diff de alta/edición de segmentos (`addProductoSegmentoAPI`/`removeProductoSegmentoAPI`) se mantiene; no se auto-limpian segmentos si el producto deja de ser gastro.

- [ ] **Step 2: tsc + build** → 0 errores.

- [ ] **Step 3: Smoke manual**

Producto con clasif gastro → aparece el selector de Segmentos; sin clasif gastro → no aparece. Asociar/desasociar segmentos persiste. La pantalla de gestión de Segmentos hace CRUD.

- [ ] **Step 4: Commit**
```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(segmentos): selector de segmentos en el modal solo si hay clasif gastro"
```

---

## Self-Review

- **Cobertura del spec:** migración SQL (T1); renombrado backend maestro+relación+referencias (T2+T3); renombrado frontend services+pantalla (T4); multiselect condicional (T5). Sin seed (no hay task de seed). ✔
- **Placeholders:** los `ALTER`/FK exactos dependen del schema real (T1 Step 1 lo inspecciona) — es una instrucción de inspección, no un placeholder de implementación. El resto del renombrado es mecánico con archivos exactos.
- **Riesgo principal:** confundir el maestro `Cliente` con los clientes de Dux — mitigado con la advertencia en Global Constraints y en T2. Y `ddl-auto=validate`: la migración (T1) debe aplicarse antes de verificar T2.
- **Consistencia:** `Segmento`/`ProductoSegmento` (T2) ↔ services `*SegmentoAPI` (T4) ↔ `segmentosSel` + condicional (T5).
- **Atómico:** T2 es un commit (el backend no compila a medias). T4 renombra también las refs del modal para no romper el build antes de T5.
