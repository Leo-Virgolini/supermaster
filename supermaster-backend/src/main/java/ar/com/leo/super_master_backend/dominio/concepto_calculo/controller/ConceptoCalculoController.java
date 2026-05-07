package ar.com.leo.super_master_backend.dominio.concepto_calculo.controller;

import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConcepto;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoRepository;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoCreateDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoPatchDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.service.ConceptoCalculoService;
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
@RequestMapping("/api/conceptos-calculo")
public class ConceptoCalculoController {

    private final ConceptoCalculoService service;
    private final CanalConceptoRepository canalConceptoRepository;

    @GetMapping
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<Page<ConceptoCalculoDTO>> listar(@RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<ConceptoCalculoDTO> obtener(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<ConceptoCalculoDTO> crear(@Valid @RequestBody ConceptoCalculoCreateDTO dto) {
        ConceptoCalculoDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<ConceptoCalculoDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody ConceptoCalculoUpdateDTO dto
    ) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<ConceptoCalculoDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @RequestBody ConceptoCalculoPatchDTO patch
    ) {
        return ResponseEntity.ok(service.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<Void> eliminar(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/canales")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<List<String>> canalesDelConcepto(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        List<String> canales = canalConceptoRepository.findByConceptoIdWithCanalFetch(id).stream()
                .map(cc -> cc.getCanal().getNombre())
                .sorted()
                .toList();
        return ResponseEntity.ok(canales);
    }

}

