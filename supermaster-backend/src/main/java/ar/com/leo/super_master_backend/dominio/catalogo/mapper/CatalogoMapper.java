package ar.com.leo.super_master_backend.dominio.catalogo.mapper;

import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoCreateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface CatalogoMapper {

    CatalogoDTO toDTO(Catalogo c);

    Catalogo toEntity(CatalogoCreateDTO dto);

    void updateEntityFromDTO(CatalogoUpdateDTO dto, @MappingTarget Catalogo entity);
}