package ar.com.leo.super_master_backend.dominio.catalogo_pdf_global_config.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_global_config.dto.CatalogoPdfGlobalConfigDTO;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_global_config.service.CatalogoPdfGlobalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/catalogos-pdf/configuracion-global")
public class CatalogoPdfGlobalConfigController {

    private final CatalogoPdfGlobalConfigService service;

    @GetMapping
    @PreAuthorize(Permisos.CATALOGOS_PDF_VER)
    public ResponseEntity<CatalogoPdfGlobalConfigDTO> obtener() {
        return ResponseEntity.ok(service.obtener());
    }
}
