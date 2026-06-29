# Estado de Dux y ML desde el panel (desacoplado de "Activo") — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Controlar el estado de publicación de Dux (`habilitado` S/N) y ML (active/paused) desde el panel "Estado de publicación", no desde el checkbox "Activo"; al crear: Dux "S", ML pausado.

**Architecture:** Dux fluye por el export (payload full; `DuxItemBuilder` usa un `@Transient duxHabilitado` con fallback a `activo`; el modal pasa el valor por el endpoint). ML: el alta queda siempre pausada y el update deja de tocar el status (lo maneja el panel vía `aplicar`/`updateItemStatus`). "Activo" sigue como flag interno.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven (backend); Next.js/React/TS (frontend); Lombok; records DTO.

## Global Constraints

- Backend offline: `mvn -o test` (el `mvn` del PATH, NO `mvnw`). Jackson 3 donde aplique.
- Frontend: `npx tsc --noEmit` exit 0.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` SOLO de los archivos de cada tarea (hay WIP de Dux en el working tree que NO se toca), nunca `-A` ni `.superpowers/`.
- No agregar columnas a la BD (el estado es transient). No tocar Nube. No eliminar el campo `activo`.
- Records DTO: agregar un componente rompe `new XDTO(...)` posicional; actualizar todos los call sites.

---

## File Structure

- `dominio/producto/entity/Producto.java` — nuevo `@Transient String duxHabilitado` (no persistido).
- `apis/dux/service/DuxItemBuilder.java` — `habilitado` desde `duxHabilitado` con fallback a `activo`.
- `apis/dux/dto/ExportDuxRequestDTO.java` — nuevo componente `habilitado`.
- `apis/dux/controller/DuxController.java` — pasa `habilitado` al servicio.
- `apis/dux/service/DuxService.java` — propaga `habilitado` hasta el armado del item.
- `apis/ml/service/MercadoLibreService.java` — alta siempre pausada; update no toca status (limpieza de `putStatus`).
- `supermaster-frontend/src/app/productos/productosService.ts` — `exportarProductosADuxAPI` acepta `habilitado`.
- `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` — tarjeta Dux editable + pasa `habilitado` al export.

---

## Task 1: Dux `habilitado` desde transient (con fallback a `activo`)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/dux/service/DuxItemBuilder.java:29`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/dux/DuxItemBuilderTest.java`

**Interfaces:**
- Produces: `Producto.getDuxHabilitado()` / `Producto.setDuxHabilitado(String)` (Lombok); `DuxItemBuilder.construir` usa `duxHabilitado` con fallback a `activo`.

- [ ] **Step 1: Test que falla — `duxHabilitado` pisa a `activo`**

En `DuxItemBuilderTest.java`, agregar:

```java
    @Test
    void construir_habilitadoUsaDuxHabilitadoSiEstaSeteado() {
        Producto p = productoCompleto();   // activo = true
        p.setActivo(false);                // activo en false...
        p.setDuxHabilitado("S");           // ...pero duxHabilitado fuerza "S"
        assertThat(builder().construir(p)).containsEntry("habilitado", "S");

        p.setDuxHabilitado("N");
        assertThat(builder().construir(p)).containsEntry("habilitado", "N");
    }

    @Test
    void construir_habilitadoCaeAActivoSiDuxHabilitadoNull() {
        Producto p = productoCompleto();   // activo = true, sin duxHabilitado
        assertThat(builder().construir(p)).containsEntry("habilitado", "S");
        p.setActivo(false);
        assertThat(builder().construir(p)).containsEntry("habilitado", "N");
    }
```

- [ ] **Step 2: Correr el test — falla a compilar (no existe `setDuxHabilitado`)**

Run: `cd supermaster-backend && mvn -o -Dtest=DuxItemBuilderTest test`
Expected: error de compilación `cannot find symbol: method setDuxHabilitado`.

- [ ] **Step 3: Agregar el `@Transient` en `Producto`**

Junto a los otros campos `@Transient` no persistidos (donde están `descripcionNube` / `equipamientoGastro`), agregar:

```java
    /** Estado de Dux a enviar en el export (no persistido). "S"/"N"; null = usar `activo`. */
    @Transient
    private String duxHabilitado;
```

(Lombok genera `getDuxHabilitado()`/`setDuxHabilitado()` como con los demás transient.)

- [ ] **Step 4: Usar `duxHabilitado` en `DuxItemBuilder` (con fallback a `activo`)**

En `DuxItemBuilder.java`, reemplazar la línea 29:

```java
        item.put("habilitado", Boolean.TRUE.equals(p.getActivo()) ? "S" : "N");
