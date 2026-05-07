package ar.com.leo.super_master_backend.dominio.usuario.controller;

import ar.com.leo.super_master_backend.dominio.usuario.dto.PermisoDTO;
import ar.com.leo.super_master_backend.dominio.usuario.dto.RolDTO;
import ar.com.leo.super_master_backend.dominio.usuario.dto.RolPermisosUpdateDTO;
import ar.com.leo.super_master_backend.dominio.usuario.service.RolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/roles")
public class RolController {

    private final RolService rolService;

    @GetMapping
    @PreAuthorize(Permisos.USUARIOS_VER)
    public ResponseEntity<List<RolDTO>> listarRoles() {
        return ResponseEntity.ok(rolService.listarRoles());
    }

    @GetMapping("/permisos")
    @PreAuthorize(Permisos.USUARIOS_VER)
    public ResponseEntity<List<PermisoDTO>> listarPermisos() {
        return ResponseEntity.ok(rolService.listarPermisos());
    }

    @PutMapping("/{id}/permisos")
    @PreAuthorize(Permisos.USUARIOS_EDITAR)
    public ResponseEntity<RolDTO> actualizarPermisos(
            @PathVariable Integer id,
            @Valid @RequestBody RolPermisosUpdateDTO dto) {
        return ResponseEntity.ok(rolService.actualizarPermisos(id, dto.permisoIds()));
    }
}
