package ar.com.leo.super_master_backend.dominio.apto.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoCreateDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoPatchDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.apto.service.AptoService;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/aptos")
public class AptoController {

    private final AptoService service;

    @GetMapping
    @PreAuthorize(Permisos.MAESTROS_VER)
    public ResponseEntity<Page<AptoDTO>> listar(@RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_VER)
    public ResponseEntity<AptoDTO> obtener(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<AptoDTO> crear(@Valid @RequestBody AptoCreateDTO dto) {
        AptoDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<AptoDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody AptoUpdateDTO dto
    ) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<AptoDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @RequestBody AptoPatchDTO patch
    ) {
        return ResponseEntity.ok(service.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<Void> eliminar(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/productos")
    @PreAuthorize(Permisos.MAESTROS_VER)
    public ResponseEntity<List<ProductoResumenDTO>> listarProductos(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.listarProductos(id));
    }

}

