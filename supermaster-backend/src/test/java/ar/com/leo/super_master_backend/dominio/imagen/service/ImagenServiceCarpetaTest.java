package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenServiceCarpetaTest {

    private ImagenService nueva(Path baseDir, Path rawDir) {
        return new ImagenService(baseDir.toString(), 60000L, rawDir.toString());
    }

    @Test
    void eliminarCrudas_borraTodasLasDelSku(@TempDir Path baseDir, @TempDir Path rawDir) throws Exception {
        Files.writeString(rawDir.resolve("ABC.jpg"), "x");
        Files.writeString(rawDir.resolve("ABC_1.jpg"), "x");
        Files.writeString(rawDir.resolve("OTRO.jpg"), "x");

        int n = nueva(baseDir, rawDir).eliminarCrudasPorSku("ABC");

        assertThat(n).isEqualTo(2);
        assertThat(Files.exists(rawDir.resolve("ABC.jpg"))).isFalse();
        assertThat(Files.exists(rawDir.resolve("ABC_1.jpg"))).isFalse();
        assertThat(Files.exists(rawDir.resolve("OTRO.jpg"))).isTrue();
    }

    @Test
    void eliminarCrudas_sinArchivos_cero(@TempDir Path baseDir, @TempDir Path rawDir) {
        assertThat(nueva(baseDir, rawDir).eliminarCrudasPorSku("NADA")).isZero();
    }

    @Test
    void estadoCrudaDir_existente_legible(@TempDir Path baseDir, @TempDir Path rawDir) {
        ImagenService.EstadoCarpeta e = nueva(baseDir, rawDir).estadoCrudaDir();
        assertThat(e.existe()).isTrue();
        assertThat(e.esDirectorio()).isTrue();
        assertThat(e.legible()).isTrue();
    }
}
