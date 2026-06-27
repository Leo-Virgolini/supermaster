package ar.com.leo.super_master_backend.apis.openai.controller;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenPromptDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenPromptUpdateDTO;
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

    @GetMapping("/prompt")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ImagenPromptDTO> prompt() { return ResponseEntity.ok(configService.obtener()); }

    @PutMapping("/prompt")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<ImagenPromptDTO> actualizar(@Valid @RequestBody ImagenPromptUpdateDTO body) {
        return ResponseEntity.ok(configService.actualizar(body.contenido()));
    }

    @GetMapping("/uso")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<ImagenUsoDTO> uso() { return ResponseEntity.ok(usoService.obtener()); }
}
