package ar.com.leo.super_master_backend.dominio.producto.mla.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.*;
import ar.com.leo.super_master_backend.dominio.producto.mla.service.MlaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mlas")
public class MlaController {

    private final MlaService mlaService;

    @GetMapping
    @PreAuthorize(Permisos.MLAS_VER)
    public ResponseEntity<Page<MlaDTO>> listar(@RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(mlaService.listar(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.MLAS_VER)
    public ResponseEntity<MlaDTO> obtener(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(mlaService.obtener(id));
    }

    /**
     * Busca en MercadoLibre la publicación del SKU, crea/asegura el MLA y le calcula
     * precio de envío y comisión. Usado por el alta de producto para autocompletar el MLA.
     */
    @GetMapping("/por-sku-ml")
    @PreAuthorize(Permisos.MLAS_EDITAR)
    public ResponseEntity<MlaDTO> obtenerPorSkuDesdeML(@RequestParam String sku) {
        return ResponseEntity.ok(mlaService.obtenerOcrearPorSkuDesdeML(sku));
    }

    @PostMapping
    @PreAuthorize(Permisos.MLAS_EDITAR)
    public ResponseEntity<MlaDTO> crear(@Valid @RequestBody MlaCreateDTO dto) {
        MlaDTO creado = mlaService.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.MLAS_EDITAR)
    public ResponseEntity<MlaDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody MlaUpdateDTO dto) {
        return ResponseEntity.ok(mlaService.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.MLAS_EDITAR)
    public ResponseEntity<MlaDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @RequestBody MlaPatchDTO patch) {
        return ResponseEntity.ok(mlaService.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.MLAS_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        mlaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/productos")
    @PreAuthorize(Permisos.MLAS_VER)
    public ResponseEntity<List<ProductoResumenDTO>> listarProductos(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(mlaService.listarProductos(id));
    }

    @GetMapping("/topes-promocion")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<List<MlaTopePromocionDTO>> listarTopesPromocion() {
        return ResponseEntity.ok(mlaService.listarTopesPromocion());
    }

    @PutMapping("/topes-promocion")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<List<MlaTopePromocionDTO>> actualizarTopesPromocion(
            @RequestBody List<MlaTopePromocionDTO> topes) {
        return ResponseEntity.ok(mlaService.actualizarTopesPromocion(topes));
    }
}

