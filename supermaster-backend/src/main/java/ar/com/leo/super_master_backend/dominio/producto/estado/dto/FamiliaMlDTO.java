package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

import java.util.List;

/**
 * Familia de variantes ML de un producto (modelo nuevo User Products).
 * modelo = "NUEVO" cuando el MLA del producto tiene family_id; null si el producto no es de familia.
 * (2b-1: se arma desde la BD por family_id; el enriquecimiento desde ML —valor de eje/stock— es un
 * incremento posterior.)
 */
public record FamiliaMlDTO(
        String modelo,
        String familyId,
        String familyName,
        List<FamiliaVarianteDTO> variantes
) {
    public static FamiliaMlDTO ninguna() {
        return new FamiliaMlDTO(null, null, null, List.of());
    }
}
