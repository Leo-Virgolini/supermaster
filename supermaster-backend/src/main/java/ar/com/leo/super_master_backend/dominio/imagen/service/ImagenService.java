package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
    private final Path rawDir;

    // Índice cacheado de nombres de archivo de imagen (ya ordenado, case-insensitive).
    private volatile List<String> indice = List.of();
    // Mapa derivado sku(lowercase) -> nombre de archivo, para resolución O(1) por SKU (sin stats a disco).
    private volatile Map<String, String> indiceSku = Map.of();
    private volatile long indiceTimestamp = 0L;

    public ImagenService(
            @Value("${app.imagenes-dir}") String imagenesDir,
            @Value("${app.imagenes-index-ttl-ms:60000}") long ttlMillis,
            @Value("${app.imagenes-raw-dir}") String imagenesRawDir) {
        this.baseDir = Path.of(imagenesDir).normalize();
        this.ttlMillis = ttlMillis;
        this.rawDir = Path.of(imagenesRawDir).normalize();
    }

    /**
     * Resuelve el nombre del archivo de imagen para un SKU usando el índice cacheado (O(1),
     * sin stats a disco). Case-insensitive. Devuelve null si no hay archivo para ese SKU.
     * Ante colisión (mismo SKU con distintas extensiones), gana la de mayor prioridad (jpg primero).
     */
    public String resolverArchivoPorSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return obtenerIndiceSku().get(sku.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Resuelve TODOS los archivos de imagen de un SKU: la principal ({sku}.{ext}) primero y luego
     * las adicionales ({sku}_N.{ext}) por N ascendente. Case-insensitive. Lista vacía si no hay.
     * Ante varias extensiones de un mismo slot, gana la de mayor prioridad (jpg primero).
     */
    public List<String> resolverArchivosPorSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return List.of();
        }
        String skuLower = sku.trim().toLowerCase(Locale.ROOT);
        // slot 0 = principal; slot N (>=1) = sufijo _N. TreeMap ordena 0,1,2,3... (principal primero).
        TreeMap<Integer, String> porSlot = new TreeMap<>();
        for (String nombre : obtenerIndice()) {
            int dot = nombre.lastIndexOf('.');
            if (dot <= 0) continue;
            String base = nombre.substring(0, dot).toLowerCase(Locale.ROOT);
            Integer slot = slotDe(base, skuLower);
            if (slot == null) continue;
            String existente = porSlot.get(slot);
            if (existente == null || prioridadExtension(nombre) < prioridadExtension(existente)) {
                porSlot.put(slot, nombre);
            }
        }
        return List.copyOf(porSlot.values());
    }

    /** Límite de tamaño por imagen aceptado por ML y Tienda Nube. */
    public static final long MAX_BYTES_CANAL = 10L * 1024 * 1024; // 10 MB

    /** Una imagen resuelta con su metadata (extensión en minúscula, sin punto). */
    public record ImagenDetalle(String nombre, String extension, long bytes) {}

    /** Motivo por el que una imagen no se sube a un canal. */
    public enum MotivoRechazo { FORMATO, TAMANO }

    /** Una imagen que no se sube, con su motivo. */
    public record ImagenRechazada(String nombre, MotivoRechazo motivo) {}

    /** Resultado de filtrar las imágenes de un SKU para un canal: las que se suben y las que no. */
    public record FiltroImagenes(List<String> validas, List<ImagenRechazada> rechazadas) {}

    /** Resuelve las imágenes del SKU con su extensión (minúscula) y tamaño en bytes. */
    public List<ImagenDetalle> resolverDetallePorSku(String sku) {
        List<ImagenDetalle> out = new ArrayList<>();
        for (String nombre : resolverArchivosPorSku(sku)) {
            int dot = nombre.lastIndexOf('.');
            String ext = dot > 0 ? nombre.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            try {
                out.add(new ImagenDetalle(nombre, ext, Files.size(baseDir.resolve(nombre))));
            } catch (java.io.IOException e) {
                // El archivo desapareció entre el índice y la lectura: se omite (best-effort).
            }
        }
        return out;
    }

    /** Filtra las imágenes del SKU para un canal: válida si su extensión está permitida y no supera 10 MB. */
    public FiltroImagenes filtrarParaCanal(String sku, Set<String> extensionesPermitidas) {
        return filtrarParaCanal(sku, extensionesPermitidas, MAX_BYTES_CANAL);
    }

    /** Variante con límite configurable (para tests). */
    FiltroImagenes filtrarParaCanal(String sku, Set<String> extensionesPermitidas, long maxBytes) {
        List<String> validas = new ArrayList<>();
        List<ImagenRechazada> rechazadas = new ArrayList<>();
        for (ImagenDetalle d : resolverDetallePorSku(sku)) {
            if (!extensionesPermitidas.contains(d.extension())) {
                rechazadas.add(new ImagenRechazada(d.nombre(), MotivoRechazo.FORMATO));
            } else if (d.bytes() > maxBytes) {
                rechazadas.add(new ImagenRechazada(d.nombre(), MotivoRechazo.TAMANO));
            } else {
                validas.add(d.nombre());
            }
        }
        return new FiltroImagenes(validas, rechazadas);
    }

    /** Texto para el resumen del export con las imágenes omitidas; null si no hay ninguna. */
    public static String describirRechazadas(List<ImagenRechazada> rechazadas) {
        if (rechazadas == null || rechazadas.isEmpty()) return null;
        String detalle = rechazadas.stream()
                .map(r -> r.nombre() + " (" + (r.motivo() == MotivoRechazo.FORMATO ? "formato" : "supera 10MB") + ")")
                .collect(Collectors.joining(", "));
        return rechazadas.size() + " omitida(s): " + detalle;
    }

    /** Lee {baseDir}/{filename} y devuelve sus bytes crudos. */
    public byte[] leerBytes(String filename) {
        try {
            return Files.readAllBytes(baseDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen " + filename, e);
        }
    }

    /** Lee {baseDir}/{filename} y devuelve su contenido en Base64. */
    public String leerBase64(String filename) {
        try {
            byte[] bytes = Files.readAllBytes(baseDir.resolve(filename));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen " + filename, e);
        }
    }

    /** Nombre del archivo crudo {SKU}.{ext} en la carpeta de entrada, o null si no existe. */
    public String resolverCrudaPorSku(String sku) {
        validarNombreSeguro(sku);
        for (String ext : EXTENSIONES) {
            String nombre = sku.trim() + "." + ext;
            if (Files.isRegularFile(rawDir.resolve(nombre))) return nombre;
        }
        return null;
    }

    /** Bytes de un archivo de la carpeta cruda. */
    public byte[] leerCrudaBytes(String filename) {
        validarNombreSeguro(filename);
        try {
            return Files.readAllBytes(rawDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la cruda " + filename, e);
        }
    }

    /** Guarda la carátula como {sku}.{ext} en baseDir e invalida el índice. */
    public void guardarCaratula(String sku, byte[] datos, String ext) {
        validarNombreSeguro(sku);
        try {
            Files.createDirectories(baseDir);
            Files.write(baseDir.resolve(sku.trim() + "." + ext), datos);
            invalidarIndice();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar la carátula de " + sku, e);
        }
    }

    /**
     * Valida que el nombre/SKU no contenga separadores de path ni secuencias de traversal.
     * Protege contra path traversal en los métodos que resuelven archivos por SKU o filename.
     */
    private static void validarNombreSeguro(String s) {
        if (s == null || s.isBlank() || s.contains("/") || s.contains("\\") || s.contains("..")) {
            throw new IllegalArgumentException("Nombre/SKU inválido");
        }
    }

    /** Fuerza el re-escaneo del índice en la próxima consulta (tras escribir un archivo). */
    public synchronized void invalidarIndice() {
        indiceTimestamp = 0L;
    }

    /** Slot del archivo respecto del SKU: 0 si es la principal ({sku}), N si es {sku}_N (N>=1), null si no matchea. */
    private static Integer slotDe(String base, String skuLower) {
        if (base.equals(skuLower)) {
            return 0;
        }
        String prefijo = skuLower + "_";
        if (base.startsWith(prefijo)) {
            String resto = base.substring(prefijo.length());
            if (!resto.isEmpty() && resto.chars().allMatch(Character::isDigit)) {
                int n = Integer.parseInt(resto);
                if (n >= 1) {
                    return n;
                }
            }
        }
        return null;
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
        refrescarSiExpiro();
        return indice;
    }

    private Map<String, String> obtenerIndiceSku() {
        refrescarSiExpiro();
        return indiceSku;
    }

    /** Re-escanea el directorio (lista + mapa por SKU) cuando el índice cacheado expiró. */
    private void refrescarSiExpiro() {
        if (System.currentTimeMillis() - indiceTimestamp < ttlMillis) {
            return;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - indiceTimestamp < ttlMillis) {
                return;
            }
            List<String> nombres = escanear();
            indice = nombres;
            indiceSku = construirMapaSku(nombres);
            indiceTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * Construye el mapa sku(lowercase) → nombre de archivo. Ante colisión (mismo SKU con
     * distintas extensiones), gana la extensión de mayor prioridad según {@link #EXTENSIONES}.
     */
    private static Map<String, String> construirMapaSku(List<String> nombres) {
        Map<String, String> mapa = new HashMap<>(Math.max(16, nombres.size() * 2));
        for (String nombre : nombres) {
            int dot = nombre.lastIndexOf('.');
            if (dot <= 0) continue;
            String sku = nombre.substring(0, dot).toLowerCase(Locale.ROOT);
            String existente = mapa.get(sku);
            if (existente == null || prioridadExtension(nombre) < prioridadExtension(existente)) {
                mapa.put(sku, nombre);
            }
        }
        return mapa;
    }

    /** Índice de prioridad de la extensión del archivo (menor = mayor prioridad; jpg primero). */
    private static int prioridadExtension(String nombre) {
        int dot = nombre.lastIndexOf('.');
        String ext = dot >= 0 ? nombre.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        for (int i = 0; i < EXTENSIONES.length; i++) {
            if (EXTENSIONES[i].equals(ext)) return i;
        }
        return Integer.MAX_VALUE;
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
