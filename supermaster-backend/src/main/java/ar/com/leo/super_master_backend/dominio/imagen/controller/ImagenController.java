package ar.com.leo.super_master_backend.dominio.imagen.controller;

import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenListado;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {

    private final ImagenService imagenService;

    public ImagenController(ImagenService imagenService) {
        this.imagenService = imagenService;
    }

    @GetMapping("/buscar/{sku}")
    public ResponseEntity<String> buscarPorSku(@PathVariable String sku) {
        String nombre = imagenService.buscarPorSku(sku);
        return nombre != null ? ResponseEntity.ok(nombre) : ResponseEntity.notFound().build();
    }

    @GetMapping("/listar")
    public ResponseEntity<ImagenListado> listar(@RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(imagenService.listar(search));
    }
}
