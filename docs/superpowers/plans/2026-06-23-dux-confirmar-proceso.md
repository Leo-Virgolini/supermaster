# Confirmar el alta en Dux — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el export a Dux confirme el resultado real del alta consultando el proceso async (`obtenerEstadoItem`) con polling, y lo reporte como creado / error / encolado-sin-confirmar.

**Architecture:** El POST de Dux encola (devuelve `idProceso`). Tras encolar, `DuxService` hace polling de `obtenerEstadoProceso(idProceso)` hasta `estado=FINALIZADO` (tope 30s) y mapea `{estado, errores}` al `ExportCanalResultDTO` común (el mismo de Nube/ML). El front usa `clasificarExport` para Dux como con los otros canales. La lógica de parseo/mapeo se extrae a métodos puros testeables offline.

**Tech Stack:** Spring Boot / Java 25 (backend, `mvn -o test`), Next.js 16 / React 19 / TS (frontend, `npx tsc --noEmit`).

## Global Constraints

- Trabajar en `main`. Backend: usar `mvn` instalado (NO `./mvnw`): `mvn -o -Dtest=Clase test`. Frontend: `npx tsc --noEmit` desde `supermaster-frontend/`.
- **NO ejecutar nada contra la API real de Dux/Nube/ML.** El test del proceso usa JSON de ejemplo (POJO/Mockito), sin HTTP real.
- Valores de `estado`: `FINALIZADO` = terminó; cualquier otro (`PENDIENTE`, …) = sigue procesando. **`errores` vacío/ausente = creado; con items = falló.**
- Polling: tope total **30s**, intervalo **5s**. Respetar el rate limit (cada consulta pasa por `DuxRetryHandler`).
- Timeout sin `FINALIZADO` → advertencia `"encolado sin confirmar (proceso #N)"`.
- DTO de resultado: el común `ExportCanalResultDTO(int creados, List<String> actualizados, List<String> yaExistian, List<String> errores, List<String> advertencias)` (en `dominio/common/dto`).
- Commits terminan EXACTAMENTE con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: Backend — confirmar el proceso de Dux y migrar al DTO común

**Files:**
- Modify: `supermaster-backend/.../apis/dux/service/DuxService.java` (`exportarProductosADux` + helpers de parseo/polling/mapeo)
- Modify: `supermaster-backend/.../apis/dux/controller/DuxController.java` (endpoint `exportar-productos` devuelve `ExportCanalResultDTO`)
- Delete/deprecate: `supermaster-backend/.../apis/dux/dto/ExportDuxResultDTO.java` (solo lo usan DuxService + DuxController)
- Test: `supermaster-backend/src/test/java/.../apis/dux/service/DuxProcesoResultadoTest.java` (nuevo)

**Interfaces:**
- Produces: `DuxService.exportarProductosADux(List<String> skus)` ahora devuelve `ExportCanalResultDTO`. Helpers package-private estáticos: `DuxService.parsearEstadoProceso(String json, ObjectMapper)` → `DuxService.EstadoProceso`; `EstadoProceso` es un `record EstadoProceso(String estado, List<String> errores)` con `boolean finalizado()`.

- [ ] **Step 1: Test del parseo + mapeo (falla primero)**

Crear `DuxProcesoResultadoTest.java`. Usa los JSON reales de Dux; sin Spring/HTTP:
```java
package ar.com.leo.super_master_backend.apis.dux.service;

import ar.com.leo.super_master_backend.dominio.common.dto.ExportCanalResultDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DuxProcesoResultadoTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void finalizadoSinErrores_esCreado() {
        DuxService.EstadoProceso e = DuxService.parsearEstadoProceso("{\"estado\":\"FINALIZADO\"}", om);
        assertThat(e.finalizado()).isTrue();
        assertThat(e.errores()).isEmpty();
        ExportCanalResultDTO r = DuxService.mapearResultadoProceso(e, 1);
        assertThat(r.creados()).isEqualTo(1);
        assertThat(r.errores()).isEmpty();
        assertThat(r.advertencias()).isEmpty();
    }

    @Test
    void finalizadoConErrores_esError() {
        String json = "{\"estado\":\"FINALIZADO\",\"errores\":[\"codigo_marca no encontrado para el producto con cod_item : 4276.\"]}";
        DuxService.EstadoProceso e = DuxService.parsearEstadoProceso(json, om);
        assertThat(e.finalizado()).isTrue();
        ExportCanalResultDTO r = DuxService.mapearResultadoProceso(e, 1);
        assertThat(r.creados()).isZero();
        assertThat(r.errores()).containsExactly("codigo_marca no encontrado para el producto con cod_item : 4276.");
    }

    @Test
    void pendiente_noFinalizado() {
        DuxService.EstadoProceso e = DuxService.parsearEstadoProceso("{\"estado\":\"PENDIENTE\"}", om);
        assertThat(e.finalizado()).isFalse();
    }

    @Test
    void sinConfirmar_esAdvertencia() {
        ExportCanalResultDTO r = DuxService.resultadoSinConfirmar(7);
        assertThat(r.creados()).isZero();
        assertThat(r.errores()).isEmpty();
        assertThat(r.advertencias()).containsExactly("encolado sin confirmar (proceso #7)");
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `mvn -o -Dtest=DuxProcesoResultadoTest test`
Expected: FAIL de compilación (los métodos `parsearEstadoProceso`/`mapearResultadoProceso`/`resultadoSinConfirmar`/`EstadoProceso` aún no existen).

- [ ] **Step 3: Helpers de parseo/mapeo en `DuxService`**

Agregar a `DuxService` (imports: `ar.com.leo.super_master_backend.dominio.common.dto.ExportCanalResultDTO`, `java.util.List`, `tools.jackson.databind.JsonNode`/`ObjectMapper`):
```java
record EstadoProceso(String estado, List<String> errores) {
    boolean finalizado() { return "FINALIZADO".equalsIgnoreCase(estado); }
}

