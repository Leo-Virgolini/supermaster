package ar.com.leo.super_master_backend.dominio.catalogo.controller;


import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoCreateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoPatchDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.dto.CatalogoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo.service.CatalogoService;
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
@RequestMapping("/api/catalogos")
public class CatalogoController {

    private final CatalogoService service;

    // ===============================
    // LISTAR
    // ===============================
    @GetMapping
    @PreAuthorize(Permisos.MAESTROS_VER)
    public ResponseEntity<Page<CatalogoDTO>> listar(@RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    // ===============================
    // OBTENER UNO
    // ===============================
    @GetMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_VER)
    public ResponseEntity<CatalogoDTO> obtener(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    // ===============================
    // CREAR
    // ===============================
    @PostMapping
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<CatalogoDTO> crear(@Valid @RequestBody CatalogoCreateDTO dto) {
        CatalogoDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    // ===============================
    // ACTUALIZAR
    // ===============================
    @PutMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<CatalogoDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody CatalogoUpdateDTO dto
    ) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<CatalogoDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @RequestBody CatalogoPatchDTO patch
    ) {
        return ResponseEntity.ok(service.patch(id, patch));
    }

    // ===============================
    // ELIMINAR
    // ===============================
    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<Void> eliminar(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // ===============================
    // LISTAR PRODUCTOS DEL CATÁLOGO
    // ===============================
    @GetMapping("/{id}/productos")
    @PreAuthorize(Permisos.MAESTROS_VER)
    public ResponseEntity<List<ProductoResumenDTO>> listarProductos(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.listarProductos(id));
    }

}

