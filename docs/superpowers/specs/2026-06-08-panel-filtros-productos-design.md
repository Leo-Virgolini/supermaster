# Panel de filtros de Productos

**Fecha:** 2026-06-08
**Estado:** Aprobado (diseño)

## Problema

La tabla de Productos sólo se puede filtrar abriendo el menú contextual de
cada columna (`ColumnContextMenu`) una por una. Filtrar por varias variables a
la vez es tedioso: hay que abrir un header, elegir, cerrar, abrir el siguiente.
Se quiere un **panel unificado** que reúna todas las variables de filtrado en un
solo lugar, con buen UI/UX.

## Decisiones de diseño (confirmadas con el usuario)

1. **Formato:** barra colapsable superior (franja entre la cabecera y la tabla
   que se expande/colapsa).
2. **Alcance:** filtros categóricos + compuesto/estado. NO incluye rangos
   numéricos ni de fecha (queda preparado para sumarlos después).
3. **Convivencia:** el panel convive con el menú contextual por columna; ambos
   editan el mismo estado de filtros.

## Arquitectura

El panel es un componente nuevo y autónomo, `ProductosFilterBar`, ubicado entre
la cabecera y la tabla en `productos/page.tsx`. No toca el backend ni
`useProductos`.

Lee y escribe el **mismo estado `filters`** que ya existe en `page.tsx`, vía
props:

- `filters: Record<string, unknown>` — estado actual de filtros.
- `filterValueLabels` — mapa apiParam → (id → nombre) para mostrar nombres.
- `onChange(apiParam, value, labels?)` — reutiliza la lógica de
  `handleColumnFilterChange` ya existente.
- `onClearAll()` — reutiliza `clearAllFilters`.

Con esto el panel, el menú por columna y los chips de filtros activos quedan
siempre sincronizados, porque los tres editan el mismo objeto `filters`. El
sistema de vistas guardadas queda cubierto sin cambios (ya serializa `filters`
entero).

### Backend

Sin cambios. `ProductoController` ya acepta todos los `@RequestParam`
necesarios: `marcaIds`, `origenIds`, `tipoIds`, `clasifGralIds`,
`clasifGastroIds`, `proveedorIds`, `materialIds`, `aptoIds`, `canalIds`,
`catalogoIds`, `clienteIds`, `mlaIds`, `tags`, `esCombo`, `activo`,
`tagReposicion`.

## Estructura visual

- Franja con botón **"Filtros ▾ (N)"** (N = cantidad de filtros activos) que
  expande/colapsa la grilla. Estado de colapso persistido en `localStorage`
  bajo `productos_filtros_expandido`.
- Grilla responsive (2 columnas en móvil → 4-6 en desktop), agrupada:
  - **Clasificación:** Rubro (clasif. gral), Gastro, Tipo, Material.
  - **Comercial:** Marca, Proveedor, Origen, Catálogo, Apto, Cliente, Canal, MLA.
  - **Atributos:** Compuesto (Todos / Individual / Combo), Estado
    (Todos / Activo / Inactivo), Tag reposición (PRIO / LIQ),
    Tag (Máquina / Repuesto / Menaje).
- Pie del panel: contador "N filtros activos" + botón **"Limpiar todo"**
  (reusa `clearAllFilters`).

## Componentes nuevos

### `MultiSelectFilter` (reutilizable)

Control para variables con muchas opciones (marca, proveedor, etc.).

- Label arriba + un *trigger* que muestra el resumen: nombre si hay 1
  seleccionado, "N sel." si hay varios, placeholder si no hay ninguno.
- Al abrir, despliega un popover con **buscador interno + lista de checkboxes**.
  Carga opciones con el `search*` correspondiente (`searchMarcas`,
  `searchCanales`, etc.).
- Aplica los cambios **al cerrar el popover** (un solo refetch por interacción,
  no uno por click), llamando `onChange(apiParam, ids, labels)`.
- Props: `label`, `apiParam`, `loadOptions`, `value` (ids actuales),
  `valueLabels`, `onChange`.

Resuelve la fricción del `ColumnContextMenu` actual (que carga 9999 de golpe y
obliga a ir header por header), pero la búsqueda interna mantiene la lista
manejable.

### `SegmentedFilter` (para booleanos/enums)

Botones segmentados tipo "Todos · Individual · Combo". Aplicación inmediata
(una sola opción a la vez). Usado para Compuesto (`esCombo`), Estado (`activo`),
Tag reposición (`tagReposicion`) y Tag (`tags`).

## Flujo de datos

```
Usuario toca un control
  → MultiSelectFilter / SegmentedFilter llama onChange(apiParam, value, labels)
  → page.tsx reusa la lógica de handleColumnFilterChange
    (setFilters + setFilterValueLabels + setPageIndex(0))
  → useProductos detecta cambio en filters → refetch
  → chips activos + menú por columna reflejan el nuevo estado
```

Los `labels` (id → nombre) se propagan al estado existente `filterValueLabels`
para que los chips muestren nombres en vez de IDs.

## Fuera de alcance (YAGNI)

- Sin cambios en backend, `useProductos`, columnas ni sistema de vistas.
- Sin rangos numéricos (costo, stock, PVP, IVA) ni de fecha. El layout queda
  preparado para sumarlos en una sección futura.
- El menú contextual por columna se mantiene intacto.

## Testing

- Verificación manual en la app: abrir/cerrar panel, aplicar un filtro
  categórico (p. ej. Marca) y confirmar que la tabla refetcha y aparece el chip
  con el nombre correcto; aplicar un segmentado (Combo) y confirmar; "Limpiar
  todo" deja la tabla sin filtros; persistencia del colapso al recargar;
  sincronización con el menú por columna (filtrar por header se refleja en el
  panel y viceversa).
- Lint/build del frontend (`npm run lint`, `npm run build`) sin errores nuevos.
