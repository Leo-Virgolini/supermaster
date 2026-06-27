package ar.com.leo.super_master_backend.dominio.imagen.service;

import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.FiltroImagenes;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenDetalle;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenRechazada;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.MotivoRechazo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenServiceFiltroTest {

    private static final Set<String> EXT_ML = Set.of("jpg", "jpeg", "png");

    private ImagenService servicioSobre(Path dir) {
        return new ImagenService(dir.toString(), 0L, dir.toString());
    }

    @Test
    void resolverDetalle_devuelveExtensionYBytes(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("ABC123.JPG"), new byte[]{1, 2, 3, 4, 5});
        List<ImagenDetalle> det = servicioSobre(dir).resolverDetallePorSku("abc123");
        assertThat(det).hasSize(1);
        assertThat(det.getFirst().extension()).isEqualTo("jpg");
        assertThat(det.getFirst().bytes()).isEqualTo(5);
    }

    @Test
    void filtrar_formatoNoPermitido_vaARechazadasFormato(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("SKU1_2.webp"), new byte[]{1, 2});
        FiltroImagenes f = servicioSobre(dir).filtrarParaCanal("SKU1", EXT_ML);
        assertThat(f.validas()).isEmpty();
        assertThat(f.rechazadas()).containsExactly(new ImagenRechazada("SKU1_2.webp", MotivoRechazo.FORMATO));
    }

    @Test
    void filtrar_superaTamano_vaARechazadasTamano(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("SKU1.jpg"), new byte[]{1, 2, 3, 4});
        // overload con maxBytes chico para no crear 10MB en disco
        FiltroImagenes f = servicioSobre(dir).filtrarParaCanal("SKU1", EXT_ML, 2L);
        assertThat(f.validas()).isEmpty();
        assertThat(f.rechazadas()).containsExactly(new ImagenRechazada("SKU1.jpg", MotivoRechazo.TAMANO));
    }

    @Test
    void filtrar_formatoYTamanoOk_esValida(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("SKU1.jpg"), new byte[]{1, 2, 3, 4});
        FiltroImagenes f = servicioSobre(dir).filtrarParaCanal("SKU1", EXT_ML);
        assertThat(f.validas()).containsExactly("SKU1.jpg");
        assertThat(f.rechazadas()).isEmpty();
    }

    @Test
    void describirRechazadas_formateaConMotivo() {
        String txt = ImagenService.describirRechazadas(List.of(
                new ImagenRechazada("foto.webp", MotivoRechazo.FORMATO),
                new ImagenRechazada("grande.jpg", MotivoRechazo.TAMANO)));
        assertThat(txt).isEqualTo("2 omitida(s): foto.webp (formato), grande.jpg (supera 10MB)");
    }

    @Test
    void describirRechazadas_vacio_devuelveNull() {
        assertThat(ImagenService.describirRechazadas(List.of())).isNull();
    }
}
