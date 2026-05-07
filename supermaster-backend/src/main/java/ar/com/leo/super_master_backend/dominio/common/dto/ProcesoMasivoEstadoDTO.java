package ar.com.leo.super_master_backend.dominio.common.dto;

import java.time.LocalDateTime;

public record ProcesoMasivoEstadoDTO(
        boolean enEjecucion,
        int total,
        int procesados,
        int exitosos,
        int errores,
        String estado,  // "idle", "ejecutando", "completado", "cancelado"
        LocalDateTime iniciadoEn,
        LocalDateTime finalizadoEn,
        String mensaje,
        String usuario
) {
    /** Constructor sin usuario (compatibilidad con código existente). */
    public ProcesoMasivoEstadoDTO(boolean enEjecucion, int total, int procesados, int exitosos,
                                   int errores, String estado, LocalDateTime iniciadoEn,
                                   LocalDateTime finalizadoEn, String mensaje) {
        this(enEjecucion, total, procesados, exitosos, errores, estado, iniciadoEn, finalizadoEn, mensaje, null);
    }

    public ProcesoMasivoEstadoDTO conUsuario(String usuario) {
        return new ProcesoMasivoEstadoDTO(enEjecucion, total, procesados, exitosos, errores,
                estado, iniciadoEn, finalizadoEn, mensaje, usuario);
    }

    public static ProcesoMasivoEstadoDTO idle() {
        return new ProcesoMasivoEstadoDTO(
                false, 0, 0, 0, 0, "idle", null, null,
                "No hay proceso en ejecución", null);
    }

    public static ProcesoMasivoEstadoDTO iniciado(int total, LocalDateTime iniciadoEn, String usuario) {
        return new ProcesoMasivoEstadoDTO(
                true, total, 0, 0, 0, "ejecutando", iniciadoEn, null,
                "Proceso iniciado", usuario);
    }

    public static ProcesoMasivoEstadoDTO iniciado(int total, LocalDateTime iniciadoEn) {
        return iniciado(total, iniciadoEn, null);
    }
}
