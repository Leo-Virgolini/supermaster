# Mejoras en creación/edición de producto — Diseño

**Fecha:** 2026-06-16
**Estado:** Aprobado

## Objetivo

Conjunto de correcciones y mejoras al formulario de alta/edición de producto (modal en
`supermaster-frontend/src/app/productos/page.tsx`) y su backend, para hacer el flujo más
correcto, limpio y unificado.

## Decisiones tomadas (brainstorming)

| Tema | Decisión |
|---|---|
| Canales de venta (nuevos checkboxes) | **Solo visuales** (placeholders), sin persistencia por ahora. |
| Validaciones obligatorias | En **frontend + backend**. |
| Alcance de obligatoriedad | Al **crear y editar**. |
| Recálculo al crear | **Marcar pendiente** (mismo flujo que al editar). |
| Imagen ↔ SKU | La imagen **se llama igual que el SKU**. |
| Margen % obligatorio | **Al menos uno** (minorista o mayorista) > 0. |

## Contexto del sistema (relevante)

- El form es un **modal unificado** (crear/editar) en `productos/page.tsx` con 9 secciones.
- Al **crear**, el front hace dos llamadas: `createProductoAPI` y luego `updateProductoMargenAPI`
  (que es un **upsert**: si no existe el margen, lo crea). El upsert del margen **ya marca el
  producto para recálculo pendiente** (detecta margen null→valor en `ProductoMargenServiceImpl`).
- `ProductoServiceImpl.crear()` hoy **no** marca recálculo; `actualizar`/`patch` sí.
- **Margen fijo** (`margenFijoMinorista`/`margenFijoMayorista`) se usa en: entidad `ProductoMargen`,
  `ProductoMargenDTO`/`ProductoMargenPatchDTO`, `ProductoDTO`, `SimulacionPrecioInputDTO`,
  `ProductoMargenServiceImpl` (captura/auditoría/recálculo), `ProductoMargenController`, y
  `CalculoPrecioServiceImpl` (lo suma al PVP, en cálculo e indicadores y en la fórmula paso a paso).
  0 productos lo usan en la BD → quitarlo es seguro.
- Enum `Tag` (`dominio/producto/entity/Tag.java`): `MAQUINA, REPUESTO, MENAJE`. El campo
  `Producto.tag` es nullable.
- `esCombo` (nullable) distingue producto simple (false) de combo (true).
- "Subir a Dux" ya es un checkbox (`subirADux`) que dispara `exportarProductosADuxAPI` al guardar,
  con permiso `INTEGRACIONES_EDITAR`.
- Schema: `ddl-auto=validate` → cambios de columnas requieren script SQL manual en
  `src/main/resources/db/`.

## Alcance por ítem

### A. Cambios de UI puntuales

**A1 — Precio inflado: KT HOGAR por defecto.**
En el form de "agregar precio inflado" (`PreciosInfladosSection.tsx`), el `<select>` de canal
arranca preseleccionado en **KT HOGAR** si está entre los canales libres (los que aún no tienen
precio inflado asignado). Si KT HOGAR ya está asignado, queda en blanco/primer disponible como hoy.

**A2 — Imagen por SKU.**
Convención: el archivo de imagen se nombra igual que el SKU. Al ingresar/confirmar el SKU (onBlur o
debounce), se consulta el repositorio de imágenes (endpoint de listado existente, `search=<sku>`) y,
si hay una imagen cuyo nombre (sin extensión) coincide exactamente con el SKU, se autocompleta
`imagenUrl` y se muestra la preview. Solo autocompleta si el usuario no eligió una imagen a mano.
Se conserva el selector manual (`ImagePickerModal`) como override.

**A6 — Mover "Aptos" a Dimensiones.**
El `MultiAsyncSelect` de Aptos se mueve del bloque "Catálogos, Aptos y Clientes" a la sección
"Dimensiones físicas". El bloque de catálogos/clientes queda sin aptos.

**A7 — Bug del "0" en Costo Base.**
El input de costo arranca **vacío** (string vacío) en alta en vez de `0`, para no tener que borrar
el `0` al tipear. Se valida que el costo sea > 0 (no vacío, no 0). El estado pasa a manejar string
vacío / número. Se aplica el mismo criterio a inputs numéricos donde el `0` inicial moleste (revisar
uxb/moq; mantener su validación `>= 1`).

### B. Validaciones obligatorias (frontend + backend; crear y editar)

**B1 — Margen % obligatorio: al menos uno.**
Front: al guardar, exigir margen minorista **o** mayorista > 0 (si ambos vacíos/0 → error).
Back: en el endpoint del margen (`ProductoMargenServiceImpl`), validar que al menos uno sea > 0
(`BadRequestException` si ambos son 0/null).

