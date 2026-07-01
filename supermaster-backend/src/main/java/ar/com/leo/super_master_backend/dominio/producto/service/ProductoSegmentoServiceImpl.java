package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.segmento.entity.Segmento;
import ar.com.leo.super_master_backend.dominio.segmento.repository.SegmentoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoSegmentoDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoSegmento;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoSegmentoId;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoSegmentoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoSegmentoRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductoSegmentoServiceImpl implements ProductoSegmentoService {

    private final ProductoSegmentoRepository repo;
    private final ProductoSegmentoMapper mapper;
    private final ProductoRepository productoRepository;
    private final SegmentoRepository segmentoRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoSegmentoDTO> listar(Integer productoId) {
        return repo.findByProductoId(productoId)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public ProductoSegmentoDTO agregar(Integer productoId, Integer segmentoId) {
        // Validar que existan
        productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        segmentoRepository.findById(segmentoId)
                .orElseThrow(() -> new NotFoundException("Segmento no encontrado"));

        // Verificar si ya existe
        ProductoSegmentoId id = new ProductoSegmentoId(productoId, segmentoId);
        if (repo.findById(id).isPresent()) {
            throw new ConflictException("La relación Producto-Segmento ya existe");
        }

        ProductoSegmento entity = new ProductoSegmento();

        entity.setId(id);
        entity.setProducto(new Producto(productoId));
        entity.setSegmento(new Segmento(segmentoId));

        repo.save(entity);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRODUCTO_SEGMENTO,
                null,
                "P" + productoId + "-S" + segmentoId,
                AuditoriaAccion.CREATE,
                Map.of(),
                Map.of("productoId", String.valueOf(productoId), "segmentoId", String.valueOf(segmentoId))
        );

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer productoId, Integer segmentoId) {
        ProductoSegmentoId id = new ProductoSegmentoId(productoId, segmentoId);
        if (repo.findById(id).isEmpty()) {
            throw new NotFoundException("Relación Producto-Segmento no existe");
        }
        repo.deleteByProductoIdAndSegmentoId(productoId, segmentoId);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRODUCTO_SEGMENTO,
                null,
                "P" + productoId + "-S" + segmentoId,
                AuditoriaAccion.DELETE,
                Map.of("productoId", String.valueOf(productoId), "segmentoId", String.valueOf(segmentoId)),
                Map.of()
        );
    }

}
