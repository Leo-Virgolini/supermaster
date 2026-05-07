package ar.com.leo.super_master_backend.dominio.producto.controller;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoAptoDTO;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoAptoService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/productos/{productoId}/aptos")
public class ProductoAptoController {

    private final ProductoAptoService productoAptoService;

    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<List<ProductoAptoDTO>> listar(@PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId) {
        return ResponseEntity.ok(productoAptoService.listar(productoId));
    }

    @PostMapping("/{aptoId}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<ProductoAptoDTO> agregar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @PathVariable @Positive(message = "El ID de apto debe ser positivo") Integer aptoId
    ) {
        ProductoAptoDTO creado = productoAptoService.agregar(productoId, aptoId);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("")
                .build()
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @DeleteMapping("/{aptoId}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @PathVariable @Positive(message = "El ID de apto debe ser positivo") Integer aptoId
    ) {
        productoAptoService.eliminar(productoId, aptoId);
        return ResponseEntity.noContent().build();
    }

}