**B2 — Producto simple: marca, origen, proveedor, material, tag obligatorios.**
Solo cuando `esCombo=false`. En combos siguen opcionales.
- Front: validación condicional en `validateForm` (si no es combo, exigir los 5).
- Back: validación condicional en el servicio de producto (`crear` y `actualizar`/`patch`), ya que
  los DTOs no pueden expresar "obligatorio solo si esCombo=false" con anotaciones simples. Lanzar
  `BadRequestException` con mensaje claro por campo faltante.

**B3 — Tag: quitar "Sin tag", agregar INSUMO.**
- Enum `Tag`: agregar `INSUMO` → `MAQUINA, REPUESTO, MENAJE, INSUMO`.
- Front: el `<select>` de tag quita la opción "Sin tag" y agrega "Insumo". El tipo TS de tag pasa a
  `"MAQUINA" | "REPUESTO" | "MENAJE" | "INSUMO"` (sin `""`), salvo donde se necesite vacío
  transitorio para combos.
- Tag obligatorio forma parte de B2 (solo simples).
- Nota de datos: productos simples existentes sin tag quedarán inválidos al editarse (deberán
  elegir uno). No se migran datos automáticamente.

### C. Quitar margen fijo (frontend + backend + BD)

Eliminar `margenFijoMinorista` y `margenFijoMayorista` por completo:
- **Entidad** `ProductoMargen`: quitar los dos campos.
- **DTOs**: `ProductoMargenDTO`, `ProductoMargenPatchDTO`, `ProductoDTO`, `SimulacionPrecioInputDTO`.
- **Servicio** `ProductoMargenServiceImpl`: quitar captura de anteriores, set en create, detección de
  cambios para recálculo y snapshot de auditoría de ambos campos.
- **Mapper/Controller**: `ProductoMargenController` (reconstrucción del DTO) y mappers afectados.
- **Cálculo** `CalculoPrecioServiceImpl`: quitar `obtenerMargenFijo()` y sus usos; eliminar el paso
  que suma el margen fijo al PVP, tanto en el cálculo de indicadores como en la fórmula paso a paso
  y en la simulación. Verificar que la suite de cálculo siga verde.
- **Frontend**: quitar los inputs "Margen fijo minorista/mayorista" del form y de la edición inline
  de la tabla de productos; quitar los campos de los tipos TS (`ProductoMargenDTO`, `ProductoDTO`,
  simulador).
- **BD**: script `src/main/resources/db/quitar-margen-fijo.sql` con
  `ALTER TABLE producto_margen DROP COLUMN margen_fijo_minorista, DROP COLUMN margen_fijo_mayorista;`
  (correr manualmente; `ddl-auto=validate`).

### D. Recálculo al crear

`ProductoServiceImpl.crear()` marca el producto recién creado como **pendiente de recálculo**
(usando el mismo `RecalculoPendienteService`/facade que usan `actualizar`/`patch`). Esto deja el
recálculo al crear **explícito** y robusto, además del refuerzo que ya da el upsert del margen
(ahora obligatorio). El cálculo efectivo corre por el flujo de "recálculo pendiente" existente.

### E. Nueva sección "Canales de venta"

Nueva sección/fieldset en el form, llamada **"Canales de venta"** (o "Subir a"), con checkboxes:
- **Subir a DUX**: se **mueve** el checkbox `subirADux` actual aquí (mantiene su comportamiento:
  exporta a DUX al guardar; visible con permiso `INTEGRACIONES_EDITAR`).
- **KT GASTRO**, **KT HOGAR**, **ML**: checkboxes **placeholder** deshabilitados, con tooltip
  "Próximamente" (sin estado persistido ni efecto). Se implementará cada API en una iteración futura.

## Fuera de alcance (YAGNI por ahora)

- Persistencia del estado de los checkboxes KT GASTRO / KT HOGAR / ML (y su lógica de API).
- Migración de datos de productos viejos incompletos (sin marca/tag/etc.).
- Cambiar el contrato de creación para incluir el margen en `ProductoCreateDTO` (se mantiene el flujo
  de dos llamadas: crear producto + upsert margen).

## Testing

- **Backend:**
  - Quitar margen fijo: la suite de cálculo (`CalculoPrecioFormulaTest`, recálculo) debe seguir verde
    sin el componente de margen fijo.
  - Validación de margen "al menos uno": test del servicio de margen (ambos 0 → `BadRequestException`;
    uno > 0 → OK).
  - Validación condicional de producto simple: test del servicio (`esCombo=false` sin marca/origen/
    proveedor/material/tag → `BadRequestException`; combo sin esos campos → OK).
  - Recálculo al crear: verificar que `crear()` marca pendiente.
- **Frontend:** typecheck + build sin errores; verificación manual del form (autocompletado de imagen,
  obligatorios, costo vacío, aptos en dimensiones, KT HOGAR por defecto, sección canales de venta).
