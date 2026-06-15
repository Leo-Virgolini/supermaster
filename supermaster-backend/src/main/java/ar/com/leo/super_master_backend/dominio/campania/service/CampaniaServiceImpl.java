package ar.com.leo.super_master_backend.dominio.campania.service;

import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaDTO;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaProductoDTO;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.campania.entity.Campania;
import ar.com.leo.super_master_backend.dominio.campania.entity.CampaniaProducto;
import ar.com.leo.super_master_backend.dominio.campania.mapper.CampaniaMapper;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaProductoRepository;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CampaniaServiceImpl implements CampaniaService {

    private final CampaniaRepository repository;
    private final CampaniaProductoRepository productoRepository;
    private final CampaniaMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Page<CampaniaDTO> listar(String search, Pageable pageable) {
        Page<Campania> page = (search != null && !search.isBlank())
                ? repository.findByNombreContainingIgnoreCase(search, pageable)
                : repository.findAll(pageable);
        return page.map(this::toDTOConConteo);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaniaDTO obtenerPorId(Integer id) {
        Campania campania = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Campaña no encontrada con ID: " + id));
        return toDTOConConteo(campania);
    }

    @Override
    @Transactional
    public CampaniaDTO actualizar(Integer id, CampaniaUpdateDTO dto) {
        Campania campania = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Campaña no encontrada con ID: " + id));
        // Solo se editan vigencia, estado y observaciones; nombre y canal vienen de TN.
        // Null = no se modifica (PATCH parcial).
        if (dto.fechaDesde() != null) campania.setFechaDesde(dto.fechaDesde());
        if (dto.fechaHasta() != null) campania.setFechaHasta(dto.fechaHasta());
        if (dto.activa() != null) campania.setActiva(dto.activa());
        if (dto.observaciones() != null) campania.setObservaciones(dto.observaciones());
        campania = repository.save(campania);
        return toDTOConConteo(campania);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CampaniaProductoDTO> listarProductos(Integer campaniaId, Pageable pageable) {
        if (!repository.existsById(campaniaId)) {
            throw new NotFoundException("Campaña no encontrada con ID: " + campaniaId);
        }
        return productoRepository.findByCampaniaId(campaniaId, pageable).map(mapper::toProductoDTO);
    }

    @Override
    @Transactional
    public CampaniaProductoDTO actualizarPrecio(Integer campaniaProductoId, BigDecimal precioManual) {
        CampaniaProducto cp = productoRepository.findById(campaniaProductoId)
                .orElseThrow(() -> new NotFoundException(
                        "Producto de campaña no encontrado con ID: " + campaniaProductoId));
        cp.setPrecioManual(precioManual);
        cp = productoRepository.save(cp);
        return mapper.toProductoDTO(cp);
    }

    private CampaniaDTO toDTOConConteo(Campania campania) {
        CampaniaDTO base = mapper.toDTO(campania);
        long cantidad = productoRepository.countByCampaniaId(campania.getId());
        return new CampaniaDTO(
                base.id(), base.tnCategoriaId(), base.nombre(), base.canalId(), base.canalNombre(),
                base.fechaDesde(), base.fechaHasta(), base.activa(), base.fechaUltimaSync(),
                base.observaciones(), cantidad);
    }
}
