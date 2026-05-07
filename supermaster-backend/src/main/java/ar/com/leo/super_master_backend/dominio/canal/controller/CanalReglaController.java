package ar.com.leo.super_master_backend.dominio.canal.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaPatchDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.service.CanalReglaService;
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
@RequestMapping("/api/canal-reglas")
public class CanalReglaController {

    private final CanalReglaService service;

    @GetMapping
    @PreAuthorize(Permisos.CANALES_VER)
    public ResponseEntity<Page<CanalReglaDTO>> listar(Pageable pageable) {
        return ResponseEntity.ok(service.listar(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_VER)
    public ResponseEntity<CanalReglaDTO> obtener(
            @PathVariable @Positive(message = "El ID debe ser positivo") Long id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @GetMapping("/canal/{canalId}")
    @PreAuthorize(Permisos.CANALES_VER)
    public ResponseEntity<List<CanalReglaDTO>> listarPorCanal(
            @PathVariable @Positive(message = "El ID del canal debe ser positivo") Integer canalId) {
        return ResponseEntity.ok(service.listarPorCanal(canalId));
    }

    @PostMapping
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<CanalReglaDTO> crear(@Valid @RequestBody CanalReglaCreateDTO dto) {
        CanalReglaDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<CanalReglaDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Long id,
            @Valid @RequestBody CanalReglaUpdateDTO dto) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<CanalReglaDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Long id,
            @RequestBody CanalReglaPatchDTO patch) {
        return ResponseEntity.ok(service.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
