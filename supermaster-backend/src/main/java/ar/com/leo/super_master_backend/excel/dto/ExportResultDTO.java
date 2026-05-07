package ar.com.leo.super_master_backend.excel.dto;

import java.util.List;

public record ExportResultDTO(
        byte[] archivo,
        List<String> advertencias,
        String nombreArchivo
) {
    public static ExportResultDTO of(byte[] archivo) {
        return new ExportResultDTO(archivo, List.of(), null);
    }

    public static ExportResultDTO of(byte[] archivo, List<String> advertencias) {
        return new ExportResultDTO(archivo, advertencias, null);
    }

    public static ExportResultDTO of(byte[] archivo, List<String> advertencias, String nombreArchivo) {
        return new ExportResultDTO(archivo, advertencias, nombreArchivo);
    }

    public boolean tieneAdvertencias() {
        return advertencias != null && !advertencias.isEmpty();
    }
}
