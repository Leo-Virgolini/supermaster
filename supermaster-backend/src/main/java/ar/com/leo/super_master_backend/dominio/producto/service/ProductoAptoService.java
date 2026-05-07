package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoAptoDTO;

import java.util.List;

public interface ProductoAptoService {

    List<ProductoAptoDTO> listar(Integer productoId);

    ProductoAptoDTO agregar(Integer productoId, Integer aptoId);

    void eliminar(Integer productoId, Integer aptoId);
}