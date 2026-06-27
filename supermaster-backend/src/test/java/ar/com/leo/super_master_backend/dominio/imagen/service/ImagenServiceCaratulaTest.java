package ar.com.leo.super_master_backend.dominio.imagen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenServiceCaratulaTest {

    private ImagenService servicio(Path imagenes, Path crudas) {
        return new ImagenService(imagenes.toString(), 0L, crudas.toString());
    }

    @Test
    void resolverYLeerCruda_desdeLaCarpetaDeEntrada(@TempDir Path imagenes, @TempDir Path crudas) throws Exception {
        Files.write(crudas.resolve("ABC.png"), new byte[]{1, 2, 3});
        ImagenService s = servicio(imagenes, crudas);
        assertThat(s.resolverCrudaPorSku("ABC")).isEqualTo("ABC.png");
        assertThat(s.leerCrudaBytes("ABC.png")).containsExactly(1, 2, 3);
    }

    @Test
    void resolverCruda_sinArchivo_devuelveNull(@TempDir Path imagenes, @TempDir Path crudas) {
        assertThat(servicio(imagenes, crudas).resolverCrudaPorSku("NOPE")).isNull();
    }

    @Test
    void guardarCaratula_escribeJpgEnImagenesYSeResuelve(@TempDir Path imagenes, @TempDir Path crudas) {
        ImagenService s = servicio(imagenes, crudas);
        s.guardarCaratula("ABC", new byte[]{9, 9, 9});
        assertThat(imagenes.resolve("ABC.jpg")).exists();
        assertThat(s.resolverArchivoPorSku("ABC")).isEqualTo("ABC.jpg");
    }
}
