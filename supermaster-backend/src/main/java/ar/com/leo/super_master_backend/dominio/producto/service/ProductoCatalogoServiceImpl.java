package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import ar.com.leo.super_master_backend.dominio.catalogo.repository.CatalogoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCatalogoDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCatalogo;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCatalogoId;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoCatalogoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCatalogoRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductoCatalogoServiceImpl implements ProductoCatalogoService {

    private final ProductoCatalogoRepository repo;
    private final ProductoCatalogoMapper mapper;
    private final ProductoRepository productoRepository;
    private final CatalogoRepository catalogoRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoCatalogoDTO> listar(Integer productoId) {
        return repo.findByProductoId(productoId)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public ProductoCatalogoDTO agregar(Integer productoId, Integer catalogoId) {
        // Validar que existan
        productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        catalogoRepository.findById(catalogoId)
                .orElseThrow(() -> new NotFoundException("Catálogo no encontrado"));

        // Verificar si ya existe
        ProductoCatalogoId id = new ProductoCatalogoId(productoId, catalogoId);
        if (repo.findById(id).isPresent()) {
            throw new ConflictException("La relación Producto-Catálogo ya existe");
        }

        ProductoCatalogo entidad = new ProductoCatalogo();
        entidad.setId(id);
        entidad.setProducto(new Producto(productoId));
        entidad.setCatalogo(new Catalogo(catalogoId));

        repo.save(entidad);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRODUCTO_CATALOGO,
                null,
                "P" + productoId + "-C" + catalogoId,
                AuditoriaAccion.CREATE,
                Map.of(),
                Map.of("productoId", String.valueOf(productoId), "catalogoId", String.valueOf(catalogoId))
        );

        return mapper.toDTO(entidad);
    }

    @Override
    @Transactional
    public void eliminar(Integer productoId, Integer catalogoId) {
        ProductoCatalogoId id = new ProductoCatalogoId(productoId, catalogoId);
        if (repo.findById(id).isEmpty()) {
            throw new NotFoundException("Relación Producto-Catálogo no existe");
        }
        repo.deleteByProductoIdAndCatalogoId(productoId, catalogoId);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRODUCTO_CATALOGO,
                null,
                "P" + productoId + "-C" + catalogoId,
                AuditoriaAccion.DELETE,
                Map.of("productoId", String.valueOf(productoId), "catalogoId", String.valueOf(catalogoId)),
                Map.of()
        );
    }

}
