# Edición de producto y clasificación flexible — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exigir al menos una de las dos clasificaciones (general o gastro) en vez de la general siempre, permitir desasignarlas (incl. inline), y agregar un panel de edición de producto que reutiliza el form del alta.

**Architecture:** El backend es la fuente de verdad de la validación "al menos una clasificación" (se valida sobre la entidad resultante en `crear`/`actualizar`/`patch`). El frontend agrega la validación visual en el form y un modo "edición" al modal de alta existente en `page.tsx`, ramificando crear vs editar por un estado `editandoProductoId`.

**Tech Stack:** Spring Boot 4 / Java 25 / MapStruct (backend); Next.js + React + TypeScript + Tailwind (frontend). Verificación: `mvnw compile`/tests backend, `tsc --noEmit` + manual frontend.

**Convención de trabajo:** Leo trabaja directo en `main`. Verificación frontend = type-check + prueba manual (no hay tests de componentes React). Backend = compilación + tests donde aplique.

---

## Fase A — Backend: validación "al menos una clasificación"

### Task A1: Quitar `@NotNull` de `clasifGralId` en el CreateDTO

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ProductoCreateDTO.java` (líneas ~36-38)

- [ ] **Step 1: Quitar la anotación `@NotNull` de `clasifGralId`**

Dejar solo `@Positive`. Antes:

```java
@NotNull(message = "El ID de clasificación general es obligatorio")
@Positive(message = "El ID de clasificación general debe ser positivo")
Integer clasifGralId,
```

Después:

```java
@Positive(message = "El ID de clasificación general debe ser positivo")
Integer clasifGralId,
```

Dejar `tipoId` con su `@NotNull` intacto.

- [ ] **Step 2: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/dto/ProductoCreateDTO.java
git commit -m "backend: clasifGral deja de ser obligatorio en el alta (validacion movida a regla cruzada)"
```

---

### Task A2: `toEntity` arma `clasifGral` con check de null

**Files:**
- Modify: `supermaster-backend/.../producto/mapper/ProductoMapper.java` (el `@Mapping` de `clasifGral` en `toEntity`)

- [ ] **Step 1: Cambiar el mapping de `clasifGral` para tolerar null**

Antes:

```java
@Mapping(target = "clasifGral", expression = "java(new ClasifGral(dto.clasifGralId()))")
```

Después (igual patrón que `clasifGastro`):

```java
@Mapping(target = "clasifGral", expression = "java(dto.clasifGralId() != null ? new ClasifGral(dto.clasifGralId()) : null)")
```

Dejar `tipo` como está (`new Tipo(dto.tipoId())`).

- [ ] **Step 2: Compilar (MapStruct regenera el impl)**

Run: `cd supermaster-backend && ./mvnw.cmd -q -DskipTests compile` (PowerShell: `.\mvnw.cmd -q -DskipTests compile`)
Expected: BUILD SUCCESS (exit 0)

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/mapper/ProductoMapper.java
git commit -m "backend: toEntity tolera clasifGral null"
```

---

### Task A3: Validación cruzada "al menos una clasificación" en crear/actualizar/patch

**Files:**
- Modify: `supermaster-backend/.../producto/service/ProductoServiceImpl.java`
  - `crear` (~92-103), `actualizar` (~110-127), `aplicarPatch` (~1114, bloque de `clasifGral` ~1153-1155)
- Import: `BadRequestException` (`...dominio.common.exception.BadRequestException`)

- [ ] **Step 1: Agregar el helper de validación**

Agregar este método privado en `ProductoServiceImpl` (cerca de los otros helpers de validación):

```java
/**
 * Regla de negocio: un producto debe tener al menos una clasificación
 * (general o gastronómica). Se valida sobre la entidad ya armada.
 */
