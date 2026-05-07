package ar.com.leo.super_master_backend.dominio.apto.mapper;

import ar.com.leo.super_master_backend.dominio.apto.dto.AptoCreateDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.apto.entity.Apto;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface AptoMapper {

    AptoDTO toDTO(Apto entity);

    Apto toEntity(AptoCreateDTO dto);

    void updateEntityFromDTO(AptoUpdateDTO dto, @MappingTarget Apto entity);
}