```

por:

```java
        // El habilitado viene del panel (transient duxHabilitado); si no se setea (export masivo),
        // cae al flag `activo` (comportamiento previo).
        String habilitado = (p.getDuxHabilitado() != null && !p.getDuxHabilitado().isBlank())
                ? p.getDuxHabilitado().trim()
                : (Boolean.TRUE.equals(p.getActivo()) ? "S" : "N");
        item.put("habilitado", habilitado);
```

- [ ] **Step 5: Correr los tests — pasan**

Run: `cd supermaster-backend && mvn -o -Dtest=DuxItemBuilderTest test`
Expected: `Tests run: 5, Failures: 0, Errors: 0` (los 3 previos + los 2 nuevos).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/dux/service/DuxItemBuilder.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/dux/DuxItemBuilderTest.java
git commit -m "feat(dux): habilitado desde transient duxHabilitado con fallback a activo"
```

---

## Task 2: Propagar `habilitado` por el export de Dux

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/dux/dto/ExportDuxRequestDTO.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/dux/controller/DuxController.java:218-234`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/dux/service/DuxService.java` (líneas 1322, 1326, 1363-1364, 1415-1420)

**Interfaces:**
- Consumes: `Producto.setDuxHabilitado(String)` (Task 1).
- Produces: `DuxService.exportarProductosADuxConfirmado(List<String> skus, String habilitado)`; body del POST `/api/dux/exportar-productos[/confirmar]` acepta `{ skus, habilitado }`.

- [ ] **Step 1: Agregar `habilitado` al request DTO**

`ExportDuxRequestDTO.java` completo:

```java
package ar.com.leo.super_master_backend.apis.dux.dto;

import java.util.List;

public record ExportDuxRequestDTO(
        List<String> skus,
        String habilitado   // "S"/"N" (flujo del modal); null = export masivo (usa `activo`)
) {}
```

- [ ] **Step 2: Pasar `habilitado` desde el controller**

En `DuxController.java`, los dos endpoints de export:

```java
    @PostMapping("/exportar-productos")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<ExportDuxResultDTO> exportarProductos(
            @RequestBody(required = false) ExportDuxRequestDTO request) {
        List<String> skus = request != null ? request.skus() : null;
        String habilitado = request != null ? request.habilitado() : null;
        ExportDuxResultDTO resultado = duxService.exportarProductosADux(skus, habilitado);
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/exportar-productos/confirmar")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<ExportCanalResultDTO> exportarProductosConfirmado(
            @RequestBody(required = false) ExportDuxRequestDTO request) {
        List<String> skus = request != null ? request.skus() : null;
        String habilitado = request != null ? request.habilitado() : null;
        ExportCanalResultDTO resultado = duxService.exportarProductosADuxConfirmado(skus, habilitado);
        return ResponseEntity.ok(resultado);
    }
```

- [ ] **Step 3: Propagar en `DuxService` (3 firmas)**

1. `exportarProductosADux` (línea 1322): cambiar firma y la llamada interna:

```java
    public ExportDuxResultDTO exportarProductosADux(List<String> skus, String habilitado) {
        log.info("Iniciando exportación de productos a DUX...");
        List<String> errores = new ArrayList<>();

        List<Map<String, Object>> productosJson = self.cargarYArmarItemsDux(skus, errores, habilitado);
```

(el resto del método queda igual.)

2. `exportarProductosADuxConfirmado` (línea 1363):

```java
    public ExportCanalResultDTO exportarProductosADuxConfirmado(List<String> skus, String habilitado) {
        ExportDuxResultDTO encolado = exportarProductosADux(skus, habilitado);
```

(el resto igual.)

3. `cargarYArmarItemsDux` (línea 1415): agregar el parámetro y setearlo en cada producto antes de armar:

```java
    public List<Map<String, Object>> cargarYArmarItemsDux(List<String> skus, List<String> errores, String habilitado) {
        List<Producto> productos = cargarProductosParaExportacion(skus, errores);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Producto producto : productos) {
            try {
                if (habilitado != null) producto.setDuxHabilitado(habilitado);
                items.add(duxItemBuilder.construir(producto));
            } catch (Exception e) {
                errores.add("SKU " + producto.getSku() + ": Error mapeando - " + e.getMessage());
                log.warn("Error mapeando producto {} para DUX: {}", producto.getSku(), e.getMessage());
            }
        }
        return items;
    }
```