private void validarAlMenosUnaClasificacion(Producto entity) {
    if (entity.getClasifGral() == null && entity.getClasifGastro() == null) {
        throw new BadRequestException(
            "El producto debe tener al menos una clasificación: general o gastronómica.");
    }
}
```

Agregar el import si falta:

```java
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
```

- [ ] **Step 2: Invocar en `crear` antes de persistir**

En `crear`, después de `Producto entity = productoMapper.toEntity(dto);` y antes de `productoRepository.save(entity);`:

```java
Producto entity = productoMapper.toEntity(dto);
validarAlMenosUnaClasificacion(entity);
productoRepository.save(entity);
```

- [ ] **Step 3: Invocar en `actualizar` antes de persistir**

En `actualizar`, después de `productoMapper.updateEntityFromDTO(dto, entity);` y antes de `productoRepository.save(entity);`:

```java
productoMapper.updateEntityFromDTO(dto, entity);
validarAlMenosUnaClasificacion(entity);
productoRepository.save(entity);
```

- [ ] **Step 4: En `aplicarPatch`, permitir null en `clasifGral`**

Reemplazar el bloque de `clasifGral` (que usa `leerIdRequerido`) por el patrón opcional (igual que `clasifGastro`):

Antes:

```java
if (presente(patchDto.getClasifGralId())) {
    entity.setClasifGral(new ClasifGral(leerIdRequerido(patchDto.getClasifGralId(), "clasifGralId")));
}
```

Después:

```java
if (presente(patchDto.getClasifGralId())) {
    Integer clasifGralId = leerIdOpcional(patchDto.getClasifGralId(), "clasifGralId");
    entity.setClasifGral(clasifGralId != null ? new ClasifGral(clasifGralId) : null);
}
```

Dejar `tipo` con `leerIdRequerido` (sigue obligatorio).

- [ ] **Step 5: Validar en el flujo de patch antes de persistir**

Localizar dónde `patch` guarda la entidad tras `aplicarPatch(entity, patchDto);` (método `patch`, ~171). Insertar la validación justo antes del `save`:

```java
aplicarPatch(entity, patchDto);
validarAlMenosUnaClasificacion(entity);
// ... save existente
```

(Si `aplicarPatch` es llamado y el save ocurre dentro del mismo método `patch`, poner la llamada entre ambos. Verificar la ubicación exacta del `save` en `patch` al implementar.)

- [ ] **Step 6: Compilar**

Run: `cd supermaster-backend && .\mvnw.cmd -q -DskipTests compile`
Expected: BUILD SUCCESS (exit 0)

- [ ] **Step 7: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/service/ProductoServiceImpl.java
git commit -m "backend: exigir al menos una clasificacion (gral o gastro) en crear/actualizar/patch"
```

---

### Task A4: Test de la validación cruzada

**Files:**
- Create/Modify: un test de `ProductoServiceImpl` en `supermaster-backend/src/test/...` (seguir el patrón de tests existentes del dominio producto; ubicar uno y replicar el estilo de mocks/`@SpringBootTest` o test unitario con Mockito según lo que use el proyecto).

- [ ] **Step 1: Escribir el test que falla**

Cubrir: crear/patch con ambas clasificaciones en null → `BadRequestException`; con solo gastro → OK; con solo gral → OK. Ejemplo (adaptar a la infraestructura de test existente):

```java
@Test
void crear_sinNingunaClasificacion_lanzaBadRequest() {
    ProductoCreateDTO dto = /* dto válido con clasifGralId=null y clasifGastroId=null, tipoId set */;
    assertThrows(BadRequestException.class, () -> productoService.crear(dto));
}

@Test
void crear_soloGastro_ok() {
    ProductoCreateDTO dto = /* clasifGralId=null, clasifGastroId=5, tipoId set */;
    assertDoesNotThrow(() -> productoService.crear(dto));
}
```

- [ ] **Step 2: Correr el test y verlo fallar/pasar**

