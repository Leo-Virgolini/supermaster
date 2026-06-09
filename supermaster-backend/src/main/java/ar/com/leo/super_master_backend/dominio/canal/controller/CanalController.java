package ar.com.leo.super_master_backend.dominio.canal.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalPatchDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.service.CanalService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/canales")
public class CanalController {

    private final CanalService service;

    @GetMapping
    @PreAuthorize(Permisos.CANALES_VER)
    public ResponseEntity<Page<CanalDTO>> listar(@RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_VER)
    public ResponseEntity<CanalDTO> obtener(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<CanalDTO> crear(@Valid @RequestBody CanalCreateDTO dto) {
        CanalDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<CanalDTO> actualizar(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody CanalUpdateDTO dto) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<CanalDTO> patch(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @RequestBody CanalPatchDTO patch) {
        return ResponseEntity.ok(service.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<Void> eliminar(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

}

