package ar.com.leo.super_master_backend.dominio.segmento.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoCreateDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoPatchDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SegmentoService {

    Page<SegmentoDTO> listar(String search, Pageable pageable);

    SegmentoDTO obtener(Integer id);

    SegmentoDTO crear(SegmentoCreateDTO dto);

    SegmentoDTO actualizar(Integer id, SegmentoUpdateDTO dto);

    SegmentoDTO patch(Integer id, SegmentoPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer segmentoId);

    Page<AuditoriaCambioDTO> listarAuditoria(Integer segmentoId, Pageable pageable);
}
