package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests de {@link ImagenService#resolverArchivoPorSku(String)} usando un directorio temporal.
 * TTL=0 fuerza el re-escaneo en cada consulta, así los archivos creados en cada test se ven.
 */
class ImagenServiceTest {

    @TempDir
    Path dir;

    private ImagenService servicio() {
        return new ImagenService(dir.toString(), 0L);
    }

    private void crear(String nombre) throws IOException {
        Files.createFile(dir.resolve(nombre));
    }

    @Test
    @DisplayName("Resuelve el archivo cuyo nombre es el SKU")
    void resuelvePorSku() throws IOException {
        crear("ABC123.jpg");
        assertEquals("ABC123.jpg", servicio().resolverArchivoPorSku("ABC123"));
    }

    @Test
    @DisplayName("La resolución es case-insensitive en el SKU y en la extensión")
    void resuelveCaseInsensitive() throws IOException {
        crear("ABC123.JPG");
        ImagenService s = servicio();
        assertEquals("ABC123.JPG", s.resolverArchivoPorSku("abc123"));
        assertEquals("ABC123.JPG", s.resolverArchivoPorSku("ABC123"));
    }

    @Test
    @DisplayName("Ante colisión de formatos, gana la extensión de mayor prioridad (jpg sobre png)")
    void prioridadDeExtension() throws IOException {
        crear("P1.png");
        crear("P1.jpg");
        assertEquals("P1.jpg", servicio().resolverArchivoPorSku("P1"));
    }

    @Test
    @DisplayName("SKU sin archivo devuelve null")
    void skuInexistente() throws IOException {
        crear("OTRO.jpg");
        assertNull(servicio().resolverArchivoPorSku("NOEXISTE"));
    }

    @Test
    @DisplayName("SKU null o vacío devuelve null")
    void skuNuloOVacio() {
        ImagenService s = servicio();
        assertNull(s.resolverArchivoPorSku(null));
        assertNull(s.resolverArchivoPorSku("   "));
    }
}
