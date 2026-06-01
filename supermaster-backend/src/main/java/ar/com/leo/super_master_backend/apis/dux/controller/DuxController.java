package ar.com.leo.super_master_backend.apis.dux.controller;

import ar.com.leo.super_master_backend.dominio.common.response.ErrorResponse;
import ar.com.leo.super_master_backend.apis.dux.dto.ExportDuxRequestDTO;
import ar.com.leo.super_master_backend.apis.dux.dto.ExportDuxResultDTO;
import ar.com.leo.super_master_backend.apis.dux.dto.ImportDuxResultDTO;
import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService.ProductoPrecioData;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dux")
public class DuxController {

    private final DuxService duxService;

    // =====================================================
    // STATUS
    // =====================================================

    @GetMapping("/status")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Map<String, Object>> obtenerStatus() {
        return ResponseEntity.ok(Map.of(
                "configurado", duxService.isConfigured(),
                "servicio", "DUX ERP"
        ));
    }

    // =====================================================
    // RATE LIMIT
    // =====================================================

    @GetMapping("/config/rate-limit")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Map<String, Object>> getRateLimit() {
        return ResponseEntity.ok(Map.of("segundos", duxService.getRateLimitSegundos()));
    }

    @PutMapping("/config/rate-limit")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Map<String, Object>> setRateLimit(@RequestParam double segundos) {
        duxService.setRateLimitSegundos(segundos);
        return ResponseEntity.ok(Map.of("segundos", duxService.getRateLimitSegundos()));
    }

    // =====================================================
    // PRODUCTOS
    // =====================================================

    @GetMapping("/productos/{codItem}")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Item> obtenerProducto(@PathVariable String codItem) {
        Item item = duxService.obtenerProductoPorCodigo(codItem);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }

    // =====================================================
    // LISTAS DE PRECIOS
    // =====================================================

    @GetMapping("/listas-precios")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<JsonNode> obtenerListasPrecios() {
        return ResponseEntity.ok(duxService.obtenerListasPrecios());
    }

    @GetMapping("/listas-precios/{nombre}/id")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Map<String, Object>> obtenerIdListaPrecio(@PathVariable String nombre) {
        long id = duxService.obtenerIdListaPrecio(nombre);
        return ResponseEntity.ok(Map.of(
                "nombre", nombre,
                "id", id
        ));
    }

