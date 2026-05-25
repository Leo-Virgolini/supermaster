package ar.com.leo.super_master_backend.dominio.producto.mla.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaCreateDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaTopePromocionDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaPatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.mla.mapper.MlaMapper;
import ar.com.leo.super_master_backend.dominio.producto.mla.repository.MlaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MlaServiceImpl implements MlaService {

    private final MlaRepository repo;
    private final MlaMapper mapper;
    private final RecalculoPendienteService recalculoPendienteService;
    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<MlaDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByMlaContainingIgnoreCaseOrMlauContainingIgnoreCase(search, search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable).map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public MlaDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado"));
    }

    @Override
    @Transactional
    public MlaDTO crear(MlaCreateDTO dto) {
        Mla entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.MLA,
                entity.getId(),
                entity.getMla(),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(entity)
        );
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public MlaDTO actualizar(Integer id, MlaUpdateDTO dto) {
        Mla entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        // Guardar valores anteriores para detectar cambios
        BigDecimal precioEnvioAnterior = entity.getPrecioEnvio();
        BigDecimal comisionPorcentajeAnterior = entity.getComisionPorcentaje();

        mapper.updateEntity(dto, entity);

        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.MLA,
                id,
                entity.getMla(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );

        // Solo marcar pendiente si cambió algo que impacta el cálculo de precio.
        // Se marcan SOLO los productos asociados a este MLA (no todos), evitando un
        // recálculo masivo de 5500+ productos cuando puede afectar a 1-50.
        boolean cambioPrecioEnvio = !Objects.equals(precioEnvioAnterior, entity.getPrecioEnvio());
        boolean cambioComision = !Objects.equals(comisionPorcentajeAnterior, entity.getComisionPorcentaje());

        if (cambioPrecioEnvio || cambioComision) {
            marcarProductosDelMla(id, "Cambio en MLA");
        }

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public MlaDTO patch(Integer id, MlaPatchDTO patchDto) {
        if (!presente(patchDto.getMla())
                && !presente(patchDto.getMlau())
                && !presente(patchDto.getPrecioEnvio())
                && !presente(patchDto.getComisionPorcentaje())
                && !presente(patchDto.getTopePromocion())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        Mla entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        BigDecimal precioEnvioAnterior = entity.getPrecioEnvio();
        BigDecimal comisionPorcentajeAnterior = entity.getComisionPorcentaje();

        if (presente(patchDto.getMla())) {
            entity.setMla(leerStringRequerido(patchDto.getMla(), "mla", 20));
        }
        if (presente(patchDto.getMlau())) {
            entity.setMlau(leerStringOpcional(patchDto.getMlau(), "mlau", 20));
        }
        if (presente(patchDto.getPrecioEnvio())) {
            entity.setPrecioEnvio(leerDecimalNoNegativoOpcional(patchDto.getPrecioEnvio(), "precioEnvio"));
        }
        if (presente(patchDto.getComisionPorcentaje())) {
            entity.setComisionPorcentaje(leerDecimalNoNegativoOpcional(patchDto.getComisionPorcentaje(), "comisionPorcentaje"));
        }
        if (presente(patchDto.getTopePromocion())) {
            entity.setTopePromocion(leerIntegerNoNegativoRequerido(patchDto.getTopePromocion(), "topePromocion"));
        }

        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.MLA,
                id,
                entity.getMla(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );

        boolean cambioPrecioEnvio = !Objects.equals(precioEnvioAnterior, entity.getPrecioEnvio());
        boolean cambioComision = !Objects.equals(comisionPorcentajeAnterior, entity.getComisionPorcentaje());

        if (cambioPrecioEnvio || cambioComision) {
            marcarProductosDelMla(id, "Cambio en MLA");
        }

        return mapper.toDTO(entity);
    }

    private void marcarProductosDelMla(Integer mlaId, String motivo) {
        List<Integer> productoIds = productoRepository.findByMlaId(mlaId).stream()
                .map(p -> p.getId())
                .toList();
        recalculoPendienteService.marcarProductos(motivo, productoIds);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Mla entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String mlaCode = entity.getMla();
        repo.delete(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.MLA,
                id,
                mlaCode,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer mlaId) {
        if (!repo.existsById(mlaId)) {
            throw new NotFoundException("MLA no encontrado");
        }
        return productoRepository.findByMlaId(mlaId)
                .stream()
                .map(productoMapper::toResumenDTO)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public List<MlaTopePromocionDTO> listarTopesPromocion() {
        return repo.findByTopePromocionGreaterThan(0).stream()
                .map(m -> new MlaTopePromocionDTO(m.getId(), m.getMla(), m.getTopePromocion()))
                .toList();
    }

    @Override
    @Transactional
    public List<MlaTopePromocionDTO> actualizarTopesPromocion(List<MlaTopePromocionDTO> topes) {
        // Resetear todos los topes existentes a 0
        List<Mla> existentes = repo.findByTopePromocionGreaterThan(0);
        for (Mla m : existentes) {
            m.setTopePromocion(0);
            repo.save(m);
        }

        // Aplicar los nuevos topes
        for (MlaTopePromocionDTO dto : topes) {
            if (dto.mla() == null || dto.mla().isBlank() || dto.topePromocion() == null || dto.topePromocion() <= 0) {
                continue;
            }
            repo.findFirstByMla(dto.mla().trim().toUpperCase()).ifPresent(m -> {
                m.setTopePromocion(dto.topePromocion());
                repo.save(m);
            });
        }

        return listarTopesPromocion();
    }

    private Map<String, String> capturarSnapshot(Mla entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("mla", normalizar(entity.getMla()));
        snapshot.put("mlau", normalizar(entity.getMlau()));
        snapshot.put("precioEnvio", normalizar(entity.getPrecioEnvio()));
        snapshot.put("comisionPorcentaje", normalizar(entity.getComisionPorcentaje()));
        snapshot.put("topePromocion", normalizar(entity.getTopePromocion()));
        return snapshot;
    }
}

