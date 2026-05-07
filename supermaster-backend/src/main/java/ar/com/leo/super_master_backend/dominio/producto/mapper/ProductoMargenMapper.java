package ar.com.leo.super_master_backend.dominio.producto.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;
import org.mapstruct.*;

@Mapper(config = GlobalMapperConfig.class)
public interface ProductoMargenMapper {

    @Mapping(source = "producto.id", target = "productoId")
    ProductoMargenDTO toDTO(ProductoMargen entity);

    @Mapping(target = "producto", expression = "java(new Producto(dto.productoId()))")
    ProductoMargen toEntity(ProductoMargenDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "producto", ignore = true)
    void updateEntityFromDTO(ProductoMargenDTO dto, @MappingTarget ProductoMargen entity);
}
