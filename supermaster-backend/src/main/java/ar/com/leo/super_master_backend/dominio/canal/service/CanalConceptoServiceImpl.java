package ar.com.leo.super_master_backend.dominio.canal.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConcepto;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoId;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoRegla;
import ar.com.leo.super_master_backend.dominio.canal.mapper.CanalConceptoMapper;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoReglaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.repository.ConceptoCalculoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CanalConceptoServiceImpl implements CanalConceptoService {

    private final CanalConceptoRepository canalConceptoRepository;
    private final CanalConceptoReglaRepository canalConceptoReglaRepository;
    private final CanalRepository canalRepository;
    private final ConceptoCalculoRepository conceptoRepository;
    private final ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService recalculoPendienteService;
    private final CanalConceptoMapper canalConceptoMapper;
    private final CanalScopeService canalScopeService;
    private final AuditoriaService auditoriaService;

    // ==========================================
    // LISTAR
    // ==========================================
    @Override
    @Transactional(readOnly = true)
    public List<CanalConceptoDTO> listarPorCanal(Integer canalId) {

        return canalConceptoRepository.findByCanalId(canalId)
                .stream()
                .map(canalConceptoMapper::toDTO)
                .toList();
    }

    // ==========================================
    // ASIGNAR CONCEPTO A CANAL
    // ==========================================
    @Override
    @Transactional
    public CanalConceptoDTO asignarConcepto(Integer canalId, Integer conceptoId) {

        // 1) validar canal
        Canal canal = canalRepository.findById(canalId)
                .orElseThrow(() -> new NotFoundException("Canal no encontrado"));

        // 2) validar concepto
        ConceptoCalculo concepto = conceptoRepository.findById(conceptoId)
                .orElseThrow(() -> new NotFoundException("Concepto no encontrado"));

        // 3) crear relación
        CanalConcepto cc = new CanalConcepto();
        cc.setId(new CanalConceptoId(canalId, conceptoId));
        cc.setCanal(canal);
        cc.setConcepto(concepto);

        canalConceptoRepository.save(cc);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_CONCEPTO,
                conceptoId,
                codigoRelacion(canal, concepto),
                AuditoriaAccion.CREATE,
                Map.of(),
                snapshotRelacion(canal, concepto)
        );

        // Recalcular precios de todos los productos del canal
        recalculoPendienteService.marcarCanales("Asignación de concepto a canal",
                canalScopeService.idsConSubcanales(canalId));

        return canalConceptoMapper.toDTO(cc);
    }

    // ==========================================
    // ELIMINAR CONCEPTO DE CANAL
    // ==========================================
    @Override
    @Transactional
    public void eliminarConcepto(Integer canalId, Integer conceptoId) {
        CanalConceptoId id = new CanalConceptoId(canalId, conceptoId);
        CanalConcepto existente = canalConceptoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Relación Canal-Concepto no existe"));
        Map<String, String> estadoAnterior = snapshotRelacion(existente.getCanal(), existente.getConcepto());
        String codigo = codigoRelacion(existente.getCanal(), existente.getConcepto());

        canalConceptoRepository.deleteByCanalIdAndConceptoId(canalId, conceptoId);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_CONCEPTO,
                conceptoId,
                codigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );

        // Recalcular precios de todos los productos del canal
        recalculoPendienteService.marcarCanales("Eliminación de concepto del canal",
                canalScopeService.idsConSubcanales(canalId));
    }

    // ==========================================
    // CLONAR CONCEPTOS DESDE OTRO CANAL
    // ==========================================
    @Override
    @Transactional
    public int clonarConceptosDesde(Integer targetCanalId, Integer srcCanalId) {
        if (targetCanalId.equals(srcCanalId)) {
            throw new ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException(
                    "El canal destino no puede ser igual al canal origen");
        }

        Canal target = canalRepository.findById(targetCanalId)
                .orElseThrow(() -> new NotFoundException("Canal destino no encontrado"));
        Canal src = canalRepository.findById(srcCanalId)
                .orElseThrow(() -> new NotFoundException("Canal origen no encontrado"));

        // 1) Copiar canal_concepto (asignaciones).
        // Si el destino ya tiene la asignación al mismo concepto, no se duplica.
        List<CanalConcepto> srcConceptos = canalConceptoRepository.findByCanalId(srcCanalId);
        int copiadas = 0;
        for (CanalConcepto srcCc : srcConceptos) {
            ConceptoCalculo concepto = srcCc.getConcepto();
            CanalConceptoId nuevoId = new CanalConceptoId(targetCanalId, concepto.getId());
            if (canalConceptoRepository.findById(nuevoId).isPresent()) continue;

            CanalConcepto nueva = new CanalConcepto();
            nueva.setId(nuevoId);
            nueva.setCanal(target);
            nueva.setConcepto(concepto);
            canalConceptoRepository.save(nueva);
            copiadas++;

            Map<String, String> estadoNuevo = snapshotRelacion(target, concepto);
            estadoNuevo.put("clonado_desde_canal", src.getId() + " - " + src.getNombre());
            auditoriaService.registrarCambios(
                    AuditoriaEntidad.CANAL_CONCEPTO,
                    concepto.getId(),
                    codigoRelacion(target, concepto),
                    AuditoriaAccion.CREATE,
                    Map.of(),
                    estadoNuevo
            );
        }

        // 2) Copiar canal_concepto_regla.
        // Las reglas tienen id auto-generado, así que se crean nuevas para el target.
        // No deduplicamos por contenido — si el destino ya tenía reglas, las del src
        // se agregan a las existentes (puede causar reglas redundantes; el caller
        // debería usar este método sobre un canal recién creado típicamente).
        List<CanalConceptoRegla> srcReglas = canalConceptoReglaRepository.findByCanalId(srcCanalId);
        for (CanalConceptoRegla srcRegla : srcReglas) {
            CanalConceptoRegla nueva = new CanalConceptoRegla();
            nueva.setCanal(target);
            nueva.setConcepto(srcRegla.getConcepto());
            nueva.setTipoRegla(srcRegla.getTipoRegla());
            nueva.setTipo(srcRegla.getTipo());
            nueva.setClasifGastro(srcRegla.getClasifGastro());
            nueva.setClasifGral(srcRegla.getClasifGral());
            nueva.setMarca(srcRegla.getMarca());
            nueva.setEsMaquina(srcRegla.getEsMaquina());
            nueva.setTag(srcRegla.getTag());
            nueva.setTieneEnvio(srcRegla.getTieneEnvio());
            CanalConceptoRegla persisted = canalConceptoReglaRepository.save(nueva);

            Map<String, String> estadoReglaNuevo = snapshotRegla(persisted);
            estadoReglaNuevo.put("clonado_desde_canal", src.getId() + " - " + src.getNombre());
            auditoriaService.registrarCambios(
                    AuditoriaEntidad.CANAL_CONCEPTO_REGLA,
                    persisted.getId() == null ? null : persisted.getId().intValue(),
                    codigoRegla(persisted),
                    AuditoriaAccion.CREATE,
                    Map.of(),
                    estadoReglaNuevo
            );
        }

        // 3) Marcar el canal destino como pendiente de recálculo.
        if (copiadas > 0 || !srcReglas.isEmpty()) {
            recalculoPendienteService.marcarCanales("Clonación de conceptos desde otro canal",
                    canalScopeService.idsConSubcanales(targetCanalId));
        }

        return copiadas;
    }

    // ==========================================
    // HELPERS DE AUDITORÍA
    // ==========================================

    private String codigoRelacion(Canal canal, ConceptoCalculo concepto) {
        String canalNombre = canal == null ? "Canal" : canal.getNombre();
        String conceptoNombre = concepto == null ? "Concepto" : concepto.getNombre();
        return canalNombre + " ⇆ " + conceptoNombre;
    }

    private Map<String, String> snapshotRelacion(Canal canal, ConceptoCalculo concepto) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("canal", canal == null ? null : canal.getId() + " - " + canal.getNombre());
        snapshot.put("concepto", concepto == null ? null : concepto.getId() + " - " + concepto.getNombre());
        if (concepto != null) {
            snapshot.put("aplica_sobre", concepto.getAplicaSobre() == null ? null : concepto.getAplicaSobre().name());
            snapshot.put("porcentaje", concepto.getPorcentaje() == null ? null
                    : concepto.getPorcentaje().stripTrailingZeros().toPlainString());
        }
        return snapshot;
    }

    private String codigoRegla(CanalConceptoRegla regla) {
        String canalNombre = regla.getCanal() == null ? "Canal" : regla.getCanal().getNombre();
        String conceptoNombre = regla.getConcepto() == null ? "Concepto" : regla.getConcepto().getNombre();
        String tipoRegla = regla.getTipoRegla() == null ? "?" : regla.getTipoRegla().name();
        return canalNombre + " - " + conceptoNombre + " (" + tipoRegla + ")";
    }

    private Map<String, String> snapshotRegla(CanalConceptoRegla regla) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("canal", regla.getCanal() == null ? null
                : regla.getCanal().getId() + " - " + regla.getCanal().getNombre());
        snapshot.put("concepto", regla.getConcepto() == null ? null
                : regla.getConcepto().getId() + " - " + regla.getConcepto().getNombre());
        snapshot.put("tipo_regla", regla.getTipoRegla() == null ? null : regla.getTipoRegla().name());
        snapshot.put("tipo", regla.getTipo() == null ? null
                : regla.getTipo().getId() + " - " + regla.getTipo().getNombre());
        snapshot.put("marca", regla.getMarca() == null ? null
                : regla.getMarca().getId() + " - " + regla.getMarca().getNombre());
        snapshot.put("clasif_gral", regla.getClasifGral() == null ? null
                : regla.getClasifGral().getId() + " - " + regla.getClasifGral().getNombre());
        snapshot.put("clasif_gastro", regla.getClasifGastro() == null ? null
                : regla.getClasifGastro().getId() + " - " + regla.getClasifGastro().getNombre());
        snapshot.put("tag", regla.getTag() == null ? null : regla.getTag().name());
        snapshot.put("tiene_envio", regla.getTieneEnvio() == null ? null : String.valueOf(regla.getTieneEnvio()));
        return snapshot;
    }

}
