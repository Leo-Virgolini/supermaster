# Clientes → Rubros (Grupo 2) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconvertir el maestro "Cliente" (asociado a productos) en "Rubro": renombrado completo de BD + código, sin datos que migrar, y un multiselect de rubros en el modal condicionado a la clasificación gastronómica.

**Architecture:** Renombrado mecánico atómico (entidad/tablas/dominio/endpoints/frontend) más una migración SQL de estructura (sin datos) y una sola feature nueva (el selector condicional).

**Tech Stack:** Spring Boot 4 / Java 25 (ddl-auto=validate) / MySQL; Next.js / React / TypeScript.

## Global Constraints

- **NO TOCAR los clientes reales del ERP Dux.** Solo se renombra el maestro genérico `dominio/cliente/` (entidad `Cliente`, tabla `clientes`) y la relación `producto_cliente`. NO tocar `apis/dux/` ni nada con `DeudaCliente`/`ClienteDux`/`deuda` — son otro concepto.
- `ddl-auto=validate`: el schema debe quedar idéntico a las entidades renombradas. La migración SQL (Task 1) se aplica a la BD ANTES de verificar el backend.
- Tests backend: `mvn -o test` desde `supermaster-backend/` (con MySQL local arriba para los tests de integración; ver memoria "Arrancar backend local"/"Acceso BD MySQL local"). Frontend: `npx tsc --noEmit` + `npm run build`.
- Commits: `git add` SOLO rutas explícitas; NUNCA `git add -A`/`.`.
- El renombrado backend es ATÓMICO (un commit): el código no compila si se renombra a medias.
- Sin seed: la tabla `rubros` arranca vacía.

---

## Task 1: Migración SQL (estructura, sin datos)

**Files:**
- Create: `supermaster-backend/src/main/resources/db/2026-06-30-clientes-a-rubros.sql`

- [ ] **Step 1: Inspeccionar el schema real**

Conectarse a MySQL local (ver memoria "Acceso BD MySQL local") e inspeccionar las tablas y sus FKs/índices:
```sql
SHOW CREATE TABLE supermaster.clientes;
SHOW CREATE TABLE supermaster.producto_cliente;
```
Anotar los nombres reales de columnas, FKs e índices (necesarios para los `ALTER`/`RENAME` exactos).

- [ ] **Step 2: Escribir el script**

Crear `2026-06-30-clientes-a-rubros.sql` con (ajustar tipos/constraints/nombres de FK a lo que mostró el Step 1):
```sql
-- Sin datos: renombrado de estructura. Idempotencia no requerida (one-shot).
RENAME TABLE supermaster.clientes TO supermaster.rubros;
RENAME TABLE supermaster.producto_cliente TO supermaster.producto_rubro;

ALTER TABLE supermaster.rubros CHANGE COLUMN id_cliente id_rubro INT NOT NULL AUTO_INCREMENT;
ALTER TABLE supermaster.producto_rubro CHANGE COLUMN id_cliente id_rubro INT NOT NULL;

-- Recrear la FK de producto_rubro.id_rubro -> rubros.id_rubro con el nombre real:
-- ALTER TABLE supermaster.producto_rubro DROP FOREIGN KEY <fk_vieja>;
-- ALTER TABLE supermaster.producto_rubro ADD CONSTRAINT fk_producto_rubro_rubro FOREIGN KEY (id_rubro) REFERENCES supermaster.rubros (id_rubro);
```

- [ ] **Step 3: Aplicar a la BD local**

Aplicar el script a la BD de desarrollo (necesario para que el Step de verificación del backend valide). Confirmar:
```sql
SHOW TABLES LIKE 'rubros'; SHOW TABLES LIKE 'producto_rubro';
```

- [ ] **Step 4: Commit**
```bash
git add supermaster-backend/src/main/resources/db/2026-06-30-clientes-a-rubros.sql
git commit -m "feat(rubros): migracion SQL clientes->rubros (estructura, sin datos)"
```

