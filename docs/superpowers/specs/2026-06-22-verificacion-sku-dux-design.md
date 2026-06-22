# Verificación de SKU en Dux al crear (no pisar un ítem existente)

**Fecha:** 2026-06-22
**Estado:** Diseño aprobado, pendiente de plan.

## Objetivo

El export a Dux es un **upsert ciego** por `cod_item` (`POST /item/nuevoItem`): si el SKU ya existe en Dux, lo **sobrescribe**. A diferencia de Nube y ML (que detectan por SKU/MLA antes de mandar y por eso actualizan en vez de duplicar), Dux no verifica nada. Como un `cod_item` puede existir en Dux **sin** estar en supermaster (la importación Dux→local solo actualiza productos que ya están, nunca crea), al crear un producto con un SKU que ya vive en Dux se lo pisaría sin aviso.

Esta feature **bloquea la creación** de un producto en supermaster si su SKU ya existe en Dux.

## Decisiones tomadas (brainstorming 2026-06-22)

1. **Comportamiento:** **bloquear la creación** (no crear el producto) si el SKU ya existe en Dux. No es "avisar y crear igual" ni "forzar".
2. **Alcance:** se verifica **siempre** al crear, sin importar si el checkbox "Sincronizar con Dux" está tildado (mantiene el SKU único también contra Dux, evita pisar al sincronizar más adelante).
3. **Momento:** al **crear** (en el backend, al procesar el alta) — una sola consulta a Dux por intento de alta. No en vivo mientras se tipea (Dux es lento).
4. **Fail-closed:** si la verificación a Dux **falla** (Dux caído, no configurado, timeout, error de parseo), **se bloquea el alta** con un mensaje distinto ("no se pudo verificar, reintentá") — coherente con que se eligió bloquear para no pisar: si no se puede confirmar que el SKU está libre en Dux, no se arriesga. (Nota: "Dux lento" no es "Dux caído" — si Dux responde, aunque tarde, se verifica igual; el bloqueo por fallo solo ocurre ante error/timeout/no configurado.)
5. **Solo al crear, no al editar:** al editar, el SKU está deshabilitado (no cambia) y el sync debe actualizar el producto. El gate vive únicamente en `crear`.
6. **El modal NO se cierra ante el bloqueo:** si el alta falla (SKU ya existe en Dux, o no se pudo verificar), el modal queda **abierto** con el error visible, para que el usuario corrija el SKU o reintente. Esto **ya es el comportamiento actual** (verificado): `useProductos.createProducto` re-lanza el error tras mostrar el toast, y en `handleCreate` el `resetForm()`/`setIsModalOpen(false)` están dentro del `try`, así que un error salta al `catch` sin cerrar el modal. **No requiere cambios de frontend.**

## Alcance

### Incluye
- **Backend:** en `ProductoServiceImpl.crear`, tras la validación de "SKU duplicado en supermaster", agregar una verificación contra Dux (`DuxService.obtenerProductoPorCodigo(sku)`); si existe → `ConflictException` (409) y no se crea. Fail-open si la consulta falla.

### NO incluye (fuera de alcance)
- Verificación en vivo mientras se tipea el SKU (solo al submit).
- Verificación en la edición.
- Opción de "forzar / sobrescribir igual".
- Verificación contra Nube/ML al crear (ya hacen upsert con detección; no pisan a ciegas).
- Cambios de UI más allá de mostrar el error 409 (el front ya captura y muestra el error del alta).

## Contexto del código existente

- `ProductoServiceImpl.crear(ProductoCreateDTO)` (`dominio/producto/service/ProductoServiceImpl.java:92-107`): valida SKU único en supermaster (`productoRepository.findBySku(...).isPresent()` → `ConflictException`, líneas 96-98), luego mapea/valida/guarda. El nuevo gate va **después** de la validación de supermaster y **antes** de `toEntity`/`save`.
- `DuxService.obtenerProductoPorCodigo(String codItem)` (`apis/dux/service/DuxService.java:406`): `verificarTokens()` + `GET /items?codigoItem=...` → parsea `DuxResponse` → devuelve el primer `Item` o `null` (null si no hay resultados o response null; loguea y devuelve null ante error de parseo; **puede lanzar** si `verificarTokens()` falla por Dux no configurado).
- `ConflictException` → 409 con `{message, path}` (manejado por `GlobalExceptionHandler`). El frontend (`page.tsx` `handleCreate`) ya captura el error de `createProducto` y lo muestra.

