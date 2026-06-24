# Sincronización de `id_dux` en Clasificaciones (Gral y Gastro) desde Dux

**Goal:** Un botón en las tablas *Clasificaciones Generales* y *Clasif. Gastro* que trae de Dux todos los rubros y subrubros, los compara por nombre con las clasificaciones del sistema y completa/actualiza el `id_dux` de cada una que coincida.

**Arquitectura:** El backend hace todo el trabajo. Un endpoint `POST` por tabla trae rubros/subrubros de Dux (paginando, respetando el rate limit existente), matchea por nombre normalizado contra las clasificaciones (`findAll`), actualiza `id_dux` y devuelve un resumen. El frontend solo dispara la acción, muestra el resumen en un toast y refresca la tabla. La lógica de matcheo se aísla en una clase pura testeable offline.

**Tech Stack:** Spring Boot 4 / Java 25 (backend), Next.js 16 / React 19 / TS (frontend). JSON: Jackson 3 (`tools.jackson`). Cliente Dux existente: `DuxService` + `DuxRetryHandler.get(uri, token)`.

## Decisiones de diseño (cerradas con el usuario)

1. **Nivel por jerarquía de padre:** clasificación sin padre = nivel 1 → se matchea contra `rubro` de Dux. Clasificación con padre = nivel 2 → se matchea contra `sub_rubro`. Clasificaciones de nivel 3+ (padre que a su vez tiene padre) **se ignoran**.
2. **Matcheo de nombres normalizado:** `trim` + minúsculas + colapsar espacios internos a uno solo. `"  Bazar   Cocina "` ≡ `"bazar cocina"`.
3. **Subrubros (nivel 2) requieren coincidencia de padre:** se asigna `id_sub_rubro` solo si `normaliza(nombre) == normaliza(sub_rubro)` **Y** `normaliza(nombre del padre) == normaliza(rubro)`. Evita ambigüedad entre subrubros homónimos bajo distintos rubros.
4. **Sobrescribe:** si hay match por nombre, se setea el `id_dux` aunque ya tuviera un valor distinto.

## Datos de Dux

- `GET /rubros?offset=&limit=` → array de `{ id_rubro: int, rubro: string }`. `limit` máx 50 → paginar.
- `GET /subrubros?offset=&limit=` → array de `{ id_rubro: int, id_sub_rubro: int, rubro: string, sub_rubro: string }`. `limit` máx 50 → paginar.

Ambas se invocan vía `retryHandler.get(uri, tokens.token)` (rate limit y reintentos automáticos) y se parsean con `objectMapper`.

## Componentes

### Backend

**DTOs de Dux** (`apis/dux/dto/`, records):
- `DuxRubro(int idRubro, String rubro)` — `@JsonProperty` para mapear `id_rubro`/`rubro`.
- `DuxSubrubro(int idRubro, int idSubRubro, String rubro, String subRubro)` — `id_rubro`/`id_sub_rubro`/`rubro`/`sub_rubro`.

**DuxService** (nuevos métodos):
- `List<DuxRubro> obtenerRubros()` — pagina `/rubros` (offset += 50) hasta recibir una página vacía; acumula y devuelve todos.
- `List<DuxSubrubro> obtenerSubrubros()` — ídem con `/subrubros`.

**Matcher puro** (`dominio/common/.../ClasifDuxMatcher` o `apis/dux/service/ClasifDuxMatcher`):
- Entrada: `List<ClasifNodo>` (record interno `ClasifNodo(Integer id, String nombre, String nombrePadre, boolean tienePadre, boolean padreEsRaiz)`), `List<DuxRubro>`, `List<DuxSubrubro>`.
  - `nombrePadre` = nombre del padre directo (o null). `tienePadre` distingue nivel 1 vs 2. `padreEsRaiz` indica que el padre NO tiene a su vez padre (es decir, el nodo es exactamente nivel 2). Si `tienePadre && !padreEsRaiz` ⇒ nivel 3+ ⇒ se ignora.
- Salida: `Map<Integer, Integer>` (id de clasificación → id_dux a asignar). Solo incluye los que matchean.
- Reglas:
  - Nivel 1 (`!tienePadre`): buscar `DuxRubro` con `normaliza(rubro) == normaliza(nombre)`. Si existe → `idRubro`.
  - Nivel 2 (`tienePadre && padreEsRaiz`): buscar `DuxSubrubro` con `normaliza(subRubro) == normaliza(nombre)` **y** `normaliza(rubro) == normaliza(nombrePadre)`. Si existe → `idSubRubro`.
  - Nivel 3+: no se agrega al mapa.
