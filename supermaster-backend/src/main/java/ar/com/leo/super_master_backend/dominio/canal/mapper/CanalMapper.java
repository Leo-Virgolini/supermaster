package ar.com.leo.super_master_backend.dominio.canal.mapper;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import org.mapstruct.*;

@Mapper(config = GlobalMapperConfig.class)
public interface CanalMapper {

    // ================
    // ENTITY → DTO
    // ================
    @Mapping(source = "canalBase.id", target = "canalBaseId")
    CanalDTO toDTO(Canal entity);

    // ================
    // CREATE DTO → ENTITY
    // ================
    @Mapping(target = "canalBase",
            expression = "java(dto.canalBaseId() != null ? new Canal(dto.canalBaseId()) : null)")
    Canal toEntity(CanalCreateDTO dto);

    // ================
    // UPDATE DTO → ENTITY (PATCH)
    // ================
    @Mapping(target = "canalBase",
            expression = "java(dto.canalBaseId() != null ? new Canal(dto.canalBaseId()) : entity.getCanalBase())")
    void updateEntityFromDTO(CanalUpdateDTO dto, @MappingTarget Canal entity);
}