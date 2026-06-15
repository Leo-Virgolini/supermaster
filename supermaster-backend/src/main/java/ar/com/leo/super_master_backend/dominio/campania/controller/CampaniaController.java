package ar.com.leo.super_master_backend.dominio.campania.controller;

import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.campania.dto.*;
import ar.com.leo.super_master_backend.dominio.campania.service.CampaniaService;
import ar.com.leo.super_master_backend.dominio.campania.service.CampaniaSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/campanias")
public class CampaniaController {

    private final CampaniaService service;
    private final CampaniaSyncService syncService;

    @GetMapping
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Page<CampaniaDTO>> listar(@RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<CampaniaDTO> obtenerPorId(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<CampaniaDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody CampaniaUpdateDTO dto) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @GetMapping("/{id}/productos")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<Page<CampaniaProductoDTO>> listarProductos(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id, Pageable pageable) {
        return ResponseEntity.ok(service.listarProductos(id, pageable));
    }

    @PatchMapping("/productos/{campaniaProductoId}/precio")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<CampaniaProductoDTO> actualizarPrecio(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer campaniaProductoId,
            @Valid @RequestBody CampaniaProductoPrecioDTO dto) {
        return ResponseEntity.ok(service.actualizarPrecio(campaniaProductoId, dto.precioManual()));
    }

    @PostMapping("/sincronizar")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<SincronizacionResultadoDTO> sincronizar() {
        return ResponseEntity.ok(syncService.sincronizar(TiendaNubeService.STORE_HOGAR));
    }
}
