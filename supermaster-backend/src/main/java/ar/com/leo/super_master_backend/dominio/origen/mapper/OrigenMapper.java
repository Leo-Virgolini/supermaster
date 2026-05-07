package ar.com.leo.super_master_backend.dominio.origen.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenCreateDTO;
import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenDTO;
import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenUpdateDTO;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface OrigenMapper {

    OrigenDTO toDTO(Origen entity);

    Origen toEntity(OrigenCreateDTO dto);

    void updateEntityFromDTO(OrigenUpdateDTO dto, @MappingTarget Origen entity);
}