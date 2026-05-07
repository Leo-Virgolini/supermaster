package ar.com.leo.super_master_backend.dominio.canal.controller;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaPatchDTO;
import ar.com.leo.super_master_backend.dominio.canal.service.CanalConceptoReglaService;
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
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/canal-concepto-reglas")
public class CanalConceptoReglaController {

    private final CanalConceptoReglaService service;

    @GetMapping
    @PreAuthorize(Permisos.CANALES_VER)
    public ResponseEntity<Page<CanalConceptoReglaDTO>> listar(Pageable pageable) {
        return ResponseEntity.ok(service.listar(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_VER)
    public ResponseEntity<CanalConceptoReglaDTO> obtener(
            @PathVariable @Positive(message = "El ID debe ser positivo") Long id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @GetMapping("/canal/{canalId}")
    @PreAuthorize(Permisos.CANALES_VER)
    public ResponseEntity<List<CanalConceptoReglaDTO>> listarPorCanal(
            @PathVariable @Positive(message = "El ID del canal debe ser positivo") Integer canalId) {
        return ResponseEntity.ok(service.listarPorCanal(canalId));
    }

    @GetMapping("/concepto/{conceptoId}")
    @PreAuthorize(Permisos.CANALES_VER)
    public ResponseEntity<List<CanalConceptoReglaDTO>> listarPorConcepto(
            @PathVariable @Positive(message = "El ID del concepto debe ser positivo") Integer conceptoId) {
        return ResponseEntity.ok(service.listarPorConcepto(conceptoId));
    }

    @PostMapping
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<CanalConceptoReglaDTO> crear(@Valid @RequestBody CanalConceptoReglaCreateDTO dto) {
        CanalConceptoReglaDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<CanalConceptoReglaDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Long id,
            @Valid @RequestBody CanalConceptoReglaUpdateDTO dto
    ) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.CANALES_EDITAR)
    public ResponseEntity<CanalConceptoReglaDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Long id,
            @RequestBody CanalConceptoReglaPatchDTO patch
    ) {
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

