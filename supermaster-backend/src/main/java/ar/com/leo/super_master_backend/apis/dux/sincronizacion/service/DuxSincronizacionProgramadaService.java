package ar.com.leo.super_master_backend.apis.dux.sincronizacion.service;

import ar.com.leo.super_master_backend.apis.dux.dto.ImportDuxResultDTO;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.common.service.EstadoProcesoMasivo;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesoGlobalService;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.entity.ConfigAutomatizacion;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.repository.ConfigAutomatizacionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Orquestador del sync DUX → tabla {@code productos} con dos modos:
 * <ul>
 *   <li><b>Incremental por cursor</b>: usa la fecha persistida en
 *       {@code config_automatizacion[clave='ultima_fecha_dux']} (menos 1 min
 *       de margen) y pide a DUX solo items modificados desde esa fecha.
 *       Si todavía no hay cursor, baja todo el catálogo.</li>
 *   <li><b>Completo</b>: ignora el cursor y baja todo el catálogo.</li>
 * </ul>
 *
 * <p>El cursor se comparte con {@code AutomatizacionPreciosService} (que también
 * importa desde DUX y avanza la misma clave). Así, ambos puntos de entrada
 * mantienen un único cursor consistente.
 *
 * <p>La persistencia la hace {@link DuxService#importarProductosSincrono} (actualiza
 * {@code productos}, recalcula precios). Aquí solo se envuelve para tener tracking
 * propio (procesoId {@code "dux-sync-programada"}) y avanzar el cursor al terminar OK.
 */
@Slf4j
@Service
public class DuxSincronizacionProgramadaService {

    /** Clave en {@code config_automatizacion} donde se persiste el cursor. */
    private static final String CLAVE_CURSOR = "ultima_fecha_dux";

    /** Margen restado al cursor incremental para cubrir clock drift entre app y DUX. */
    private static final int MARGEN_CURSOR_MINUTOS = 1;

    private final DuxService duxService;
    private final ConfigAutomatizacionRepository configRepo;
    private final EstadoProcesoMasivo tracker;

    @Autowired
    @Lazy
    private DuxSincronizacionProgramadaService self;

    private volatile ImportDuxResultDTO ultimoResultado;
    private volatile LocalDateTime ultimoIniciadoEn;
    private volatile LocalDateTime ultimoDesde;

    public DuxSincronizacionProgramadaService(DuxService duxService,
                                              ConfigAutomatizacionRepository configRepo,
                                              ProcesoGlobalService procesoGlobal) {
        this.duxService = duxService;
        this.configRepo = configRepo;
        this.tracker = new EstadoProcesoMasivo(
                "dux-sync-programada", "Sincronización programada DUX", procesoGlobal);
    }

    // ======================================================================
    // API pública
    // ======================================================================

    public enum Modo {
        /** Usa el cursor persistido. Si está vacío, baja todo. */
        INCREMENTAL,
        /** Ignora el cursor y baja todo el catálogo. */
        COMPLETO
    }

    /**
     * Inicia el sync en background.
     *
     * @param modo estrategia para definir el filtro {@code fecha} de DUX.
     * @return true si se inició, false si ya hay sync (u otro proceso DUX/BD) activo.
     */
    public boolean iniciarSync(Modo modo) {
        if (!duxService.isConfigured()) {
            log.warn("DUX sync programada - DUX no está configurado");
            return false;
        }
        LocalDateTime desde = resolverDesde(modo);
        if (!tracker.adquirir()) {
            log.warn("DUX sync programada - No se inició: ya en ejecución u otro proceso DUX/BD activo");
            return false;
        }
        ultimoResultado = null;
        ultimoIniciadoEn = LocalDateTime.now();
        ultimoDesde = desde;
        self.ejecutarAsync(modo, desde);
        return true;
    }

    public boolean cancelarSync() {
        if (tracker.estaEjecutando()) {
            tracker.cancelar();
            return true;
        }
        return false;
    }

    public ProcesoMasivoEstadoDTO obtenerEstado() {
        return tracker.obtener();
    }

    public ImportDuxResultDTO obtenerUltimoResultado() {
        return ultimoResultado;
    }

