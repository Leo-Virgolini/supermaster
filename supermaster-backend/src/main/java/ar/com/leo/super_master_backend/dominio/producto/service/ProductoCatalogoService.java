package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCatalogoDTO;

import java.util.List;

public interface ProductoCatalogoService {

    List<ProductoCatalogoDTO> listar(Integer productoId);

    ProductoCatalogoDTO agregar(Integer productoId, Integer catalogoId);

    void eliminar(Integer productoId, Integer catalogoId);
}