- [ ] **Step 4: Buscar otros callers rotos por el cambio de firma**

Run: `cd supermaster-backend && grep -rn "exportarProductosADux\|cargarYArmarItemsDux\|exportarProductosADuxConfirmado" src/main/java`
Expected: los únicos usos son los editados arriba (controller + dentro de `DuxService`). Si aparece otro caller, actualizarlo pasando `null` como `habilitado`.

- [ ] **Step 5: Compilar y correr el test del builder**

Run: `cd supermaster-backend && mvn -o -Dtest=DuxItemBuilderTest test`
Expected: `BUILD SUCCESS`, `Tests run: 5, Failures: 0`. (El threading no tiene test unitario propio: se valida por compilación + smoke manual; la lógica del `habilitado` ya está cubierta por el builder.)

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/dux/dto/ExportDuxRequestDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/dux/controller/DuxController.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/dux/service/DuxService.java
git commit -m "feat(dux): el endpoint de export acepta habilitado y lo propaga al armado del item"
```

---

## Task 3: ML — alta siempre pausada + update no toca el status

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (líneas 1877, 1970, 1981, 2038-2041, 2127, 2326-2333)

**Interfaces:**
- Consumes: nada de tareas previas.
- Produces: comportamiento — alta ML siempre `paused`; `actualizarItemEnMlCore` ya no recibe `putStatus` ni cambia el status.

- [ ] **Step 1: Alta siempre pausada (quitar la condición por `activo`)**

En el bloque post-alta (líneas 2326-2333), reemplazar:

```java
        // Producto inactivo: dejar la publicación recién creada en paused (best-effort).
        // concatAdv preserva una advertencia previa del alta (descripción / imágenes omitidas).
        if (r.estado() == ResultadoAltaMl.Estado.CREADO
                && r.itemId() != null
                && !Boolean.TRUE.equals(producto.getActivo())
                && !updateItemStatus(r.itemId(), "paused")) {
            return r.conAdvertencia(MercadoLibreService.concatAdv(r.advertencia(), "estado de publicación no actualizado (no se pudo pausar)"));
        }
        return r;
```

por (las altas SIEMPRE quedan pausadas; el status se maneja luego desde el panel):

```java
        // Toda alta queda PAUSADA (el status lo decide el usuario desde el panel de estado, no `activo`).
        // concatAdv preserva una advertencia previa del alta (descripción / imágenes omitidas).
        if (r.estado() == ResultadoAltaMl.Estado.CREADO
                && r.itemId() != null
                && !updateItemStatus(r.itemId(), "paused")) {
            return r.conAdvertencia(MercadoLibreService.concatAdv(r.advertencia(), "estado de publicación no actualizado (no se pudo pausar)"));
        }
        return r;
```

- [ ] **Step 2: Update — quitar el seteo de status desde `activo`**

En `actualizarItemEnMlCore`, eliminar el bloque de las líneas 2038-2041:

```java
            String estadoTarget = Boolean.TRUE.equals(producto.getActivo()) ? "active" : "paused";
            if (!putStatus.actualizar(mla, estadoTarget)) {
                advertencia = concatAdv(advertencia, "estado de publicación no actualizado");
            }
```

(borrarlo por completo; no se reemplaza por nada.)

- [ ] **Step 3: Quitar el parámetro `putStatus` (queda sin uso) y su doc**

En `actualizarItemEnMlCore`:
- Borrar la línea de la firma (1981): `            ActualizadorEstadoItem putStatus,`
- Borrar la línea del javadoc (1970): `     *  - putStatus(mla, status) → cambia el estado de publicación ("active"/"paused", best-effort).`

- [ ] **Step 4: Quitar el argumento en el caller**

En la llamada a `actualizarItemEnMlCore` (~línea 2127), borrar el argumento:

```java
                this::updateItemStatus,
