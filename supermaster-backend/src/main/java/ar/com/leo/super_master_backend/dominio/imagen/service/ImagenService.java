package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Acceso a la carpeta de imágenes de productos.
 *
 * La carpeta tiene decenas de miles de archivos y suele estar montada sobre una unidad
 * de red / Google Drive File Stream, donde listar el directorio es costoso. Para que la
 * búsqueda en el selector de imágenes sea rápida, el listado de nombres se cachea en
 * memoria con un TTL: solo se vuelve a escanear el directorio cuando el índice expira,
 * y las búsquedas filtran sobre la lista cacheada (sin tocar el disco).
 */
@Service
public class ImagenService {

    private static final String[] EXTENSIONES = {"jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"};
    /** Máximo de resultados devueltos por búsqueda (evita mandar miles de nombres al frontend). */
    private static final int MAX_RESULTADOS = 500;

    private final Path baseDir;
    private final long ttlMillis;

    // Índice cacheado de nombres de archivo de imagen (ya ordenado, case-insensitive).
    private volatile List<String> indice = List.of();
    private volatile long indiceTimestamp = 0L;

    public ImagenService(
            @Value("${app.imagenes-dir}") String imagenesDir,
            @Value("${app.imagenes-index-ttl-ms:60000}") long ttlMillis) {
        this.baseDir = Path.of(imagenesDir).normalize();
        this.ttlMillis = ttlMillis;
    }

    /** Busca el archivo de imagen para un SKU probando las extensiones conocidas (acceso directo, sin escanear). */
    public String buscarPorSku(String sku) {
        if (!Files.isDirectory(baseDir)) {
            return null;
        }
        for (String ext : EXTENSIONES) {
            Path candidate = baseDir.resolve(sku + "." + ext);
            if (Files.isRegularFile(candidate)) {
                return candidate.getFileName().toString();
            }
            Path candidateUpper = baseDir.resolve(sku + "." + ext.toUpperCase(Locale.ROOT));
            if (Files.isRegularFile(candidateUpper)) {
                return candidateUpper.getFileName().toString();
            }
        }
        return null;
    }

    /**
     * Resultado de un listado: los archivos de la página (capados a {@link #MAX_RESULTADOS})
     * y el total de coincidencias, para que el frontend pueda avisar si hay que refinar.
     */
    public record ImagenListado(List<String> archivos, int total) {}

    /** Lista nombres de imagen que contienen {@code search} (case-insensitive), usando el índice cacheado. */
    public ImagenListado listar(String search) {
        String q = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<String> coincidencias = obtenerIndice().stream()
                .filter(name -> q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q))
                .toList();
        List<String> pagina = coincidencias.size() > MAX_RESULTADOS
                ? List.copyOf(coincidencias.subList(0, MAX_RESULTADOS))
                : coincidencias;
        return new ImagenListado(pagina, coincidencias.size());
    }

    private List<String> obtenerIndice() {
        long ahora = System.currentTimeMillis();
        if (ahora - indiceTimestamp < ttlMillis) {
            return indice;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - indiceTimestamp < ttlMillis) {
                return indice;
            }
            indice = escanear();
            indiceTimestamp = System.currentTimeMillis();
            return indice;
        }
    }

    private List<String> escanear() {
        if (!Files.isDirectory(baseDir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(baseDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(ImagenService::esImagen)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo indexar la carpeta de imágenes", e);
        }
    }

    private static boolean esImagen(String nombre) {
        int dot = nombre.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = nombre.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (String e : EXTENSIONES) {
            if (e.equals(ext)) return true;
        }
        return false;
    }
}
