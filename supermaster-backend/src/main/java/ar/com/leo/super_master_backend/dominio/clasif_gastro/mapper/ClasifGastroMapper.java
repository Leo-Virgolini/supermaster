package ar.com.leo.super_master_backend.dominio.clasif_gastro.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroCreateDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroUpdateDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface ClasifGastroMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    @Mapping(source = "padre.id", target = "padreId")
    @Mapping(target = "nombreCompleto", expression = "java(buildNombreCompleto(entity))")
    ClasifGastroDTO toDTO(ClasifGastro entity);

    /** Construye "ABUELO > PADRE > HIJO" navegando recursivamente la cadena de padres. */
    default String buildNombreCompleto(ClasifGastro c) {
        return buildNombreCompleto(c, new java.util.HashSet<>());
    }

    /** Corta ciclos (datos con padre = sí mismo u otro ancestro) para no caer en StackOverflow. */
    private String buildNombreCompleto(ClasifGastro c, java.util.Set<Integer> visitados) {
        if (c == null) return null;
        if (c.getId() != null && !visitados.add(c.getId())) return c.getNombre(); // ciclo: corta
        if (c.getPadre() == null) return c.getNombre();
        return buildNombreCompleto(c.getPadre(), visitados) + " > " + c.getNombre();
    }

    // =============================
    // CREATE DTO → ENTITY
    // =============================
    @Mapping(target = "padre", expression = "java(dto.padreId() != null ? new ClasifGastro(dto.padreId()) : null)")
    ClasifGastro toEntity(ClasifGastroCreateDTO dto);

    // =============================
    // UPDATE DTO → ENTITY (PATCH)
    // =============================
    @Mapping(target = "padre", expression = "java(dto.padreId() != null ? new ClasifGastro(dto.padreId()) : entity.getPadre())")
    void updateEntityFromDTO(ClasifGastroUpdateDTO dto, @MappingTarget ClasifGastro entity);
}