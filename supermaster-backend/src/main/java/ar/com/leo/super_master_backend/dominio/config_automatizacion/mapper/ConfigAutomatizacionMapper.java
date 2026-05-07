package ar.com.leo.super_master_backend.dominio.config_automatizacion.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionCreateDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.entity.ConfigAutomatizacion;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface ConfigAutomatizacionMapper {

    ConfigAutomatizacionDTO toDTO(ConfigAutomatizacion entity);

    ConfigAutomatizacion toEntity(ConfigAutomatizacionCreateDTO dto);

    void updateEntityFromDTO(ConfigAutomatizacionUpdateDTO dto, @MappingTarget ConfigAutomatizacion entity);
}
