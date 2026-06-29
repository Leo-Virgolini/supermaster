package ar.com.leo.super_master_backend.dominio.imagen.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenControllerMimeTest {

    @Test
    void subtipoMimeDe_mapeaExtensiones() {
        assertThat(ImagenController.subtipoMimeDe("foto.jpg")).isEqualTo("jpeg");
        assertThat(ImagenController.subtipoMimeDe("foto.JPEG")).isEqualTo("jpeg");
        assertThat(ImagenController.subtipoMimeDe("X.png")).isEqualTo("png");
        assertThat(ImagenController.subtipoMimeDe("X.webp")).isEqualTo("webp");
    }
}
