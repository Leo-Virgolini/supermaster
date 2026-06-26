# Diseño: Descripción manual del producto (ML + Nube)

**Fecha:** 2026-06-25
**Estado:** Aprobado

## Problema

Hoy la descripción del producto se **autogenera** en ambos canales: `MlDescripcionBuilder` (texto plano para ML) y `NubeDescripcionBuilder` (HTML para Tienda Nube), a partir de marca/material/dimensiones/aptos. No hay forma de que el usuario escriba una descripción propia.

## Objetivo

Agregar un campo **`descripcion`** editable al producto, persistido en la BD, que se **combina** con el bloque autogenerado: el texto manual va primero y debajo el bloque CARACTERÍSTICAS actual. Aplica a **ML y Nube**.

## Decisiones

| Tema | Decisión |
|------|----------|
| Alcance | ML **y** Nube (un solo texto manual alimenta ambos). |
| Combinación | El texto manual va **primero**; debajo, el bloque CARACTERÍSTICAS autogenerado (sin cambios). |
| Vacío | Si `descripcion` está vacía, la salida es solo el bloque autogenerado (comportamiento actual). |
| Persistencia | Columna nueva `productos.descripcion` (TEXT NULL). |
| Formato ML | **Texto plano**: se le quitan etiquetas HTML al texto manual antes de mandarlo a ML (ML rechaza HTML). |
| Formato Nube | El texto manual se **escapa a HTML** (`& < >`) y los saltos `\n` pasan a `<br>`, dentro de un `<p>`. |

## Cambios

### Backend
- **DB**: script `db/2026-06-25-producto-descripcion.sql` → `ALTER TABLE supermaster.productos ADD COLUMN descripcion TEXT NULL;` (aplicar al MySQL local; `ddl-auto=validate`).
- **Entity**: `Producto.descripcion` (`@Column(columnDefinition = "TEXT")`).
- **DTOs**: `descripcion` en `ProductoDTO`, `ProductoCreateDTO`, `ProductoUpdateDTO`, `ProductoPatchDTO`. MapStruct la mapea por nombre (no requiere `@Mapping`).
- **MlDescripcionBuilder.construir**: si `p.getDescripcion()` no está en blanco, anteponer `plano(descripcion) + "\n\n"` antes de `"CARACTERÍSTICAS\n…"`. `plano(s)` quita etiquetas HTML (`s.replaceAll("<[^>]*>", "")`) y hace trim. Reusado por alta (`crearItemEnMlCore`) y update (`actualizarItemEnMlCore`).
- **NubeDescripcionBuilder.construir**: si hay descripción, anteponer `"<p>" + escape(descripcion).replace("\n","<br>") + "</p>"` antes del bloque `<p><b>CARACTERÍSTICAS</b></p><ul>…`. Reusado por alta y update de Nube.

### Frontend
- `types.ts`: `descripcion` (string | null) en `ProductoDTO` y `ProductoCreateDTO`.
- `ProductoFormModal.tsx`: estado `descripcion`, carga en edición, envío en create/update, y un `<textarea>` "Descripción" en la sección de Identificación (junto a los títulos), con ayuda "Se combina con las características automáticas".

### Tests
- `MlDescripcionBuilderTest`: con descripción manual → empieza con el texto manual (sin HTML) y luego "CARACTERÍSTICAS"; con HTML en el texto → las etiquetas se quitan; sin descripción → igual que hoy.
- `NubeDescripcionBuilderTest`: con descripción manual → el HTML arranca con el `<p>` del texto escapado; sin descripción → igual que hoy.
- `ProductoMlAtributosPersistTest` o test de mapper: round-trip de `descripcion` (opcional; MapStruct + columna).

## Fuera de alcance
- Emojis y otros caracteres que ML rechaza (la doc marca `item.description.type.invalid`): en v1 solo se quita HTML. Sanitizar emojis o hacer best-effort el PUT de descripción queda para después.
- Límite de longitud de la descripción.
