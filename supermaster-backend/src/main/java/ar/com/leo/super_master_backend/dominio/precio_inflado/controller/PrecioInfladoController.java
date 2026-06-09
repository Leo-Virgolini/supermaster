package ar.com.leo.super_master_backend.dominio.precio_inflado.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoCreateDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoPatchDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.service.PrecioInfladoService;
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
@RequestMapping("/api/precios-inflados")
public class PrecioInfladoController {

    private final PrecioInfladoService service;

    @GetMapping
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<Page<PrecioInfladoDTO>> listar(@RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<PrecioInfladoDTO> obtenerPorId(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @GetMapping("/codigo/{codigo}")
    @PreAuthorize(Permisos.PRECIOS_VER)
    public ResponseEntity<PrecioInfladoDTO> obtenerPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(service.obtenerPorCodigo(codigo));
    }

    @PostMapping
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<PrecioInfladoDTO> crear(@Valid @RequestBody PrecioInfladoCreateDTO dto) {
        PrecioInfladoDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<PrecioInfladoDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody PrecioInfladoUpdateDTO dto) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<PrecioInfladoDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @RequestBody PrecioInfladoPatchDTO patch) {
        return ResponseEntity.ok(service.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.PRECIOS_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

