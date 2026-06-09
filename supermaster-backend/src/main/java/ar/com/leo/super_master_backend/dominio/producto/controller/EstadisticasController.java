package ar.com.leo.super_master_backend.dominio.producto.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO.*;
import ar.com.leo.super_master_backend.dominio.producto.service.EstadisticasService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/estadisticas")
public class EstadisticasController {

    private final EstadisticasService service;

    @GetMapping
    @PreAuthorize(Permisos.ESTADISTICAS_VER)
    public ResponseEntity<EstadisticasDTO> obtenerEstadisticas() {
        return ResponseEntity.ok(service.obtenerEstadisticas());
    }

    @GetMapping("/resumen")
    @PreAuthorize(Permisos.ESTADISTICAS_VER)
    public ResponseEntity<ResumenDTO> obtenerResumen() {
        return ResponseEntity.ok(service.obtenerResumen());
    }

    @GetMapping("/cuotas")
    @PreAuthorize(Permisos.ESTADISTICAS_VER)
    public ResponseEntity<List<CuotaDisponibleDTO>> obtenerCuotasDisponibles() {
        return ResponseEntity.ok(service.obtenerCuotasDisponibles());
    }

    @GetMapping("/margenes")
    @PreAuthorize(Permisos.ESTADISTICAS_VER)
    public ResponseEntity<MargenesPorCuotasDTO> obtenerMargenes(
            @RequestParam(required = false) Integer cuotas) {
        return ResponseEntity.ok(service.obtenerMargenesPorCuotas(cuotas));
    }

    @GetMapping("/proveedores")
    @PreAuthorize(Permisos.ESTADISTICAS_VER)
    public ResponseEntity<List<ProductosPorProveedor>> obtenerProductosPorProveedor() {
        return ResponseEntity.ok(service.obtenerProductosPorProveedor());
    }

    @GetMapping("/catalogos")
    @PreAuthorize(Permisos.ESTADISTICAS_VER)
    public ResponseEntity<List<ProductosPorCatalogo>> obtenerProductosPorCatalogo() {
        return ResponseEntity.ok(service.obtenerProductosPorCatalogo());
    }

    @GetMapping("/margen-negativo")
    @PreAuthorize(Permisos.ESTADISTICAS_VER)
    public ResponseEntity<List<ProductoMargenNegativo>> obtenerProductosConMargenNegativo() {
        return ResponseEntity.ok(service.obtenerProductosConMargenNegativo());
    }
}
