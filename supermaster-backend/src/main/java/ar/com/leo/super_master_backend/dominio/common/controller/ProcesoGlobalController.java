package ar.com.leo.super_master_backend.dominio.common.controller;

import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.dominio.automatizacion_precios.service.AutomatizacionPreciosService;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoActivoDTO;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesoGlobalService;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesosActivosSseService;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.CalculoPrecioService;
import ar.com.leo.super_master_backend.dominio.reposicion.service.ReposicionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/procesos")
public class ProcesoGlobalController {

    private final ProcesoGlobalService procesoGlobalService;
    private final ProcesosActivosSseService sseService;
    private final DuxService duxService;
    private final MercadoLibreService mercadoLibreService;
    private final ReposicionService reposicionService;
    private final AutomatizacionPreciosService automatizacionPreciosService;
    private final CalculoPrecioService calculoPrecioService;

    @GetMapping("/activo")
    public ResponseEntity<ProcesoActivoDTO> procesoActivo() {
        return ResponseEntity.ok(procesoGlobalService.snapshotDTO());
    }

    /**
     * Mapping de grupos de exclusión por proceso. Único origen de verdad — el
     * frontend lo consume al inicializar para sincronizar la lógica de
     * "tieneConflicto" sin duplicar el mapping.
     */
    @GetMapping("/grupos")
    public ResponseEntity<Map<String, List<String>>> grupos() {
        return ResponseEntity.ok(procesoGlobalService.obtenerGruposPorProceso());
    }

    /**
     * Stream SSE que empuja el snapshot completo de procesos activos cada vez que
     * cambia (cuando se adquiere o libera un lock) y un heartbeat cada 25s.
     * Endpoint público (ver SecurityConfig): la info es sólo id + descripción de
     * procesos, no hay datos sensibles, y así el front puede usar `EventSource`
     * nativo sin el workaround de fetch para pasar `Authorization`.
     */
    @GetMapping(value = "/activo/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProcesosActivos(HttpServletResponse response) {
        // Deshabilita el buffering en nginx (y otros reverse proxies que lo respetan),
        // así los eventos llegan inmediatamente al cliente en lugar de acumularse.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return sseService.subscribe(procesoGlobalService.snapshotDTO());
    }

    @GetMapping("/estados")
    public ResponseEntity<List<Map<String, Object>>> todosLosEstados() {
        List<Map<String, Object>> procesos = List.of(
                buildProcesoInfo("dux-importacion", "Importar productos DUX",
                        duxService.obtenerEstadoImportacion()),
                buildProcesoInfo("dux-deudas", "Deudas de clientes DUX",
                        duxService.obtenerEstadoDeudas()),
                buildProcesoInfo("costo-envio", "Costo de envío ML",
                        mercadoLibreService.obtenerEstadoProcesoMasivo()),
                buildProcesoInfo("costo-venta", "Costo de venta ML",
                        mercadoLibreService.obtenerEstadoProcesoMasivoCostoVenta()),
                buildProcesoInfo("reposicion", "Cálculo de reposición",
                        reposicionService.obtenerEstadoCalculo()),
                buildProcesoInfo("automatizacion-precios", "Automatización de precios",
                        automatizacionPreciosService.obtenerEstado()),
                buildProcesoInfo("recalculo-precios", "Recálculo masivo de precios",
                        calculoPrecioService.obtenerEstadoRecalculo())
        );
        return ResponseEntity.ok(procesos);
    }

    private Map<String, Object> buildProcesoInfo(String id, String nombre, ProcesoMasivoEstadoDTO estado) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", id);
        info.put("nombre", nombre);
        info.put("estado", estado.estado());
        info.put("enEjecucion", estado.enEjecucion());
        info.put("total", estado.total());
        info.put("procesados", estado.procesados());
        info.put("exitosos", estado.exitosos());
        info.put("errores", estado.errores());
        info.put("mensaje", estado.mensaje());
        info.put("iniciadoEn", estado.iniciadoEn());
        info.put("finalizadoEn", estado.finalizadoEn());

        // Usuario: del estado o del lock global
        String usuario = estado.usuario();
        if (usuario == null) {
            var lockInfo = procesoGlobalService.getProcesosActivos().stream()
                    .filter(p -> p.id().equals(id)).findFirst().orElse(null);
            if (lockInfo != null) usuario = lockInfo.usuario();
        }
        info.put("usuario", usuario);

        return info;
    }
}
