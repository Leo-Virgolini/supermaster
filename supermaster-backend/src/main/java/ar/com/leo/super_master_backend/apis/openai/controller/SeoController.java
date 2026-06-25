package ar.com.leo.super_master_backend.apis.openai.controller;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoPromptDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoPromptUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.service.SeoConfigService;
import ar.com.leo.super_master_backend.apis.openai.service.SeoUsoService;
import ar.com.leo.super_master_backend.config.Permisos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seo")
public class SeoController {

    private final SeoConfigService seoConfigService;
    private final SeoUsoService seoUsoService;

    @GetMapping("/prompts")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<List<SeoPromptDTO>> prompts() {
        return ResponseEntity.ok(seoConfigService.obtenerTodos());
    }

    @PutMapping("/prompts/{canal}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<SeoPromptDTO> actualizarPrompt(
            @PathVariable SeoCanal canal,
            @Valid @RequestBody SeoPromptUpdateDTO body) {
        return ResponseEntity.ok(seoConfigService.actualizar(canal, body.contenido()));
    }

    @GetMapping("/uso")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<SeoUsoDTO> uso() {
        return ResponseEntity.ok(seoUsoService.obtener());
    }
}
