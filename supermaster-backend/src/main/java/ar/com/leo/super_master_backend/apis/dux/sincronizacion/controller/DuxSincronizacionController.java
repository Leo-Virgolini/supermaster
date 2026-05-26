package ar.com.leo.super_master_backend.apis.dux.sincronizacion.controller;

import ar.com.leo.super_master_backend.apis.dux.dto.ImportDuxResultDTO;
import ar.com.leo.super_master_backend.apis.dux.sincronizacion.dto.DuxHorarioSyncDTO;
import ar.com.leo.super_master_backend.apis.dux.sincronizacion.service.DuxHorarioSyncSchedulerService;
import ar.com.leo.super_master_backend.apis.dux.sincronizacion.service.DuxSincronizacionProgramadaService;
import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dux/sincronizacion")
public class DuxSincronizacionController {

    private final DuxSincronizacionProgramadaService syncService;
    private final DuxHorarioSyncSchedulerService scheduler;

    // =====================================================
    // HORARIOS DIARIOS PROGRAMADOS
    // =====================================================

    @GetMapping("/horarios")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<List<DuxHorarioSyncDTO>> listarHorarios() {
        return ResponseEntity.ok(scheduler.listar());
    }

    @PutMapping("/horarios")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<List<DuxHorarioSyncDTO>> reemplazarHorarios(
            @RequestBody @Valid List<DuxHorarioSyncDTO> horarios) {
        return ResponseEntity.ok(scheduler.reemplazar(horarios));
    }

    // =====================================================
    // SYNC MANUAL
    // =====================================================

    /**
     * Inicia un sync manual.
     * <ul>
     *   <li>Sin parametros: incremental usando el cursor persistido (si está vacío, completo).</li>
     *   <li>{@code force=true}: ignora el cursor y baja todo.</li>
     * </ul>
     */
    @PostMapping("/iniciar")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Map<String, Object>> iniciar(
            @RequestParam(required = false) Boolean force) {

        DuxSincronizacionProgramadaService.Modo modo = Boolean.TRUE.equals(force)
                ? DuxSincronizacionProgramadaService.Modo.COMPLETO
                : DuxSincronizacionProgramadaService.Modo.INCREMENTAL;

        boolean iniciado = syncService.iniciarSync(modo);
        Map<String, Object> body = new HashMap<>();
        body.put("iniciado", iniciado);
        body.put("modo", modo.name());
        if (iniciado) {
            body.put("mensaje", "Sincronización iniciada.");
            return ResponseEntity.accepted().body(body);
        }
        body.put("mensaje", "No se pudo iniciar: ya hay un sync activo, otro proceso DUX/BD en curso, o DUX no configurado.");
        return ResponseEntity.badRequest().body(body);
    }

    @PostMapping("/cancelar")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Map<String, Object>> cancelar() {
        return ResponseEntity.ok(Map.of("cancelado", syncService.cancelarSync()));
    }

    @GetMapping("/estado")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ProcesoMasivoEstadoDTO> estado() {
        return ResponseEntity.ok(syncService.obtenerEstado());
    }

    @GetMapping("/resultado")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ImportDuxResultDTO> resultado() {
        ImportDuxResultDTO r = syncService.obtenerUltimoResultado();
        if (r == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(r);
    }

    /** Última corrida + cursor incremental persistido (para banners). */
    @GetMapping("/ultima-sync")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Map<String, Object>> ultimaSync() {
        Map<String, Object> body = new HashMap<>();
        body.put("ultimaSyncGlobalAt", syncService.obtenerUltimaSyncGlobalAt());
        body.put("ultimoIniciadoEn", syncService.obtenerUltimoIniciadoEn());
        body.put("ultimoDesde", syncService.obtenerUltimoDesde());
        return ResponseEntity.ok(body);
    }
}
