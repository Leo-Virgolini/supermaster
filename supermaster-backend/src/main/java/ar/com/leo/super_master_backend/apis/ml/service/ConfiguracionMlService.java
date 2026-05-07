package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.apis.ml.dto.ConfiguracionMlDTO;
import ar.com.leo.super_master_backend.apis.ml.entity.ConfiguracionMl;
import ar.com.leo.super_master_backend.apis.ml.repository.ConfiguracionMlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConfiguracionMlService {

    private final ConfiguracionMlRepository repository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public ConfiguracionMlDTO obtener() {
        ConfiguracionMl config = repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("Configuración de ML no encontrada"));
        return toDTO(config);
    }

    @Transactional(readOnly = true)
    public ConfiguracionMl obtenerEntidad() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("Configuración de ML no encontrada"));
    }

    @Transactional
    public ConfiguracionMlDTO actualizar(ConfiguracionMlDTO dto) {
        ConfiguracionMl config = repository.findFirstByOrderByIdAsc()
                .orElseGet(ConfiguracionMl::new);
        Map<String, String> estadoAnterior = capturarSnapshot(config);

        config.setUmbralEnvioGratis(dto.umbralEnvioGratis());
        config.setTier1Hasta(dto.tier1Hasta());
        config.setTier1Costo(dto.tier1Costo());
        config.setTier2Hasta(dto.tier2Hasta());
        config.setTier2Costo(dto.tier2Costo());
        config.setTier3Costo(dto.tier3Costo());

        repository.save(config);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CONFIGURACION_ML,
                config.getId(),
                "MERCADOLIBRE",
                estadoAnterior.isEmpty() ? AuditoriaAccion.CREATE : AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(config)
        );
        return toDTO(config);
    }

    /**
     * Obtiene el costo de envío según el PVP base del producto.
     *
     * @param pvp El PVP calculado sin envío
     * @return El costo de envío del tier correspondiente, o null si debe usar API ML
     */
    @Transactional(readOnly = true)
    public BigDecimal obtenerCostoEnvioPorPvp(BigDecimal pvp) {
        ConfiguracionMl config = obtenerEntidad();

        // Verificar que los tiers estén configurados
        if (config.getTier1Hasta() == null || config.getTier1Costo() == null ||
            config.getTier2Hasta() == null || config.getTier2Costo() == null ||
            config.getTier3Costo() == null) {
            return null; // Tiers no configurados, usar lógica anterior
        }

        if (pvp.compareTo(config.getTier1Hasta()) < 0) {
            return config.getTier1Costo();
        } else if (pvp.compareTo(config.getTier2Hasta()) < 0) {
            return config.getTier2Costo();
        } else if (pvp.compareTo(config.getUmbralEnvioGratis()) < 0) {
            return config.getTier3Costo();
        }
        return null; // >= umbral, usar API ML
    }

    private ConfiguracionMlDTO toDTO(ConfiguracionMl entity) {
        return new ConfiguracionMlDTO(
                entity.getId(),
                entity.getUmbralEnvioGratis(),
                entity.getTier1Hasta(),
                entity.getTier1Costo(),
                entity.getTier2Hasta(),
                entity.getTier2Costo(),
                entity.getTier3Costo()
        );
    }

    private Map<String, String> capturarSnapshot(ConfiguracionMl entity) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        if (entity.getId() == null
                && entity.getUmbralEnvioGratis() == null
                && entity.getTier1Hasta() == null
                && entity.getTier1Costo() == null
                && entity.getTier2Hasta() == null
                && entity.getTier2Costo() == null
                && entity.getTier3Costo() == null) {
            return snapshot;
        }
        snapshot.put("umbralEnvioGratis", normalizarDecimal(entity.getUmbralEnvioGratis()));
        snapshot.put("tier1Hasta", normalizarDecimal(entity.getTier1Hasta()));
        snapshot.put("tier1Costo", normalizarDecimal(entity.getTier1Costo()));
        snapshot.put("tier2Hasta", normalizarDecimal(entity.getTier2Hasta()));
        snapshot.put("tier2Costo", normalizarDecimal(entity.getTier2Costo()));
        snapshot.put("tier3Costo", normalizarDecimal(entity.getTier3Costo()));
        return snapshot;
    }

    private String normalizarDecimal(BigDecimal value) {
        return value != null ? value.stripTrailingZeros().toPlainString() : null;
    }
}