## Diseño

### Gate en `crear`

En `ProductoServiceImpl.crear`, después del bloque que valida SKU duplicado en supermaster (línea 98) y antes de `productoMapper.toEntity(dto)`:

```java
verificarSkuLibreEnDux(dto.sku());
```

Método privado helper (fail-closed) — separa el fallo de la consulta (→ "no se pudo verificar") de la existencia del SKU (→ "ya existe"):

```java
/**
 * Bloquea el alta si el SKU ya existe en Dux. Fail-closed: si no se puede verificar
 * (Dux caído/no configurado/timeout), también bloquea, con un mensaje distinto.
 */
private void verificarSkuLibreEnDux(String sku) {
    Item itemDux;
    try {
        itemDux = duxService.obtenerProductoPorCodigo(sku);
    } catch (Exception e) {
        log.warn("No se pudo verificar el SKU '{}' en Dux: {}", sku, e.getMessage());
        throw new ConflictException("No se pudo verificar el SKU en Dux (¿Dux no disponible?). Intentá de nuevo en un momento.");
    }
    if (itemDux != null) {
        throw new ConflictException("El SKU ya existe en Dux: " + sku);
    }
}
```

> Ambos casos usan `ConflictException` (409) para que el frontend los muestre igual (bloquean el alta) — lo que distingue al usuario es el **mensaje**. El plan puede usar una excepción de 503 para el caso "no se pudo verificar" si se considera semánticamente más correcto; el efecto (no crear + mostrar el mensaje) es el mismo.

### Dependencia / ciclo
`ProductoServiceImpl` necesita una referencia a `DuxService`. Verificar en el plan que **no haya ciclo de dependencias** (`DuxService` usa `ProductoRepository`, no `ProductoServiceImpl`, así que en principio no hay ciclo). Si Spring detecta un ciclo al arrancar, resolverlo con `@Lazy` en la inyección de `DuxService` dentro de `ProductoServiceImpl`.

## Manejo de errores
- **SKU existe en Dux** → `ConflictException` (409) "El SKU ya existe en Dux", no se crea.
- **Dux no responde / no configurado / error** → `ConflictException` (409) "No se pudo verificar el SKU en Dux…" (fail-closed): tampoco se crea. Se registra un `log.warn` para diagnóstico.
- El front muestra el `message` del 409 (ya lo hace hoy); el usuario distingue ambos casos por el texto.
- No cambia la transaccionalidad de `crear` (la consulta a Dux es una lectura HTTP previa al `save`).

## Pruebas
- **Backend (unitario, con mocks):** sobre `ProductoServiceImpl.crear` con `duxService` y `productoRepository` mockeados:
  - `duxService.obtenerProductoPorCodigo(sku)` devuelve un `Item` (no null) → `crear` lanza `ConflictException` y NO llama `productoRepository.save`.
  - devuelve `null` → `crear` procede normal (guarda).
  - lanza excepción (Dux caído) → `crear` lanza `ConflictException` ("no se pudo verificar") y NO guarda (fail-closed).
  - (El SKU duplicado en supermaster sigue teniendo prioridad: si ya existe en supermaster, ni se consulta Dux.)
- **Smoke (red):** crear un producto con un SKU que exista en Dux → 409; con uno nuevo → se crea.

## Archivos afectados (resumen)
**Backend:**
- `dominio/producto/service/ProductoServiceImpl.java` — inyectar `DuxService`; gate en `crear` + helper `skuExisteEnDux`.
- Test: `dominio/producto/service/...` (unitario de `crear` con mocks de `duxService`).

**Frontend:** sin cambios (el 409 ya se muestra).

## Pendiente de validar en smoke (usuario)
- Crear un producto con un SKU existente en Dux → bloqueado con "El SKU ya existe en Dux".
- Con Dux caído/no configurado → el alta queda bloqueada con "No se pudo verificar el SKU en Dux…" (fail-closed).