Run: `cd supermaster-backend && .\mvnw.cmd -q -Dtest=ProductoServiceImplTest test`
Expected: el caso "sin ninguna" pasa a verde con la validación de A3.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/test/...
git commit -m "test: validacion al menos una clasificacion"
```

---

## Fase B — Frontend: validación y null inline

### Task B1: `validateForm` exige "al menos una clasificación"

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx` (`validateForm`, ~507)

- [ ] **Step 1: Reemplazar la regla de `clasifGralId`**

Antes:

```ts
if (!clasifGralId) errors.clasifGralId = "La clasificación general es obligatoria";
```

Después:

```ts
if (!clasifGralId && !clasifGastroId) {
    errors.clasificacion = "Seleccioná al menos una clasificación (general o gastronómica)";
}
```

Dejar la regla de `tipoId` intacta.

- [ ] **Step 2: Type-check**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "front: validar al menos una clasificacion en el alta/edicion"
```

---

### Task B2: Marcado visual "al menos una" en el form

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx` (JSX de Clasif. Gral y Clasif. Gastro, ~1109-1136)

- [ ] **Step 1: Reemplazar el `*` de Clasif. Gral por badge "al menos una" en ambos campos**

Para Clasif. Gral, cambiar el label de:

```tsx
label={<>Clasif. Gral <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></>}
```

a:

```tsx
label={<>Clasif. Gral <span className="ml-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">al menos una</span></>}
```

Para Clasif. Gastro, cambiar el label de `label="Clasif. Gastro"` a:

```tsx
label={<>Clasif. Gastro <span className="ml-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">al menos una</span></>}
```

- [ ] **Step 2: Marcar ambos inputs en rojo y mostrar el mensaje cuando falta la clasificación**

En el `inputClassName` de ambos AsyncSelect, agregar el error de clasificación. Para Clasif. Gral:

```tsx
inputClassName={`${inputBaseClassName} ${formErrors.clasificacion ? inputErrorClassName : ""}`}
```

Para Clasif. Gastro igual:

```tsx
inputClassName={`${inputBaseClassName} ${formErrors.clasificacion ? inputErrorClassName : ""}`}
```

Debajo del par de campos (o del contenedor de la fila de clasificaciones) agregar el mensaje único:

```tsx
{formErrors.clasificacion && <p className="mt-1 text-xs text-red-500">{formErrors.clasificacion}</p>}
```

Quitar el `{formErrors.clasifGralId && ...}` previo si existía.

- [ ] **Step 3: Type-check**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "front: marcado visual 'al menos una' en clasificaciones"
```

---

### Task B3: Clasif. Gral desasignable en la tabla inline

**Files:**
- Modify: `supermaster-frontend/src/app/productos/columns.tsx` (celda "rubro", ~441-453)

- [ ] **Step 1: Agregar `nullable` a la celda de rubro (clasifGral)**

En el `<EditableRelationCell>` de la columna `id: "rubro"`, agregar la prop `nullable` (igual que ya la tiene `subrubro`):

```tsx
<EditableRelationCell
    fullName={row.original.clasifGralNombreCompleto}
    initialId={row.original.clasifGralId}
    loadOptions={searchClasifGral}
    placeholder="Rubro..."
    endpoint="clasif-gral"
    nullable
    displayClassName={FONT.relation}
    disabled={!canEdit}
    onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "clasifGralId", newId)}
/>
```

- [ ] **Step 2: Type-check**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0

- [ ] **Step 3: Verificación manual**

Con backend corriendo: en la tabla, desasignar Clasif. Gral de un producto que tiene gastro → OK. Desasignar ambas → toast de error del backend y la celda revierte.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/columns.tsx
git commit -m "front: permitir desasignar Clasif. Gral en la tabla inline"
```

---

## Fase C — Frontend: panel de edición de producto

