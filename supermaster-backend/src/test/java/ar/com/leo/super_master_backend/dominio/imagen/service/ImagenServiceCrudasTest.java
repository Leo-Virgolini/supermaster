package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagenServiceCrudasTest {

    private ImagenService nueva(Path baseDir, Path rawDir) {
        return new ImagenService(baseDir.toString(), 60000L, rawDir.toString());
    }

    @Test
    void resolverCrudas_principalPrimeroLuegoAdicionales(@TempDir Path baseDir, @TempDir Path rawDir) throws Exception {
        Files.writeString(rawDir.resolve("ABC.jpg"), "x");
        Files.writeString(rawDir.resolve("ABC_2.png"), "x");
        Files.writeString(rawDir.resolve("ABC_1.jpg"), "x");
        Files.writeString(rawDir.resolve("OTRO.jpg"), "x");

        List<String> r = nueva(baseDir, rawDir).resolverCrudasPorSku("ABC");

        assertThat(r).containsExactly("ABC.jpg", "ABC_1.jpg", "ABC_2.png");
    }

    @Test
    void resolverCrudas_skuSinArchivos_vacio(@TempDir Path baseDir, @TempDir Path rawDir) {
        assertThat(nueva(baseDir, rawDir).resolverCrudasPorSku("NADA")).isEmpty();
    }

    @Test
    void resolverCrudas_skuInseguro_lanza(@TempDir Path baseDir, @TempDir Path rawDir) {
        assertThatThrownBy(() -> nueva(baseDir, rawDir).resolverCrudasPorSku("../x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
