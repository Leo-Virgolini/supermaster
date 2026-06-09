package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenPatchDTO;

import java.util.Optional;

public interface ProductoMargenService {

    Optional<ProductoMargenDTO> obtener(Integer productoId);

    ProductoMargenDTO guardar(ProductoMargenDTO dto);

    ProductoMargenDTO patch(Integer productoId, ProductoMargenPatchDTO patch);

    void eliminar(Integer productoId);
}

