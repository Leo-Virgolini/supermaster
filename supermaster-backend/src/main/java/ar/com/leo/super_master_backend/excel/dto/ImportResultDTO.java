package ar.com.leo.super_master_backend.excel.dto;

import java.util.List;

public record ImportResultDTO(
        int totalRows,
        int successRows,
        int errorRows,
        List<String> errors,
        String message
) {
    public static ImportResultDTO success(int totalRows, int successRows) {
        return new ImportResultDTO(
                totalRows,
                successRows,
                0,
                List.of(),
                String.format("Importación exitosa: %d de %d filas procesadas correctamente", successRows, totalRows)
        );
    }

    public static ImportResultDTO withErrors(int totalRows, int successRows, int errorRows, List<String> errors) {
        return new ImportResultDTO(
                totalRows,
                successRows,
                errorRows,
                errors,
                String.format("Importación completada con errores: %d exitosas, %d con errores de %d totales", successRows, errorRows, totalRows)
        );
    }
}

