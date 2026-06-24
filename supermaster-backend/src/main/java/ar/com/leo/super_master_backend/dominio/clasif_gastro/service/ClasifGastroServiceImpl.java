package ar.com.leo.super_master_backend.dominio.clasif_gastro.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.apis.dux.dto.DuxRubro;
import ar.com.leo.super_master_backend.apis.dux.dto.DuxSubrubro;
import ar.com.leo.super_master_backend.apis.dux.service.ClasifDuxMatcher;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroCreateDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroPatchDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroUpdateDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.mapper.ClasifGastroMapper;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.repository.ClasifGastroRepository;
import ar.com.leo.super_master_backend.dominio.common.dto.SincronizacionDuxResultDTO;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
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
import java.util.Objects;

import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClasifGastroServiceImpl implements ClasifGastroService {

    private final ClasifGastroRepository repo;
    private final ClasifGastroMapper mapper;
    private final RecalculoPendienteService recalculoPendienteService;
    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;
    private final DuxService duxService;

    @Override
    @Transactional(readOnly = true)
    public Page<ClasifGastroDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByNombreContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ClasifGastroDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Clasificación Gastro no encontrada"));
    }

    @Override
    @Transactional
    public ClasifGastroDTO crear(ClasifGastroCreateDTO dto) {
        ClasifGastro entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CLASIF_GASTRO, entity.getId(), entity.getNombre(), AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ClasifGastroDTO actualizar(Integer id, ClasifGastroUpdateDTO dto) {
        ClasifGastro entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Clasificación Gastro no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        Boolean esMaquinaAnterior = entity.getEsMaquina();

        if (dto.padreId() != null && dto.padreId().equals(id)) {
            throw new BadRequestException("Una clasificación gastro no puede pertenecer a sí misma");
        }

        mapper.updateEntityFromDTO(dto, entity);
        repo.save(entity);

        // Si cambió esMaquina, recalcular todos los productos de esta clasificación
        if (dto.esMaquina() != null && !Objects.equals(dto.esMaquina(), esMaquinaAnterior)) {
            // Solo los productos de esta clasificación — no recalcular los 5500+ del catálogo.
            marcarProductosDeClasifGastro(id, "Cambio en clasificación gastro");
        }

        auditoriaService.registrarCambios(AuditoriaEntidad.CLASIF_GASTRO, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ClasifGastroDTO patch(Integer id, ClasifGastroPatchDTO patchDto) {
        if (!presente(patchDto.getNombre())
                && !presente(patchDto.getEsMaquina())
                && !presente(patchDto.getIdDux())
                && !presente(patchDto.getPadreId())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        ClasifGastro entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Clasificación Gastro no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        Boolean esMaquinaAnterior = entity.getEsMaquina();

        if (presente(patchDto.getNombre())) {
            entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));
        }
        if (presente(patchDto.getEsMaquina())) {
            entity.setEsMaquina(leerBooleanOpcional(patchDto.getEsMaquina(), "esMaquina"));
        }
        if (presente(patchDto.getIdDux())) {
            entity.setIdDux(leerIntegerOpcional(patchDto.getIdDux(), "idDux"));
        }
        if (presente(patchDto.getPadreId())) {
            Integer padreId = leerIdOpcional(patchDto.getPadreId(), "padreId");
            if (padreId != null && padreId.equals(id)) {
                throw new BadRequestException("Una clasificación gastro no puede pertenecer a sí misma");
            }
            entity.setPadre(padreId != null ? new ClasifGastro(padreId) : null);
        }

        repo.save(entity);

        if (presente(patchDto.getEsMaquina()) && !Objects.equals(esMaquinaAnterior, entity.getEsMaquina())) {
            // Solo los productos de esta clasificación — no recalcular los 5500+ del catálogo.
            marcarProductosDeClasifGastro(id, "Cambio en clasificación gastro");
        }

        auditoriaService.registrarCambios(AuditoriaEntidad.CLASIF_GASTRO, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        ClasifGastro entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Clasificación Gastro no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = entity.getNombre();
        repo.delete(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CLASIF_GASTRO, id, codigo, AuditoriaAccion.DELETE, estadoAnterior, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer clasifGastroId) {
        if (!repo.existsById(clasifGastroId)) {
            throw new NotFoundException("Clasificación Gastro no encontrada");
        }
        return productoRepository.findByClasifGastroId(clasifGastroId)
                .stream()
                .map(productoMapper::toResumenDTO)
                .toList();
    }


    @Override
    @Transactional
    public SincronizacionDuxResultDTO sincronizarDuxIds() {
        List<ClasifGastro> entidades = repo.findAll();

        List<ClasifDuxMatcher.ClasifNodo> nodos = new ArrayList<>();
        for (ClasifGastro e : entidades) {
            ClasifGastro padre = e.getPadre();
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
        List<ClasifGastro> modificadas = new ArrayList<>();

        for (ClasifGastro e : entidades) {
            ClasifGastro padre = e.getPadre();
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

        log.info("ClasifGastro - Sincronización Dux: nivel1={}, nivel2={}, actualizados={}, sinMatch={}",
                nivel1, nivel2, actualizados, sinMatch);

        return new SincronizacionDuxResultDTO(nivel1, nivel2, actualizados, sinMatch);
    }

    private void marcarProductosDeClasifGastro(Integer clasifGastroId, String motivo) {
        List<Integer> productoIds = productoRepository.findByClasifGastroId(clasifGastroId).stream()
                .map(p -> p.getId())
                .toList();
        recalculoPendienteService.marcarProductos(motivo, productoIds);
    }

    private Map<String, String> capturarSnapshot(ClasifGastro entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(entity.getNombre()));
        snapshot.put("esMaquina", normalizar(entity.getEsMaquina()));
        snapshot.put("idDux", normalizar(entity.getIdDux()));
        snapshot.put("padreId", entity.getPadre() != null ? normalizar(entity.getPadre().getId()) : null);
        snapshot.put("padre", entity.getPadre() != null ? normalizar(entity.getPadre().getNombre()) : null);
        return snapshot;
    }
}






