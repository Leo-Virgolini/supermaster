# Feature C — Completar campos de canal (EAN a Nube, garantía ML)

Fecha: 2026-06-26

## Contexto

Del análisis de los payload builders, el usuario decidió qué tocar y qué dejar. Todo es **backend** (payload builders); no requiere inputs nuevos en el modal.

## Alcance

### C1 — Nube: EAN como `barcode`

Hoy el EAN no se manda a Nube. Cambio: agregar `barcode` al variant ([NubeProductoPayloadBuilder.java:28-43](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java#L28)) con `p.getEan()` cuando sea un EAN válido (reusar la validación `MlItemPayloadBuilder.esGtinValido`). Si no es válido o está vacío, no se agrega.

### C2 — ML: garantía siempre "Sin garantía"

Hoy no se manda garantía. Cambio: incluir en el payload de ML la garantía fija **"Sin garantía"**. Implementación: agregar `WARRANTY_TYPE = "Sin garantía"` (ML usa `sale_terms` para la garantía). Verificar en la implementación si ML lo espera en `sale_terms` (`[{ "id": "WARRANTY_TYPE", "value_name": "Sin garantía" }]`) o como atributo, y usar el formato que la API acepte. Valor fijo, sin input.

## Decisiones de NO cambiar (confirmadas por el usuario)

- **Nube — peso y dimensiones del variant:** quedan HARDCODEADOS como están hoy (`weight "0.050"`, `depth "8.00"`, `width "5.00"`, `height "5.00"`).
- **Nube — stock:** queda vacío (`stock: ""`).
- **`free_shipping`:** se mantiene en `false` (ML y Nube).

## Componentes afectados

- `NubeProductoPayloadBuilder` (C1) — y verificar si la actualización de variant en `TiendaNubeService` (PUT) también debe incluir el `barcode`.
- `MlItemPayloadBuilder` (C2) — agregar la garantía en `construir`.

## Error handling

- C1: EAN inválido se omite (no rompe el alta, mismo criterio que el GTIN de ML).

## Fuera de alcance

- Wiring de peso/dimensiones/stock reales a Nube (se dejan como están).
- Inputs nuevos en el modal.
- `free_shipping` configurable por producto.

## Testing

- **Backend (TDD):** test de `NubeProductoPayloadBuilder` para C1 (barcode con EAN válido presente; ausente si inválido/vacío). Test de `MlItemPayloadBuilder` para C2 (garantía "Sin garantía" presente). Correr con `mvn -o test`.
- Verificar que el `barcode` se incluya tanto en alta como en actualización de Nube si ambos arman el variant.
