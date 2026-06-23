# Confirmar el alta en Dux consultando el estado del proceso

**Fecha:** 2026-06-23
**Estado:** Diseño aprobado, pendiente de plan.

## Objetivo

Al subir un producto a Dux, el front mostraba **"Dux: subido"** aunque el producto **no se creara**. Causa: el `POST /item/nuevoItem` de Dux es **asíncrono** — responde `200` con `"ID de proceso: N"` (la petición se **encola**, no confirma la creación), y la API de test devuelve `200` siempre. El resultado real (éxito o error con motivos) solo se conoce consultando el proceso.

Esta feature **confirma el alta** consultando `GET /obtenerEstadoItem?idProceso=N` (con polling, porque el proceso tarda) y reporta el **resultado real**: creado, error (con motivos), o encolado-sin-confirmar.

## Hechos de la API de Dux (verificados con el usuario)

- `POST /item/nuevoItem` → `200` con texto que incluye `"ID de proceso: N"`. Si **no** hay "ID de proceso" → no se encoló (fallo).
- `GET /obtenerEstadoItem?idProceso=N` → `200` con JSON `{ "estado": String, "errores": [String] }`. `400` ante error de la consulta.
- Valores de `estado` conocidos:
  - **`PENDIENTE`** → el proceso aún se está ejecutando → hay que volver a consultar.
  - **`FINALIZADO`** → terminó. **`errores` ausente/vacío = creado OK**; **`errores` con items = falló** (cada motivo incluye el `cod_item`, p. ej. `"codigo_marca no encontrado para el producto con cod_item : 4276."`).
  - Cualquier `estado` distinto de `FINALIZADO` se trata como "sigue procesando".
- Dux tiene **rate limit** (ya respetado por `DuxRetryHandler`, que throttlea y reintenta 429).

## Decisiones tomadas (brainstorming 2026-06-23)

1. **Polling sincrónico** tras encolar: consultar `obtenerEstadoItem` hasta `FINALIZADO` o timeout. Bloquea el alta (el spinner del form ya cubre la espera).
2. **Tope total 30s**, intervalo **~5s** entre consultas (respeta el rate limit; cada consulta pasa por `DuxRetryHandler`).
3. **`errores` es la señal de éxito/fallo**, no `estado`.
4. **Timeout sin `FINALIZADO`** → **aviso** "encolado sin confirmar (proceso #N)" (ni éxito ni error; el usuario puede revisar en Dux con el idProceso).
5. **Unificar el resultado de Dux** con el patrón de Nube/ML: el export de Dux devuelve el `ExportCanalResultDTO` común y el front usa el **mismo `clasificarExport`** que los otros canales (DRY + 3 desenlaces consistentes).

## Alcance

### Incluye
- Polling del estado del proceso en `DuxService.exportarProductosADux` (también cubre el export masivo: un solo `idProceso` por batch).
- Parseo de `{estado, errores}` y mapeo a creado / error / encolado-sin-confirmar.
- Migrar el resultado del export de Dux a `ExportCanalResultDTO` (creados/errores/advertencias).
- Frontend: que el `ResultadoCanal` de Dux use `clasificarExport` y refleje el resultado real.

### NO incluye
- Polling asíncrono / en background (queda sincrónico con el spinner).
- Reintentar la creación si falló (solo reportar el motivo).
- Cambiar el rate-limit ni el retry HTTP de Dux (ya existen).

## Diseño

### Backend — `DuxService.exportarProductosADux`
Hoy: hace el POST, extrae `idProceso`, y devuelve `ExportDuxResultDTO(productosJson.size(), idProceso, errores)` **siempre** (de ahí el falso positivo).

Nuevo flujo:
1. POST → extraer `idProceso` (ya existe el helper que devuelve `idProceso` o `0`). Si `idProceso == 0` → resultado **error** ("Dux no devolvió ID de proceso").
2. **Polling** (nuevo método privado, p. ej. `esperarResultadoProceso(int idProceso)`):
   - Bucle hasta `deadline = ahora + 30s`:
     - `String json = obtenerEstadoProceso(idProceso)` (ya existe; pasa por el rate-limiter).
     - Parsear con `objectMapper`: `estado` (String), `errores` (List<String>, puede faltar).
     - Si `estado == "FINALIZADO"`: devolver `{finalizado:true, errores}`.
     - Si no: `sleep(~5s)` y reintentar.
   - Si se alcanza el deadline sin `FINALIZADO` → devolver `{finalizado:false}` (timeout).
