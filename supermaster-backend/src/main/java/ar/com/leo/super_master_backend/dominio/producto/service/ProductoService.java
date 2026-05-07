package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoConPreciosDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCreateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoFilter;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoPatchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

public interface ProductoService {

    Page<ProductoDTO> listar(Pageable pageable);

    ProductoDTO obtener(Integer id);

    ProductoDTO crear(ProductoCreateDTO dto);

    ProductoDTO actualizar(Integer id, ProductoUpdateDTO dto);

    ProductoDTO patch(Integer id, ProductoPatchDTO patch);

    void eliminar(Integer id);

    ProductoDTO obtenerPorSku(String sku);

    Page<AuditoriaCambioDTO> listarAuditoria(Integer productoId, Pageable pageable);

    Page<ProductoDTO> filtrar(ProductoFilter filter, Pageable pageable);

    Page<ProductoConPreciosDTO> listarConPrecios(ProductoFilter filter, Pageable pageable);

    /**
     * Lista todos los productos con precios sin paginación (para exportación).
     * @param filter Filtros a aplicar
     * @param sort Ordenamiento (puede ser null)
     * @return Lista completa de productos con precios
     */
    List<ProductoConPreciosDTO> listarConPreciosSinPaginar(ProductoFilter filter, Sort sort);

    void actualizarCosto(Integer productoId, BigDecimal nuevoCosto);
}