static EstadoProceso parsearEstadoProceso(String json, ObjectMapper om) {
    try {
        JsonNode root = om.readTree(json);
        String estado = root.path("estado").asString();
        List<String> errores = new java.util.ArrayList<>();
        JsonNode errs = root.path("errores");
        if (errs.isArray()) for (JsonNode e : errs) errores.add(e.asString());
        return new EstadoProceso(estado, errores);
    } catch (Exception ex) {
        return new EstadoProceso("", List.of());
    }
}

// Mapeo del proceso FINALIZADO a resultado de canal.
static ExportCanalResultDTO mapearResultadoProceso(EstadoProceso e, int cantidadProductos) {
    if (!e.errores().isEmpty()) {
        return new ExportCanalResultDTO(0, List.of(), List.of(), List.copyOf(e.errores()), List.of());
    }
    return new ExportCanalResultDTO(cantidadProductos, List.of(), List.of(), List.of(), List.of());
}

static ExportCanalResultDTO resultadoSinConfirmar(int idProceso) {
    return new ExportCanalResultDTO(0, List.of(), List.of(), List.of(),
            List.of("encolado sin confirmar (proceso #" + idProceso + ")"));
}
```
(Nota: `asString()` es Jackson 3, el del proyecto. Si el método del JsonNode fuera `asText()` por la versión, usar el que compile — el proyecto usa Jackson 3 `tools.jackson`.)

- [ ] **Step 4: Polling en `exportarProductosADux` + cambiar la firma a `ExportCanalResultDTO`**

Reemplazar el final de `exportarProductosADux` (hoy: extrae `idProceso` y devuelve `ExportDuxResultDTO(productosJson.size(), idProceso, errores)`). El método ahora devuelve `ExportCanalResultDTO`:
```java
// ... tras obtener `response` del POST y `errores` previos ...
int idProceso = extraerIdProceso(response); // método existente que devuelve idProceso o 0
if (idProceso == 0) {
    errores.add("Dux no devolvió ID de proceso");
    return new ExportCanalResultDTO(0, List.of(), List.of(), List.copyOf(errores), List.of());
}

