package ar.com.leo.super_master_backend.excel.dto;

import java.util.List;

/**
 * DTO para el resultado de la importación de costos desde Excel.
 */
public record ImportCostosResultDTO(
        int productosActualizados,
        int productosNoEncontrados,
        int proveedoresCreados,
        List<String> skusNoEncontrados,
        List<String> errores
) {
    public static ImportCostosResultDTO success(
            int actualizados,
            int noEncontrados,
            int proveedoresCreados,
            List<String> skusNoEncontrados
    ) {
        return new ImportCostosResultDTO(
                actualizados,
                noEncontrados,
                proveedoresCreados,
                skusNoEncontrados,
                List.of()
        );
    }

    public static ImportCostosResultDTO withErrors(List<String> errores) {
        return new ImportCostosResultDTO(0, 0, 0, List.of(), errores);
    }
}
