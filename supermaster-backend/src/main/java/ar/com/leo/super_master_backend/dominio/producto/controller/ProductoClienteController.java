package ar.com.leo.super_master_backend.dominio.producto.controller;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoClienteDTO;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoClienteService;
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
@RequestMapping("/api/productos/{productoId}/clientes")
public class ProductoClienteController {

    private final ProductoClienteService service;

    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<List<ProductoClienteDTO>> listar(@PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId) {
        return ResponseEntity.ok(service.listar(productoId));
    }

    @PostMapping("/{clienteId}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<ProductoClienteDTO> agregar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @PathVariable @Positive(message = "El ID de cliente debe ser positivo") Integer clienteId
    ) {
        ProductoClienteDTO creado = service.agregar(productoId, clienteId);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("")
                .build()
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @DeleteMapping("/{clienteId}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @PathVariable @Positive(message = "El ID de cliente debe ser positivo") Integer clienteId
    ) {
        service.eliminar(productoId, clienteId);
        return ResponseEntity.noContent().build();
    }

}