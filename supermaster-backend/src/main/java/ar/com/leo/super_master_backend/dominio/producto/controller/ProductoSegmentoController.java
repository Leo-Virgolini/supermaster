package ar.com.leo.super_master_backend.dominio.producto.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoSegmentoDTO;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoSegmentoService;
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
@RequestMapping("/api/productos/{productoId}/segmentos")
public class ProductoSegmentoController {

    private final ProductoSegmentoService service;

    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<List<ProductoSegmentoDTO>> listar(@PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId) {
        return ResponseEntity.ok(service.listar(productoId));
    }

    @PostMapping("/{segmentoId}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<ProductoSegmentoDTO> agregar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @PathVariable @Positive(message = "El ID de segmento debe ser positivo") Integer segmentoId
    ) {
        ProductoSegmentoDTO creado = service.agregar(productoId, segmentoId);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("")
                .build()
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @DeleteMapping("/{segmentoId}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @PathVariable @Positive(message = "El ID de segmento debe ser positivo") Integer segmentoId
    ) {
        service.eliminar(productoId, segmentoId);
        return ResponseEntity.noContent().build();
    }

}