3. Mapear a `ExportCanalResultDTO`:
   - `finalizado` + `errores` vacío → **creado** (creados = cantidad de productos del batch).
   - `finalizado` + `errores` con items → **errores** = esos motivos; creados = 0 (o los que no aparezcan en errores, si se puede inferir por `cod_item`; MVP: si hay errores, reportarlos y no contar creados).
   - **timeout** → **advertencia** `"encolado sin confirmar (proceso #" + idProceso + ")"`.
4. La firma pública pasa a devolver `ExportCanalResultDTO` (el controller y el front se ajustan).

`sleep` debe ser interrumpible y no romper la transacción; el export de Dux ya corre fuera de una tx larga (revisar que el polling no quede dentro de un `@Transactional`). El bloqueo de hasta 30s es de un hilo de request; aceptable para alta de 1 producto (poco frecuente). Documentarlo.

### Backend — DTO y controller
- El export de Dux deja de usar `ExportDuxResultDTO` y usa `ExportCanalResultDTO` (común, ya existente: `creados`, `actualizados`, `yaExistian`, `errores`, `advertencias`). El `idProceso` ya no va en el DTO; si se quiere conservar, va embebido en la advertencia del timeout.
- Ajustar el endpoint `POST /api/dux/exportar-productos` para devolver `ExportCanalResultDTO`.

### Frontend
- `types.ts` / `productosService.ts`: `exportarProductosADuxAPI` devuelve `ExportCanalResultDTO` (el mismo tipo que Nube/ML).
- `ProductoFormModal.ejecutarExportsCanales`: la rama de Dux deja de armar el resultado a mano (`productosEnviados > 0 ? "subido"`) y usa **`clasificarExport("Dux", r, skuExport)`** como Nube/ML. Resultado:
  - creado → "Dux: 1 creado" (success)
  - errores → "Dux: error — <motivo>" (error)
  - timeout → "Dux: ⚠ encolado sin confirmar (proceso #N)" (warning, vía el `conAvisos` ya implementado)

## Manejo de errores
- `400`/excepción en la consulta del estado → tratar como error de la subida (reportar el mensaje), sin reventar el resto de canales (el wrapper del front ya captura por canal).
- El polling nunca debe colgar más de 30s.

## Pruebas
- **Backend (offline, sin tocar la API real):** test del **parseo + mapeo** del estado del proceso. Como `obtenerEstadoProceso` hace HTTP, extraer la lógica de polling/mapeo a una pieza testeable que reciba un "proveedor de estado" (función `int -> String json`) y un reloj/intervalo inyectables, o testear el mapeo de `{estado, errores}` → `ExportCanalResultDTO` con JSONs de ejemplo:
  - `{"estado":"FINALIZADO"}` → creado.
  - `{"estado":"FINALIZADO","errores":["...cod_item : 4276."]}` → error con ese motivo.
  - `"PENDIENTE"` repetido hasta timeout → advertencia "encolado sin confirmar".
  Usar los JSON reales que pasó el usuario. **No** llamar a Dux real.
- **Frontend:** `npx tsc --noEmit`.
- **Smoke (usuario):** crear un producto que falle en Dux (p. ej. sin marca) → debe mostrar **"Dux: error — codigo_marca no encontrado…"**, no "subido". Y uno válido → "Dux: 1 creado".

## Archivos afectados
**Backend:**
- `apis/dux/service/DuxService.java` (`exportarProductosADux` + método de polling + mapeo).
- DTO: usar `ExportCanalResultDTO` (común); `ExportDuxResultDTO` puede quedar deprecado/eliminado si no se usa en otro lado.
- `apis/dux/controller/...` (endpoint export devuelve el DTO común).
- Test nuevo del mapeo/polling.

**Frontend:**
- `productos/productosService.ts` (`exportarProductosADuxAPI` → `ExportCanalResultDTO`).
- `productos/ProductoFormModal.tsx` (rama Dux de `ejecutarExportsCanales` usa `clasificarExport`).

## Pendiente de validar en smoke (usuario)
- Un alta que falle en Dux reporta el motivo real (no "subido").
- Los tiempos reales de Dux caben en el tope de 30s (si suele tardar más, ajustar el tope).
