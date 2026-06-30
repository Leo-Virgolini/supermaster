package ar.com.leo.super_master_backend.dominio.imagen.controller;

import ar.com.leo.super_master_backend.apis.openai.dto.CaratulaGuardarDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.CaratulaGeneradaDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.CrudasDisponiblesDTO;
import ar.com.leo.super_master_backend.dominio.imagen.service.GeneracionCaratula;
import ar.com.leo.super_master_backend.config.Permisos;
import ar.com.leo.super_master_backend.dominio.imagen.service.CaratulaService;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenDetalle;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenListado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {

    private final ImagenService imagenService;
    private final CaratulaService caratulaService;

    public ImagenController(ImagenService imagenService, CaratulaService caratulaService) {
        this.imagenService = imagenService;
        this.caratulaService = caratulaService;
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

    @GetMapping("/detalle/{sku}")
    public ResponseEntity<List<ImagenDetalle>> detalle(@PathVariable String sku) {
        return ResponseEntity.ok(imagenService.resolverDetallePorSku(sku));
    }

    @PostMapping("/caratula/generar/{sku}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<CaratulaGeneradaDTO> generarCaratula(@PathVariable String sku,
                                                               @RequestParam(required = false) String cruda) {
        GeneracionCaratula g = caratulaService.generar(sku, cruda);
        String generadaB64 = Base64.getEncoder().encodeToString(g.generada());
        String crudaB64 = Base64.getEncoder().encodeToString(g.cruda());
        return ResponseEntity.ok(new CaratulaGeneradaDTO(
                generadaB64, caratulaService.formato(), crudaB64, subtipoMimeDe(g.crudaNombre())));
    }

    @GetMapping("/caratula/crudas/{sku}")
    @PreAuthorize(Permisos.INTEGRACIONES_VER)
    public ResponseEntity<CrudasDisponiblesDTO> crudas(@PathVariable String sku) {
        return ResponseEntity.ok(new CrudasDisponiblesDTO(
                imagenService.estadoCrudaDir(),
                imagenService.estadoDestinoDir(),
                imagenService.resolverCrudasPorSku(sku)));
    }

    @GetMapping("/cruda/{nombre}")
    public ResponseEntity<byte[]> cruda(@PathVariable String nombre) {
        byte[] bytes = imagenService.leerCrudaBytes(nombre);   // valida nombre seguro internamente
        String subtipo = subtipoMimeDe(nombre);
        MediaType contentType = subtipo.isEmpty()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType("image/" + subtipo);
        return ResponseEntity.ok()
                .contentType(contentType)
                .body(bytes);
    }

    /** Subtipo MIME para data:image/{x} derivado de la extensión del archivo crudo (jpg→jpeg). */
    static String subtipoMimeDe(String nombre) {
        int dot = nombre.lastIndexOf('.');
        String ext = dot >= 0 ? nombre.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return ext.equals("jpg") ? "jpeg" : ext;
    }

    @PostMapping("/caratula/guardar/{sku}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<Void> guardarCaratula(@PathVariable String sku, @Valid @RequestBody CaratulaGuardarDTO body) {
        byte[] jpg;
        try { jpg = Base64.getDecoder().decode(body.imagenBase64()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("La imagen en base64 es inválida"); }
        caratulaService.guardar(sku, jpg);
        return ResponseEntity.noContent().build();
    }
}
