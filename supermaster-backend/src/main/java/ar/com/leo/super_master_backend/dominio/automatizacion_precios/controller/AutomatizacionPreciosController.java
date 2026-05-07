package ar.com.leo.super_master_backend.dominio.automatizacion_precios.controller;

import ar.com.leo.super_master_backend.dominio.automatizacion_precios.dto.SincronizacionConfigDTO;
import ar.com.leo.super_master_backend.dominio.automatizacion_precios.dto.SincronizacionRequestDTO;
import ar.com.leo.super_master_backend.dominio.automatizacion_precios.dto.SincronizacionResultDTO;
import ar.com.leo.super_master_backend.dominio.automatizacion_precios.service.AutomatizacionPreciosService;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/automatizacion-precios")
@PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
public class AutomatizacionPreciosController {

    private final AutomatizacionPreciosService service;

    @PostMapping("/iniciar")
    public ResponseEntity<Void> iniciar(@RequestBody SincronizacionRequestDTO request) {
        boolean started = service.iniciar(request);
        return started ? ResponseEntity.ok().build() : ResponseEntity.status(409).build();
    }

    @GetMapping("/estado")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ProcesoMasivoEstadoDTO> estado() {
        return ResponseEntity.ok(service.obtenerEstado());
    }

    @PostMapping("/cancelar")
    public ResponseEntity<Void> cancelar() {
        service.cancelar();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/resultado")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<SincronizacionResultDTO> resultado() {
        SincronizacionResultDTO result = service.obtenerResultado();
        if (result == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<SincronizacionConfigDTO> config() {
        return ResponseEntity.ok(service.obtenerConfig());
    }

    /**
     * Endpoint para n8n / automatización externa.
     * Sin autenticación (configurado en SecurityConfig como permitAll).
     * SEGURIDAD: depende de que el backend NO esté expuesto a internet (solo LAN).
     * Si alguna vez se expone públicamente, proteger con API key o auth.
     * Inicia la sincronización y espera a que termine (sincrónico).
     * Si ya hay un proceso corriendo, retorna 409.
     */
    @PostMapping("/ejecutar")
    @PreAuthorize("permitAll()")
    public ResponseEntity<SincronizacionResultDTO> ejecutar()
            throws InterruptedException {
        SincronizacionRequestDTO allSteps = new SincronizacionRequestDTO(
                true, true, true, true, true, true, true, true, true);
        boolean started = service.iniciar(allSteps);
        if (!started) {
            return ResponseEntity.status(409).build();
        }

        // Esperar a que termine (polling interno)
        while (service.obtenerEstado().enEjecucion()) {
            Thread.sleep(2000);
        }

        SincronizacionResultDTO result = service.obtenerResultado();
        if (result == null) return ResponseEntity.noContent().build();

        // Retornar sin log (puede ser muy grande para n8n). El detalle queda en el archivo.
        return ResponseEntity.ok(new SincronizacionResultDTO(
                result.duxImportActualizados(), result.duxImportTotal(), result.duxImportErrores(),
                result.envioCalculados(), result.envioErrores(),
                result.excluidosExitosos(), result.excluidosErrores(),
                result.duxMlProductos(), result.duxMlEstado(),
                result.duxGastroProductos(), result.duxGastroEstado(),
                result.duxNubeProductos(), result.duxNubeEstado(),
                result.mlActualizados(), result.mlErrores(),
                result.promoIncluidos(), result.promoErrores(),
                result.nubeActualizados(), result.nubeErrores(),
                List.of()
        ));
    }

    /**
     * Retorna los logs en tiempo real. Soporta ?desde=N para obtener solo las líneas nuevas.
     */
    @GetMapping("/log")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<List<String>> log(@RequestParam(defaultValue = "0") int desde) {
        return ResponseEntity.ok(service.obtenerLog(desde));
    }

    /**
     * Retorna el contenido del archivo de log histórico.
     * Soporta ?lineas=N para limitar las últimas N líneas (default: 500).
     */
    @GetMapping("/log-file")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<String> logFile(@RequestParam(defaultValue = "500") int lineas) {
        Path logPath = Path.of("logs/automatizacion-precios.log");
        if (!Files.exists(logPath)) {
            return ResponseEntity.ok("");
        }
        try {
            List<String> allLines = Files.readAllLines(logPath);
            int from = Math.max(0, allLines.size() - lineas);
            String content = String.join("\n", allLines.subList(from, allLines.size()));
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error leyendo archivo de log: " + e.getMessage());
        }
    }
}