```

(El método `updateItemStatus` (línea 1619) NO se borra: sigue usándolo el alta en el Step 1 y `EstadoPublicacionService.aplicar`.)

- [ ] **Step 5: Quitar la interface `ActualizadorEstadoItem` si quedó sin uso**

Run: `cd supermaster-backend && grep -rn "ActualizadorEstadoItem" src`
Expected: tras los pasos anteriores, el único match es la declaración de la interface (~línea 1877). Borrar esa interface completa (es un `@FunctionalInterface`/`interface` de pocas líneas). Si `grep` muestra algún otro uso, NO borrarla y dejar el parámetro: reportarlo.

- [ ] **Step 6: Compilar y correr los tests de ML existentes**

Run: `cd supermaster-backend && mvn -o -Dtest=MlDescripcionSugeridaBuilderTest,MlItemPayloadBuilderTest,MlEstadoParserTest,DuxItemBuilderTest test`
Expected: `BUILD SUCCESS`, todos verdes.

Nota: los cambios de ML (alta pausada / update sin status) viven en `MercadoLibreService`, que no tiene harness de test unitario; se validan por **compilación** (la ausencia del parámetro `putStatus` ES la garantía de que el update no toca status) + **smoke manual**. No se agrega test nuevo.

- [ ] **Step 7: Compilar todo el módulo**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java
git commit -m "feat(ml): alta siempre pausada y update ya no setea status desde activo"
```

---

## Task 4: Frontend — Dux editable en el panel + pasar `habilitado` al export

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts:246-254`
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (líneas 443-458 y 1652-1656)

**Interfaces:**
- Consumes: backend `/api/dux/exportar-productos/confirmar` acepta `{ skus, habilitado }` (Task 2).

- [ ] **Step 1: `exportarProductosADuxAPI` acepta `habilitado`**

En `productosService.ts`, reemplazar la función (246-254) por:

```ts
export const exportarProductosADuxAPI = async (skus: string[], habilitado?: "S" | "N"): Promise<ExportCanalResultDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/dux/exportar-productos/confirmar`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ skus, habilitado }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Dux"));
	return await res.json();
};
```

- [ ] **Step 2: Pasar el `habilitado` del panel al export de Dux**

En `ProductoFormModal.tsx`, dentro de `ejecutarExportsCanales` (rama `if (canales.includes("Dux"))`, línea ~451), reemplazar:

```ts
                    const r = await exportarProductosADuxAPI([skuExport]);
```

por:

```ts
                    // En alta no hay panel (estadoCanales = null) → "S"; en edición, el valor del panel.
                    const duxHabilitado: "S" | "N" = estadoCanales?.dux.estado === "deshabilitado" ? "N" : "S";
                    const r = await exportarProductosADuxAPI([skuExport], duxHabilitado);
```

- [ ] **Step 3: Tarjeta Dux editable en el panel (select Habilitado/Deshabilitado)**

En `ProductoFormModal.tsx`, reemplazar el render de la tarjeta Dux (1652-1656):

```tsx
                            {renderEstadoCanal("Dux", estadoCanales?.dux,
                                <span className="text-sm font-medium text-slate-600 dark:text-slate-300">
                                    {estadoCanales?.dux.estado === "habilitado" ? "Habilitado" : "Deshabilitado"}
                                </span>,
                                <BuildingStorefrontIcon className="h-5 w-5 shrink-0 text-slate-500" />, estadoCanales?.dux.estado ?? undefined)}
```

por (mismo patrón que las tarjetas Nube/ML, con `<select>`):

```tsx
                            {renderEstadoCanal("Dux", estadoCanales?.dux,
                                <select className={`${selectBaseClassName} w-full`} value={estadoCanales?.dux.estado === "deshabilitado" ? "deshabilitado" : "habilitado"}
                                    onChange={e => setEstadoCanales(p => p && ({ ...p, dux: { ...p.dux, estado: e.target.value } }))}>
                                    <option value="habilitado">Habilitado</option>
                                    <option value="deshabilitado">Deshabilitado</option>
                                </select>,
                                <BuildingStorefrontIcon className="h-5 w-5 shrink-0 text-slate-500" />, estadoCanales?.dux.estado ?? undefined)}
```

- [ ] **Step 4: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(estado): Dux editable en el panel y habilitado al export; alta Dux=S, ML pausado"
```

---

## Verificación manual final (post-tareas)

- **Alta:** crear un producto con Dux+ML tildados → en Dux queda `habilitado:"S"`; en ML la publicación queda **pausada** (sin importar "Activo").
- **Edición:** abrir un producto publicado, en el panel poner **Dux = Deshabilitado** y guardar → Dux recibe `habilitado:"N"`. Poner **ML = Pausada/Activa** y guardar → se aplica vía el panel.
- **"Activo":** cambiar el checkbox no afecta el `habilitado` de Dux ni el status de ML.
- **Export masivo (lista):** Dux sigue usando `activo` (no se manda `habilitado`); ML: las altas masivas quedan pausadas y los updates no tocan el status (cambio documentado en el spec).
