package ar.com.leo.super_master_backend.dominio.automatizacion_precios.dto;

import java.util.List;

public record SincronizacionResultDTO(
        int duxImportActualizados,
        int duxImportTotal,
        int duxImportErrores,
        int envioCalculados,
        int envioErrores,
        int excluidosExitosos,
        int excluidosErrores,
        int duxMlProductos,
        String duxMlEstado,
        int duxGastroProductos,
        String duxGastroEstado,
        int duxNubeProductos,
        String duxNubeEstado,
        int mlActualizados,
        int mlErrores,
        int promoIncluidos,
        int promoErrores,
        int nubeActualizados,
        int nubeErrores,
        List<String> log
) {}
