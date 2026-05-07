package ar.com.leo.super_master_backend.dominio.common.dto;

import java.util.List;

/**
 * Representa los procesos activos para el endpoint global.
 *
 * <p>Los campos de progreso (total, procesados, exitosos, errores, mensaje) son
 * opcionales: solo los procesos que llevan un contador interno los exponen. Si el
 * proceso no expone progreso, llegan como null y el cliente solo muestra la
 * descripción + tiempo transcurrido.
 */
public record ProcesoActivoDTO(
        boolean activo,
        List<ProcesoItem> procesos
) {
    public record ProcesoItem(
            String proceso,
            String descripcion,
            String usuario,
            String iniciadoEn,
            Integer total,
            Integer procesados,
            Integer exitosos,
            Integer errores,
            String mensaje
    ) {}

    public static ProcesoActivoDTO ninguno() {
        return new ProcesoActivoDTO(false, List.of());
    }

    public static ProcesoActivoDTO de(List<ProcesoItem> procesos) {
        return new ProcesoActivoDTO(!procesos.isEmpty(), procesos);
    }
}