### Task C1: APIs para quitar relaciones N-a-N

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` (~273-282)

- [ ] **Step 1: Agregar el helper DELETE y las tres funciones `remove*`**

Después de las funciones `addProducto*API`, agregar:

```ts
// Quita una relación N-a-N del producto. Ignora 404 (ya no existe) para idempotencia.
const desasociarRelacion = async (productoId: number, tipo: "catalogos" | "aptos" | "clientes", relId: number) => {
	const res = await fetchAPI(`${API_URL}/${productoId}/${tipo}/${relId}`, { method: "DELETE", allowedStatuses: [404] });
	if (!res.ok && res.status !== 404) throw new Error(`Error al quitar ${tipo}`);
};

export const removeProductoCatalogoAPI = (productoId: number, catalogoId: number) => desasociarRelacion(productoId, "catalogos", catalogoId);
export const removeProductoAptoAPI = (productoId: number, aptoId: number) => desasociarRelacion(productoId, "aptos", aptoId);
export const removeProductoClienteAPI = (productoId: number, clienteId: number) => desasociarRelacion(productoId, "clientes", clienteId);
```

- [ ] **Step 2: Verificar que el backend expone DELETE de estas relaciones**

Confirmar en `ProductoController.java` que existen `@DeleteMapping("/{id}/catalogos/{catalogoId}")` (y aptos/clientes). Si no existen, agregarlos siguiendo el patrón de los `@PostMapping` correspondientes y el service (devolver 204). Documentar en este step lo encontrado.

- [ ] **Step 3: Type-check**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts
git commit -m "front: APIs para quitar relaciones N-a-N de producto"
```

---

### Task C2: Resolver IDs de catálogos/aptos/clientes para precargar el form

**Files:**
- Read: `supermaster-frontend/src/app/productos/ProductoDetalleModal.tsx` (cómo carga aptos/catálogos/clientes con sus IDs)
- Modify (según hallazgo): backend `ProductoDTO`/mapper o un endpoint, y/o `productosService.ts`

- [ ] **Step 1: Investigar cómo `ProductoDetalleModal` obtiene los IDs de las N-a-N**

El `ProductoDTO` de la tabla trae solo nombres (`aptos`, `catalogos`, `clientes: string[]`). Revisar `ProductoDetalleModal` para ver si hace un fetch por producto que devuelve `{id, nombre}` por relación, o si hay un endpoint reusable.

- [ ] **Step 2: Elegir y aplicar la vía de carga (decisión diferida del spec)**

Opción preferida si existe: reutilizar el fetch/endpoint que ya usa `ProductoDetalleModal`. Si no expone IDs de forma directa, agregar al backend la lista de ids por relación en el `ProductoDTO` (p. ej. `List<Integer> aptoIds`, `catalogoIds`, `clienteIds`) poblada en `ProductoMapper.toDTO`, y exponerla en el type `ProductoDTO` del frontend. Implementar la opción elegida.

- [ ] **Step 3: Exponer una función util `cargarRelacionesProducto(productoId): Promise<{catalogos: MultiOption[]; aptos: MultiOption[]; clientes: MultiOption[]}>`**

En `productosService.ts`, encapsular la obtención de las N-a-N como `MultiOption[]` (`{id, label}`) para que el form la consuma directamente.

- [ ] **Step 4: Verificar (type-check + compile backend si se tocó)**

Run frontend: `npx tsc --noEmit -p tsconfig.json` → exit 0
Run backend (si aplica): `.\mvnw.cmd -q -DskipTests compile` → exit 0

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "front/back: carga de relaciones N-a-N (con ids) para precargar edicion"
```

---

### Task C3: Estado de modo edición y derivados del modal

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

- [ ] **Step 1: Agregar el estado de modo**

Junto a los estados del form:

```ts
// null = modo crear; con id = modo editar (mismo modal/form).
const [editandoProductoId, setEditandoProductoId] = useState<number | null>(null);
```

- [ ] **Step 2: Limpiar el modo al cerrar/cancelar**

En el `onClose` del `<Modal>` de "Nuevo Producto" y en el botón Cancelar, agregar `setEditandoProductoId(null);` junto al `resetForm()` y `setIsModalOpen(false)` existentes.

- [ ] **Step 3: Hacer el título, el botón y el SKU dependientes del modo**

En el `<Modal>`:

```tsx
title={editandoProductoId ? "Editar Producto" : "Nuevo Producto"}
```

Botón de submit del footer:

```tsx
<Button variant="dark" onClick={editandoProductoId ? handleGuardarEdicion : handleCreate} disabled={isSaving}>
    <CheckIcon className="w-4 h-4" /> {isSaving ? "Guardando..." : (editandoProductoId ? "Guardar Cambios" : "Crear Producto")}
