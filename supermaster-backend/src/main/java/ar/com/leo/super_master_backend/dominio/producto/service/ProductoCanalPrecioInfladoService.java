package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCanalPrecioInfladoCreateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCanalPrecioInfladoDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCanalPrecioInfladoUpdateDTO;

import java.util.List;

public interface ProductoCanalPrecioInfladoService {

    List<ProductoCanalPrecioInfladoDTO> listarPorProducto(Integer productoId);

    List<ProductoCanalPrecioInfladoDTO> listarPorCanal(Integer canalId);

    List<ProductoCanalPrecioInfladoDTO> listarActivas();

    ProductoCanalPrecioInfladoDTO obtenerPorId(Integer id);

    ProductoCanalPrecioInfladoDTO obtenerPorProductoYCanal(Integer productoId, Integer canalId);

    ProductoCanalPrecioInfladoDTO crear(ProductoCanalPrecioInfladoCreateDTO dto);

    ProductoCanalPrecioInfladoDTO actualizar(Integer productoId, Integer canalId, ProductoCanalPrecioInfladoUpdateDTO dto);

    void eliminar(Integer productoId, Integer canalId);
}
