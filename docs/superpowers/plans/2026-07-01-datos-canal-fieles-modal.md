# Datos de canal fieles a la API en el modal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el modal de producto muestre el título y los atributos de ML tal como están en la API de ML (no desde datos locales al editar), externalizando por completo el título ML (BD/back/front), y que el tooltip de imágenes muestre siempre el path real de las carpetas.

**Architecture:** El título ML deja de ser columna persistida y pasa a `@Transient` en `Producto` (mismo patrón que descripción/categoría/atributos ya externalizados): se lee del ítem de ML al abrir el modal y viaja en el request de publicación. Los `useEffect` de espejado de atributos desde datos locales se limitan al modo creación. El tooltip carga los paths de carpetas al abrir el modal.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven (backend), Next.js + React + TypeScript (frontend), MySQL (`ddl-auto=validate`).

## Global Constraints

- `ddl-auto=validate`: todo cambio de columna requiere script SQL manual en `supermaster-backend/src/main/resources/db/` (ver [[cambios-schema-bd-backend]]). El script debe ser idempotente.
- Tests backend: correr con `mvnw test` (Leo local). En sandbox sin red usar el `mvn` instalado con `-o` (offline). Quitar/agregar componentes a records **rompe compilación** de los `new XDTO(...)` posicionales — ajustar todos los call sites y correr `mvn -o test` (no solo `compile`).
- El getter/setter `getTituloMl()`/`setTituloMl()` de la entidad `Producto` **se conservan** (los usa `MercadoLibreService` y los tests de ML); solo se quita la persistencia (columna) y la presencia en DTOs.
- "Null = no tocar" en publicación por lote: los datos de canal transitorios llegan null en re-sync y el publish los omite.
- No romper el formato de error ni contratos `/api` existentes.

---

## Task 1: Backend — leer el título ML del canal

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/MlCanalDTO.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionService.java:49-76` (método `leerMl`)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlCanalDTOTest.java` (nuevo, mínimo) — o extender un test existente de `leerMl` si lo hubiera.

**Interfaces:**
- Produces: `MlCanalDTO` gana un componente `String titulo` (último). `leerMl` lo completa con `item.path("title").asString(null)`; en los caminos noPublicado/error va `null`.

- [ ] **Step 1: Agregar el componente `titulo` al record**

En `MlCanalDTO.java`, agregar `titulo` como último componente:

```java
public record MlCanalDTO(
        EstadoCanalDTO estado,
        String categoryId,
        String categoryNombre,
        List<MlAtributoDTO> atributos,
        String descripcion,
        String mlaResuelto,
        Double mlPaqAlto,
        Double mlPaqAncho,
        Double mlPaqLargo,
        Double mlPaqPeso,
        String titulo
) {}
```

- [ ] **Step 2: Completar `titulo` en `leerMl`**

En `EstadoPublicacionService.leerMl`, actualizar los 4 `new MlCanalDTO(...)` para el nuevo componente final:

- noPublicado (línea ~56): agregar `, null` al final → `..., null, null, null, null, null)`.
- item == null (línea ~61): igual, `, null` al final.
- catch (línea ~73): igual, `, null` al final.
- caso OK (línea ~69-71): leer el título y pasarlo:

```java
String titulo = item.path("title").asString(null);
return new MlCanalDTO(MlEstadoParser.parse(item), catId, catNombre,
        atributos, descMl, mlaCode,
        paquete.altoCm(), paquete.anchoCm(), paquete.largoCm(), paquete.pesoKg(), titulo);
```

- [ ] **Step 3: Test — el DTO expone el título**

Test mínimo que documenta el contrato (el parseo real de `leerMl` requiere API de ML, así que se testea el DTO):

