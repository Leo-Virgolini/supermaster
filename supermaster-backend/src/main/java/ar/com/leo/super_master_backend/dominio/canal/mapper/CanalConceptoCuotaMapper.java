package ar.com.leo.super_master_backend.dominio.canal.mapper;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoCuota;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(config = GlobalMapperConfig.class)
public interface CanalConceptoCuotaMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    @Mapping(source = "canal.id", target = "canalId")
    CanalConceptoCuotaDTO toDTO(CanalConceptoCuota entity);

    // =============================
    // CREATE DTO → ENTITY
    // =============================
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "canalId", target = "canal", qualifiedByName = "canalFromId")
    CanalConceptoCuota toEntity(CanalConceptoCuotaCreateDTO dto);

    // =============================
    // UPDATE DTO → ENTITY (PATCH)
    // =============================
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "canal", ignore = true)
    void updateEntityFromDTO(CanalConceptoCuotaUpdateDTO dto, @MappingTarget CanalConceptoCuota entity);

    // =============================
    // MÉTODOS DE CONVERSIÓN
    // =============================
    @Named("canalFromId")
    default Canal canalFromId(Integer id) {
        return id != null ? new Canal(id) : null;
    }
}

