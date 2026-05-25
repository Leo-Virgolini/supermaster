package ar.com.leo.super_master_backend.dominio.regla_descuento.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.service.CanalScopeService;
import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoCreateDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoPatchDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.entity.ReglaDescuento;
import ar.com.leo.super_master_backend.dominio.regla_descuento.mapper.ReglaDescuentoMapper;
import ar.com.leo.super_master_backend.dominio.regla_descuento.repository.ReglaDescuentoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
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
public class ReglaDescuentoServiceImpl implements ReglaDescuentoService {

    private final ReglaDescuentoRepository repo;
    private final ReglaDescuentoMapper mapper;
    private final RecalculoPendienteService recalculoPendienteService;
    private final AuditoriaService auditoriaService;
    private final CanalScopeService canalScopeService;

    @Override
    @Transactional(readOnly = true)
    public Page<ReglaDescuentoDTO> listar(Pageable pageable) {
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReglaDescuentoDTO> listarPorCanal(Integer canalId) {
        return repo.findByCanalId(canalId)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReglaDescuentoDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Regla de descuento no encontrada"));
    }

    @Override
    @Transactional
    public ReglaDescuentoDTO crear(ReglaDescuentoCreateDTO dto) {
        ReglaDescuento entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.REGLA_DESCUENTO,
                entity.getId(),
                construirCodigoEntidad(entity),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(entity)
        );

        programarRecalculoPostCommit(entity.getCanal().getId());

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ReglaDescuentoDTO actualizar(Integer id, ReglaDescuentoUpdateDTO dto) {
        ReglaDescuento entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Regla de descuento no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        Integer canalId = entity.getCanal().getId();

        mapper.updateEntityFromDTO(dto, entity);
        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.REGLA_DESCUENTO,
                id,
                construirCodigoEntidad(entity),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );

        programarRecalculoPostCommit(canalId);

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ReglaDescuentoDTO patch(Integer id, ReglaDescuentoPatchDTO patchDto) {
        if (isPatchVacio(patchDto)) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        ReglaDescuento entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Regla de descuento no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        Integer canalIdAnterior = entity.getCanal().getId();

        if (presente(patchDto.getCanalId())) {
            entity.setCanal(new Canal(leerIdRequerido(patchDto.getCanalId(), "canalId")));
        }
        if (presente(patchDto.getCatalogoId())) {
            Integer catalogoId = leerIdOpcional(patchDto.getCatalogoId(), "catalogoId");
            entity.setCatalogo(catalogoId != null ? new Catalogo(catalogoId) : null);
        }
        if (presente(patchDto.getClasifGralId())) {
            Integer clasifGralId = leerIdOpcional(patchDto.getClasifGralId(), "clasifGralId");
            entity.setClasifGral(clasifGralId != null ? new ClasifGral(clasifGralId) : null);
        }
        if (presente(patchDto.getClasifGastroId())) {
            Integer clasifGastroId = leerIdOpcional(patchDto.getClasifGastroId(), "clasifGastroId");
            entity.setClasifGastro(clasifGastroId != null ? new ClasifGastro(clasifGastroId) : null);
        }
        if (presente(patchDto.getMontoMinimo())) {
            entity.setMontoMinimo(leerDecimalNoNegativoRequerido(patchDto.getMontoMinimo(), "montoMinimo"));
        }
        if (presente(patchDto.getDescuentoPorcentaje())) {
            entity.setDescuentoPorcentaje(leerPorcentajeRequerido(patchDto.getDescuentoPorcentaje(), "descuentoPorcentaje"));
        }
        if (presente(patchDto.getPrioridad())) {
            entity.setPrioridad(leerIntegerNoNegativoOpcional(patchDto.getPrioridad(), "prioridad"));
        }
        if (presente(patchDto.getActivo())) {
            entity.setActivo(leerBooleanOpcional(patchDto.getActivo(), "activo"));
        }
        if (presente(patchDto.getDescripcion())) {
            entity.setDescripcion(leerStringOpcional(patchDto.getDescripcion(), "descripcion", 200));
        }

        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.REGLA_DESCUENTO,
                id,
                construirCodigoEntidad(entity),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );

        programarRecalculoPostCommit(canalIdAnterior);
        if (!canalIdAnterior.equals(entity.getCanal().getId())) {
            programarRecalculoPostCommit(entity.getCanal().getId());
        }

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        ReglaDescuento entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Regla de descuento no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = construirCodigoEntidad(entity);

        Integer canalId = entity.getCanal().getId();

        repo.deleteById(id);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.REGLA_DESCUENTO,
                id,
                codigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );

        programarRecalculoPostCommit(canalId);
    }


    private boolean isPatchVacio(ReglaDescuentoPatchDTO patchDto) {
        return !presente(patchDto.getCanalId())
                && !presente(patchDto.getCatalogoId())
                && !presente(patchDto.getClasifGralId())
                && !presente(patchDto.getClasifGastroId())
                && !presente(patchDto.getMontoMinimo())
                && !presente(patchDto.getDescuentoPorcentaje())
                && !presente(patchDto.getPrioridad())
                && !presente(patchDto.getActivo())
                && !presente(patchDto.getDescripcion());
    }

    private Map<String, String> capturarSnapshot(ReglaDescuento entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("canal", describirCanal(entity.getCanal()));
        snapshot.put("catalogo", describirCatalogo(entity.getCatalogo()));
        snapshot.put("clasifGral", describirClasifGral(entity.getClasifGral()));
        snapshot.put("clasifGastro", describirClasifGastro(entity.getClasifGastro()));
        snapshot.put("montoMinimo", normalizar(entity.getMontoMinimo()));
        snapshot.put("descuentoPorcentaje", normalizar(entity.getDescuentoPorcentaje()));
        snapshot.put("prioridad", normalizar(entity.getPrioridad()));
        snapshot.put("activo", normalizar(entity.getActivo()));
        snapshot.put("descripcion", normalizar(entity.getDescripcion()));
        return snapshot;
    }

    private String construirCodigoEntidad(ReglaDescuento entity) {
        String canal = entity.getCanal() != null ? entity.getCanal().getNombre() : "Sin canal";
        String descuento = entity.getDescuentoPorcentaje() != null ? entity.getDescuentoPorcentaje().stripTrailingZeros().toPlainString() : "?";
        return canal + " / " + descuento + "%";
    }

    private String describirCanal(Canal canal) {
        return canal == null ? null : canal.getId() + " - " + canal.getNombre();
    }

    private String describirCatalogo(Catalogo catalogo) {
        return catalogo == null ? null : catalogo.getId() + " - " + catalogo.getNombre();
    }

    private String describirClasifGral(ClasifGral clasifGral) {
        return clasifGral == null ? null : clasifGral.getId() + " - " + clasifGral.getNombre();
    }

    private String describirClasifGastro(ClasifGastro clasifGastro) {
        return clasifGastro == null ? null : clasifGastro.getId() + " - " + clasifGastro.getNombre();
    }

    private void programarRecalculoPostCommit(Integer canalId) {
        // Incluye subcanales: el descuento del padre afecta el ingreso neto y por lo tanto
        // las columnas c/Desc del subcanal vía PVP base.
        recalculoPendienteService.marcarCanales("Cambio en regla de descuento",
                canalScopeService.idsConSubcanales(canalId));
    }
}

