package ar.com.leo.super_master_backend.apis.ml.controller;

import ar.com.leo.super_master_backend.apis.ml.dto.ConfiguracionMlDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.CostoEnvioMasivoResponseDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.CostoEnvioResponseDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.CostoVentaMasivoResponseDTO;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.apis.ml.service.ConfiguracionMlService;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ml")
public class MercadoLibreController {

    private final MercadoLibreService mercadoLibreService;
    private final ConfiguracionMlService configuracionMlService;

    /**
     * Calcula y guarda el costo de envío para productos de ML.
     *
     * Prioridad de parámetros:
     * 1. Si se proporciona 'mla', calcula solo para ese MLA (sincrónico).
     * 2. Si se proporciona 'productoId', busca el MLA asociado al producto y calcula (sincrónico).
     * 3. Si no se proporciona ninguno, inicia cálculo masivo en background (asincrónico).
     *    - Usar GET /costo-envio/estado para ver progreso
     *    - Usar POST /costo-envio/cancelar para cancelar
     *    - Usar GET /costo-envio/resultado para obtener resultados
     *
     * @param mla (Opcional) Código MLA del producto (ej: MLA123456789)
     * @param productoId (Opcional) ID del producto para buscar su MLA asociado
     * @return DTO con el costo de envío calculado o confirmación de inicio de proceso masivo
     */
    @PostMapping("/costo-envio")
    @PreAuthorize(Permisos.MLAS_EDITAR)
    public ResponseEntity<?> calcularCostoEnvio(
            @RequestParam(required = false) String mla,
            @RequestParam(required = false) Integer productoId) {

        // Si se proporciona MLA, calcular solo para ese producto (sincrónico)
        if (mla != null && !mla.isBlank()) {
            CostoEnvioResponseDTO response = mercadoLibreService.calcularCostoEnvioGratis(mla);
            return ResponseEntity.ok(response);
        }

        // Si se proporciona productoId, buscar su MLA y calcular (sincrónico)
        if (productoId != null) {
            CostoEnvioResponseDTO response = mercadoLibreService.calcularCostoEnvioPorProducto(productoId);
            return ResponseEntity.ok(response);
        }

        // Si no se proporciona ninguno, iniciar proceso masivo en background
        boolean iniciado = mercadoLibreService.iniciarCalculoCostoEnvioTodos();
        if (iniciado) {
            return ResponseEntity.accepted().body(java.util.Map.of(
                    "mensaje", "Proceso masivo iniciado en background",
                    "iniciado", true,
                    "endpoints", java.util.Map.of(
                            "estado", "GET /api/ml/costo-envio/estado",
                            "cancelar", "POST /api/ml/costo-envio/cancelar",
                            "resultado", "GET /api/ml/costo-envio/resultado"
                    )));
        }
        return ResponseEntity.badRequest().body(java.util.Map.of(
                "mensaje", "Ya hay un proceso masivo en ejecución. Use GET /api/ml/costo-envio/estado para ver el progreso.",
                "iniciado", false));
    }

    /**
     * Cancela el proceso masivo de cálculo de costos de envío en ejecución.
     *
     * @return Mensaje indicando si se canceló o no había proceso en ejecución
     */
    @PostMapping("/costo-envio/cancelar")
    @PreAuthorize(Permisos.MLAS_EDITAR)
    public ResponseEntity<?> cancelarCostoEnvio() {
        boolean cancelado = mercadoLibreService.cancelarProcesoMasivo();
        if (cancelado) {
            return ResponseEntity.ok(java.util.Map.of(
                    "mensaje", "Solicitud de cancelación enviada. El proceso se detendrá después del MLA actual.",
                    "cancelado", true));
        }
        return ResponseEntity.ok(java.util.Map.of(
                "mensaje", "No hay proceso masivo en ejecución",
                "cancelado", false));
    }

    /**
     * Obtiene el estado actual del proceso masivo de cálculo de costos de envío.
     * Incluye progreso en tiempo real: total, procesados, exitosos, errores.
     *
     * @return Estado del proceso masivo
     */
    @GetMapping("/costo-envio/estado")
    @PreAuthorize(Permisos.MLAS_VER)
    public ResponseEntity<ProcesoMasivoEstadoDTO> estadoCostoEnvio() {
        return ResponseEntity.ok(mercadoLibreService.obtenerEstadoProcesoMasivo());
    }

    /**
     * Obtiene los resultados del último proceso masivo completado.
     *
     * @return Resultados del proceso masivo o mensaje si no hay resultados disponibles
     */
    @GetMapping("/costo-envio/resultado")
    @PreAuthorize(Permisos.MLAS_VER)
    public ResponseEntity<?> resultadoCostoEnvio() {
        CostoEnvioMasivoResponseDTO resultado = mercadoLibreService.obtenerResultadoProcesoMasivo();
        if (resultado != null) {
            return ResponseEntity.ok(resultado);
        }
        return ResponseEntity.ok(java.util.Map.of(
                "mensaje", "No hay resultados disponibles. El proceso aún no ha finalizado o no se ha ejecutado.",
                "disponible", false));
    }

