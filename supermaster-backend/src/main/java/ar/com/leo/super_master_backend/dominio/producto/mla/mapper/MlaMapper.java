package ar.com.leo.super_master_backend.dominio.producto.mla.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaCreateDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface MlaMapper {

    MlaDTO toDTO(Mla entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCalculoEnvio", ignore = true)
    @Mapping(target = "productos", ignore = true)
    Mla toEntity(MlaCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCalculoEnvio", ignore = true)
    @Mapping(target = "productos", ignore = true)
    void updateEntity(MlaUpdateDTO dto, @MappingTarget Mla entity);
}