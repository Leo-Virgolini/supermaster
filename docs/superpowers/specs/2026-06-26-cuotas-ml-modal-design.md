# Selector de cuotas para Mercado Libre (modal de producto) — Diseño

**Fecha:** 2026-06-26
**Estado:** Aprobado, listo para plan

## Objetivo

En el modal de alta/edición de producto, el canal **Mercado Libre** gana un select **"Cuota del precio"** (igual que los canales KT Hogar / KT Gastro de Tienda Nube), con **CONTADO (0)** elegido por defecto. La cuota seleccionada se usa para calcular el precio que se publica en ML: aplica el recargo de financiación del plan correspondiente (igual que en Nube).

## Contexto actual

- ML **ya es un canal** (`canales`, nombre `ML`, `id_canal=1`) y **ya tiene 6 planes de cuotas** en `canal_concepto_cuota`: CONTADO (0%), CUOTA PROMOCIONADA (4%), 3 cuotas (8.5%), 6 cuotas (13.2%), 9 cuotas (18.1%), 12 cuotas (22.8%).
- El backend **ya soporta la cuota en el cálculo**: `MercadoLibreService.calcularPrecioFinalParaPublicar(producto, categoryId, int cuotas)` la recibe y la propaga a `calculoPrecioService.calcularPrecioCanalConEnvio(productoId, canalMlId, cuotas, …)`, que aplica el recargo vía `canal_concepto_cuota`. Hoy se la llama con `0` (contado) hardcodeado en **alta** (`crearItemEnMl`) y **edición** (`actualizarItemEnMl`).
- El **frontend NO tiene** selector de cuota para ML: la card de "Sincronizar con Mercado Libre" es solo un checkbox; `exportarProductosAMlAPI(skus)` manda únicamente los SKUs.
- Patrón de referencia (Nube): el modal carga las opciones por canal con `getCuotasPorCanalAPI(canalId)` (opciones `{cuotas, porcentaje, descripcion}`), guarda la elección en estado (`cuotaHogar`/`cuotaGastro`) y la manda en `DestinoNube.cuotas`. El precio precalculado por cuota vive en `producto_canal_precios`; ML, en cambio, calcula on-the-fly (necesita comisión + envío iterativos) vía `calcularPrecioFinalParaPublicar`.

## Decisiones tomadas

1. **Default:** CONTADO (`cuotas = 0`).
2. **Alcance:** solo el modal de producto (alta y edición). `exportarProductosAMlAPI` se usa únicamente ahí. Cualquier otro caller del endpoint de export ML mantiene el comportamiento actual.
3. **No se persiste** la cuota en el producto: se elige al subir, como las de Nube.
4. **La cuota es requerida.** El select arranca en CONTADO (`0`), así que el modal incluye `cuotas` en el body en todos los casos; el backend la usa directamente (validación `@NotNull` → un request sin cuota es 400). El único caller de todo el flujo (`MlExportController → MlExportService → crearItemEnMl/actualizarItemEnMl`) es ese modal — no hay export masivo ni otros clientes, así que no se necesita default ni fallback.
5. No se modifica el motor de cálculo de precios ni otros canales.

## Arquitectura

El cambio es plomería de UI → endpoint → flujo, reemplazando el `0` fijo por la cuota elegida.

### Frontend (`ProductoFormModal.tsx`, `productosService.ts`)

- Estado `cuotaMl` (default `0`) y `cuotasMlOpts: CuotaOpcion[]`.
- En el `useEffect` que hoy carga las cuotas de "KT HOGAR" / "KT GASTRO", agregar la carga del canal **"ML"** con la misma función (`searchCanales("ML")` → `getCuotasPorCanalAPI(canalId)`). Si el canal no trae la cuota `0`, se elige la primera disponible (fallback); el default deseado es `0` (CONTADO).
- Select **"Cuota del precio"** dentro de la card de Mercado Libre, con el MISMO markup/estilo y tooltip que los selects de Nube (etiqueta `etiquetaCuota`, opciones de `cuotasMlOpts`).
- Al exportar, `exportarProductosAMlAPI([skuExport], cuotaMl)` incluye la cuota en el body.

### Backend (`MercadoLibreController`, `MercadoLibreService`)

- El endpoint que hoy dispara el export de ML recibe la cuota en el body (requerida, `@NotNull`; el modal siempre la manda).
- La cuota se propaga por el flujo de export hasta `crearItemEnMl` / `actualizarItemEnMl`, reemplazando los dos `calcularPrecioFinalParaPublicar(producto, categoryId, 0)` por `(producto, categoryId, cuotas)`.
- No cambia `calcularPrecioFinalParaPublicar` (su firma ya acepta `int cuotas`).

## Flujo de datos

Modal (`cuotaMl`, default 0) → `exportarProductosAMlAPI([sku], cuotaMl)` → endpoint export ML (cuota requerida en el body) → `crearItemEnMl` / `actualizarItemEnMl` (reciben la cuota) → `calcularPrecioFinalParaPublicar(producto, categoryId, cuota)` → `calcularPrecioCanalConEnvio(productoId, canalMlId, cuota, …)` → PVP con el recargo del plan (`canal_concepto_cuota`).

## Manejo de errores / edge cases

- **Plan inexistente para la cuota elegida:** `obtenerPorcentajeCuota` ya devuelve `0%` (sin recargo) si no encuentra el registro — comportamiento seguro, igual que hoy.
- **Canal "ML" no encontrado al cargar opciones (front):** el select cae a una opción por defecto (CONTADO) y el export sigue funcionando con `cuotaMl = 0`.
- **Request sin cuota:** validación `@NotNull` → 400 con mensaje claro (no debería ocurrir; el modal siempre la manda).

## Testing (offline, sin llamar a la API real de ML)

- Un test que verifique que el flujo de export ML **propaga la cuota recibida** a `calcularPrecioFinalParaPublicar` (en vez de forzar `0`) — usando el patrón de los `*Core`/tests existentes de ML, sin red.
- El cálculo del recargo por cuota ya está cubierto por los tests del motor de precios; no se re-testea.
- Frontend: `npx tsc --noEmit` limpio; sin tests nuevos (cambio de UI/cableado).

## Fuera de alcance (YAGNI)

- Persistir la cuota de ML en el producto.
- Selector de cuota en exports masivos o en otras pantallas (no existen para ML).
- Cambios al motor de cálculo de precios o a `canal_concepto_cuota`.
