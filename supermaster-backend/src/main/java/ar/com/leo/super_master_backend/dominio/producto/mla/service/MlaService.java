package ar.com.leo.super_master_backend.dominio.producto.mla.service;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MlaService {

    Page<MlaDTO> listar(String search, Pageable pageable);

    MlaDTO obtener(Integer id);

    MlaDTO crear(MlaCreateDTO dto);

    /**
     * Busca en MercadoLibre la publicación asociada al SKU, asegura que exista el
     * registro MLA en la base y calcula/persiste su precio de envío y comisión
     * reutilizando los procesos de ML. Devuelve el MLA resultante.
     * @throws ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException si no hay publicación para ese SKU.
     */
    MlaDTO obtenerOcrearPorSkuDesdeML(String sku);

    MlaDTO actualizar(Integer id, MlaUpdateDTO dto);

    MlaDTO patch(Integer id, MlaPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer mlaId);

    List<MlaTopePromocionDTO> listarTopesPromocion();

    List<MlaTopePromocionDTO> actualizarTopesPromocion(List<MlaTopePromocionDTO> topes);
}