    /**
     * Actualiza precios de productos en una lista de precios de DUX.
     *
     * <p><b>IMPORTANTE:</b> Es obligatorio enviar el tipo de producto (SIMPLE o COMBO) junto con el precio.
     * Si no se envía el tipo correcto, DUX desconfigura el producto y puede perder su configuración
     * de componentes (en caso de combos) u otras propiedades.</p>
     *
     * @param idLista   ID de la lista de precios en DUX
     * @param productos Lista de productos con SKU, tipo (SIMPLE/COMBO) y precio
     */
    @PostMapping("/listas-precios/{idLista}/precios")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<?> modificarPrecios(
            @PathVariable long idLista,
            @RequestBody List<ProductoPrecioRequest> productos) {

        Map<String, ProductoPrecioData> productosMap = new HashMap<>();
        for (ProductoPrecioRequest p : productos) {
            productosMap.put(p.sku(), new ProductoPrecioData(p.tipo(), p.precio()));
        }

        int idProceso = duxService.modificarListaPrecios(productosMap, idLista);

        if (idProceso == 0) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("No se pudo iniciar el proceso de actualización", "/api/dux/listas-precios/" + idLista + "/precios"));
        }

        return ResponseEntity.ok(Map.of(
                "idProceso", idProceso,
                "mensaje", "Proceso de actualización iniciado"
        ));
    }

    // =====================================================
    // PROCESOS
    // =====================================================

    @GetMapping("/procesos/{idProceso}/estado")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Map<String, Object>> obtenerEstadoProceso(@PathVariable int idProceso) {
        String estado = duxService.obtenerEstadoProceso(idProceso);
        return ResponseEntity.ok(Map.of(
                "idProceso", idProceso,
                "estado", estado
        ));
    }

    // =====================================================
    // IMPORTACIÓN (async)
    // =====================================================

    @PostMapping("/importar-productos")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<?> importarProductos() {
        boolean iniciado = duxService.iniciarImportacion();
        if (iniciado) {
            return ResponseEntity.accepted().body(Map.of(
                    "mensaje", "Importación DUX iniciada en background",
                    "iniciado", true,
                    "endpoints", Map.of(
                            "estado", "GET /api/dux/importar-productos/estado",
                            "cancelar", "POST /api/dux/importar-productos/cancelar",
                            "resultado", "GET /api/dux/importar-productos/resultado"
                    )));
        }
        return ResponseEntity.badRequest().body(Map.of(
                "mensaje", "Ya hay una importación en ejecución. Use GET /api/dux/importar-productos/estado para ver el progreso.",
                "iniciado", false));
    }

    @GetMapping("/importar-productos/estado")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ProcesoMasivoEstadoDTO> estadoImportacion() {
        return ResponseEntity.ok(duxService.obtenerEstadoImportacion());
    }

    @PostMapping("/importar-productos/cancelar")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<?> cancelarImportacion() {
        boolean cancelado = duxService.cancelarImportacion();
        if (cancelado) {
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Solicitud de cancelación enviada. El proceso se detendrá después del item actual.",
                    "cancelado", true));
        }
        return ResponseEntity.ok(Map.of(
                "mensaje", "No hay importación en ejecución",
                "cancelado", false));
    }

    @GetMapping("/importar-productos/resultado")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<?> resultadoImportacion() {
        ImportDuxResultDTO resultado = duxService.obtenerResultadoImportacion();
        if (resultado != null) {
            return ResponseEntity.ok(resultado);
        }
        return ResponseEntity.ok(Map.of(
                "mensaje", "No hay resultados disponibles. El proceso aún no ha finalizado o no se ha ejecutado.",
                "disponible", false));
    }

    // =====================================================
    // EMPRESAS Y SUCURSALES
    // =====================================================

    @GetMapping("/empresas")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<JsonNode> obtenerEmpresas() {
        return ResponseEntity.ok(duxService.obtenerEmpresas());
    }

    @GetMapping("/empresas/{idEmpresa}/sucursales")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<JsonNode> obtenerSucursales(@PathVariable int idEmpresa) {
        return ResponseEntity.ok(duxService.obtenerSucursales(idEmpresa));
    }

    // =====================================================
    // EXPORTACIÓN
    // =====================================================

    @PostMapping("/exportar-productos")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<ExportDuxResultDTO> exportarProductos(
            @RequestBody(required = false) ExportDuxRequestDTO request) {
        List<String> skus = request != null ? request.skus() : null;
        ExportDuxResultDTO resultado = duxService.exportarProductosADux(skus);
        return ResponseEntity.ok(resultado);
    }

    // =====================================================
    // DEUDAS CLIENTES (COMPROBANTES) — Async
    // =====================================================

    @PostMapping("/deudas-clientes/iniciar")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<?> iniciarConsultaDeudas(
            @RequestParam String fechaDesde,
            @RequestParam String fechaHasta,
            @RequestParam int idEmpresa,
            @RequestParam List<Integer> idsSucursal,
            @RequestParam(required = false) Boolean conCobro,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) Boolean anuladas) {

        boolean iniciado = duxService.iniciarConsultaDeudas(fechaDesde, fechaHasta, idEmpresa, idsSucursal, conCobro, cliente, anuladas);
        if (iniciado) {
            return ResponseEntity.accepted().body(Map.of(
                    "mensaje", "Consulta de deudas iniciada en background",
                    "iniciado", true,
                    "endpoints", Map.of(
                            "estado", "GET /api/dux/deudas-clientes/estado",
                            "cancelar", "POST /api/dux/deudas-clientes/cancelar",
                            "resultado", "GET /api/dux/deudas-clientes/resultado"
                    )));
        }
        return ResponseEntity.badRequest().body(Map.of(
                "mensaje", "Ya hay una consulta en ejecución o hay otro proceso DUX activo. Use GET /api/dux/deudas-clientes/estado para ver el progreso.",
                "iniciado", false));
    }

    @GetMapping("/deudas-clientes/estado")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ProcesoMasivoEstadoDTO> estadoConsultaDeudas() {
        return ResponseEntity.ok(duxService.obtenerEstadoDeudas());
    }

    @PostMapping("/deudas-clientes/cancelar")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<?> cancelarConsultaDeudas() {
        boolean cancelado = duxService.cancelarConsultaDeudas();
        if (cancelado) {
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Solicitud de cancelación enviada. El proceso se detendrá después de la sucursal actual.",
                    "cancelado", true));
        }
        return ResponseEntity.ok(Map.of(
                "mensaje", "No hay consulta de deudas en ejecución",
                "cancelado", false));
    }

    @GetMapping("/deudas-clientes/resultado")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<?> resultadoConsultaDeudas() {
        Map<String, Object> resultado = duxService.obtenerResultadoDeudas();
        if (resultado != null) {
            return ResponseEntity.ok(resultado);
        }
        return ResponseEntity.ok(Map.of(
                "mensaje", "No hay resultados disponibles. El proceso aún no ha finalizado o no se ha ejecutado.",
                "disponible", false));
    }

    // =====================================================
    // DTOs
    // =====================================================

    /**
     * Request para actualizar precio de un producto en DUX.
     *
     * @param sku    Código del producto (cod_item en DUX)
     * @param tipo   Tipo de producto: "SIMPLE" o "COMBO". <b>OBLIGATORIO</b> - si no se envía
     *               correctamente, DUX desconfigura el producto
     * @param precio Precio del producto
     */
    public record ProductoPrecioRequest(
            String sku,
            String tipo,
            double precio
    ) {}
}
