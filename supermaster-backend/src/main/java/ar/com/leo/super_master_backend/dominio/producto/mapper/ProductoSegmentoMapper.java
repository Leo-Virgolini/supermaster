package ar.com.leo.super_master_backend.dominio.producto.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoSegmentoDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoSegmento;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface ProductoSegmentoMapper {

    @Mapping(source = "producto.id", target = "productoId")
    @Mapping(source = "segmento.id", target = "segmentoId")
    ProductoSegmentoDTO toDTO(ProductoSegmento entity);

    @Mapping(target = "id.productoId", source = "productoId")
    @Mapping(target = "id.segmentoId", source = "segmentoId")
    @Mapping(target = "producto", expression = "java(new Producto(dto.productoId()))")
    @Mapping(target = "segmento", expression = "java(new Segmento(dto.segmentoId()))")
    ProductoSegmento toEntity(ProductoSegmentoDTO dto);
}
