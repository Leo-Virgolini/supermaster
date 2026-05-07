package ar.com.leo.super_master_backend.dominio.precio_inflado.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoCreateDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.PrecioInflado;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface PrecioInfladoMapper {

    PrecioInfladoDTO toDTO(PrecioInflado entity);

    PrecioInflado toEntity(PrecioInfladoCreateDTO dto);

    void updateEntityFromDTO(PrecioInfladoUpdateDTO dto, @MappingTarget PrecioInflado entity);
}