```java
package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.MlCanalDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlCanalDTOTest {
    @Test
    void exponeTitulo() {
        MlCanalDTO dto = new MlCanalDTO(EstadoCanalDTO.noPublicado(), null, null,
                List.of(), null, null, null, null, null, null, "Olla acero 24cm");
        assertThat(dto.titulo()).isEqualTo("Olla acero 24cm");
    }
}
```

- [ ] **Step 4: Compilar y testear**

Run: `cd supermaster-backend && mvn -o test -Dtest=MlCanalDTOTest`
Expected: PASS. (Si hay otros usos de `new MlCanalDTO(` fuera de `leerMl`, el compilador los marcará: ajustarlos agregando `, null` / el título.)

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/.../MlCanalDTO.java supermaster-backend/src/main/java/.../EstadoPublicacionService.java supermaster-backend/src/test/java/.../MlCanalDTOTest.java
git commit -m "feat(ml): MlCanalDTO expone el titulo leido del item de ML"
```

---

## Task 2: Backend — pasar el título al publicar

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlExportRequestDTO.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlExportService.java:48-92`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/` (extender/crear test de `procesarConProductoCargado` si aplica; si no, cubrir vía el test de request abajo).

**Interfaces:**
- Consumes: `Producto.setTituloMl(String)` (setter ya existente).
- Produces: `MlExportRequestDTO` gana `String tituloMl` (último). `procesarConProductoCargado(...)` gana un parámetro `String tituloMl` y hace `if (tituloMl != null) p.setTituloMl(tituloMl);`.

- [ ] **Step 1: Agregar `tituloMl` al request DTO**

```java
public record MlExportRequestDTO(
        List<String> skus,
        @NotNull(message = "La cuota es obligatoria") Integer cuotas,
        String mlCategoryId,
        List<MlAtributoDTO> mlAtributos,
        String descripcionMl,
        String tituloMl) {}
```

- [ ] **Step 2: Setear el título en `procesarConProductoCargado`**

Agregar el parámetro y el guard "null = no tocar":

```java
ResultadoAltaMl r = self.procesarConProductoCargado(
        productoId, request.cuotas(), request.mlCategoryId(), request.mlAtributos(),
        request.descripcionMl(), request.tituloMl());
