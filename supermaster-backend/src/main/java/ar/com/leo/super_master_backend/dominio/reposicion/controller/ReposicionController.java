package ar.com.leo.super_master_backend.dominio.reposicion.controller;

import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.orden_compra.dto.OrdenCompraDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.dto.AjustePedidoDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.dto.ReposicionConfigDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.dto.ReposicionResultDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.service.ReposicionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reposiciones")
public class ReposicionController {

    private final ReposicionService reposicionService;

    // =====================================================
    // CÁLCULO ASYNC
    // =====================================================

    @PostMapping("/iniciar")
    @PreAuthorize(Permisos.REPOSICION_EDITAR)
    public ResponseEntity<Void> iniciarCalculo() {
        boolean iniciado = reposicionService.iniciarCalculo();
        if (iniciado) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.status(409).build();
    }

    @GetMapping("/estado")
    @PreAuthorize(Permisos.REPOSICION_VER)
    public ResponseEntity<ProcesoMasivoEstadoDTO> obtenerEstadoCalculo() {
        return ResponseEntity.ok(reposicionService.obtenerEstadoCalculo());
    }

    @PostMapping("/cancelar")
    @PreAuthorize(Permisos.REPOSICION_EDITAR)
    public ResponseEntity<Void> cancelarCalculo() {
        boolean cancelado = reposicionService.cancelarCalculo();
        if (cancelado) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(409).build();
    }

    // =====================================================
    // RESULTADO
    // =====================================================

    @GetMapping("/resultado")
    @PreAuthorize(Permisos.REPOSICION_VER)
    public ResponseEntity<ReposicionResultDTO> obtenerResultado() {
        ReposicionResultDTO resultado = reposicionService.obtenerResultado();
        if (resultado == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/resultado/excel")
    @PreAuthorize(Permisos.REPOSICION_VER)
    public ResponseEntity<byte[]> descargarExcelSugerencias() {
        ReposicionService.ExcelResult result = reposicionService.generarExcelSugerencias();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + result.filename())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(result.data());
    }

    @GetMapping("/resultado/excel/oc/{id}")
    @PreAuthorize(Permisos.REPOSICION_VER)
    public ResponseEntity<byte[]> descargarExcelOrdenCompra(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id
    ) {
        ReposicionService.ExcelResult result = reposicionService.generarExcelOrdenCompra(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + result.filename())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(result.data());
    }

    // =====================================================
    // AJUSTAR PEDIDOS
    // =====================================================

    @PutMapping("/resultado/ajustar")
    @PreAuthorize(Permisos.REPOSICION_EDITAR)
    public ResponseEntity<ReposicionResultDTO> ajustarPedidos(@Valid @RequestBody AjustePedidoDTO dto) {
        return ResponseEntity.ok(reposicionService.ajustarPedidos(dto));
    }

    // =====================================================
    // GENERAR OC
    // =====================================================

    @PostMapping("/generar-ordenes")
    @PreAuthorize(Permisos.REPOSICION_EDITAR)
    public ResponseEntity<List<OrdenCompraDTO>> generarOrdenes(
            @RequestParam(required = false) @Positive(message = "El ID de proveedor debe ser positivo") Integer proveedorId
    ) {
        List<OrdenCompraDTO> ordenes = reposicionService.generarOrdenesDesdeResultado(proveedorId);
        return ResponseEntity.status(201).body(ordenes);
    }

    // =====================================================
    // CONFIG
    // =====================================================

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('REPOSICION_VER') or hasAuthority('CONFIGURACION_VER')")
    public ResponseEntity<ReposicionConfigDTO> obtenerConfig() {
        return ResponseEntity.ok(reposicionService.obtenerConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('REPOSICION_EDITAR') or hasAuthority('CONFIGURACION_EDITAR')")
    public ResponseEntity<ReposicionConfigDTO> actualizarConfig(@Valid @RequestBody ReposicionConfigDTO dto) {
        return ResponseEntity.ok(reposicionService.actualizarConfig(dto));
    }
}
