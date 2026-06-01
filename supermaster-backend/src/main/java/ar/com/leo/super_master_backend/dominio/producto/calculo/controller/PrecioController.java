package ar.com.leo.super_master_backend.dominio.producto.calculo.controller;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.common.dto.RecalculoPendienteDTO;
import ar.com.leo.super_master_backend.dominio.common.service.AplicadorPendientesService;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteSseService;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.CalculoResultadoDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.FormulaCalculoDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.RecalculoMasivoResultDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.SimulacionPrecioInputDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.SimulacionResultadoDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.CalculoPrecioService;
import ar.com.leo.super_master_backend.dominio.producto.dto.CanalPreciosDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoConPreciosDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoFilter;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoService;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/precios")
public class PrecioController {

    private final ProductoService productoService;
    private final CalculoPrecioService calculoPrecioService;
    private final RecalculoPendienteService recalculoPendienteService;
    private final RecalculoPendienteSseService recalculoPendienteSseService;
    private final AplicadorPendientesService aplicadorPendientesService;
    private final AuditoriaService auditoriaService;

    // =====================================================
    // LISTAR PRODUCTOS CON PRECIOS (PAGINADO)
    // =====================================================
    @GetMapping
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<Page<ProductoConPreciosDTO>> listar(

            // =======================
            // 0) FILTRO POR ID
            // =======================
            @RequestParam(required = false) Integer productoId,

            // =======================
            // 1) TEXTO
            // =======================
            @RequestParam(required = false) String search,

            // =======================
            // 1.1) FILTROS DE TEXTO DEDICADOS
            // =======================
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String codExt,
            @RequestParam(required = false) String descripcion,
            @RequestParam(required = false) String tituloWeb,

            // =======================
            // 2) BOOLEANOS / NUMÉRICOS
            // =======================
            @RequestParam(required = false) Boolean esCombo,
            @RequestParam(required = false) Integer uxb,
            @RequestParam(required = false) Boolean esMaquina,
            @RequestParam(required = false) Boolean tieneMla,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) TagReposicion tagReposicion,
            @RequestParam(required = false) List<Tag> tags,

            // =======================
            // 2.1) FILTROS MLA
            // =======================
            @RequestParam(required = false) String mla,
            @RequestParam(required = false) String mlau,
            @RequestParam(required = false) BigDecimal precioEnvioMin,
            @RequestParam(required = false) BigDecimal precioEnvioMax,
            @RequestParam(required = false) BigDecimal comisionPorcentajeMin,
            @RequestParam(required = false) BigDecimal comisionPorcentajeMax,
            @RequestParam(required = false) Boolean tieneComision,
            @RequestParam(required = false) Boolean tienePrecioEnvio,

            // =======================
            // 3) MANY-TO-ONE (multi-valor)
            // =======================
            @RequestParam(required = false) List<Integer> marcaIds,
            @RequestParam(required = false) List<Integer> origenIds,
            @RequestParam(required = false) List<Integer> tipoIds,
            @RequestParam(required = false) List<Integer> clasifGralIds,
            @RequestParam(required = false) List<Integer> clasifGastroIds,
            @RequestParam(required = false) List<Integer> proveedorIds,
            @RequestParam(required = false) List<Integer> materialIds,

            // =======================
            // 4) RANGOS (costo / IVA / stock)
            // =======================
            @RequestParam(required = false) BigDecimal costoMin,
            @RequestParam(required = false) BigDecimal costoMax,
            @RequestParam(required = false) BigDecimal ivaMin,
            @RequestParam(required = false) BigDecimal ivaMax,
            @RequestParam(required = false) Integer stockMin,
            @RequestParam(required = false) Integer stockMax,

            // =======================
            // 5) RANGO PVP
            // =======================
            @RequestParam(required = false) BigDecimal pvpMin,
            @RequestParam(required = false) BigDecimal pvpMax,
            @RequestParam(required = false) Integer pvpCanalId,

            // =======================
            // 6) FECHAS
            // =======================
            @RequestParam(required = false) LocalDate desdeFechaUltimoCosto,
            @RequestParam(required = false) LocalDate hastaFechaUltimoCosto,

            @RequestParam(required = false) LocalDate desdeFechaCreacion,
            @RequestParam(required = false) LocalDate hastaFechaCreacion,

            @RequestParam(required = false) LocalDate desdeFechaModificacion,
            @RequestParam(required = false) LocalDate hastaFechaModificacion,

            // =======================
            // 7) MANY-TO-MANY
            // =======================
            @RequestParam(required = false) List<Integer> aptoIds,
            @RequestParam(required = false) List<Integer> canalIds,
            @RequestParam(required = false) List<Integer> catalogoIds,
            @RequestParam(required = false) List<Integer> clienteIds,
            @RequestParam(required = false) List<Integer> mlaIds,

            // =======================
            // 8) FILTRAR PRECIOS POR CANAL (también usado para ordenamiento)
            // =======================
            @RequestParam(required = false) Integer canalId,

