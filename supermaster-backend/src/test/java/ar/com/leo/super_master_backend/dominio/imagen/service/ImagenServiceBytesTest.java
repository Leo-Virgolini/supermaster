package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagenServiceBytesTest {

    private ImagenService servicioSobre(Path dir) {
        return new ImagenService(dir.toString(), 0L);
    }

    @Test
    void leerBytes_devuelveContenido(@TempDir Path dir) throws Exception {
        byte[] contenido = {10, 20, 30, 40};
        Files.write(dir.resolve("ABC123.jpg"), contenido);
        assertThat(servicioSobre(dir).leerBytes("ABC123.jpg")).containsExactly(10, 20, 30, 40);
    }

    @Test
    void leerBytes_inexistente_lanza(@TempDir Path dir) {
        assertThatThrownBy(() -> servicioSobre(dir).leerBytes("NOEXISTE.jpg"))
                .isInstanceOf(UncheckedIOException.class);
    }
}
