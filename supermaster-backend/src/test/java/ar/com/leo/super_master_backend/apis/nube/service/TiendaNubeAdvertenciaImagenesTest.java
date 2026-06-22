package ar.com.leo.super_master_backend.apis.nube.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TiendaNubeAdvertenciaImagenesTest {

    @Test
    void combinar_uneNoNulos() {
        assertThat(TiendaNubeService.combinarAdvertencias("2 de 3 imágenes subidas", "1 omitida(s): x.webp (formato)"))
                .isEqualTo("2 de 3 imágenes subidas; 1 omitida(s): x.webp (formato)");
    }

    @Test
    void combinar_ignoraNullYBlank() {
        assertThat(TiendaNubeService.combinarAdvertencias(null, "1 omitida(s): x.bmp (formato)"))
                .isEqualTo("1 omitida(s): x.bmp (formato)");
    }

    @Test
    void combinar_todoVacio_devuelveNull() {
        assertThat(TiendaNubeService.combinarAdvertencias(null, "")).isNull();
    }
}