            // =======================
            // 9) FILTRAR PRECIOS POR CUOTAS (también usado para ordenamiento)
            // =======================
            @RequestParam(required = false) Integer cuotas,

            Pageable pageable
    ) {

        ProductoFilter filter = new ProductoFilter(
                productoId,
                search,
                // Filtros de texto dedicados
                sku,
                codExt,
                descripcion,
                tituloWeb,
                // Booleanos/Numéricos
                esCombo,
                uxb,
                esMaquina,
                tieneMla,
                activo,
                tagReposicion,
                tags,
                // Filtros MLA
                mla,
                mlau,
                precioEnvioMin,
                precioEnvioMax,
                comisionPorcentajeMin,
                comisionPorcentajeMax,
                tieneComision,
                tienePrecioEnvio,
                // Many-to-One (multi-valor)
                marcaIds,
                origenIds,
                tipoIds,
                clasifGralIds,
                clasifGastroIds,
                proveedorIds,
                materialIds,
                costoMin,
                costoMax,
                ivaMin,
                ivaMax,
                stockMin,
                stockMax,
                pvpMin,
                pvpMax,
                pvpCanalId,
                desdeFechaUltimoCosto,
                hastaFechaUltimoCosto,
                desdeFechaCreacion,
                hastaFechaCreacion,
                desdeFechaModificacion,
                hastaFechaModificacion,
                aptoIds,
                canalIds,
                catalogoIds,
                clienteIds,
                mlaIds,
                canalId,
                cuotas
        );

        return ResponseEntity.ok(productoService.listarConPrecios(filter, pageable));
    }

    // =====================================================
    // OBTENER FÓRMULA DEL CÁLCULO PASO A PASO
    // =====================================================
    @GetMapping("/formula")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<FormulaCalculoDTO> obtenerFormula(
            @RequestParam @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @RequestParam @Positive(message = "El ID de canal debe ser positivo") Integer canalId,
            @RequestParam Integer cuotas  // -1=transferencia, 0=contado, >0=cuotas
    ) {
        return ResponseEntity.ok(
                calculoPrecioService.obtenerFormulaCalculo(productoId, canalId, cuotas)
        );
    }

    // =====================================================
    // SIMULAR FÓRMULA DE PRECIO PARA UN PRODUCTO HIPOTÉTICO
    // No requiere que el producto exista en la BD: el motor de cálculo se ejecuta
    // sobre entidades transitorias construidas a partir del input.
    // Devuelve solo la fórmula paso a paso (sin indicadores).
    // =====================================================
    @PostMapping("/simular")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<FormulaCalculoDTO> simularFormula(@Valid @RequestBody SimulacionPrecioInputDTO input) {
        return ResponseEntity.ok(calculoPrecioService.simularFormulaCalculo(input));
    }

    // =====================================================
    // SIMULAR PRECIO COMPLETO: fórmula + indicadores (PVP, ganancia, costos venta,
    // ingreso neto, márgenes, markup). Mismo conjunto de métricas que el Monitor de Precios.
    // =====================================================
    @PostMapping("/simular-completo")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<SimulacionResultadoDTO> simularCompleto(@Valid @RequestBody SimulacionPrecioInputDTO input) {
        return ResponseEntity.ok(calculoPrecioService.simularPrecioCompleto(input));
    }

    // =====================================================
    // CALCULAR Y GUARDAR PRECIOS
    // - Sin parámetros: recalcula TODOS los productos en TODOS los canales
    // - Solo productoId: recalcula el producto en TODOS sus canales (todas las cuotas)
    // - productoId + cuotas: recalcula el producto en TODOS sus canales (solo esas cuotas)
    // - productoId + canalId: recalcula el producto en ese canal (todas las cuotas)
    // - productoId + canalId + cuotas: recalcula solo para esas cuotas en ese canal
    // =====================================================
    @PostMapping("/calcular")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<CalculoResultadoDTO> calcular(
            @RequestParam(required = false) @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @RequestParam(required = false) @Positive(message = "El ID de canal debe ser positivo") Integer canalId,
            @RequestParam(required = false) Integer cuotas
    ) {
        // Sin parámetros: recalcular todos
        if (productoId == null) {
            var resultado = calculoPrecioService.recalcularTodos();
            return ResponseEntity.ok(CalculoResultadoDTO.masivo(resultado));
        }

        // productoId sin canalId
        if (canalId == null) {
            // Con cuotas: recalcular todos los canales solo para esas cuotas
            if (cuotas != null) {
                var canales = calculoPrecioService.recalcularProductoTodosCanales(productoId, cuotas);
                return ResponseEntity.ok(CalculoResultadoDTO.of(canales));
            }
            // Sin cuotas: recalcular todos los canales, todas las cuotas
            var canales = calculoPrecioService.recalcularProductoTodosCanales(productoId);
            return ResponseEntity.ok(CalculoResultadoDTO.of(canales));
        }

        // productoId + canalId (+ opcional cuotas): recalcular ese canal
        CanalPreciosDTO canal = calculoPrecioService.recalcularYGuardar(productoId, canalId, cuotas);
        return ResponseEntity.ok(CalculoResultadoDTO.of(canal));
    }

    // =====================================================
    // RECÁLCULO MASIVO ASINCRÓNICO
    // =====================================================

    @PostMapping("/recalculo-masivo/iniciar")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<Void> iniciarRecalculoMasivo() {
        boolean started = calculoPrecioService.iniciarRecalculoMasivo();
        if (started) {
            // limpiar() ya NO se llama acá: el masivo limpia los flags al finalizar
            // con éxito, evitando la ventana donde el frontend leería precios sin recalcular.
            auditoriaService.registrarEvento(
                    AuditoriaEntidad.RECALCULO, "masivo", AuditoriaAccion.CREATE,
                    "recalculo_masivo_iniciado", "todos los productos y canales", usernameActual(), "MANUAL");
        }
        return started ? ResponseEntity.ok().build() : ResponseEntity.status(409).build();
    }

    // =====================================================
    // RECÁLCULO PENDIENTE (banner global)
    // El sistema marca cambios como pendientes en lugar de disparar el recálculo
    // automático. El usuario aplica todos los pendientes de una vez con un click.
    // =====================================================

    @GetMapping("/recalculo-pendiente")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<RecalculoPendienteDTO> estadoPendiente() {
        return ResponseEntity.ok(recalculoPendienteService.estado());
    }

    /**
     * Stream SSE que empuja el snapshot de recálculos pendientes cada vez que cambia
     * (cuando se marca un nuevo pendiente o se limpia el contador) y un heartbeat cada 25s.
     * Endpoint público (ver SecurityConfig): la info no es sensible, y así el front puede
     * usar `EventSource` nativo sin el workaround de fetch para pasar `Authorization`.
     */
    @GetMapping(value = "/recalculo-pendiente/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPendiente(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return recalculoPendienteSseService.subscribe(recalculoPendienteService.estado());
    }

    @PostMapping("/recalculo-pendiente/aplicar")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<Void> aplicarRecalculoPendiente() {
        // Snapshot del plan ANTES de cualquier acción (sin limpiar). Solo limpiamos
        // si efectivamente arrancamos el recálculo.
        var plan = recalculoPendienteService.plan();
        if (plan.estaVacio()) {
            return ResponseEntity.ok().build();
        }

        // Si hay scope amplio (cambio en concepto, clasif, MLA, proveedor, etc.) → masivo async.
        if (plan.recalcularTodo()) {
            boolean started = calculoPrecioService.iniciarRecalculoMasivo();
            if (started) {
                // limpiar() ya NO se llama acá: el masivo limpia al final con éxito.
                auditoriaService.registrarEvento(
                        AuditoriaEntidad.RECALCULO, "pendiente-todo", AuditoriaAccion.CREATE,
                        "aplicar_recalculo_pendiente", "recálculo masivo (todos los productos y canales)",
                        usernameActual(), "MANUAL");
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.status(409).build();
        }

        // Scope acotado: despachamos en background para no bloquear la HTTP request.
        // Single-flight: si ya hay un plan ejecutándose, devolver 409 sin tocar nada.
        if (!aplicadorPendientesService.intentarAdquirir()) {
            return ResponseEntity.status(409).build();
        }
        // limpiar() ya NO se llama acá: el bulk executor desmarca producto-por-producto
        // y canal-por-canal tras cada recálculo exitoso. Cambios nuevos que entren durante
        // el async quedan marcados y se aplican en el próximo ciclo de forma natural.
        // La auditoría se registra al FINAL del proceso (con el conteo real de precios
        // recalculados); pasamos el usuario actual porque el SecurityContext no llega al
        // hilo @Async.
        aplicadorPendientesService.ejecutarPlanScopedAsync(plan, usernameActual());
        return ResponseEntity.ok().build();
    }

    /** Username autenticado actual (o null si anónimo). Se captura en el hilo HTTP. */
    private String usernameActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    @GetMapping("/recalculo-masivo/estado")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<ProcesoMasivoEstadoDTO> estadoRecalculoMasivo() {
        return ResponseEntity.ok(calculoPrecioService.obtenerEstadoRecalculo());
    }

    @PostMapping("/recalculo-masivo/cancelar")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<Void> cancelarRecalculoMasivo() {
        calculoPrecioService.cancelarRecalculo();
        auditoriaService.registrarCambios(
                AuditoriaEntidad.RECALCULO,
                null,
                "masivo",
                AuditoriaAccion.DELETE,
                Map.of("evento", "recalculo_masivo_en_curso"),
                Map.of("evento", "recalculo_masivo_cancelado")
        );
        return ResponseEntity.ok().build();
    }

    @GetMapping("/recalculo-masivo/resultado")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<RecalculoMasivoResultDTO> resultadoRecalculoMasivo() {
        RecalculoMasivoResultDTO result = calculoPrecioService.obtenerResultadoRecalculo();
        if (result == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(result);
    }

}
