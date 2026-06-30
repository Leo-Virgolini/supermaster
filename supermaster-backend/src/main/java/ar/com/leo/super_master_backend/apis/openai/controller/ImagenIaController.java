package ar.com.leo.super_master_backend.apis.openai.controller;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.service.ImagenIaConfigService;
import ar.com.leo.super_master_backend.apis.openai.service.ImagenUsoService;
import ar.com.leo.super_master_backend.config.Permisos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/imagen-ia")
public class ImagenIaController {

    private final ImagenIaConfigService configService;
    private final ImagenUsoService usoService;

    @GetMapping("/config")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ImagenConfigDTO> config() { return ResponseEntity.ok(configService.obtener()); }

    @PutMapping("/config")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<ImagenConfigDTO> actualizar(@Valid @RequestBody ImagenConfigUpdateDTO body) {
        return ResponseEntity.ok(configService.actualizar(body));
    }

    @GetMapping("/uso")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ImagenUsoDTO> uso() { return ResponseEntity.ok(usoService.obtener()); }

    @PostMapping("/uso/reset")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Void> resetUso() {
        usoService.reset();
        return ResponseEntity.noContent().build();
    }
}
