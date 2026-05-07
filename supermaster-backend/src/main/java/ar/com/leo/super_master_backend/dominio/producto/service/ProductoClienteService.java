package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoClienteDTO;

import java.util.List;

public interface ProductoClienteService {

    List<ProductoClienteDTO> listar(Integer productoId);

    ProductoClienteDTO agregar(Integer productoId, Integer clienteId);

    void eliminar(Integer productoId, Integer clienteId);
}