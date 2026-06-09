package ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto.CatalogoPdfConfigCreateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto.CatalogoPdfConfigDTO;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto.CatalogoPdfConfigUpdateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.service.CatalogoPdfConfigService;
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

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/catalogos-pdf-config")
public class CatalogoPdfConfigController {

    private final CatalogoPdfConfigService service;

    @GetMapping
    @PreAuthorize(Permisos.CATALOGOS_PDF_VER)
    public ResponseEntity<Page<CatalogoPdfConfigDTO>> listar(@RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.CATALOGOS_PDF_VER)
    public ResponseEntity<CatalogoPdfConfigDTO> obtener(@PathVariable @Positive Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(Permisos.CATALOGOS_PDF_EDITAR)
    public ResponseEntity<CatalogoPdfConfigDTO> crear(@Valid @RequestBody CatalogoPdfConfigCreateDTO dto) {
        CatalogoPdfConfigDTO creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.CATALOGOS_PDF_EDITAR)
    public ResponseEntity<CatalogoPdfConfigDTO> actualizar(@PathVariable @Positive Integer id, @Valid @RequestBody CatalogoPdfConfigUpdateDTO dto) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.CATALOGOS_PDF_EDITAR)
    public ResponseEntity<Void> eliminar(@PathVariable @Positive Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
