package ar.com.leo.super_master_backend.dominio.tipo.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoCreateDTO;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoDTO;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import org.mapstruct.*;

@Mapper(config = GlobalMapperConfig.class)
public interface TipoMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    @Mapping(source = "padre.id", target = "padreId")
    TipoDTO toDTO(Tipo entity);

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