</Button>
```

Input de SKU (deshabilitar en edición):

```tsx
<input type="text" disabled={!!editandoProductoId} className={`${inputBaseClassName} ${editandoProductoId ? "opacity-60 cursor-not-allowed" : ""} ${formErrors.sku ? inputErrorClassName : (skuYaExiste ? "border-amber-400 bg-amber-50 dark:bg-amber-900/20" : "")}`} value={sku} onChange={...} placeholder="Ej: CUT-001" autoFocus required />
```

(`handleGuardarEdicion` se define en Task C5; este paso compila igual porque la referencia se resuelve al definirla — implementar C5 antes del type-check final o stubbear `const handleGuardarEdicion = async () => {}` temporalmente.)

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "front: modo edicion en el modal de producto (titulo/boton/sku)"
```

---

### Task C4: Abrir el modal en modo edición y precargar el form

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

- [ ] **Step 1: Escribir `abrirEdicion(producto: ProductoDTO)`**

Función que setea `editandoProductoId`, puebla todos los estados escalares/relaciones/márgenes desde `producto`, carga las N-a-N con `cargarRelacionesProducto` (Task C2) y abre el modal. Para los `AsyncSelect` de relaciones simples, además del `value` (id) hace falta el `displayValue` (nombre) — usar los `*NombreCompleto`/`*Nombre` ya disponibles en `producto` (marca/tipo/clasif traen `*NombreCompleto`; origen/material/proveedor traen `*Nombre`; mla `mlaNombre`). Setear los estados `mlaDisplay` y, si los AsyncSelect de relación usan `displayValue`, pasarles el nombre correspondiente.

```ts
const abrirEdicion = async (producto: ProductoDTO) => {
    setEditandoProductoId(producto.id);
    setSku(producto.sku ?? "");
    setCodExt(producto.codExt ?? "");
    setTituloWeb(producto.tituloWeb ?? "");
    setDescripcion(producto.descripcion ?? "");
    setImagenUrl(producto.imagenUrl ?? "");
    setEsCombo(!!producto.esCombo);
    setUxb(producto.uxb ?? 1);
    setActivo(!!producto.activo);
    setSubirADux(false);
    setCapacidad(producto.capacidad ?? "");
    setLargo(producto.largo ?? ""); setAncho(producto.ancho ?? ""); setAlto(producto.alto ?? "");
    setDiamboca(producto.diamboca ?? ""); setDiambase(producto.diambase ?? ""); setEspesor(producto.espesor ?? "");
    setCosto(producto.costo ?? 0); setIva(producto.iva ?? 21);
    setStock(producto.stock ?? ""); setMoq(producto.moq ?? "");
    setTagReposicion((producto.tagReposicion as any) ?? ""); setTag((producto.tag as any) ?? "");
    setMarcaId(producto.marcaId ?? null); setOrigenId(producto.origenId ?? null);
    setClasifGralId(producto.clasifGralId ?? null); setClasifGastroId(producto.clasifGastroId ?? null);
    setTipoId(producto.tipoId ?? null); setProveedorId(producto.proveedorId ?? null);
    setMaterialId(producto.materialId ?? null); setMlaId(producto.mlaId ?? null);
    setMlaDisplay(producto.mlaNombre ?? "");
    setMargenMinorista(producto.margenMinorista ?? ""); setMargenMayorista(producto.margenMayorista ?? "");
    setMargenFijoMinorista(producto.margenFijoMinorista ?? ""); setMargenFijoMayorista(producto.margenFijoMayorista ?? "");
    setFormErrors({});
    setIsModalOpen(true);
    try {
        const rel = await cargarRelacionesProducto(producto.id);
        setCatalogosSel(rel.catalogos); setAptosSel(rel.aptos); setClientesSel(rel.clientes);
    } catch {
        setCatalogosSel([]); setAptosSel([]); setClientesSel([]);
        notificar.error("No se pudieron cargar catálogos/aptos/clientes del producto");
    }
};
```

