package ar.com.leo.super_master_backend.dominio.catalogo.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoCreateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoPatchDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import ar.com.leo.super_master_backend.dominio.catalogo.mapper.CatalogoMapper;
import ar.com.leo.super_master_backend.dominio.catalogo.repository.CatalogoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;
import ar.com.leo.super_master_backend.dominio.regla_descuento.repository.ReglaDescuentoRepository;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCatalogoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CatalogoServiceImpl implements CatalogoService {

    private final CatalogoRepository repo;
    private final CatalogoMapper mapper;
    private final ProductoCatalogoRepository productoCatalogoRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;
    private final ReglaDescuentoRepository reglaDescuentoRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<CatalogoDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByNombreContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Catálogo no encontrado"));
    }

    @Override
    @Transactional
    public CatalogoDTO crear(CatalogoCreateDTO dto) {
        Catalogo entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CATALOGO,
                entity.getId(),
                entity.getNombre(),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(entity)
        );
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public CatalogoDTO actualizar(Integer id, CatalogoUpdateDTO dto) {

        Catalogo entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Catálogo no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        mapper.updateEntityFromDTO(dto, entity);

        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CATALOGO,
                id,
                entity.getNombre(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public CatalogoDTO patch(Integer id, CatalogoPatchDTO patchDto) {
        if (!presente(patchDto.getNombre())
                && !presente(patchDto.getExportarConIva())
                && !presente(patchDto.getRecargoPorcentaje())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        Catalogo entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Catálogo no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (presente(patchDto.getNombre())) {
            entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));
        }
        if (presente(patchDto.getExportarConIva())) {
            entity.setExportarConIva(leerBooleanOpcional(patchDto.getExportarConIva(), "exportarConIva"));
        }
        if (presente(patchDto.getRecargoPorcentaje())) {
            // El recargo es un markup sobre el PVP (pvp × (1 + recargo/100)) y puede superar
            // el 100%. Rango [0, 999.999] alineado con la precisión de la columna (6,3).
            BigDecimal recargo = leerDecimalNoNegativoOpcional(patchDto.getRecargoPorcentaje(), "recargoPorcentaje");
            if (recargo != null && recargo.compareTo(new BigDecimal("999.999")) > 0) {
                throw new BadRequestException("El campo 'recargoPorcentaje' debe ser menor o igual a 999.999");
            }
            entity.setRecargoPorcentaje(recargo);
        }

        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CATALOGO,
                id,
                entity.getNombre(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Catalogo entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Catálogo no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (productoCatalogoRepository.existsByCatalogoId(id)) {
            throw new ConflictException("No se puede eliminar porque tiene productos asignados.");
        }
        if (reglaDescuentoRepository.existsByCatalogoId(id)) {
            throw new ConflictException("No se puede eliminar porque tiene reglas de descuento asociadas.");
        }

        repo.delete(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CATALOGO,
                id,
                entity.getNombre(),
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer catalogoId) {
        if (!repo.existsById(catalogoId)) {
            throw new NotFoundException("Catálogo no encontrado");
        }
        return productoCatalogoRepository.findByCatalogoId(catalogoId)
                .stream()
                .map(pc -> productoMapper.toResumenDTO(pc.getProducto()))
                .toList();
    }


    private Map<String, String> capturarSnapshot(Catalogo entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(entity.getNombre()));
        snapshot.put("exportarConIva", normalizar(entity.getExportarConIva()));
        snapshot.put("recargoPorcentaje", normalizar(entity.getRecargoPorcentaje()));
        return snapshot;
    }
}

