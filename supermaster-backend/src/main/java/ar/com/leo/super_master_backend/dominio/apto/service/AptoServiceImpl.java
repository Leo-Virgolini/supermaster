package ar.com.leo.super_master_backend.dominio.apto.service;

import ar.com.leo.super_master_backend.dominio.apto.dto.AptoCreateDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoPatchDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.apto.entity.Apto;
import ar.com.leo.super_master_backend.dominio.apto.mapper.AptoMapper;
import ar.com.leo.super_master_backend.dominio.apto.repository.AptoRepository;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoAptoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;

@Service
@RequiredArgsConstructor
public class AptoServiceImpl implements AptoService {

    private final AptoRepository repo;
    private final AptoMapper mapper;
    private final ProductoAptoRepository productoAptoRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public AptoDTO obtener(Integer id) {
        Apto entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Apto no encontrado con id: " + id));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AptoDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByNombreContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional
    public AptoDTO crear(AptoCreateDTO dto) {
        Apto entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.APTO, entity.getId(), entity.getNombre(), AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public AptoDTO actualizar(Integer id, AptoUpdateDTO dto) {
        Apto entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Apto no encontrado con id: " + id));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        mapper.updateEntityFromDTO(dto, entity);
        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.APTO, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public AptoDTO patch(Integer id, AptoPatchDTO patchDto) {
        if (!presente(patchDto.getNombre())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        Apto entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Apto no encontrado con id: " + id));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.APTO, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Apto entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Apto no encontrado con id: " + id));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        repo.delete(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.APTO, id, entity.getNombre(), AuditoriaAccion.DELETE, estadoAnterior, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer aptoId) {
        if (!repo.existsById(aptoId)) {
            throw new NotFoundException("Apto no encontrado con id: " + aptoId);
        }
        return productoAptoRepository.findByAptoId(aptoId)
                .stream()
                .map(pa -> productoMapper.toResumenDTO(pa.getProducto()))
                .toList();
    }


    private Map<String, String> capturarSnapshot(Apto entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(entity.getNombre()));
        return snapshot;
    }
}