- [ ] **Step 2: Pasar nombres a los AsyncSelect de relación simple en edición**

Verificar si los `AsyncSelect` de marca/origen/clasif/tipo/proveedor/material aceptan `displayValue`. Si lo aceptan, pasar el nombre desde `producto.*NombreCompleto`/`*Nombre` para que se vea el valor precargado. Si solo aceptan `value` (id) y resuelven el nombre por fetch, no hace falta. Documentar lo encontrado y ajustar.

- [ ] **Step 3: Type-check**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "front: precargar el form al abrir en modo edicion"
```

---

### Task C5: Guardar la edición (PATCH + márgenes + diff N-a-N)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/page.tsx`

- [ ] **Step 1: Guardar el snapshot inicial de N-a-N para el diff**

Agregar refs/estados con las N-a-N originales al abrir edición (en `abrirEdicion`, tras cargar `rel`): `setCatalogosOriginal(rel.catalogos)`, etc. Declarar:

```ts
const [catalogosOriginal, setCatalogosOriginal] = useState<MultiOption[]>([]);
const [aptosOriginal, setAptosOriginal] = useState<MultiOption[]>([]);
const [clientesOriginal, setClientesOriginal] = useState<MultiOption[]>([]);
```

- [ ] **Step 2: Escribir `handleGuardarEdicion`**

```ts
const handleGuardarEdicion = async () => {
    if (!validateForm() || editandoProductoId == null) return;
    try {
        setIsSaving(true);
        const id = editandoProductoId;
        const patch: ProductoPatchDTO = {
            codExt, tituloWeb: tituloWeb.trim(), descripcion: descripcion.trim(), esCombo, uxb, activo, imagenUrl,
            capacidad, largo: largo || null, ancho: ancho || null, alto: alto || null,
            diamboca: diamboca || null, diambase: diambase || null, espesor: espesor || null,
            costo, iva, stock: stock !== "" ? stock : null, moq: moq !== "" ? moq : null,
            tagReposicion: tagReposicion || null, tag: tag || null,
            marcaId, origenId, clasifGralId, clasifGastroId, tipoId, proveedorId, materialId, mlaId,
        } as ProductoPatchDTO;
        await updateProducto(id, patch);

        // Márgenes
        const margenDto: Record<string, number | null> = {
            margenMinorista: margenMinorista === "" ? null : margenMinorista,
            margenMayorista: margenMayorista === "" ? null : margenMayorista,
            margenFijoMinorista: margenFijoMinorista === "" ? null : margenFijoMinorista,
            margenFijoMayorista: margenFijoMayorista === "" ? null : margenFijoMayorista,
        };
        await updateProductoMargen(id, margenDto);

        // Diff de N-a-N
        const diff = (orig: MultiOption[], curr: MultiOption[]) => {
            const oid = new Set(orig.map(o => Number(o.id)));
            const cid = new Set(curr.map(c => Number(c.id)));
            return {
                add: curr.filter(c => !oid.has(Number(c.id))).map(c => Number(c.id)),
                remove: orig.filter(o => !cid.has(Number(o.id))).map(o => Number(o.id)),
            };
        };
        const dCat = diff(catalogosOriginal, catalogosSel);
        const dApt = diff(aptosOriginal, aptosSel);
        const dCli = diff(clientesOriginal, clientesSel);
        await Promise.all([
            ...dCat.add.map(cid => addProductoCatalogoAPI(id, cid)),
            ...dCat.remove.map(cid => removeProductoCatalogoAPI(id, cid)),
            ...dApt.add.map(aid => addProductoAptoAPI(id, aid)),
            ...dApt.remove.map(aid => removeProductoAptoAPI(id, aid)),
            ...dCli.add.map(clid => addProductoClienteAPI(id, clid)),
            ...dCli.remove.map(clid => removeProductoClienteAPI(id, clid)),
        ]);

        notificar.success(`Producto ${sku} actualizado`);
        resetForm();
        setEditandoProductoId(null);
        setIsModalOpen(false);
        await refresh(); // recargar el listado (usar el refresh de useProductos)
    } catch (e) { /* hook ya togglea toasts */ } finally { setIsSaving(false); }
};
```

