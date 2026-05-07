package ar.com.leo.super_master_backend.dominio.orden_compra.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.orden_compra.dto.OrdenCompraDTO;
import ar.com.leo.super_master_backend.dominio.orden_compra.dto.OrdenCompraLineaDTO;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.OrdenCompra;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.OrdenCompraLinea;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = GlobalMapperConfig.class)
public interface OrdenCompraMapper {

    @Mapping(source = "proveedor.id", target = "proveedorId")
    @Mapping(source = "proveedor.apodo", target = "proveedorNombre")
    OrdenCompraDTO toDTO(OrdenCompra entity);

    @Mapping(source = "producto.id", target = "productoId")
    @Mapping(source = "producto.sku", target = "productoSku")
    @Mapping(source = "producto.descripcion", target = "productoDescripcion")
    OrdenCompraLineaDTO toLineaDTO(OrdenCompraLinea entity);

    List<OrdenCompraLineaDTO> toLineaDTOList(List<OrdenCompraLinea> entities);
}
