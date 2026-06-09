package ar.com.leo.super_master_backend.apis.dux.sincronizacion.service;

import ar.com.leo.super_master_backend.apis.dux.sincronizacion.dto.DuxHorarioSyncDTO;
import ar.com.leo.super_master_backend.apis.dux.sincronizacion.entity.DuxHorarioSync;
import ar.com.leo.super_master_backend.apis.dux.sincronizacion.repository.DuxHorarioSyncRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * Programa la importación DUX → {@code productos} según los horarios persistidos
 * en {@link DuxHorarioSync}. Cada fila representa un disparo diario (HH:mm) en
 * zona AR. El disparo programado siempre se ejecuta en modo INCREMENTAL
 * (usa el cursor de {@code config_automatizacion[ultima_fecha_dux]},
 * compartido con {@code AutomatizacionPreciosService}).
 *
 * <p>Reprogramación atómica: al cambiar horarios vía {@link #reemplazar(List)}
 * se cancelan todas las tareas pendientes y se vuelven a registrar desde cero
 * — el caller no necesita preocuparse por el orden ni por estados intermedios.
 */
@Slf4j
@Service
public class DuxHorarioSyncSchedulerService {

    private static final TimeZone ZONA_AR = TimeZone.getTimeZone("America/Argentina/Buenos_Aires");

    private final DuxHorarioSyncRepository horarioRepo;
    private final DuxSincronizacionProgramadaService syncService;
    private final TaskScheduler taskScheduler;

    /** Tareas programadas vivas. Se cancelan todas en bloque al reprogramar. */
    private final CopyOnWriteArrayList<ScheduledFuture<?>> tareasActivas = new CopyOnWriteArrayList<>();

    /** Self para que llamadas internas pasen por el proxy @Transactional. */
    @Autowired
    @Lazy
    private DuxHorarioSyncSchedulerService self;

    public DuxHorarioSyncSchedulerService(DuxHorarioSyncRepository horarioRepo,
                                          DuxSincronizacionProgramadaService syncService) {
        this.horarioRepo = horarioRepo;
        this.syncService = syncService;
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("dux-sync-");
        s.setRemoveOnCancelPolicy(true);
        s.initialize();
        this.taskScheduler = s;
    }

    // ======================================================================
    // Bootstrap
    // ======================================================================

    @EventListener(ApplicationReadyEvent.class)
    public void programarAlIniciar() {
        try {
            List<DuxHorarioSyncDTO> horarios = self.listar();
            programar(horarios);
            if (horarios.isEmpty()) {
                log.info("DUX sync - Sin horarios configurados; sync automatica deshabilitada");
            } else {
                log.info("DUX sync - Sync automatica programada con {} disparo(s) diario(s) (zona AR): {}",
                        horarios.size(),
                        horarios.stream()
                                .map(h -> String.format("%02d:%02d", h.hora(), h.minuto()))
                                .collect(Collectors.joining(", ")));
            }
        } catch (Exception e) {
            log.error("DUX sync - Error programando horarios al iniciar: {}", e.getMessage(), e);
        }
    }

    // ======================================================================
    // API pública (consumida por el controller)
    // ======================================================================

    @Transactional(readOnly = true)
    public List<DuxHorarioSyncDTO> listar() {
        return horarioRepo.findAllByOrderByHoraAscMinutoAsc().stream()
                .map(h -> new DuxHorarioSyncDTO(h.getHora(), h.getMinuto()))
                .toList();
    }

    /**
     * Reemplaza la lista de horarios de forma atómica.
     * Valida que no haya duplicados (misma hora+minuto).
     */
    @Transactional
    public List<DuxHorarioSyncDTO> reemplazar(List<DuxHorarioSyncDTO> nuevos) {
        validar(nuevos);
        horarioRepo.deleteAllInBatch();
        // flush implícito en saveAll
        List<DuxHorarioSync> entities = nuevos.stream()
                .sorted(Comparator.comparingInt(DuxHorarioSyncDTO::hora)
                        .thenComparingInt(DuxHorarioSyncDTO::minuto))
                .map(dto -> {
                    DuxHorarioSync h = new DuxHorarioSync();
                    h.setHora(dto.hora());
                    h.setMinuto(dto.minuto());
                    return h;
                })
                .toList();
        List<DuxHorarioSync> persisted = horarioRepo.saveAll(entities);

        List<DuxHorarioSyncDTO> resultado = persisted.stream()
                .map(h -> new DuxHorarioSyncDTO(h.getHora(), h.getMinuto()))
                .toList();
        programar(resultado);
        log.info("DUX sync - Horarios reprogramados: {}",
                resultado.stream()
                        .map(h -> String.format("%02d:%02d", h.hora(), h.minuto()))
                        .collect(Collectors.joining(", ")));
        return resultado;
    }

    // ======================================================================
    // Programación interna
    // ======================================================================

    private void programar(List<DuxHorarioSyncDTO> horarios) {
        // Cancelar todo lo pendiente.
        for (ScheduledFuture<?> f : tareasActivas) {
            f.cancel(false);
        }
        tareasActivas.clear();

        if (horarios == null || horarios.isEmpty()) return;

        for (DuxHorarioSyncDTO h : horarios) {
            String cron = String.format("0 %d %d * * *", h.minuto(), h.hora());
            CronTrigger trigger = new CronTrigger(cron, ZONA_AR);
            ScheduledFuture<?> future = taskScheduler.schedule(this::dispararDesdeScheduler, trigger);
            tareasActivas.add(future);
        }
    }

    private void dispararDesdeScheduler() {
        try {
            log.info("DUX sync - Disparando sync programado (incremental por cursor)");
            boolean ok = syncService.iniciarSync(
                    DuxSincronizacionProgramadaService.Modo.INCREMENTAL);
            if (!ok) {
                log.warn("DUX sync - Sync programado no se inició (ya hay otro proceso DUX/BD activo o DUX no configurado)");
            }
        } catch (Exception e) {
            log.error("DUX sync - Error en disparo programado: {}", e.getMessage(), e);
        }
    }

    private void validar(List<DuxHorarioSyncDTO> nuevos) {
        if (nuevos == null) return;
        Set<String> visto = new HashSet<>();
        List<String> errores = new ArrayList<>();
        for (DuxHorarioSyncDTO h : nuevos) {
            if (h.hora() == null || h.minuto() == null
                    || h.hora() < 0 || h.hora() > 23
                    || h.minuto() < 0 || h.minuto() > 59) {
                errores.add(String.format("Horario inválido: %s:%s", h.hora(), h.minuto()));
                continue;
            }
            String key = h.hora() + ":" + h.minuto();
            if (!visto.add(key)) {
                errores.add(String.format("Horario duplicado: %02d:%02d", h.hora(), h.minuto()));
            }
        }
        if (!errores.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errores));
        }
    }
}
