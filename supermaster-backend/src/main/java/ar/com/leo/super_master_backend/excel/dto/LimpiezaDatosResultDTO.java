package ar.com.leo.super_master_backend.excel.dto;

import java.util.List;

public record LimpiezaDatosResultDTO(
        List<String> tablasLimpiadas,
        List<String> errores,
        String message
) {
    public static LimpiezaDatosResultDTO success(List<String> tablasLimpiadas) {
        return new LimpiezaDatosResultDTO(
                tablasLimpiadas,
                List.of(),
                String.format("Limpieza exitosa: %d tablas vaciadas y AUTO_INCREMENT reseteado", tablasLimpiadas.size())
        );
    }

    public static LimpiezaDatosResultDTO withErrors(List<String> tablasLimpiadas, List<String> errores) {
        return new LimpiezaDatosResultDTO(
                tablasLimpiadas,
                errores,
                String.format("Limpieza completada con errores: %d tablas vaciadas, %d errores", tablasLimpiadas.size(), errores.size())
        );
    }
}
