package ar.com.leo.super_master_backend.dominio.orden_compra.dto;

import ar.com.leo.super_master_backend.dominio.orden_compra.entity.EstadoOrdenCompra;

import java.time.LocalDateTime;
import java.util.List;

public record OrdenCompraDTO(
        Integer id,
        Integer proveedorId,
        String proveedorNombre,
        EstadoOrdenCompra estado,
        String observaciones,
        List<OrdenCompraLineaDTO> lineas,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaModificacion
) {
}
