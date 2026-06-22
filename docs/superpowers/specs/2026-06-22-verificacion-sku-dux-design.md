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
4. **Fail-open:** si la verificación a Dux **falla** (Dux caído, no configurado, timeout, error de parseo), **se permite crear** — una caída de Dux no debe bloquear el alta de productos. El bloqueo ocurre SOLO cuando Dux responde afirmativamente que el SKU existe.
5. **Solo al crear, no al editar:** al editar, el SKU está deshabilitado (no cambia) y el sync debe actualizar el producto. El gate vive únicamente en `crear`.

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
if (skuExisteEnDux(dto.sku())) {
    throw new ConflictException("El SKU ya existe en Dux: " + dto.sku());
}
```

Método privado helper (fail-open):

```java
/** True si el SKU ya existe en Dux. Fail-open: si no se puede verificar (Dux caído/no configurado), devuelve false (permite crear). */
private boolean skuExisteEnDux(String sku) {
    try {
        return duxService.obtenerProductoPorCodigo(sku) != null;
    } catch (Exception e) {
        log.warn("No se pudo verificar el SKU '{}' en Dux (se permite crear): {}", sku, e.getMessage());
        return false;
    }
}
```

### Dependencia / ciclo
`ProductoServiceImpl` necesita una referencia a `DuxService`. Verificar en el plan que **no haya ciclo de dependencias** (`DuxService` usa `ProductoRepository`, no `ProductoServiceImpl`, así que en principio no hay ciclo). Si Spring detecta un ciclo al arrancar, resolverlo con `@Lazy` en la inyección de `DuxService` dentro de `ProductoServiceImpl`.

## Manejo de errores
- **SKU existe en Dux** → `ConflictException` (409), no se crea. El front muestra el mensaje.
- **Dux no responde / no configurado / error** → `skuExisteEnDux` devuelve `false` (fail-open): se crea el producto. Se registra un `log.warn` para diagnóstico.
- No cambia la transaccionalidad de `crear` (la consulta a Dux es una lectura HTTP previa al `save`; si lanza, el `try/catch` del helper la contiene).

## Pruebas
- **Backend (unitario, con mocks):** sobre `ProductoServiceImpl.crear` con `duxService` y `productoRepository` mockeados:
  - `duxService.obtenerProductoPorCodigo(sku)` devuelve un `Item` (no null) → `crear` lanza `ConflictException` y NO llama `productoRepository.save`.
  - devuelve `null` → `crear` procede normal (guarda).
  - lanza excepción (Dux caído) → `crear` procede normal (fail-open, guarda).
  - (El SKU duplicado en supermaster sigue teniendo prioridad: si ya existe en supermaster, ni se consulta Dux.)
- **Smoke (red):** crear un producto con un SKU que exista en Dux → 409; con uno nuevo → se crea.

## Archivos afectados (resumen)
**Backend:**
- `dominio/producto/service/ProductoServiceImpl.java` — inyectar `DuxService`; gate en `crear` + helper `skuExisteEnDux`.
- Test: `dominio/producto/service/...` (unitario de `crear` con mocks de `duxService`).

**Frontend:** sin cambios (el 409 ya se muestra).

## Pendiente de validar en smoke (usuario)
- Crear un producto con un SKU existente en Dux → bloqueado con "El SKU ya existe en Dux".
- Con Dux caído/no configurado → el alta funciona igual (fail-open).