Importar `removeProductoCatalogoAPI`, `removeProductoAptoAPI`, `removeProductoClienteAPI` (Task C1). Verificar el nombre del refetch del hook `useProductos` (p. ej. `refresh`) y usarlo.

- [ ] **Step 3: Type-check**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/page.tsx
git commit -m "front: guardar edicion de producto (patch + margenes + diff N-a-N)"
```

---

### Task C6: Botón "Editar" por fila

**Files:**
- Modify: `supermaster-frontend/src/app/productos/columns.tsx` (columna "detalle"/acciones, ~282-307)
- Modify: `supermaster-frontend/src/app/productos/page.tsx` (pasar el callback a `getColumns`)

- [ ] **Step 1: Agregar el parámetro `onEditarProducto` a `getColumns`**

En la firma de `getColumns(onOpenDetalle, canEdit, ...)`, agregar `onEditarProducto: (p: ProductoDTO) => void`. En `page.tsx`, donde se llama `getColumns(...)`, pasar `abrirEdicion`.

- [ ] **Step 2: Agregar el botón en la celda de acciones**

Dentro del `<div className="flex flex-col items-stretch gap-1">` de la columna "detalle", agregar (antes o después de "Detalle"):

```tsx
<TableActionButton
    onClick={() => onEditarProducto(row.original)}
    title="Editar producto"
    icon={<PencilSquareIcon className="w-3.5 h-3.5" />}
    tone="secondary"
    disabled={!canEdit}
>
    Editar
</TableActionButton>
```

Importar el ícono: `import { PencilSquareIcon } from "@heroicons/react/24/outline";` (verificar `tone` válido en `TableActionButton`; usar uno existente).

- [ ] **Step 3: Type-check**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/columns.tsx supermaster-frontend/src/app/productos/page.tsx
git commit -m "front: boton Editar por fila que abre el panel en modo edicion"
```

---

### Task C7: Verificación integral manual

- [ ] **Step 1: Backend arriba, frontend arriba**

- [ ] **Step 2: Probar alta** con solo gastro (sin gral) → crea OK. Con ninguna de las dos → error visual "al menos una".

- [ ] **Step 3: Probar edición**: abrir Editar, cambiar marca/tipo/clasif, agregar y quitar un catálogo y un apto, cambiar un margen, Guardar → verificar persistencia recargando. SKU se ve deshabilitado.

- [ ] **Step 4: Probar inline**: desasignar Clasif. Gral de un producto con gastro → OK; intentar dejar ambas null → toast de error y revert.

---

## Self-Review (cubierto por el plan)

- **Parte 1 (validación)**: Tasks A1–A4 (backend), B1–B2 (frontend). ✔
- **Parte 2 (null inline)**: Task A3 (patch backend) + B3 (columns nullable). ✔
- **Parte 3 (panel edición)**: Tasks C1–C7. ✔
- Decisión diferida (IDs de N-a-N): Task C2, explícita. ✔
- Tipos/nombres consistentes: `editandoProductoId`, `handleGuardarEdicion`, `cargarRelacionesProducto`, `abrirEdicion`, `validarAlMenosUnaClasificacion`, `remove*API` usados de forma coherente entre tasks. ✔
