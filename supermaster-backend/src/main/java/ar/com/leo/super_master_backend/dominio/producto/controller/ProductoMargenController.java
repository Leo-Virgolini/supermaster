package ar.com.leo.super_master_backend.dominio.producto.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenPatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoMargenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/productos/{productoId}/margen")
public class ProductoMargenController {

    private final ProductoMargenService service;

    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<ProductoMargenDTO> obtener(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId) {
        return service.obtener(productoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<ProductoMargenDTO> guardar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @Valid @RequestBody ProductoMargenDTO dto) {
        // Asegurar que el productoId del path coincida con el del DTO
        ProductoMargenDTO dtoConProductoId = new ProductoMargenDTO(
                dto.id(),
                productoId,
                dto.margenMinorista(),
                dto.margenMayorista(),
                dto.margenFijoMinorista(),
                dto.margenFijoMayorista(),
                dto.observaciones()
        );
        return ResponseEntity.ok(service.guardar(dtoConProductoId));
    }

    @PatchMapping
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<ProductoMargenDTO> patch(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId,
            @RequestBody ProductoMargenPatchDTO patch) {
        return ResponseEntity.ok(service.patch(productoId, patch));
    }

    @DeleteMapping
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID de producto debe ser positivo") Integer productoId) {
        service.eliminar(productoId);
        return ResponseEntity.noContent().build();
    }

}

