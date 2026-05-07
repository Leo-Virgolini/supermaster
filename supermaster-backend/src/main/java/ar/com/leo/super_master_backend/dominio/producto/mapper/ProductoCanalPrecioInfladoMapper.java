package ar.com.leo.super_master_backend.dominio.producto.mapper;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.PrecioInflado;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCanalPrecioInfladoCreateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCanalPrecioInfladoDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCanalPrecioInfladoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecioInflado;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface ProductoCanalPrecioInfladoMapper {

    @Mapping(source = "producto.id", target = "productoId")
    @Mapping(source = "canal.id", target = "canalId")
    @Mapping(source = "precioInflado", target = "precioInflado")
    ProductoCanalPrecioInfladoDTO toDTO(ProductoCanalPrecioInflado entity);

    @Mapping(target = "producto", expression = "java(new Producto(dto.productoId()))")
    @Mapping(target = "canal", expression = "java(new Canal(dto.canalId()))")
    @Mapping(target = "precioInflado", expression = "java(new PrecioInflado(dto.precioInfladoId()))")
    @Mapping(target = "id", ignore = true)
    ProductoCanalPrecioInflado toEntity(ProductoCanalPrecioInfladoCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "producto", ignore = true)
    @Mapping(target = "canal", ignore = true)
    @Mapping(target = "precioInflado", expression = "java(dto.precioInfladoId() != null ? new PrecioInflado(dto.precioInfladoId()) : entity.getPrecioInflado())")
    void updateEntityFromDTO(ProductoCanalPrecioInfladoUpdateDTO dto, @MappingTarget ProductoCanalPrecioInflado entity);
}
