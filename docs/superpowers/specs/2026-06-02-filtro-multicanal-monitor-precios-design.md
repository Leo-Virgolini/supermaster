# Filtro multi-canal en el Monitor de Precios

**Fecha:** 2026-06-02
**Estado:** Aprobado (diseño)

## Problema

El filtro "CANAL" del Monitor de Precios permite elegir **un solo** canal o "TODOS".
Se necesita poder seleccionar **uno o más** canales para mostrar simultáneamente,
manteniendo "TODOS" como la opción que muestra todos los canales.

## Objetivo

Convertir el selector de canal de selección única a selección múltiple con checkboxes,
sin romper el comportamiento existente cuando hay un único canal seleccionado.

## Decisiones de diseño (confirmadas con el usuario)

1. **UX del selector:** checkboxes con opción **"TODOS" exclusiva**.
   - Marcar "TODOS" limpia el resto y muestra todos los canales.
   - Marcar un canal específico destilda "TODOS".
   - Destildar todos los canales vuelve a "TODOS".
2. **Cuotas con 2+ canales:** el filtro de Cuotas se **fuerza a "Todas" y se deshabilita**
   (las cuotas son específicas por canal; con varios no hay una descripción única).
   Con 1 solo canal, el filtro de cuotas funciona como hoy.

## Contrato frontend → backend

Según la cantidad de canales seleccionados:

| Selección | Parámetro enviado | Cuotas |
|---|---|---|
| TODOS (o ninguno marcado) | *(sin filtro de canal)* | habilitado |
| 1 canal | `canalId` (single, como hoy) | habilitado |
| 2+ canales | `canalIds` (lista) | forzado a "Todas" |

**Razón:** preservar intacto el caso de 1 canal (descripciones de cuotas por canal,
ordenamiento por ese canal, persistencia de columnas por canal). El backend ya
soporta `canalIds` vía `PrecioSpecifications.canalIds` (`WHERE canal.id IN (...)`).

Enfoques descartados:
- **Mandar siempre `canalIds`:** rompería las descripciones de cuotas y el orden por
  canal en el caso de 1 canal, o requeriría lógica extra para reactivarlos.
- **Nuevo parámetro dedicado:** innecesario, `canalIds` ya existe.

## Cambios por componente

### Frontend

**Estado** (`page.tsx`)
- `selectedCanalId: number | "all" | null` → `selectedCanales: number[]`
  (vacío = TODOS; `null`/no-inicializado se modela con un flag de "ready" como hoy).
- Armado de `filters` (`page.tsx:75-82`):
  - 0 canales → sin `canalId`/`canalIds`.
  - 1 canal → `f.canalId = canales[0]`.
  - 2+ canales → `f.canalIds = canales`; **no** setear `f.cuotas`.

**Selector** (`BadgeSelect` en `MonitorPrecios.tsx:1069`)
- Nuevo modo multi-selección con checkboxes (puede ser una variante del componente
  actual o un componente hermano `BadgeMultiSelect` para no complicar el modo single
  usado por Cuotas).
- "TODOS" exclusivo (ver decisiones).
- El botón muestra los chips de los canales elegidos, o "TODOS" si vacío.

**Interacciones derivadas** (`MonitorPrecios.tsx`)
- **Cuotas:** con 2+ canales, fijar selección en "Todas" y deshabilitar el `BadgeSelect`
  de cuotas. Con 1 canal, comportamiento actual.
- **Filtrado local de filas** (`aplanarParaExport`, `MonitorPrecios.tsx:183-185`):
  `filtro.canal` pasa a aceptar un conjunto de canales — incluir la fila si
  `canalId ∈ conjunto`, o todas si el conjunto está vacío.
- **Persistencia de columnas ocultas/preset** (`storageKey`/`presetStorageKey`,
  `MonitorPrecios.tsx:1158-1159`): key por canal solo cuando hay exactamente 1 canal;
  con multi o TODOS, una key compartida (sufijo `_multi`).

### Backend

Sin cambios de lógica:
- `PrecioSpecifications.canalIds(List)` ya filtra por `IN`.
- El ordenamiento especial (`aplicarOrdenamientoEspecial` / `getComparator`) usa el
  MAX entre los canales del resultado cuando `canalId` es `null` — comportamiento
  correcto para 2+ canales.
- `validarCanalConCuotas(canalId, cuotas)` no valida nada cuando `canalId` es `null`
  y cuotas es `null` (caso multi). OK.
- El parámetro `canalIds` ya está declarado en `PrecioController.java:147`.

Verificación: confirmar que el endpoint `GET /api/precios` responde correctamente al
recibir `canalIds` desde el monitor (filtra solo filas de esos canales).

## Ordenamiento con 2+ canales

Se ordena por el **MAX** del valor de la columna entre los canales seleccionados
presentes en cada producto (comportamiento existente del backend cuando no hay un
`canalId` único). No se introduce lógica nueva de ordenamiento.

## Testing

**Frontend**
- Lógica del selector: "TODOS" exclusivo, marcar/destildar canales, volver a TODOS al
  vaciar.
- Armado de `filters`: 0 canales → sin filtro; 1 → `canalId`; 2+ → `canalIds` y sin
  `cuotas`.
- Cuotas deshabilitadas y en "Todas" con 2+ canales; reactivadas con 1 canal.

**Backend**
- `GET /api/precios?canalIds=...` devuelve solo filas cuyos canales están en la lista.

## Fuera de alcance (YAGNI)

- Unión de cuotas genéricas entre canales (se descartó a favor de forzar "Todas").
- Cambios en el cálculo de precios o en los indicadores.
- Persistencia del set de canales seleccionados entre sesiones (más allá de lo que ya
  exista para el canal único).
