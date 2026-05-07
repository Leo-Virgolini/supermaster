package ar.com.leo.super_master_backend.dominio.orden_compra.service;

import ar.com.leo.super_master_backend.dominio.orden_compra.dto.*;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.EstadoOrdenCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Map;

public interface OrdenCompraService {

    Page<OrdenCompraDTO> listar(Pageable pageable, Integer proveedorId, EstadoOrdenCompra estado);

    OrdenCompraDTO obtener(Integer id);

    OrdenCompraDTO crear(OrdenCompraCreateDTO dto);

    OrdenCompraDTO actualizar(Integer id, OrdenCompraUpdateDTO dto);

    OrdenCompraDTO patch(Integer id, OrdenCompraPatchDTO patch);

    void eliminar(Integer id);

    OrdenCompraDTO enviar(Integer id);

    OrdenCompraDTO registrarRecepcion(Integer id, RecepcionDTO dto);

    Map<Integer, Integer> obtenerPendientesPorProducto();

    Map<Integer, UltimaCompra> obtenerUltimaCompraPorProducto();

    record UltimaCompra(LocalDateTime fecha, int cantidad) {}
}