> **PENDIENTE OPERATIVO de Leo:** aplicar este script en cada entorno (prod/staging) antes de desplegar el renombrado de código.

---

## Task 2: Backend — renombrado atómico Cliente→Rubro (maestro + relación + referencias)

**Files (renombrar / mover):**
- `dominio/cliente/` → `dominio/rubro/`: `entity/Cliente.java`→`entity/Rubro.java`, `controller/ClienteController.java`→`controller/RubroController.java`, `service/ClienteService(Impl).java`→`RubroService(Impl).java`, `repository/ClienteRepository.java`→`RubroRepository.java`, `mapper/ClienteMapper.java`→`RubroMapper.java`, `service/ClienteAuditoriaService(Impl).java`→`RubroAuditoriaService(Impl).java`, `dto/Cliente{,Create,Update,Patch}DTO.java`→`Rubro*DTO.java`.
- `dominio/producto/`: `entity/ProductoCliente.java`→`ProductoRubro.java`, `entity/ProductoClienteId.java`→`ProductoRubroId.java`, `controller/ProductoClienteController.java`→`ProductoRubroController.java`, `service/ProductoClienteService(Impl).java`→`ProductoRubroService(Impl).java`, `repository/ProductoClienteRepository.java`→`ProductoRubroRepository.java`, `mapper/ProductoClienteMapper.java`→`ProductoRubroMapper.java`, `dto/ProductoClienteDTO.java`→`ProductoRubroDTO.java`.
- Modify (referencias): `dominio/producto/mapper/ProductoMapper.java`, `service/ProductoServiceImpl.java`, `dto/ProductoDTO.java`, `dto/ProductoConPreciosDTO.java`.
- Modify (tests): renombrar las clases de test correspondientes.

**Interfaces:**
- Produces: entidad `Rubro` (`@Table(name="rubros")`, `@Column(name="id_rubro")`, `nombre`); `ProductoRubro`/`ProductoRubroId` (`@Table(name="producto_rubro")`, columnas `id_rubro`/`id_producto`); endpoints `/api/rubros` y `/api/productos/{id}/rubros/{rubroId}`.

> **Renombrado mecánico pero con cuidado quirúrgico.** Reglas:
> - Renombrar identificadores `Cliente`→`Rubro`, `cliente`→`rubro`, `clientes`→`rubros`, `clienteId`→`rubroId`, `id_cliente`→`id_rubro` SOLO dentro de los archivos del maestro genérico y la relación producto↔maestro listados arriba.
> - **NO tocar** nada en `apis/dux/`, ni `DeudaClienteDux`, ni métodos/campos relativos a clientes reales del ERP. Si un archivo (p. ej. un service) mezcla ambos conceptos, renombrar SOLO las referencias al maestro `Cliente`/`ProductoCliente`, no las de Dux. Ante duda, abrir el archivo y leer el contexto.

- [ ] **Step 1: Renombrar el maestro (entity/repo/mapper/service/controller/DTOs/auditoría)**

Mover `dominio/cliente/` → `dominio/rubro/` con todos sus archivos renombrados. En la entidad: `@Table(name = "rubros", schema = "supermaster")`, `@Column(name = "id_rubro")`, la relación `@OneToMany(mappedBy = "rubro") Set<ProductoRubro> productoRubros`. En `RubroController`: `@RequestMapping("/api/rubros")`. Actualizar package declarations a `...dominio.rubro...`.

- [ ] **Step 2: Renombrar la relación (ProductoCliente→ProductoRubro)**

Renombrar `ProductoCliente`/`ProductoClienteId` y su controller/service/repo/mapper/DTO. En `ProductoRubro`: `@Table(name="producto_rubro")`, FK a `Rubro` (`@JoinColumn(name="id_rubro")`), `@ManyToOne Rubro rubro`. En `ProductoRubroController`: `@PostMapping("/api/productos/{id}/rubros/{rubroId}")` etc. (mantener `201/404/409/204`).

