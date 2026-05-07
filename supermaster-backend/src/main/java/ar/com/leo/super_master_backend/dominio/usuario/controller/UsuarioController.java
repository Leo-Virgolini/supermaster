package ar.com.leo.super_master_backend.dominio.usuario.controller;

import ar.com.leo.super_master_backend.dominio.usuario.dto.*;
import ar.com.leo.super_master_backend.dominio.usuario.service.UsuarioService;
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
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @PreAuthorize(Permisos.USUARIOS_VER)
    public ResponseEntity<Page<UsuarioDTO>> listar(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(usuarioService.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.USUARIOS_VER)
    public ResponseEntity<UsuarioDTO> obtener(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(usuarioService.obtener(id));
    }

    @PostMapping
    @PreAuthorize(Permisos.USUARIOS_EDITAR)
    public ResponseEntity<UsuarioDTO> crear(@Valid @RequestBody UsuarioCreateDTO dto) {
        UsuarioDTO creado = usuarioService.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.USUARIOS_EDITAR)
    public ResponseEntity<UsuarioDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody UsuarioUpdateDTO dto) {
        return ResponseEntity.ok(usuarioService.actualizar(id, dto));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize(Permisos.USUARIOS_EDITAR)
    public ResponseEntity<Void> cambiarPassword(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody CambioPasswordDTO dto) {
        usuarioService.cambiarPassword(id, dto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.USUARIOS_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    @PreAuthorize(Permisos.USUARIOS_VER)
    public ResponseEntity<List<RolDTO>> listarRoles() {
        return ResponseEntity.ok(usuarioService.listarRoles());
    }
}
