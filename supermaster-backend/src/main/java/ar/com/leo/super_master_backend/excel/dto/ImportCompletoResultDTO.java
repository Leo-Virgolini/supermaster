package ar.com.leo.super_master_backend.excel.dto;

import java.util.List;
import java.util.Map;

public record ImportCompletoResultDTO(
        int totalHojas,
        int hojasProcesadas,
        int hojasConErrores,
        Map<String, ImportResultDTO> resultadosPorHoja,
        List<String> erroresGenerales,
        Map<String, List<String>> valoresNoEncontrados,
        String message
) {
    public static ImportCompletoResultDTO success(int totalHojas, int hojasProcesadas, Map<String, ImportResultDTO> resultadosPorHoja) {
        return new ImportCompletoResultDTO(
                totalHojas,
                hojasProcesadas,
                0,
                resultadosPorHoja,
                List.of(),
                Map.of(),
                String.format("Importación completa exitosa: %d de %d hojas procesadas correctamente", hojasProcesadas, totalHojas)
        );
    }

    public static ImportCompletoResultDTO success(int totalHojas, int hojasProcesadas,
                                                  Map<String, ImportResultDTO> resultadosPorHoja,
                                                  Map<String, List<String>> valoresNoEncontrados) {
        return new ImportCompletoResultDTO(
                totalHojas,
                hojasProcesadas,
                0,
                resultadosPorHoja,
                List.of(),
                valoresNoEncontrados,
                String.format("Importación completa exitosa: %d de %d hojas procesadas correctamente", hojasProcesadas, totalHojas)
        );
    }

    public static ImportCompletoResultDTO withErrors(
            int totalHojas,
            int hojasProcesadas,
            int hojasConErrores,
            Map<String, ImportResultDTO> resultadosPorHoja,
            List<String> erroresGenerales
    ) {
        return new ImportCompletoResultDTO(
                totalHojas,
                hojasProcesadas,
                hojasConErrores,
                resultadosPorHoja,
                erroresGenerales,
                Map.of(),
                String.format("Importación completada con errores: %d exitosas, %d con errores de %d totales",
                        hojasProcesadas, hojasConErrores, totalHojas)
        );
    }

    public static ImportCompletoResultDTO withErrors(
            int totalHojas,
            int hojasProcesadas,
            int hojasConErrores,
            Map<String, ImportResultDTO> resultadosPorHoja,
            List<String> erroresGenerales,
            Map<String, List<String>> valoresNoEncontrados
    ) {
        return new ImportCompletoResultDTO(
                totalHojas,
                hojasProcesadas,
                hojasConErrores,
                resultadosPorHoja,
                erroresGenerales,
                valoresNoEncontrados,
                String.format("Importación completada con errores: %d exitosas, %d con errores de %d totales",
                        hojasProcesadas, hojasConErrores, totalHojas)
        );
    }
}
