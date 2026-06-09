package ar.com.leo.super_master_backend.dominio.regla_descuento.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoCreateDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoPatchDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.service.ReglaDescuentoService;
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
@RequestMapping("/api/reglas-descuento")
public class ReglaDescuentoController {

    private final ReglaDescuentoService service;

    @GetMapping
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<Page<ReglaDescuentoDTO>> listar(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    @GetMapping("/canal/{canalId}")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<List<ReglaDescuentoDTO>> listarPorCanal(@PathVariable @Positive(message = "El ID de canal debe ser positivo") Integer canalId) {
        return ResponseEntity.ok(service.listarPorCanal(canalId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<ReglaDescuentoDTO> obtener(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<ReglaDescuentoDTO> crear(@Valid @RequestBody ReglaDescuentoCreateDTO dto) {
        ReglaDescuentoDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<ReglaDescuentoDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody ReglaDescuentoUpdateDTO dto
    ) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<ReglaDescuentoDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @RequestBody ReglaDescuentoPatchDTO patch
    ) {
        return ResponseEntity.ok(service.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<Void> eliminar(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

}

