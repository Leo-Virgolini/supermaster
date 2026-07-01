package ar.com.leo.super_master_backend.dominio.segmento.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoCreateDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.segmento.entity.Segmento;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface SegmentoMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    SegmentoDTO toDTO(Segmento entity);

    // =============================
    // CREATE DTO → ENTITY
    // =============================
    Segmento toEntity(SegmentoCreateDTO dto);

    // =============================
    // UPDATE DTO → ENTITY (PATCH)
    // =============================
    void updateEntityFromDTO(SegmentoUpdateDTO dto, @MappingTarget Segmento entity);
}
