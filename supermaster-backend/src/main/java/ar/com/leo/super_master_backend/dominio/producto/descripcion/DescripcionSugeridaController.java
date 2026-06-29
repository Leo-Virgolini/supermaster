package ar.com.leo.super_master_backend.dominio.producto.descripcion;

import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.descripcion.dto.DescripcionSugeridaDTO;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/productos/{id}/descripcion-sugerida")
public class DescripcionSugeridaController {

    private final DescripcionSugeridaService descripcionSugeridaService;

    @GetMapping
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<DescripcionSugeridaDTO> sugerir(@PathVariable @Positive Integer id,
                                                          @RequestParam String canal) {
        return ResponseEntity.ok(descripcionSugeridaService.sugerir(id, canal));
    }
}
