package ar.com.leo.super_master_backend.dominio.producto.controller;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCreateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoFilter;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoPatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoAuditoriaService;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoService;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import ar.com.leo.super_master_backend.config.Permisos;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;
    private final ProductoAuditoriaService productoAuditoriaService;

    // =====================================================
    // LISTAR / FILTRAR PRODUCTOS
    // =====================================================
    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<Page<ProductoDTO>> listar(

            // =======================
            // 1) TEXTO
            // =======================
            @RequestParam(required = false) String search,

            // =======================
            // 1.1) FILTROS DE TEXTO DEDICADOS
            // =======================
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String codExt,
            @RequestParam(required = false) String descripcion,
            @RequestParam(required = false) String tituloWeb,

            // =======================
            // 2) BOOLEANOS / NUMÉRICOS
            // =======================
            @RequestParam(required = false) Boolean esCombo,
            @RequestParam(required = false) Integer uxb,
            @RequestParam(required = false) Boolean esMaquina,
            @RequestParam(required = false) Boolean tieneMla,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) TagReposicion tagReposicion,
            @RequestParam(required = false) List<ar.com.leo.super_master_backend.dominio.producto.entity.Tag> tags,

            // =======================
            // 2.1) FILTROS MLA
            // =======================
            @RequestParam(required = false) String mla,
            @RequestParam(required = false) String mlau,
            @RequestParam(required = false) BigDecimal precioEnvioMin,
            @RequestParam(required = false) BigDecimal precioEnvioMax,
            @RequestParam(required = false) BigDecimal comisionPorcentajeMin,
            @RequestParam(required = false) BigDecimal comisionPorcentajeMax,
            @RequestParam(required = false) Boolean tieneComision,
            @RequestParam(required = false) Boolean tienePrecioEnvio,

            // =======================
            // 3) MANY-TO-ONE (multi-valor)
            // =======================
            @RequestParam(required = false) List<Integer> marcaIds,
            @RequestParam(required = false) List<Integer> origenIds,
            @RequestParam(required = false) List<Integer> tipoIds,
            @RequestParam(required = false) List<Integer> clasifGralIds,
            @RequestParam(required = false) List<Integer> clasifGastroIds,
            @RequestParam(required = false) List<Integer> proveedorIds,
            @RequestParam(required = false) List<Integer> materialIds,

            // =======================
            // 4) RANGOS (costo / IVA / stock)
            // =======================
            @RequestParam(required = false) BigDecimal costoMin,
            @RequestParam(required = false) BigDecimal costoMax,
            @RequestParam(required = false) BigDecimal ivaMin,
            @RequestParam(required = false) BigDecimal ivaMax,
            @RequestParam(required = false) Integer stockMin,
            @RequestParam(required = false) Integer stockMax,

            // =======================
            // 5) RANGO PVP
            // =======================
            @RequestParam(required = false) BigDecimal pvpMin,
            @RequestParam(required = false) BigDecimal pvpMax,
            @RequestParam(required = false) Integer pvpCanalId,

            // =======================
            // 6) FECHAS
            // =======================
            @RequestParam(required = false) LocalDate desdeFechaUltimoCosto,
            @RequestParam(required = false) LocalDate hastaFechaUltimoCosto,

            @RequestParam(required = false) LocalDate desdeFechaCreacion,
            @RequestParam(required = false) LocalDate hastaFechaCreacion,

            @RequestParam(required = false) LocalDate desdeFechaModificacion,
            @RequestParam(required = false) LocalDate hastaFechaModificacion,

            // =======================
            // 7) MANY-TO-MANY
            // =======================
            @RequestParam(required = false) List<Integer> aptoIds,
            @RequestParam(required = false) List<Integer> canalIds,
            @RequestParam(required = false) List<Integer> catalogoIds,
            @RequestParam(required = false) List<Integer> clienteIds,
            @RequestParam(required = false) List<Integer> mlaIds,

            Pageable pageable
    ) {

        ProductoFilter filter = new ProductoFilter(
                null,  // productoId
                search,
                // Filtros de texto dedicados
                sku,
                codExt,
                descripcion,
                tituloWeb,
                // Booleanos/Numéricos
                esCombo,
                uxb,
                esMaquina,
                tieneMla,
                activo,
                tagReposicion,
                tags,
                // Filtros MLA
                mla,
                mlau,
                precioEnvioMin,
                precioEnvioMax,
                comisionPorcentajeMin,
                comisionPorcentajeMax,
                tieneComision,
                tienePrecioEnvio,
                // Many-to-One (multi-valor)
                marcaIds,
                origenIds,
                tipoIds,
                clasifGralIds,
                clasifGastroIds,
                proveedorIds,
                materialIds,
                costoMin,
                costoMax,
                ivaMin,
                ivaMax,
                stockMin,
                stockMax,
                pvpMin,
                pvpMax,
                pvpCanalId,
                desdeFechaUltimoCosto,
                hastaFechaUltimoCosto,
                desdeFechaCreacion,
                hastaFechaCreacion,
                desdeFechaModificacion,
                hastaFechaModificacion,
                aptoIds,
                canalIds,
                catalogoIds,
                clienteIds,
                mlaIds,
                null,  // canalId (no aplica, no devuelve precios)
                null   // cuotas (no aplica, no devuelve precios)
        );

        return ResponseEntity.ok(productoService.filtrar(filter, pageable));
    }

    @GetMapping("/auditoria")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<Page<AuditoriaCambioDTO>> listarAuditoriaGlobal(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String campo,
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) Integer productoId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(productoAuditoriaService.listarGlobal(search, accion, campo, origen, usuario, productoId, pageable));
    }

    // =====================================================
    // CRUD
    // =====================================================

    @PostMapping
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<ProductoDTO> crear(@Valid @RequestBody ProductoCreateDTO dto) {
        ProductoDTO creado = productoService.crear(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<ProductoDTO> obtener(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        return ResponseEntity.ok(productoService.obtener(id));
    }

    @GetMapping("/{id}/auditoria")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<Page<AuditoriaCambioDTO>> listarAuditoria(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            Pageable pageable) {
        return ResponseEntity.ok(productoService.listarAuditoria(id, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<ProductoDTO> actualizar(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @Valid @RequestBody ProductoUpdateDTO dto) {
        return ResponseEntity.ok(productoService.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<ProductoDTO> patch(
            @PathVariable @Positive(message = "El ID debe ser positivo") Integer id,
            @RequestBody ProductoPatchDTO patch) {
        return ResponseEntity.ok(productoService.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Permisos.PRODUCTOS_EDITAR)
    public ResponseEntity<Void> eliminar(@PathVariable @Positive(message = "El ID debe ser positivo") Integer id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

}

