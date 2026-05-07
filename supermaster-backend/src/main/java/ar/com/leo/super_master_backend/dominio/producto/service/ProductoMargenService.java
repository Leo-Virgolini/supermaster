package ar.com.leo.super_master_backend.dominio.producto.service;

import java.util.Optional;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenPatchDTO;

public interface ProductoMargenService {

    Optional<ProductoMargenDTO> obtener(Integer productoId);

    ProductoMargenDTO guardar(ProductoMargenDTO dto);

    ProductoMargenDTO patch(Integer productoId, ProductoMargenPatchDTO patch);

    void eliminar(Integer productoId);
}

