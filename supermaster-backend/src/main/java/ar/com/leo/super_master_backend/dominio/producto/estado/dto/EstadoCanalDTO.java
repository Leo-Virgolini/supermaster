package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

import java.math.BigDecimal;

/** Estado + snapshot read-only de la publicación de un canal. */
public record EstadoCanalDTO(
        boolean publicado,
        String estado,        // ML: "active"/"paused"; Nube: "visible"/"oculta"; null si no aplica
        BigDecimal precio,
        Integer stock,
        String peso,          // ej "1.5 kg" / "214 g"
        String dimensiones,   // ej "10 × 20 × 30 cm"
        boolean error
) {
    public static EstadoCanalDTO noPublicado() {
        return new EstadoCanalDTO(false, null, null, null, null, null, false);
    }
    public static EstadoCanalDTO ofError() {
        return new EstadoCanalDTO(false, null, null, null, null, null, true);
    }
}
