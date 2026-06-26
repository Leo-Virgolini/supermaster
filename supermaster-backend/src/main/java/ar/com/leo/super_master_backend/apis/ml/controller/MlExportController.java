package ar.com.leo.super_master_backend.apis.ml.controller;

import ar.com.leo.super_master_backend.apis.ml.dto.MlExportRequestDTO;
import ar.com.leo.super_master_backend.dominio.common.dto.ExportCanalResultDTO;
import ar.com.leo.super_master_backend.apis.ml.service.MlExportService;
import ar.com.leo.super_master_backend.config.Permisos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
public class MlExportController {

    private final MlExportService mlExportService;

    @PostMapping("/exportar-productos")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<ExportCanalResultDTO> exportar(@Valid @RequestBody(required = false) MlExportRequestDTO request) {
        return ResponseEntity.ok(mlExportService.exportar(request));
    }
}