    public LocalDateTime obtenerUltimoDesde() {
        return ultimoDesde;
    }

    public LocalDateTime obtenerUltimoIniciadoEn() {
        return ultimoIniciadoEn;
    }

    /**
     * Última fecha persistida del cursor incremental. Es la misma clave que usa
     * {@code AutomatizacionPreciosService}, así que ambos flujos avanzan juntos.
     */
    public LocalDateTime obtenerUltimaSyncGlobalAt() {
        String valor = configRepo.findByClaveIgnoreCase(CLAVE_CURSOR)
                .map(ConfigAutomatizacion::getValor)
                .orElse(null);
        if (valor == null || valor.isBlank()) return null;
        try {
            return LocalDateTime.parse(valor.trim());
        } catch (Exception e) {
            log.warn("DUX sync programada - Cursor inválido en config_automatizacion[{}]: {}",
                    CLAVE_CURSOR, valor);
            return null;
        }
    }

    // ======================================================================
    // Worker
    // ======================================================================

    @Async
    public void ejecutarAsync(Modo modo, LocalDateTime desde) {
        try {
            String etiqueta = describirCorrida(modo, desde);
            tracker.actualizar("Importación " + etiqueta);
            log.info("DUX sync programada - Iniciando importación {}", etiqueta);

            // Delegamos a la implementación existente que actualiza `productos`,
            // crea proveedores faltantes y dispara los recálculos de precios.
            ImportDuxResultDTO resultado = duxService.importarProductosSincrono(
                    tracker.getFlagCancelado(),
                    msg -> log.info("DUX sync programada - {}", msg),
                    desde);

            ultimoResultado = resultado;

            int total = resultado.totalProductosDux();
            int actualizados = resultado.productosActualizados();
            int erroresCount = resultado.errores() == null ? 0 : resultado.errores().size();
            boolean cancelado = tracker.estaCancelado();

            if (!cancelado) {
                // Solo avanzamos el cursor si terminó OK. Si falla la persistencia
                // del cursor, no invalidamos la corrida (los productos ya se
                // actualizaron); solo lo logueamos.
                try {
                    self.guardarCursor(ultimoIniciadoEn);
                } catch (Exception e) {
                    log.error("DUX sync programada - No se pudo persistir el cursor: {}",
                            e.getMessage(), e);
                }
            }

            tracker.completar(total, actualizados, erroresCount,
                    cancelado
                            ? String.format("Cancelado. %d/%d procesados.", actualizados, total)
                            : String.format("Completado. %d actualizados de %d items DUX (%d no encontrados, %d errores).",
                                    actualizados, total,
                                    resultado.productosNoEncontrados(), erroresCount));
        } catch (Exception e) {
            log.error("DUX sync programada - Error: {}", e.getMessage(), e);
            tracker.completarConError(e.getMessage());
        } finally {
            tracker.liberar();
        }
    }

    @Transactional
    public void guardarCursor(LocalDateTime fecha) {
        ConfigAutomatizacion entity = configRepo.findByClaveIgnoreCase(CLAVE_CURSOR).orElse(null);
        if (entity == null) {
            entity = new ConfigAutomatizacion();
            entity.setClave(CLAVE_CURSOR);
            entity.setDescripcion("Última fecha de importación exitosa desde DUX (cursor incremental)");
        }
        entity.setValor(fecha.toString());
        configRepo.save(entity);
    }

    private static String describirCorrida(Modo modo, LocalDateTime desde) {
        return switch (modo) {
            case COMPLETO -> "completa (force, ignora cursor)";
            case INCREMENTAL -> desde == null
                    ? "completa (incremental sin cursor previo)"
                    : "incremental desde " + desde;
        };
    }

    private LocalDateTime resolverDesde(Modo modo) {
        return switch (modo) {
            case COMPLETO -> null;
            case INCREMENTAL -> {
                LocalDateTime ultima = obtenerUltimaSyncGlobalAt();
                yield ultima == null ? null : ultima.minusMinutes(MARGEN_CURSOR_MINUTOS);
            }
        };
    }
}
