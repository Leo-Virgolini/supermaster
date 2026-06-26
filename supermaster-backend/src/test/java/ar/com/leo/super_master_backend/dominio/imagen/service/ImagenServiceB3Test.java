package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagenServiceB3Test {

    // ttl 0 => el índice se re-escanea en cada acceso (sin caché entre llamadas en el test).
    private ImagenService servicioSobre(Path dir) {
        return new ImagenService(dir.toString(), 0L);
    }

    @Test
    void resolverArchivos_principalYAdicionalesEnOrden(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("ABC123.jpg"), "x");
        Files.writeString(dir.resolve("ABC123_1.png"), "x");
        Files.writeString(dir.resolve("ABC123_2.jpg"), "x");
        Files.writeString(dir.resolve("ABC123_3.jpg"), "x");
        Files.writeString(dir.resolve("OTRO.jpg"), "x"); // otro SKU, no debe aparecer

        List<String> archivos = servicioSobre(dir).resolverArchivosPorSku("ABC123");

        assertThat(archivos).containsExactly("ABC123.jpg", "ABC123_1.png", "ABC123_2.jpg", "ABC123_3.jpg");
    }

    @Test
    void resolverArchivos_caseInsensitive(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("abc123.JPG"), "x");
        Files.writeString(dir.resolve("abc123_1.jpg"), "x");

        List<String> archivos = servicioSobre(dir).resolverArchivosPorSku("ABC123");

        assertThat(archivos).containsExactly("abc123.JPG", "abc123_1.jpg");
    }

    @Test
    void resolverArchivos_sinArchivos_listaVacia(@TempDir Path dir) {
        List<String> archivos = servicioSobre(dir).resolverArchivosPorSku("NOEXISTE");
        assertThat(archivos).isEmpty();
    }

    @Test
    void resolverArchivos_skuNuloOBlank_listaVacia(@TempDir Path dir) {
        assertThat(servicioSobre(dir).resolverArchivosPorSku(null)).isEmpty();
        assertThat(servicioSobre(dir).resolverArchivosPorSku("  ")).isEmpty();
    }

    @Test
    void leerBase64_devuelveContenidoCodificado(@TempDir Path dir) throws Exception {
        byte[] contenido = {1, 2, 3, 4, 5};
        Files.write(dir.resolve("ABC123.jpg"), contenido);

        String base64 = servicioSobre(dir).leerBase64("ABC123.jpg");

        assertThat(base64).isEqualTo(Base64.getEncoder().encodeToString(contenido));
    }

    @Test
    void resolverArchivos_incluyeSlot1YDescartaSlot0(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("ABC123.jpg"), "x");
        Files.writeString(dir.resolve("ABC123_1.jpg"), "x");
        Files.writeString(dir.resolve("ABC123_0.jpg"), "x"); // _0 no es convención: se descarta
        Files.writeString(dir.resolve("ABC123_2.jpg"), "x");

        List<String> archivos = servicioSobre(dir).resolverArchivosPorSku("ABC123");

        assertThat(archivos).containsExactly("ABC123.jpg", "ABC123_1.jpg", "ABC123_2.jpg");
    }

    @Test
    void leerBase64_archivoInexistente_lanzaUncheckedIOException(@TempDir Path dir) {
        assertThatThrownBy(() -> servicioSobre(dir).leerBase64("NOEXISTE.jpg"))
                .isInstanceOf(UncheckedIOException.class);
    }
}