- `normaliza(String)`: `s.trim().toLowerCase().replaceAll("\\s+", " ")` (null-safe → "").

**Servicios** (`ClasifGralServiceImpl.sincronizarDuxIds()` y `ClasifGastroServiceImpl.sincronizarDuxIds()`):
1. `repo.findAll()` (dentro de `@Transactional`; el `padre` es LAZY pero se accede en la transacción).
2. Construir `List<ClasifNodo>` resolviendo `nombrePadre`, `tienePadre`, `padreEsRaiz` (padre != null y padre.getPadre() == null).
3. `rubros = duxService.obtenerRubros()`, `subrubros = duxService.obtenerSubrubros()`.
4. `asignaciones = ClasifDuxMatcher.match(nodos, rubros, subrubros)`.
5. Para cada entidad cuyo id esté en `asignaciones` y cuyo `idDux` actual difiera, `setIdDux(...)`; `saveAll(modificados)`.
6. Devolver `SincronizacionDuxResultDTO`.

**DTO de resultado** (`dominio/common/dto/` o por dominio):
`SincronizacionDuxResultDTO(int nivel1, int nivel2, int actualizados, int sinMatch)`
- `nivel1`/`nivel2`: cantidad de clasificaciones de cada nivel consideradas.
- `actualizados`: cuántas cambiaron su `id_dux`.
- `sinMatch`: nivel 1+2 que no encontraron coincidencia.

**Controllers:**
- `POST /api/clasif-gral/sincronizar-dux` → `SincronizacionDuxResultDTO`, `@PreAuthorize(Permisos.MAESTROS_EDITAR)`.
- `POST /api/clasif-gastro/sincronizar-dux` → ídem.

### Frontend

- En `clasificaciones/page.tsx` y `clasif-gastro/page.tsx`: botón **"Sincronizar id_dux con Dux"** (con ícono, junto a los otros botones del header de la página). Al click:
  - Estado `sincronizando` (deshabilita el botón + spinner).
  - `POST` al endpoint; al volver, toast con el resumen (ej. `"Dux: 12 actualizados, 3 sin coincidencia"`), y `refresh()` de la tabla.
  - Si falla, toast de error con el mensaje.
- Service: `sincronizarDuxIdsAPI()` en cada `xxxService.ts`.

## Data flow

```
[Botón page.tsx] → POST /api/clasif-xxx/sincronizar-dux
  → ServiceImpl.sincronizarDuxIds()
      → repo.findAll() → List<ClasifNodo>
      → DuxService.obtenerRubros() / obtenerSubrubros()  (paginado, rate-limited)
      → ClasifDuxMatcher.match(nodos, rubros, subrubros) → Map<id, idDux>
      → setIdDux + saveAll
      → SincronizacionDuxResultDTO
  → toast(resumen) + refresh()
```

## Error handling

- Falla de Dux (token ausente, red, 429 tras agotar reintentos): el GET propaga excepción → el endpoint responde error → el front muestra toast de error. No se modifica ninguna clasificación si la traída de Dux falla (las llamadas a Dux ocurren antes de cualquier `setIdDux`).
- Nombre sin coincidencia: **no es error**; se cuenta en `sinMatch`.
- Nivel 3+: se ignora silenciosamente (no se cuenta como sinMatch; queda fuera del alcance).

## Testing (todo offline — NO se llama a la API real de Dux)

- **`ClasifDuxMatcherTest`** (unitario puro):
  - Nivel 1 matchea rubro por nombre exacto y normalizado (mayúsculas/espacios).
  - Nivel 2 matchea subrubro solo si coincide nombre **y** rubro del padre.
  - Nivel 2 con nombre de subrubro correcto pero rubro del padre distinto → NO asigna.
  - Nivel 3+ (padre con padre) → ignorado (no aparece en el mapa).
  - Sin coincidencia → no aparece en el mapa.
  - Sobrescritura: el matcher devuelve el id aunque la entidad ya tuviera idDux (la decisión de sobrescribir vive en el service; el matcher siempre propone el match).
- **Parseo de DTOs**: un JSON de ejemplo de `/rubros` y `/subrubros` → `DuxRubro[]`/`DuxSubrubro[]` con `objectMapper` (verifica el mapeo `@JsonProperty`).
- **Frontend**: `npx tsc --noEmit`.

## Constraints

- Trabajar en `main`. Backend: `mvn -o` instalado (no `./mvnw`). Frontend: `npx tsc --noEmit`.
- **NO ejecutar contra la API real de Dux.** El smoke real lo hace el usuario.
- `id_dux` es `Integer` en ambas entidades; `id_rubro`/`id_sub_rubro` de Dux son enteros.
