package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

import java.util.List;

/**
 * Familia de variantes ML de un producto.
 * modelo = "NUEVO" (User Products, agrupado por family_id) | "LEGACY" (variations[] anidado) | null (no es familia).
 * ejeNombre = nombre del atributo que varía (ej. "Color"), si se pudo inferir.
 */
public record FamiliaMlDTO(
        String modelo,
        String familyId,
        String familyName,
        String ejeAtributoId,
        String ejeNombre,
        List<FamiliaVarianteDTO> variantes
) {
    public static FamiliaMlDTO ninguna() {
        return new FamiliaMlDTO(null, null, null, null, null, List.of());
    }
}
