package ar.com.leo.super_master_backend.dominio.tipo.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoCreateDTO;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoDTO;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface TipoMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    @Mapping(source = "padre.id", target = "padreId")
    @Mapping(target = "nombreCompleto", expression = "java(buildNombreCompleto(entity))")
    TipoDTO toDTO(Tipo entity);

    /** Construye "ABUELO > PADRE > HIJO" navegando recursivamente la cadena de padres. */
    default String buildNombreCompleto(Tipo t) {
        if (t == null) return null;
        if (t.getPadre() == null) return t.getNombre();
        return buildNombreCompleto(t.getPadre()) + " > " + t.getNombre();
    }

    // =============================
    // CREATE DTO → ENTITY
    // =============================
    @Mapping(target = "padre", expression = "java(dto.padreId() != null ? new Tipo(dto.padreId()) : null)")
    Tipo toEntity(TipoCreateDTO dto);

    // =============================
    // UPDATE DTO → ENTITY (PATCH)
    // =============================
    @Mapping(target = "padre", expression = "java(dto.padreId() != null ? new Tipo(dto.padreId()) : entity.getPadre())")
    void updateEntityFromDTO(TipoUpdateDTO dto, @MappingTarget Tipo entity);
}