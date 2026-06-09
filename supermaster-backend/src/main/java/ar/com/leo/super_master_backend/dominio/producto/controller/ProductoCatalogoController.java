package ar.com.leo.super_master_backend.dominio.producto.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCatalogoDTO;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoCatalogoService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/productos/{productoId}/catalogos")
public class ProductoCatalogoController {

    private final ProductoCatalogoService service;

    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<List<ProductoCatalogoDTO>> listar(@PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId) {
        return ResponseEntity.ok(service.listar(productoId));
    }

    @PostMapping("/{catalogoId}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<ProductoCatalogoDTO> agregar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @PathVariable @Positive(message = "El ID de catálogo debe ser positivo") Integer catalogoId
    ) {
        ProductoCatalogoDTO creado = service.agregar(productoId, catalogoId);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("")
                .build()
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @DeleteMapping("/{catalogoId}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @PathVariable @Positive(message = "El ID de catálogo debe ser positivo") Integer catalogoId
    ) {
        service.eliminar(productoId, catalogoId);
        return ResponseEntity.noContent().build();
    }

}