# Rename: unidad de medida → sector de depósito (frontend)

**Date:** 2026-06-20

## Changes applied

- `productosService.ts`: `searchUnidadesMedida` → `searchSectoresDeposito`; endpoint `"unidades-medida"` → `"sectores-deposito"` (labelKey `"codigo"` unchanged).
- `types.ts`: field `unidadMedidaId` → `sectorDepositoId` in both `ProductoDTO` and `ProductoCreateDTO`.
- `page.tsx`:
  - Import updated to `searchSectoresDeposito`.
  - State: `unidadMedidaId`/`setUnidadMedidaId` → `sectorDepositoId`/`setSectorDepositoId`; `unidadMedidaDisplay`/`setUnidadMedidaDisplay` → `sectorDepositoDisplay`/`setSectorDepositoDisplay`.
  - `<AsyncSelect>` label: "Sector de depósito"; `loadOptions`, `value`, `displayValue`, `onChange` all updated; placeholder: "Buscar sector (T1, COMBOS, ...)".
  - Preload on edit (`abrirEdicion`), reset on close (`resetForm`), create payload, and PATCH payload all updated.
  - Stale comment updated.

## Verification

- Re-grep for `unidadMedida|unidad-medida|unidades-medida|unidad de medida` (case-insensitive) across `src/**/*.{ts,tsx}`: **no matches**.
- `npx tsc --noEmit`: **exit 0** (no type errors).
