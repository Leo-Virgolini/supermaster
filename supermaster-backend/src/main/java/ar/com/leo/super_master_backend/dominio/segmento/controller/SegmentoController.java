package ar.com.leo.super_master_backend.dominio.segmento.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoCreateDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoPatchDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.segmento.service.SegmentoService;
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
@RequestMapping("/api/segmentos")
public class SegmentoController {

    private final SegmentoService service;

    @GetMapping
    @PreAuthorize(Permisos.MAESTROS_VER)
    public ResponseEntity<Page<SegmentoDTO>> listar(@RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_VER)
    public ResponseEntity<SegmentoDTO> obtener(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @GetMapping("/{id}/auditoria")
    @PreAuthorize(Permisos.MAESTROS_VER)
    public ResponseEntity<Page<AuditoriaCambioDTO>> listarAuditoria(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            Pageable pageable) {
        return ResponseEntity.ok(service.listarAuditoria(id, pageable));
    }

    @PostMapping
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<SegmentoDTO> crear(@Valid @RequestBody SegmentoCreateDTO dto) {
        SegmentoDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<SegmentoDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody SegmentoUpdateDTO dto
    ) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.MAESTROS_EDITAR)
    public ResponseEntity<SegmentoDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @RequestBody SegmentoPatchDTO patch
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
