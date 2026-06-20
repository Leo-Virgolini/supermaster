package ar.com.leo.super_master_backend.apis.nube.controller;

import ar.com.leo.super_master_backend.apis.nube.dto.ExportNubeRequestDTO;
import ar.com.leo.super_master_backend.dominio.common.dto.ExportCanalResultDTO;
import ar.com.leo.super_master_backend.apis.nube.service.NubeExportService;
import ar.com.leo.super_master_backend.config.Permisos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nube")
public class NubeProductoController {

    private final NubeExportService nubeExportService;

    @PostMapping("/exportar-productos")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<ExportCanalResultDTO> exportar(@RequestBody(required = false) ExportNubeRequestDTO request) {
        return ResponseEntity.ok(nubeExportService.exportar(request));
    }
}
