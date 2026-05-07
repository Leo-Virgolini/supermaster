package ar.com.leo.super_master_backend.dominio.common.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Estado de los recálculos pendientes pendientes en el sistema.
 *
 * El sistema ya no dispara recálculos automáticos cuando se modifican entidades que
 * afectan el cálculo de precio (conceptos, reglas, cuotas, márgenes, etc.). En su
 * lugar registra el cambio como "pendiente" y el usuario lo aplica manualmente con
 * un solo click desde el banner global de la UI.
 */
public record RecalculoPendienteDTO(
        boolean pendiente,
        // Cantidad refleja la cardinalidad del scope (productos+canales únicos),
        // NO el número de calls. Un mismo producto editado 3 veces = 1.
        int cantidad,
        // Detalle del scope para que el frontend pueda mostrar "1 producto",
        // "2 canales", "Recálculo masivo", etc.
        boolean recalcularTodo,
        int productosCount,
        int canalesCount,
        // IDs específicos en el scope (para debug y visibilidad).
        List<Integer> productoIds,
        List<Integer> canalIds,
        LocalDateTime ultimaModificacion,
        List<MotivoPendiente> motivos
) {
    public static RecalculoPendienteDTO vacio() {
        return new RecalculoPendienteDTO(false, 0, false, 0, 0, List.of(), List.of(), null, List.of());
    }

    public record MotivoPendiente(String motivo, int cantidad, LocalDateTime ultimoCambio) {}
}
