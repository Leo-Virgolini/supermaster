package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoSegmentoDTO;

import java.util.List;

public interface ProductoSegmentoService {

    List<ProductoSegmentoDTO> listar(Integer productoId);

    ProductoSegmentoDTO agregar(Integer productoId, Integer segmentoId);

    void eliminar(Integer productoId, Integer segmentoId);
}
