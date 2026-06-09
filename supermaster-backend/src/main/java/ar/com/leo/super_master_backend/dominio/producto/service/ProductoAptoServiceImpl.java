package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.apto.entity.Apto;
import ar.com.leo.super_master_backend.dominio.apto.repository.AptoRepository;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoAptoDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoApto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoAptoId;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoAptoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoAptoRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductoAptoServiceImpl implements ProductoAptoService {

    private final ProductoAptoRepository repo;
    private final ProductoAptoMapper mapper;
    private final ProductoRepository productoRepository;
    private final AptoRepository aptoRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoAptoDTO> listar(Integer productoId) {
        return repo.findByProductoId(productoId)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public ProductoAptoDTO agregar(Integer productoId, Integer aptoId) {
        // Validar que existan
        productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        aptoRepository.findById(aptoId)
                .orElseThrow(() -> new NotFoundException("Apto no encontrado"));

        // Verificar si ya existe
        ProductoAptoId id = new ProductoAptoId(productoId, aptoId);
        if (repo.findById(id).isPresent()) {
            throw new ConflictException("La relación Producto-Apto ya existe");
        }

        ProductoApto entity = new ProductoApto();

        entity.setId(id);
        entity.setProducto(new Producto(productoId));
        entity.setApto(new Apto(aptoId));

        repo.save(entity);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRODUCTO_APTO,
                null,
                "P" + productoId + "-A" + aptoId,
                AuditoriaAccion.CREATE,
                Map.of(),
                Map.of("productoId", String.valueOf(productoId), "aptoId", String.valueOf(aptoId))
        );

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer productoId, Integer aptoId) {
        ProductoAptoId id = new ProductoAptoId(productoId, aptoId);
        if (repo.findById(id).isEmpty()) {
            throw new NotFoundException("Relación Producto-Apto no existe");
        }
        repo.deleteByProductoIdAndAptoId(productoId, aptoId);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRODUCTO_APTO,
                null,
                "P" + productoId + "-A" + aptoId,
                AuditoriaAccion.DELETE,
                Map.of("productoId", String.valueOf(productoId), "aptoId", String.valueOf(aptoId)),
                Map.of()
        );
    }

}