- [ ] **Step 3: Actualizar referencias en producto**

En `ProductoMapper`, `ProductoServiceImpl`, `ProductoDTO`, `ProductoConPreciosDTO`: renombrar todo lo que referencie el maestro/relación (campos como `clientes`→`rubros`, getters, imports). Ajustar los tests que construyan estos DTOs (records posicionales).

- [ ] **Step 4: Compilar + suite (MySQL arriba)**

Run: `mvn -o test` → debe compilar y validar el schema (la tabla ya se renombró en Task 1). Verde. Si Hibernate se queja de un nombre de tabla/columna, revisar que la entidad coincida con el schema migrado. Distinguir fallos ambientales de MySQL apagado de fallos reales de validación.

- [ ] **Step 5: Commit (atómico)**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/rubro/ supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/ supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/
# (verificar con `git status` que NO se borró nada de apis/dux ni clientes reales)
git commit -m "refactor(rubros): renombra maestro Cliente->Rubro y relacion producto_rubro"
```

---

## Task 3: Backend — referencias en Excel y Reposición

**Files:**
- Modify: `excel/service/ExcelServiceImpl.java`, `excel/service/ExcelService.java`, `excel/controller/ExcelController.java`, `dominio/reposicion/service/ReposicionServiceImpl.java` (y cualquier otro que el compilador marque tras Task 2).

**Interfaces:**
- Consumes: `Rubro`/`ProductoRubro` (Task 2).

> Si Task 2 ya dejó el backend compilando (porque renombró TODAS las referencias), esta tarea puede haber quedado absorbida. Esta tarea existe por si Excel/Reposición usan el maestro y quedaron pendientes. Si tras Task 2 `mvn -o test` ya pasó verde, confirmar que Excel/Reposición no referencian nombres viejos (grep `Cliente`/`cliente` en esos archivos, excluyendo Dux) y, si está limpio, esta tarea es un no-op (saltearla en el ledger).

- [ ] **Step 1: Renombrar referencias del maestro en Excel/Reposición**

Abrir cada archivo y renombrar SOLO las referencias al maestro `Cliente`/`ProductoCliente` (no las columnas de export con etiqueta "Cliente" si son texto de UI — revisar si "Cliente" es un encabezado de columna Excel que el usuario ve; si es así, cambiar a "Rubro" también, ya que el concepto cambió).

- [ ] **Step 2: Compilar + suite** → `mvn -o test` verde.

- [ ] **Step 3: Commit**
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/excel/ supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/reposicion/
git commit -m "refactor(rubros): actualiza referencias de Excel y Reposicion a Rubro"
```

---

## Task 4: Frontend — renombrado (services + pantalla de gestión)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` (funciones/tipos de clientes), la carpeta de la pantalla de clientes (`src/app/clientes/` → `src/app/rubros/` con sus `page.tsx`/`columns.tsx`/`service`/`types`/`hook`), y la config de navegación/menú.

**Interfaces:**
- Produces: `searchRubros`, `getAllRubrosAPI`, `getProductoRubrosAPI`, `addProductoRubroAPI`, `removeProductoRubroAPI` (endpoints `/api/rubros` y `/api/productos/{id}/rubros/{rid}`); tipos `Rubro*`.

> Abrir primero el código para ubicar la pantalla de clientes (buscar `app/clientes` o similar) y los nombres exactos de las funciones de service. Renombrar `cliente`→`rubro`/`Cliente`→`Rubro` en los archivos de esa feature y en los usos de `productosService`. NO tocar nada relativo a Dux/deuda.

- [ ] **Step 1: Renombrar services del maestro/relación**

En `productosService.ts`: `searchClientes`→`searchRubros`, `getAllClientesAPI`→`getAllRubrosAPI`, `getProductoClientesAPI`→`getProductoRubrosAPI`, `addProductoClienteAPI`→`addProductoRubroAPI`, `removeProductoClienteAPI`→`removeProductoRubroAPI`, con los endpoints `/api/rubros` y `/api/productos/${id}/rubros/${rid}`. Renombrar tipos `Cliente*`→`Rubro*`.

