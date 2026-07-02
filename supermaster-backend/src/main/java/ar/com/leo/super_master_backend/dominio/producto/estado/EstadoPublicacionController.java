package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.DuxCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.FamiliaMlDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoAplicarDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.MlCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.NubeCanalDTO;
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

    @GetMapping("/ml")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<MlCanalDTO> ml(@PathVariable @Positive Integer id) {
        return ResponseEntity.ok(estadoPublicacionService.leerMl(id));
    }

    @GetMapping("/hogar")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<NubeCanalDTO> hogar(@PathVariable @Positive Integer id) {
        return ResponseEntity.ok(estadoPublicacionService.leerNube(id, TiendaNubeService.STORE_HOGAR));
    }

    @GetMapping("/gastro")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<NubeCanalDTO> gastro(@PathVariable @Positive Integer id) {
        return ResponseEntity.ok(estadoPublicacionService.leerNube(id, TiendaNubeService.STORE_GASTRO));
    }

    @GetMapping("/dux")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<DuxCanalDTO> dux(@PathVariable @Positive Integer id) {
        return ResponseEntity.ok(estadoPublicacionService.leerDux(id));
    }

    @GetMapping("/familia")
    @PreAuthorize(Permisos.PRODUCTOS_VER)
    public ResponseEntity<FamiliaMlDTO> familia(@PathVariable @Positive Integer id) {
        return ResponseEntity.ok(estadoPublicacionService.leerFamilia(id));
    }

    @DeleteMapping("/familia")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Void> quitarDeFamilia(@PathVariable @Positive Integer id) {
        estadoPublicacionService.quitarDeFamilia(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<EstadoAplicarDTO> aplicar(@PathVariable @Positive Integer id,
                                                    @RequestBody EstadoPublicacionUpdateDTO cambios) {
        return ResponseEntity.ok(estadoPublicacionService.aplicar(id, cambios));
    }
}
