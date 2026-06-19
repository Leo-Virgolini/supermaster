# Adjustment B — campo `idDux` en ABM de Proveedores

## Estado: DONE

## Cambios realizados

### 1. `src/app/proveedores/proveedoresService.ts`
- Agregado `idDux?: number | null` a la interfaz `ProveedorDTO`.
- Las funciones `createProveedorAPI` y `updateProveedorAPI` ya reciben `Omit<ProveedorDTO, "id">` / `Partial<Omit<ProveedorDTO, "id">>` respectivamente, por lo que `idDux` se incluye automáticamente sin cambios adicionales en el servicio.

### 2. `src/app/proveedores/columns.tsx`
- Nueva columna `"ID Dux"` (accessorKey `idDux`) insertada entre `leadTimeDias` y `entrega`.
- Usa `EditableCell` con `type="number"` y prop `nullable`, igual que la columna `idDux` en clasificaciones.
- `onSave`: blank/null → `null`; no-blank → `Number(newValue)` con guard `isNaN`.

### 3. `src/app/proveedores/page.tsx`
- Nuevo estado `idDux: number | null` (inicializado en `null`).
- Incluido en la llamada `createProveedor({ ..., idDux, ... })`.
- Reset a `null` en: submit exitoso, `onClose` del modal, botón Cancelar.
- Nuevo `<input type="number">` con label "ID Dux" en el grid 2 columnas del formulario de alta (entre Lead Time y el checkbox de entrega). Patrón `value={idDux ?? ""}` / `onChange` con blank→null / non-blank→Number, idéntico a `leadTimeDias`.

### 4. `useProveedores.ts`
- Sin cambios necesarios: `createProveedor` acepta `Omit<ProveedorDTO,"id">` y `updateProveedor` acepta `Partial<Omit<ProveedorDTO,"id">>`, por lo que `idDux` ya fluye correctamente.

## Verificación TypeScript
- `npx tsc --noEmit` → exit 0, sin errores.

## Patrón seguido
Copia exacta del enfoque de `clasificaciones` (`3c13b3b`) para el campo `idDux` de tipo entero:
- DTO: `idDux?: number | null`
- Columna inline: `EditableCell` con `type="number"` + `nullable` + parse `Number()` con guard `isNaN`
- Formulario de alta: `<input type="number">` con blank→null, reset en close/cancel
