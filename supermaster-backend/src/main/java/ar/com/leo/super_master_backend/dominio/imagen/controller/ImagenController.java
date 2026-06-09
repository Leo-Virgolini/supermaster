package ar.com.leo.super_master_backend.dominio.imagen.controller;

import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenListado;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

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

    /**
     * Sirve la imagen de un producto resolviéndola por SKU desde la carpeta de imágenes.
     * Redirige (302) al archivo estático real (que se sirve con cache); 404 si no existe.
     */
    @GetMapping("/producto/{sku}")
    public ResponseEntity<Void> imagenPorSku(@PathVariable String sku) {
        String archivo = imagenService.resolverArchivoPorSku(sku);
        if (archivo == null) {
            return ResponseEntity.notFound().build();
        }
        String location = "/api/imagenes/" + UriUtils.encodePathSegment(archivo, StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    @GetMapping("/listar")
    public ResponseEntity<ImagenListado> listar(@RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(imagenService.listar(search));
    }
}
