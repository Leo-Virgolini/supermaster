package ar.com.leo.super_master_backend.dominio.orden_compra.controller;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.orden_compra.dto.*;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.EstadoOrdenCompra;
import ar.com.leo.super_master_backend.dominio.orden_compra.service.OrdenCompraService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ordenes-compra")
public class OrdenCompraController {

    private final OrdenCompraService ordenCompraService;

    @GetMapping
    @PreAuthorize(Permisos.ORDENES_COMPRA_VER)
    public ResponseEntity<Page<OrdenCompraDTO>> listar(
            @RequestParam(required = false) Integer proveedorId,
            @RequestParam(required = false) EstadoOrdenCompra estado,
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ordenCompraService.listar(pageable, proveedorId, estado, search));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.ORDENES_COMPRA_VER)
    public ResponseEntity<OrdenCompraDTO> obtener(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id
    ) {
        return ResponseEntity.ok(ordenCompraService.obtener(id));
    }

    @PostMapping
    @PreAuthorize(Permisos.ORDENES_COMPRA_EDITAR)
    public ResponseEntity<OrdenCompraDTO> crear(@Valid @RequestBody OrdenCompraCreateDTO dto) {
        OrdenCompraDTO creado = ordenCompraService.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.ORDENES_COMPRA_EDITAR)
    public ResponseEntity<OrdenCompraDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody OrdenCompraUpdateDTO dto
    ) {
        return ResponseEntity.ok(ordenCompraService.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.ORDENES_COMPRA_EDITAR)
    public ResponseEntity<OrdenCompraDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @RequestBody OrdenCompraPatchDTO patch
    ) {
        return ResponseEntity.ok(ordenCompraService.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.ORDENES_COMPRA_EDITAR)
    public ResponseEntity<Void> eliminar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id
    ) {
        ordenCompraService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enviar")
    @PreAuthorize(Permisos.ORDENES_COMPRA_EDITAR)
    public ResponseEntity<OrdenCompraDTO> enviar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id
    ) {
        return ResponseEntity.ok(ordenCompraService.enviar(id));
    }

    @PostMapping("/{id}/recibir")
    @PreAuthorize(Permisos.ORDENES_COMPRA_EDITAR)
    public ResponseEntity<OrdenCompraDTO> registrarRecepcion(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody RecepcionDTO dto
    ) {
        return ResponseEntity.ok(ordenCompraService.registrarRecepcion(id, dto));
    }
}

