package ar.com.leo.super_master_backend.dominio.clasif_gral.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralCreateDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralUpdateDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface ClasifGralMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    @Mapping(source = "padre.id", target = "padreId")
    @Mapping(target = "nombreCompleto", expression = "java(buildNombreCompleto(entity))")
    ClasifGralDTO toDTO(ClasifGral entity);

    /** Construye "ABUELO > PADRE > HIJO" navegando recursivamente la cadena de padres. */
    default String buildNombreCompleto(ClasifGral c) {
        if (c == null) return null;
        if (c.getPadre() == null) return c.getNombre();
        return buildNombreCompleto(c.getPadre()) + " > " + c.getNombre();
    }

    // =============================
    // CREATE DTO → ENTITY
    // =============================
    @Mapping(target = "padre", expression = "java(dto.padreId() != null ? new ClasifGral(dto.padreId()) : null)")
    ClasifGral toEntity(ClasifGralCreateDTO dto);

    // =============================
    // UPDATE DTO → ENTITY (PATCH)
    // =============================
    @Mapping(target = "padre", expression = "java(dto.padreId() != null ? new ClasifGral(dto.padreId()) : entity.getPadre())")
    void updateEntityFromDTO(ClasifGralUpdateDTO dto, @MappingTarget ClasifGral entity);
}