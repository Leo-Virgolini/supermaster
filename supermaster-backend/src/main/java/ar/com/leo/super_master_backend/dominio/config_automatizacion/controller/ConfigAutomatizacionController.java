package ar.com.leo.super_master_backend.dominio.config_automatizacion.controller;

import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionCreateDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionPatchDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.service.ConfigAutomatizacionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/config-automatizacion")
public class ConfigAutomatizacionController {

    private final ConfigAutomatizacionService service;

    @GetMapping
    @PreAuthorize(Permisos.CONFIGURACION_VER)
    public ResponseEntity<Page<ConfigAutomatizacionDTO>> listar(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.CONFIGURACION_VER)
    public ResponseEntity<ConfigAutomatizacionDTO> obtener(
            @PathVariable @Positive(message = "El ID debe ser un número positivo") Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @GetMapping("/clave/{clave}")
    @PreAuthorize(Permisos.CONFIGURACION_VER)
    public ResponseEntity<ConfigAutomatizacionDTO> obtenerPorClave(@PathVariable String clave) {
        return service.obtenerPorClave(clave)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize(Permisos.CONFIGURACION_EDITAR)
    public ResponseEntity<ConfigAutomatizacionDTO> crear(@Valid @RequestBody ConfigAutomatizacionCreateDTO dto) {
        ConfigAutomatizacionDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.CONFIGURACION_EDITAR)
    public ResponseEntity<ConfigAutomatizacionDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser un número positivo") Integer id,
            @Valid @RequestBody ConfigAutomatizacionUpdateDTO dto) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.CONFIGURACION_EDITAR)
    public ResponseEntity<ConfigAutomatizacionDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser un número positivo") Integer id,
            @RequestBody ConfigAutomatizacionPatchDTO patch) {
        return ResponseEntity.ok(service.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.CONFIGURACION_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID debe ser un número positivo") Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

