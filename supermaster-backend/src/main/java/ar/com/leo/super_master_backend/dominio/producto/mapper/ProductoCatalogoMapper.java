package ar.com.leo.super_master_backend.dominio.producto.mapper;

import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCatalogoDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCatalogo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface ProductoCatalogoMapper {

    @Mapping(source = "producto.id", target = "productoId")
    @Mapping(source = "catalogo.id", target = "catalogoId")
    ProductoCatalogoDTO toDTO(ProductoCatalogo entity);

    @Mapping(target = "id.productoId", source = "productoId")
    @Mapping(target = "id.catalogoId", source = "catalogoId")
    @Mapping(target = "producto", expression = "java(new Producto(dto.productoId()))")
    @Mapping(target = "catalogo", expression = "java(new Catalogo(dto.catalogoId()))")
    ProductoCatalogo toEntity(ProductoCatalogoDTO dto);
}