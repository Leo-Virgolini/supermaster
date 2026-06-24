package ar.com.leo.super_master_backend.dominio.clasif_gral.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.apis.dux.dto.DuxRubro;
import ar.com.leo.super_master_backend.apis.dux.dto.DuxSubrubro;
import ar.com.leo.super_master_backend.apis.dux.service.ClasifDuxMatcher;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralCreateDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralPatchDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralUpdateDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gral.mapper.ClasifGralMapper;
import ar.com.leo.super_master_backend.dominio.clasif_gral.repository.ClasifGralRepository;
import ar.com.leo.super_master_backend.dominio.common.dto.SincronizacionDuxResultDTO;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClasifGralServiceImpl implements ClasifGralService {

    private final ClasifGralRepository repo;
    private final ClasifGralMapper mapper;
    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;
    private final DuxService duxService;

    @Override
    @Transactional(readOnly = true)
    public Page<ClasifGralDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByNombreContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ClasifGralDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Clasificación General no encontrada"));
    }

    @Override
    @Transactional
    public ClasifGralDTO crear(ClasifGralCreateDTO dto) {
        ClasifGral entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CLASIF_GRAL, entity.getId(), entity.getNombre(), AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ClasifGralDTO actualizar(Integer id, ClasifGralUpdateDTO dto) {
        ClasifGral entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Clasificación General no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (dto.padreId() != null && dto.padreId().equals(id)) {
            throw new BadRequestException("Una clasificación general no puede pertenecer a sí misma");
        }

        mapper.updateEntityFromDTO(dto, entity);

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CLASIF_GRAL, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ClasifGralDTO patch(Integer id, ClasifGralPatchDTO patchDto) {
        if (!presente(patchDto.getNombre()) && !presente(patchDto.getIdDux()) && !presente(patchDto.getPadreId())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        ClasifGral entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Clasificación General no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (presente(patchDto.getNombre())) {
            entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));
        }
        if (presente(patchDto.getIdDux())) {
            entity.setIdDux(leerIntegerOpcional(patchDto.getIdDux(), "idDux"));
        }
        if (presente(patchDto.getPadreId())) {
            Integer padreId = leerIdOpcional(patchDto.getPadreId(), "padreId");
            if (padreId != null && padreId.equals(id)) {
                throw new BadRequestException("Una clasificación general no puede pertenecer a sí misma");
            }
            entity.setPadre(padreId != null ? new ClasifGral(padreId) : null);
        }

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CLASIF_GRAL, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        ClasifGral entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Clasificación General no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = entity.getNombre();
        repo.delete(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CLASIF_GRAL, id, codigo, AuditoriaAccion.DELETE, estadoAnterior, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer clasifGralId) {
        if (!repo.existsById(clasifGralId)) {
            throw new NotFoundException("Clasificación General no encontrada");
        }
        return productoRepository.findByClasifGralId(clasifGralId)
                .stream()
                .map(productoMapper::toResumenDTO)
                .toList();
    }


    @Override
    @Transactional
    public SincronizacionDuxResultDTO sincronizarDuxIds() {
        List<ClasifGral> entidades = repo.findAll();

        List<ClasifDuxMatcher.ClasifNodo> nodos = new ArrayList<>();
        for (ClasifGral e : entidades) {
            ClasifGral padre = e.getPadre();
            boolean tienePadre = padre != null;
            String nombrePadre = padre != null ? padre.getNombre() : null;
            boolean padreEsRaiz = padre != null && padre.getPadre() == null;
            nodos.add(new ClasifDuxMatcher.ClasifNodo(e.getId(), e.getNombre(), nombrePadre, tienePadre, padreEsRaiz));
        }

        List<DuxRubro> rubros = duxService.obtenerRubros();
        List<DuxSubrubro> subrubros = duxService.obtenerSubrubros();

        Map<Integer, Integer> asign = ClasifDuxMatcher.match(nodos, rubros, subrubros);

        int nivel1 = 0;
        int nivel2 = 0;
        int actualizados = 0;
        int sinMatch = 0;
        List<ClasifGral> modificadas = new ArrayList<>();

        for (ClasifGral e : entidades) {
            ClasifGral padre = e.getPadre();
            boolean tienePadre = padre != null;
            boolean padreEsRaiz = padre != null && padre.getPadre() == null;

            boolean considerada;
            if (!tienePadre) {
                nivel1++;
                considerada = true;
            } else if (padreEsRaiz) {
                nivel2++;
                considerada = true;
            } else {
                considerada = false; // nivel 3+: ignorada
            }

            if (!considerada) continue;

            Integer idDuxNuevo = asign.get(e.getId());
            if (idDuxNuevo == null) {
                sinMatch++;
                continue;
            }

            if (e.getIdDux() == null || !e.getIdDux().equals(idDuxNuevo)) {
                e.setIdDux(idDuxNuevo);
                actualizados++;
                modificadas.add(e);
            }
        }

        repo.saveAll(modificadas);

        log.info("ClasifGral - Sincronización Dux: nivel1={}, nivel2={}, actualizados={}, sinMatch={}",
                nivel1, nivel2, actualizados, sinMatch);

        return new SincronizacionDuxResultDTO(nivel1, nivel2, actualizados, sinMatch);
    }

    private Map<String, String> capturarSnapshot(ClasifGral entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(entity.getNombre()));
        snapshot.put("idDux", normalizar(entity.getIdDux()));
        snapshot.put("padreId", entity.getPadre() != null ? normalizar(entity.getPadre().getId()) : null);
        snapshot.put("padre", entity.getPadre() != null ? normalizar(entity.getPadre().getNombre()) : null);
        return snapshot;
    }
}