    /**
     * Obtiene los costos de venta (comisiones) de un producto en MercadoLibre.
     *
     * Prioridad de parámetros:
     * 1. Si se proporciona 'mla', consulta solo para ese MLA (sincrónico).
     * 2. Si se proporciona 'productoId', busca el MLA asociado al producto y consulta (sincrónico).
     * 3. Si no se proporciona ninguno, inicia consulta masiva en background (asincrónico).
     *    - Usar GET /costo-venta/estado para ver progreso
     *    - Usar POST /costo-venta/cancelar para cancelar
     *    - Usar GET /costo-venta/resultado para obtener resultados
     *
     * @param mla (Opcional) Código MLA del producto (ej: MLA123456789)
     * @param productoId (Opcional) ID del producto para buscar su MLA asociado
     * @return DTO con los costos de venta o confirmación de inicio de proceso masivo
     */
    @PostMapping("/costo-venta")
    @PreAuthorize(Permisos.MLAS_EDITAR)
    public ResponseEntity<?> calcularCostoVenta(
            @RequestParam(required = false) String mla,
            @RequestParam(required = false) Integer productoId) {

        // Si se proporciona MLA, consultar solo para ese producto (sincrónico)
        if (mla != null && !mla.isBlank()) {
            return ResponseEntity.ok(mercadoLibreService.obtenerCostoVenta(mla));
        }

        // Si se proporciona productoId, buscar su MLA y consultar (sincrónico)
        if (productoId != null) {
            return ResponseEntity.ok(mercadoLibreService.obtenerCostoVentaPorProducto(productoId));
        }

        // Si no se proporciona ninguno, iniciar proceso masivo en background
        boolean iniciado = mercadoLibreService.iniciarCalculoCostoVentaTodos();
        if (iniciado) {
            return ResponseEntity.accepted().body(java.util.Map.of(
                    "mensaje", "Proceso masivo de costo de venta iniciado en background",
                    "iniciado", true,
                    "endpoints", java.util.Map.of(
                            "estado", "GET /api/ml/costo-venta/estado",
                            "cancelar", "POST /api/ml/costo-venta/cancelar",
                            "resultado", "GET /api/ml/costo-venta/resultado"
                    )));
        }
        return ResponseEntity.badRequest().body(java.util.Map.of(
                "mensaje", "Ya hay un proceso masivo de costo de venta en ejecución. Use GET /api/ml/costo-venta/estado para ver el progreso.",
                "iniciado", false));
    }

    /**
     * Cancela el proceso masivo de consulta de costos de venta en ejecución.
     *
     * @return Mensaje indicando si se canceló o no había proceso en ejecución
     */
    @PostMapping("/costo-venta/cancelar")
    @PreAuthorize(Permisos.MLAS_EDITAR)
    public ResponseEntity<?> cancelarCostoVenta() {
        boolean cancelado = mercadoLibreService.cancelarProcesoMasivoCostoVenta();
        if (cancelado) {
            return ResponseEntity.ok(java.util.Map.of(
                    "mensaje", "Solicitud de cancelación enviada. El proceso se detendrá después del MLA actual.",
                    "cancelado", true));
        }
        return ResponseEntity.ok(java.util.Map.of(
                "mensaje", "No hay proceso masivo de costo de venta en ejecución",
                "cancelado", false));
    }

    /**
     * Obtiene el estado actual del proceso masivo de consulta de costos de venta.
     * Incluye progreso en tiempo real: total, procesados, exitosos, errores.
     *
     * @return Estado del proceso masivo
     */
    @GetMapping("/costo-venta/estado")
    @PreAuthorize(Permisos.MLAS_VER)
    public ResponseEntity<ProcesoMasivoEstadoDTO> estadoCostoVenta() {
        return ResponseEntity.ok(mercadoLibreService.obtenerEstadoProcesoMasivoCostoVenta());
    }

    /**
     * Obtiene los resultados del último proceso masivo de costo de venta completado.
     *
     * @return Resultados del proceso masivo o mensaje si no hay resultados disponibles
     */
    @GetMapping("/costo-venta/resultado")
    @PreAuthorize(Permisos.MLAS_VER)
    public ResponseEntity<?> resultadoCostoVenta() {
        CostoVentaMasivoResponseDTO resultado = mercadoLibreService.obtenerResultadoProcesoMasivoCostoVenta();
        if (resultado != null) {
            return ResponseEntity.ok(resultado);
        }
        return ResponseEntity.ok(java.util.Map.of(
                "mensaje", "No hay resultados disponibles. El proceso aún no ha finalizado o no se ha ejecutado.",
                "disponible", false));
    }

    // =====================================================
    // CONFIGURACION
    // =====================================================

    @GetMapping("/configuracion")
    @PreAuthorize(Permisos.MLAS_VER)
    public ResponseEntity<ConfiguracionMlDTO> obtenerConfiguracion() {
        return ResponseEntity.ok(configuracionMlService.obtener());
    }

    @PutMapping("/configuracion")
    @PreAuthorize(Permisos.MLAS_EDITAR)
    public ResponseEntity<ConfiguracionMlDTO> actualizarConfiguracion(
            @Valid @RequestBody ConfiguracionMlDTO dto) {
        return ResponseEntity.ok(configuracionMlService.actualizar(dto));
    }
}
