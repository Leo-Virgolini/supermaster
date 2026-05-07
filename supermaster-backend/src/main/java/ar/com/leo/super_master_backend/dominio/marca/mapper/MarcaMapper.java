package ar.com.leo.super_master_backend.dominio.marca.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaCreateDTO;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaDTO;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import org.mapstruct.*;

@Mapper(config = GlobalMapperConfig.class)
public interface MarcaMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    @Mapping(source = "padre.id", target = "padreId")
    MarcaDTO toDTO(Marca entity);

    // =============================
    // CREATE DTO → ENTITY
    // =============================
    @Mapping(target = "padre", expression = "java(dto.padreId() != null ? new Marca(dto.padreId()) : null)")
    Marca toEntity(MarcaCreateDTO dto);

    // =============================
    // UPDATE DTO → ENTITY (PATCH)
    // =============================
    @Mapping(target = "padre", expression = "java(dto.padreId() != null ? new Marca(dto.padreId()) : entity.getPadre())")
    void updateEntityFromDTO(MarcaUpdateDTO dto, @MappingTarget Marca entity);
}