- [ ] **Step 2: Renombrar la pantalla de gestión**

Mover `app/clientes/` → `app/rubros/` (page/columns/service/types/hook), renombrar identificadores y textos visibles ("Clientes"→"Rubros"). Actualizar la entrada de navegación/menú (buscar el label "Clientes" en la config de nav) a "Rubros" con su ruta nueva.

- [ ] **Step 3: tsc + build**

Run: `npx tsc --noEmit` && `npm run build` → 0 errores. (Habrá errores en `ProductoFormModal.tsx` por `clientesSel` hasta Task 5; commitear Task 4 + Task 5 juntas, o renombrar las refs del modal acá y dejar el multiselect condicional para Task 5.)

> Para mantener el build verde por commit: en esta tarea renombrar TAMBIÉN en `ProductoFormModal.tsx` los identificadores `clientesSel`→`rubrosSel`, `clientesOriginal`→`rubrosOriginal` y los usos de los services, SIN cambiar todavía la condición de visibilidad (eso es Task 5). Así el build queda verde.

- [ ] **Step 4: Commit**
```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/rubros/ supermaster-frontend/src/app/productos/ProductoFormModal.tsx <archivo-de-nav>
git commit -m "refactor(rubros): renombra clientes->rubros en frontend (services + pantalla)"
```

---

## Task 5: Frontend — multiselect de rubros condicional a clasif gastro

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: `rubrosSel`/`setRubrosSel`, `searchRubros` (Task 4), `clasifGastroId` (estado existente).

- [ ] **Step 1: Condicionar el MultiAsyncSelect de Rubros**

Ubicar el `MultiAsyncSelect` de Rubros (antes "Clientes") en el modal. Envolverlo para que se renderice solo si el producto tiene clasif gastro:
```tsx
{clasifGastroId != null && (
    <MultiAsyncSelect label="Rubros" loadOptions={(q) => searchRubros(q)} value={rubrosSel} onChange={setRubrosSel}
        placeholder="Rubros (panadería, restaurant, …)" inputClassName={inputBaseClassName}
        chipClassName="..." />
)}
```
(Conservar las clases/props del MultiAsyncSelect existente.) El diff de alta/edición de rubros (`addProductoRubroAPI`/`removeProductoRubroAPI`) se mantiene; no se auto-limpian rubros si el producto deja de ser gastro.

- [ ] **Step 2: tsc + build** → 0 errores.

- [ ] **Step 3: Smoke manual**

Producto con clasif gastro → aparece el selector de Rubros; sin clasif gastro → no aparece. Asociar/desasociar rubros persiste. La pantalla de gestión de Rubros hace CRUD.

- [ ] **Step 4: Commit**
```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(rubros): selector de rubros en el modal solo si hay clasif gastro"
```

---

## Self-Review

- **Cobertura del spec:** migración SQL (T1); renombrado backend maestro+relación+referencias (T2+T3); renombrado frontend services+pantalla (T4); multiselect condicional (T5). Sin seed (no hay task de seed). ✔
- **Placeholders:** los `ALTER`/FK exactos dependen del schema real (T1 Step 1 lo inspecciona) — es una instrucción de inspección, no un placeholder de implementación. El resto del renombrado es mecánico con archivos exactos.
- **Riesgo principal:** confundir el maestro `Cliente` con los clientes de Dux — mitigado con la advertencia en Global Constraints y en T2. Y `ddl-auto=validate`: la migración (T1) debe aplicarse antes de verificar T2.
- **Consistencia:** `Rubro`/`ProductoRubro` (T2) ↔ services `*RubroAPI` (T4) ↔ `rubrosSel` + condicional (T5).
- **Atómico:** T2 es un commit (el backend no compila a medias). T4 renombra también las refs del modal para no romper el build antes de T5.
