package ar.com.leo.super_master_backend.dominio.producto.mapper;

import ar.com.leo.super_master_backend.dominio.cliente.entity.Cliente;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoClienteDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCliente;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface ProductoClienteMapper {

    @Mapping(source = "producto.id", target = "productoId")
    @Mapping(source = "cliente.id", target = "clienteId")
    ProductoClienteDTO toDTO(ProductoCliente entity);

    @Mapping(target = "id.productoId", source = "productoId")
    @Mapping(target = "id.clienteId", source = "clienteId")
    @Mapping(target = "producto", expression = "java(new Producto(dto.productoId()))")
    @Mapping(target = "cliente", expression = "java(new Cliente(dto.clienteId()))")
    ProductoCliente toEntity(ProductoClienteDTO dto);
}