package ar.com.leo.super_master_backend.dominio.imagen.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {

    private static final String EXTENSIONES_IMAGEN = "(?i).*\\.(jpg|jpeg|png|gif|webp|bmp|svg)$";
    private static final String[] EXTENSIONES = {"jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"};

    private final Path baseDir;

    public ImagenController(@Value("${app.imagenes-dir}") String imagenesDir) {
        this.baseDir = Path.of(imagenesDir).normalize();
    }

    @GetMapping("/buscar/{sku}")
    public ResponseEntity<String> buscarPorSku(@PathVariable String sku) {
        if (!Files.isDirectory(baseDir)) {
            return ResponseEntity.notFound().build();
        }

        for (String ext : EXTENSIONES) {
            Path candidate = baseDir.resolve(sku + "." + ext);
            if (Files.isRegularFile(candidate)) {
                return ResponseEntity.ok(candidate.getFileName().toString());
            }
            // Intentar con extensión en mayúsculas
            Path candidateUpper = baseDir.resolve(sku + "." + ext.toUpperCase());
            if (Files.isRegularFile(candidateUpper)) {
                return ResponseEntity.ok(candidateUpper.getFileName().toString());
            }
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/listar")
    public ResponseEntity<List<String>> listar(@RequestParam(defaultValue = "") String search) throws IOException {
        if (!Files.isDirectory(baseDir)) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        try (Stream<Path> entries = Files.list(baseDir)) {
            List<String> archivos = entries
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.matches(EXTENSIONES_IMAGEN))
                    .filter(name -> search.isEmpty() || name.toLowerCase().contains(search.toLowerCase()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

            return ResponseEntity.ok(archivos);
        }
    }

    private String nombreSinExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
