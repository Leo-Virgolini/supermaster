package ar.com.leo.super_master_backend.apis.openai.controller;

import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.service.SeoConfigService;
import ar.com.leo.super_master_backend.apis.openai.service.SeoUsoService;
import ar.com.leo.super_master_backend.config.Permisos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seo")
public class SeoController {

    private final SeoConfigService seoConfigService;
    private final SeoUsoService seoUsoService;

    @GetMapping("/config")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<SeoConfigDTO> config() {
        return ResponseEntity.ok(seoConfigService.obtener());
    }

    @PutMapping("/config")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<SeoConfigDTO> actualizar(@Valid @RequestBody SeoConfigUpdateDTO body) {
        return ResponseEntity.ok(seoConfigService.actualizar(body));
    }

    @GetMapping("/uso")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<SeoUsoDTO> uso() {
        return ResponseEntity.ok(seoUsoService.obtener());
    }

    @PostMapping("/uso/reset")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Void> resetUso() {
        seoUsoService.reset();
        return ResponseEntity.noContent().build();
    }
}