int cantidad = productosJson.size();
final long TIMEOUT_MS = 30_000;
final long INTERVALO_MS = 5_000;
long deadline = System.currentTimeMillis() + TIMEOUT_MS;
while (System.currentTimeMillis() < deadline) {
    EstadoProceso estado = parsearEstadoProceso(obtenerEstadoProceso(idProceso), objectMapper);
    if (estado.finalizado()) {
        log.info("DUX Export - proceso {} FINALIZADO ({} errores)", idProceso, estado.errores().size());
        return mapearResultadoProceso(estado, cantidad);
    }
    try {
        Thread.sleep(INTERVALO_MS);
    } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
    }
}
log.warn("DUX Export - proceso {} no finalizó en {}ms", idProceso, TIMEOUT_MS);
return resultadoSinConfirmar(idProceso);
```
- Cambiar la firma del método a `public ExportCanalResultDTO exportarProductosADux(List<String> skus)`.
- Los `return` tempranos del método (productos vacíos, error de serialización) también deben devolver `ExportCanalResultDTO` (con `errores` poblado y `creados=0`). Reemplazar cada `new ExportDuxResultDTO(0, 0, errores)` por `new ExportCanalResultDTO(0, List.of(), List.of(), List.copyOf(errores), List.of())`.
- Usar el nombre real del método extractor del `idProceso` (el que hoy hace el `Pattern "ID de proceso"` y devuelve int; en el código actual está inline en el método — extraerlo a un helper `int extraerIdProceso(String response)` si no existe ya, o reusar el existente cerca de la línea ~230).

- [ ] **Step 5: Controller**

En `DuxController` (`exportar-productos`, ~línea 216-221): cambiar el tipo de retorno `ResponseEntity<ExportDuxResultDTO>` → `ResponseEntity<ExportCanalResultDTO>` y el import. El body sigue siendo `duxService.exportarProductosADux(skus)`.

- [ ] **Step 6: Eliminar `ExportDuxResultDTO`**

Borrar `apis/dux/dto/ExportDuxResultDTO.java` (ya nadie lo referencia tras los pasos 4-5). Si `mvn -o test-compile` reporta algún uso restante, ajustarlo.

- [ ] **Step 7: Verificar tests y compilación, commit**

Run: `mvn -o -Dtest=DuxProcesoResultadoTest test` → PASS (4/4).
Run: `mvn -o -q test-compile` → BUILD SUCCESS (sin referencias colgadas a `ExportDuxResultDTO`).
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/dux/ \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/dux/service/DuxProcesoResultadoTest.java
git commit -m "feat(dux): confirmar el alta consultando el proceso (polling) y reportar el resultado real"
```

---

### Task 2: Frontend — usar el resultado real de Dux con `clasificarExport`

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` (`exportarProductosADuxAPI` → `ExportCanalResultDTO`)
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (rama Dux de `ejecutarExportsCanales`)

**Interfaces:**
- Consumes: el endpoint `POST /api/dux/exportar-productos` ahora devuelve `{ creados, actualizados, yaExistian, errores, advertencias }` (igual que Nube/ML).

- [ ] **Step 1: Tipo del wrapper de Dux**

En `productosService.ts`: el tipo `ExportDuxResultDTO` (`{ productosEnviados, idProceso, errores }`) ya no aplica. `exportarProductosADuxAPI` debe devolver el **mismo tipo** que Nube/ML (el de `ExportCanalResultDTO`: `{ creados: number; actualizados: string[]; yaExistian: string[]; errores: string[]; advertencias: string[] }`). Reusar ese tipo si ya está declarado para Nube/ML (p. ej. `ExportCanalResult`); si no, declararlo una vez y usarlo en los tres wrappers. Quitar el tipo `ExportDuxResultDTO` y el comentario sobre `productosEnviados`.

- [ ] **Step 2: Rama Dux en `ejecutarExportsCanales`**

En `ProductoFormModal.tsx`, la rama de Dux hoy hace:
```tsx
const r = await exportarProductosADuxAPI([skuExport]);
if (r.productosEnviados > 0) return { canal: "Dux", estado: "ok", detalle: "subido" };
return { canal: "Dux", estado: "error", detalle: r.errores?.length ? r.errores.join("; ") : "no se envió a Dux" };
```
Reemplazar por el mismo patrón que Nube/ML:
```tsx
const r = await exportarProductosADuxAPI([skuExport]);
return clasificarExport("Dux", r, skuExport);
```
(El `catch` de la rama se mantiene igual.) Así Dux pasa por `clasificarExport`: creado → "Dux: 1 creado"; errores → "Dux: error…"; advertencia (timeout) → "Dux: ⚠ encolado sin confirmar (proceso #N)" como warning.

- [ ] **Step 3: Typecheck y commit**

Run (desde `supermaster-frontend/`): `npx tsc --noEmit` → 0 errores.
```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(front/dux): reflejar el resultado real del proceso de Dux (clasificarExport)"
```

---

## Verificación final
- [ ] Backend: `mvn -o -Dtest=DuxProcesoResultadoTest test` verde + `mvn -o -q test-compile` OK.
- [ ] Frontend: `npx tsc --noEmit` sin errores.
- [ ] **Smoke (usuario):** alta que falle en Dux (p. ej. producto sin marca) → "Dux: error — codigo_marca no encontrado…" (no "subido"); alta válida → "Dux: 1 creado"; si Dux tarda > 30s → "Dux: ⚠ encolado sin confirmar (proceso #N)".

## Notas
- El polling bloquea el hilo de request hasta 30s en el peor caso; aceptable para alta de a 1 producto (el spinner del front cubre la espera). Si en la práctica Dux tarda más, subir `TIMEOUT_MS`.
- El test cubre el parseo y el mapeo (la lógica que decide creado/error/sin-confirmar); el bucle de polling en sí es trivial (loop + `Thread.sleep`) y no se testea su timing para no introducir esperas reales.