```

Y en la firma del método (`procesarConProductoCargado`), agregar `String tituloMl` y, junto a los otros datos transitorios (después de `if (descripcionMl != null) p.setDescripcionMl(descripcionMl);`):

```java
if (tituloMl != null && !tituloMl.isBlank()) p.setTituloMl(tituloMl);
```

- [ ] **Step 3: Test — el request lleva el título y no lo pisa en null**

```java
@Test
void requestLlevaTitulo() {
    var req = new MlExportRequestDTO(java.util.List.of("SKU1"), 1, null, null, null, "Olla acero 24cm");
    assertThat(req.tituloMl()).isEqualTo("Olla acero 24cm");
}
```

(Ubicar en un test nuevo `MlExportRequestDTOTest` en el paquete `apis.ml.dto`.)

- [ ] **Step 4: Compilar y testear**

Run: `cd supermaster-backend && mvn -o test -Dtest=MlExportRequestDTOTest`
Expected: PASS. Verificar que no queden otros `new MlExportRequestDTO(` sin el nuevo argumento (el compilador los marca).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/.../MlExportRequestDTO.java supermaster-backend/src/main/java/.../MlExportService.java supermaster-backend/src/test/java/.../MlExportRequestDTOTest.java
git commit -m "feat(ml): el request de publicacion lleva tituloMl (null = no tocar)"
```

---

## Task 3: Backend — externalizar `tituloMl` en la entidad + SQL

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java:53-55`
- Create: `supermaster-backend/src/main/resources/db/2026-07-01-drop-titulo-ml.sql`

**Interfaces:**
- Produces: `Producto.tituloMl` pasa de `@Column(name="titulo_ml")` a `@Transient`; getter/setter intactos (Lombok).

- [ ] **Step 1: Cambiar la anotación a `@Transient`**

En `Producto.java`, reemplazar:

```java
    @Size(max = 100)
    @Column(name = "titulo_ml", length = 100)
    private String tituloMl;
```

por (moverlo junto al bloque de datos de canal `@Transient` existente, o dejarlo en su lugar con la anotación cambiada):

```java
    // Título ML NO persistido (fuente de verdad: el ítem de ML). Lo setea el export desde el request
    // antes de publicar; en lote va null y el publish lo omite. Ver plan 2026-07-01-datos-canal-fieles-modal.
    @Transient
    private String tituloMl;
```

- [ ] **Step 2: Script SQL idempotente de drop**

Crear `db/2026-07-01-drop-titulo-ml.sql`:

```sql
-- 2026-07-01 — Externalizar el título ML: deja de persistirse (fuente de verdad = el ítem de ML).
-- DESTRUCTIVO de una sola vía: para ítems publicados el título sigue en ML; para no publicados se pierde.
-- Idempotente: el drop se condiciona a que la columna exista.
USE supermaster;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'supermaster' AND TABLE_NAME = 'productos' AND COLUMN_NAME = 'titulo_ml');
SET @sql := IF(@col > 0, 'ALTER TABLE productos DROP COLUMN titulo_ml', 'SELECT "titulo_ml ya no existe"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

- [ ] **Step 3: Aplicar el SQL en MySQL local**

Run (ver [[acceso-bd-mysql-local]] para la ruta de `mysql.exe`):
`mysql --default-character-set=utf8mb4 -u <user> -p supermaster < supermaster-backend/src/main/resources/db/2026-07-01-drop-titulo-ml.sql`
Expected: sin error (re-ejecutable). Con `@Transient`, `validate` ya no exige la columna.

- [ ] **Step 4: Verificar arranque/tests**

Run: `cd supermaster-backend && mvn -o test -Dtest=CrearItemEnMlTest`
Expected: PASS (los tests de ML usan `p.setTituloMl(...)` sobre la entidad, que sigue funcionando con `@Transient`).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/.../entity/Producto.java supermaster-backend/src/main/resources/db/2026-07-01-drop-titulo-ml.sql
git commit -m "feat(productos): titulo_ml deja de persistirse (@Transient) + SQL drop idempotente"
```

---

## Task 4: Backend — quitar `tituloMl` de DTOs, mapper, patch, auditoría y Excel

**Files (todos Modify):**
- `dominio/producto/dto/ProductoDTO.java`, `ProductoCreateDTO.java`, `ProductoUpdateDTO.java`, `ProductoResumenDTO.java`, `ProductoConPreciosDTO.java` — quitar el componente `tituloMl`.
- `dominio/producto/dto/ProductoPatchDTO.java:17` — quitar el campo `tituloMl`.
- `dominio/producto/mapper/ProductoMapper.java:60,363` — quitar el argumento `tituloMl` de las construcciones.
- `dominio/producto/service/ProductoServiceImpl.java:1123,1193-1195` — quitar de `isPatchVacio` y `aplicarPatch`.
- `dominio/producto/service/ProductoAuditoriaServiceImpl.java:31` — quitar del snapshot.
- `excel/service/ExcelServiceImpl.java:3136` — quitar la celda de la columna "Título ML" (y su header correspondiente).
- Tests: `ProductoMapperEanTest.java:21`, `RecalculoAutomaticoIntegrationTest.java:398`, y cualquier `new ProductoDTO(`/`new ProductoResumenDTO(`/etc. posicional.

**Interfaces:**
- Produces: ninguno de los DTOs de producto expone ya `tituloMl`. La entidad conserva el campo `@Transient`.

- [ ] **Step 1: Localizar todos los usos**

Run: `cd supermaster-backend && grep -rn "tituloMl\|getTituloMl\|titulo_ml" src/main src/test`
Anotar cada `new XDTO(...)`, `.tituloMl()`, `.getTituloMl()` en construcción de DTO, `setCellValue(...tituloMl...)`. **No** tocar los `p.setTituloMl(...)`/`producto.getTituloMl()` que operan sobre la entidad en el flujo de publicación ML (`MercadoLibreService`, tests de ML) — esos se conservan.

- [ ] **Step 2: Quitar el componente de los 5 records de lectura/escritura**

En cada uno de `ProductoDTO`, `ProductoCreateDTO`, `ProductoUpdateDTO`, `ProductoResumenDTO`, `ProductoConPreciosDTO`: eliminar la línea `String tituloMl,`. En `ProductoPatchDTO`: eliminar `private JsonNullable<String> tituloMl = JsonNullable.undefined();`.

- [ ] **Step 3: Ajustar mapper, patch, auditoría y Excel**

- `ProductoMapper.java`: en las dos construcciones (líneas ~60 y ~363), quitar el argumento correspondiente a `tituloMl` (`entity.getTituloMl()` / `producto.getTituloMl()`).
- `ProductoServiceImpl.java`: quitar la línea `&& !presente(patchDto.getTituloMl())` de `isPatchVacio`; quitar el bloque `if (presente(patchDto.getTituloMl())) { ... }` de `aplicarPatch`.
- `ProductoAuditoriaServiceImpl.java`: quitar `snapshot.put("tituloMl", normalizar(producto.getTituloMl()));`.
- `ExcelServiceImpl.java:3136`: quitar la línea `setCellValue(row.createCell(cellIndex++), producto.tituloMl(), currentDataStyle);` y su header "Título ML" (buscar el header para mantener alineadas las columnas — quitar ambos y verificar que no se desalineen las columnas siguientes).

- [ ] **Step 4: Ajustar los tests posicionales**

- `ProductoMapperEanTest.java:21`: quitar el argumento `null, // tituloMl` del `new XDTO(...)`.
- `RecalculoAutomaticoIntegrationTest.java:398`: quitar `producto.getTituloMl(),` de la construcción del DTO.
- Cualquier otro `new ProductoDTO(`/`new ProductoResumenDTO(`/`new ProductoConPreciosDTO(`/`new ProductoCreateDTO(`/`new ProductoUpdateDTO(` que el compilador marque.

- [ ] **Step 5: Compilar y correr la suite de producto**

Run: `cd supermaster-backend && mvn -o test`
Expected: PASS (o al menos compila y los tests afectados pasan). Resolver cualquier `new XDTO(` restante que marque el compilador (ver [[dtos-record-constructores-posicionales]]).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java supermaster-backend/src/test/java
git commit -m "refactor(productos): quita tituloMl de DTOs, mapper, patch, auditoria y Excel"
```

---

## Task 5: Frontend — título ML desde el canal en el modal

**Files (todos Modify):**
- `supermaster-frontend/src/app/productos/productosService.ts:314-327,536` — tipo `MlCanal` + firma de `exportarProductosAMlAPI`.
- `supermaster-frontend/src/app/productos/ProductoFormModal.tsx:531,636,712-722,559,781`

**Interfaces:**
- Consumes: `MlCanal.titulo` (nuevo). `exportarProductosAMlAPI(..., tituloMl?)`.
- Produces: en edición, el estado `tituloMl` se puebla desde el canal; se manda en la publicación ML, no en el create/update del producto.

- [ ] **Step 1: Tipo `MlCanal` y firma del export**

En `productosService.ts`, agregar `titulo` al tipo (línea 536):

```ts
export type MlCanal = { estado: EstadoCanal; categoryId: string | null; categoryNombre: string | null; atributos: ProductoMlAtributo[]; descripcion: string | null; mlaResuelto: string | null; mlPaqAlto: number | null; mlPaqAncho: number | null; mlPaqLargo: number | null; mlPaqPeso: number | null; titulo: string | null };
```

Y agregar `tituloMl` a `exportarProductosAMlAPI`:

```ts
export const exportarProductosAMlAPI = async (
	skus: string[], cuotas: number,
	mlCategoryId?: string | null,
	mlAtributos?: ProductoMlAtributo[],
	descripcionMl?: string | null,
	tituloMl?: string | null,
): Promise<ExportCanalResultDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/ml/exportar-productos`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ skus, cuotas, mlCategoryId, mlAtributos, descripcionMl, tituloMl }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Mercado Libre"));
	return await res.json();
};
```

- [ ] **Step 2: Precargar el título desde el canal (edición)**

En `ProductoFormModal.tsx`:
- Línea 636: reemplazar `setTituloMl(producto.tituloMl ?? "");` por `setTituloMl("");` (arranca vacío; el canal lo llena). Nota: `producto.tituloMl` ya no existe tras Task 6.
- En el handler de `getEstadoMlAPI` (líneas 712-722), agregar dentro del `.then(e => { ... })`:

```ts
setTituloMl(e.titulo ?? "");
```

(colocarlo junto a `setDescripcionMl(e.descripcion ?? "");`, línea ~715.)

- [ ] **Step 3: Mandar el título en la publicación ML, no en create/update**

- Línea 531: pasar el título al export:

```ts
mlCategoryId, Object.values(mlAtributosVal), descripcionMl.trim() || null, tituloMl.trim() || null);
```

- Líneas 559 y 781 (payloads `ProductoCreateDTO` / `ProductoUpdateDTO`): quitar `tituloMl: tituloMl.trim() || null,` de ambos objetos.

- [ ] **Step 4: Typecheck/build**

Run: `cd supermaster-frontend && npm run build`
Expected: compila sin errores de tipo (los tipos `ProductoCreateDTO`/`ProductoUpdateDTO` se limpian en Task 6; si build corre antes de Task 6, quitar `tituloMl` de esos tipos primero o hacer Task 6 antes que este step — ver orden abajo).

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(modal): el titulo ML se lee del canal y viaja en la publicacion"
```

---

## Task 6: Frontend — quitar la columna "Título ML" de la grilla y tipos

**Files (todos Modify):**
- `supermaster-frontend/src/app/productos/types.ts:9,87`
- `supermaster-frontend/src/app/productos/columns.tsx:143`
- `supermaster-frontend/src/app/productos/HistorialSection.tsx:45`

**Interfaces:**
- Produces: `Producto`/tipos de fila ya no tienen `tituloMl`; la grilla ya no muestra ni edita "Título ML".

- [ ] **Step 1: Quitar de los tipos**

En `types.ts`, eliminar las líneas `tituloMl: string | null;` (líneas 9 y 87).

- [ ] **Step 2: Quitar la columna de la grilla**

En `columns.tsx`, eliminar el objeto de columna `{ accessorKey: "tituloMl", header: "Título ML", ... }` (línea 143).

- [ ] **Step 3: Quitar el label de auditoría**

En `HistorialSection.tsx`, eliminar `tituloMl: "Título ML",` (línea 45).

- [ ] **Step 4: Typecheck/build**

Run: `cd supermaster-frontend && npm run build`
Expected: compila sin errores. Resolver cualquier referencia restante a `.tituloMl` que el typecheck marque (buscar con `grep -rn "tituloMl" src`).

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/types.ts supermaster-frontend/src/app/productos/columns.tsx supermaster-frontend/src/app/productos/HistorialSection.tsx
git commit -m "refactor(productos): quita la columna Titulo ML de la grilla y tipos"
```

> **Orden:** ejecutar Task 6 antes del Step 4 (build) de Task 5, o hacer ambos y correr el build una sola vez al final. Ambos tocan `tituloMl` en el front; el build queda verde recién con los dos aplicados.

---

## Task 7: Frontend — espejado de atributos ML solo al crear

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx:1126-1143,1165-1180,1183-1199`

**Interfaces:**
- Consumes: `editandoProductoId` (estado ya existente; `null` en creación, id en edición).
- Produces: en edición, los atributos ML quedan poblados solo desde `e.atributos` (API). En creación, se conserva el espejado desde datos locales.

- [ ] **Step 1: Condicionar los 3 `useEffect` de espejado a creación**

En cada uno de los tres `useEffect` (dimensiones ~1126, BRAND ~1165, MATERIAL ~1183), agregar un early-return al inicio del cuerpo del efecto:

```ts
if (editandoProductoId) return; // en edición los atributos vienen del canal, no de datos locales
```

Colocar ese guard **después** del guard existente `if (!mlFicha) ...` / `if (!mlFicha || !fichaAttrIds.has("BRAND")) return;`, y agregar `editandoProductoId` al array de dependencias de cada efecto.

Dejar SIN tocar los `useEffect` de poda de atributos "stale" (~1112) y de limpieza de `noAplica` en required (~1147): esos no inventan datos y deben correr en ambos modos.

- [ ] **Step 2: Typecheck/build**

Run: `cd supermaster-frontend && npm run build`
Expected: compila sin errores.

- [ ] **Step 3: Smoke manual**

Editar un producto publicado en ML: los atributos (Marca/Material/dimensiones) deben reflejar el ítem de ML, sin pisarse con datos locales. Crear un producto nuevo con categoría ML: Marca/Material/dimensiones deben autocompletarse desde los datos locales como hoy.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "fix(modal): el espejado de atributos ML desde datos locales corre solo al crear"
```

---

## Task 8: Frontend — tooltip de imágenes con path real

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (efecto de precarga en edición, ~712-740; el tooltip usa `crudasDisp` en 1975-1976)

**Interfaces:**
- Consumes: `getCrudasAPI(sku): Promise<CrudasDisponibles>` (existente), `setCrudasDisp` (estado existente).
- Produces: `crudasDisp` poblado al abrir el modal (no solo al abrir el selector de carátula), de modo que el tooltip muestre `destinoDir.ruta` / `crudaDir.ruta`.

- [ ] **Step 1: Cargar `crudasDisp` al abrir el modal en edición**

Dentro del bloque `if (producto) { ... }` del efecto de precarga (junto a los otros `getEstado*API`, ~línea 712), agregar:

```ts
getCrudasAPI(producto.sku ?? "").then(c => { if (!cancelled) setCrudasDisp(c); }).catch(() => { /* silencioso: el tooltip cae al fallback */ });
```

(Requiere `sku` no vacío; en edición siempre lo hay. No pisa la recarga que hace `abrirSelectorCaratula`, que vuelve a setearlo.)

- [ ] **Step 2: Typecheck/build**

Run: `cd supermaster-frontend && npm run build`
Expected: compila sin errores.

- [ ] **Step 3: Smoke manual**

Abrir el modal de un producto y pasar el mouse por el ícono ⓘ de "Imágenes (por SKU)": el tooltip debe mostrar los paths reales de las carpetas (no `app.imagenes-dir` / `app.imagenes-raw-dir`).

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "fix(modal): el tooltip de imagenes muestra el path real de las carpetas"
```

---

## Verificación final

- [ ] `cd supermaster-backend && mvn -o test` → verde.
- [ ] `cd supermaster-frontend && npm run build` → verde; `grep -rn "tituloMl" supermaster-frontend/src` no debe dejar referencias a un campo persistido (solo el estado local del modal y el envío al export ML).
- [ ] Aplicar `db/2026-07-01-drop-titulo-ml.sql` en cada entorno antes de desplegar (idempotente).
- [ ] Smoke visual de Leo: (a) editar producto publicado en ML → título y atributos reflejan el canal; (b) crear producto nuevo → espejado local funciona; (c) tooltip con paths reales; (d) Tienda Nube sin cambios de comportamiento.
