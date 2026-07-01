package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

import java.math.BigDecimal;
import java.util.List;

/** Estado + snapshot read-only de la publicación de un canal. */
public record EstadoCanalDTO(
        boolean publicado,
        String estado,        // ML: "active"/"paused"; Nube: "visible"/"oculta"; null si no aplica
        BigDecimal precio,
        BigDecimal promo,     // precio promocional (Nube: variant.promotional_price); null si no aplica
        Integer stock,
        String peso,          // ej "1.5 kg" / "214 g"
        String dimensiones,   // ej "10 × 20 × 30 cm"
        boolean error,
        Integer imagenes,     // cantidad de imágenes del canal (ML: pictures.size(); Nube: images.size())
        List<String> imagenesUrls  // URLs de las imágenes subidas al canal (ML: pictures[].secure_url; Nube: images[].src); vacío si no aplica
) {
    public static EstadoCanalDTO noPublicado() {
        return new EstadoCanalDTO(false, null, null, null, null, null, null, false, null, List.of());
    }
    public static EstadoCanalDTO ofError() {
        return new EstadoCanalDTO(false, null, null, null, null, null, null, true, null, List.of());
    }
}
