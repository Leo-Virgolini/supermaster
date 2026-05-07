package ar.com.leo.super_master_backend.apis.nube.controller;

import ar.com.leo.super_master_backend.apis.nube.dto.StockNubeDTO;
import ar.com.leo.super_master_backend.apis.nube.dto.VentaNubeDTO;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nube")
public class TiendaNubeController {

    private final TiendaNubeService tiendaNubeService;

    // =====================================================
    // STATUS
    // =====================================================

    @GetMapping("/status")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Map<String, Object>> obtenerStatus() {
        return ResponseEntity.ok(Map.of(
                "configurado", tiendaNubeService.isConfigured(),
                "servicio", "Tienda Nube",
                "stores", tiendaNubeService.getStoresDisponibles()
        ));
    }

    // =====================================================
    // VENTAS
    // =====================================================

    @GetMapping("/ventas")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<List<VentaNubeDTO>> obtenerTodasLasVentas() {
        return ResponseEntity.ok(tiendaNubeService.obtenerTodasLasVentas());
    }

    @GetMapping("/ventas/hogar")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<List<VentaNubeDTO>> obtenerVentasHogar() {
        return ResponseEntity.ok(tiendaNubeService.obtenerVentasHogar());
    }

    @GetMapping("/ventas/gastro")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<List<VentaNubeDTO>> obtenerVentasGastro() {
        return ResponseEntity.ok(tiendaNubeService.obtenerVentasGastro());
    }

    // =====================================================
    // STOCK
    // =====================================================

    @GetMapping("/stock/{sku}")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<StockNubeDTO> obtenerStock(@PathVariable String sku) {
        StockNubeDTO stock = tiendaNubeService.obtenerStockPorSku(sku);
        if (stock == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stock);
    }

    // =====================================================
    // ÓRDENES
    // =====================================================

    @GetMapping("/ordenes/{numero}")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<JsonNode> buscarOrden(@PathVariable String numero) {
        JsonNode orden = tiendaNubeService.buscarOrdenPorNumero(numero);
        if (orden == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(orden);
    }
}
