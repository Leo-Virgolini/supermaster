package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionUpdateDTO;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/productos/{id}/estado-publicacion")
public class EstadoPublicacionController {

    private final EstadoPublicacionService estadoPublicacionService;

    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<EstadoPublicacionDTO> leer(@PathVariable @Positive Integer id) {
        return ResponseEntity.ok(estadoPublicacionService.leer(id));
    }

    @PutMapping
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Void> aplicar(@PathVariable @Positive Integer id,
                                        @RequestBody EstadoPublicacionUpdateDTO cambios) {
        estadoPublicacionService.aplicar(id, cambios);
        return ResponseEntity.noContent().build();
    }
}
