package ar.com.leo.super_master_backend.dominio.imagen.controller;

import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenDetalle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenControllerDetalleTest {

    private ImagenController controllerSobre(Path dir) {
        return new ImagenController(new ImagenService(dir.toString(), 0L));
    }

    @Test
    void detalle_devuelveImagenesDelSku(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("SKU9.jpg"), new byte[]{1, 2, 3});
        List<ImagenDetalle> body = controllerSobre(dir).detalle("SKU9").getBody();
        assertThat(body).hasSize(1);
        assertThat(body.getFirst().nombre()).isEqualTo("SKU9.jpg");
    }

    @Test
    void detalle_sinImagenes_listaVacia(@TempDir Path dir) {
        assertThat(controllerSobre(dir).detalle("NADA").getBody()).isEmpty();
    }
}
