package ar.com.leo.super_master_backend.dominio.producto.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCanalPrecioInfladoDTO;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoCanalPrecioInfladoService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lista todas las asignaciones de precios inflados de un producto (todos los
 * canales de una). Complementa al controller por canal, que opera una a una.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/productos/{productoId}/precios-inflados")
public class ProductoPreciosInfladosController {

    private final ProductoCanalPrecioInfladoService service;

    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<List<ProductoCanalPrecioInfladoDTO>> listar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId) {
        return ResponseEntity.ok(service.listarPorProducto(productoId));
    }
}
