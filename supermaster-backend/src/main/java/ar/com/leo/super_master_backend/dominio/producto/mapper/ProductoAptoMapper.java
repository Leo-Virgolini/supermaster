package ar.com.leo.super_master_backend.dominio.producto.mapper;

import ar.com.leo.super_master_backend.dominio.apto.entity.Apto;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoAptoDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoApto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface ProductoAptoMapper {

    @Mapping(source = "producto.id", target = "productoId")
    @Mapping(source = "apto.id", target = "aptoId")
    ProductoAptoDTO toDTO(ProductoApto entity);

    @Mapping(target = "id.productoId", source = "productoId")
    @Mapping(target = "id.aptoId", source = "aptoId")
    @Mapping(target = "producto", expression = "java(new Producto(dto.productoId()))")
    @Mapping(target = "apto", expression = "java(new Apto(dto.aptoId()))")
    ProductoApto toEntity(ProductoAptoDTO dto);
}