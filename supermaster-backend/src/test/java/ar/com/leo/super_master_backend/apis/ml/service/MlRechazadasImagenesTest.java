package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.ImagenRechazada;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.MotivoRechazo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlRechazadasImagenesTest {

    private static final List<ImagenRechazada> UNA = List.of(new ImagenRechazada("x.webp", MotivoRechazo.FORMATO));

    @Test
    void creado_conRechazadas_agregaAdvertencia() {
        ResultadoAltaMl r = MercadoLibreService.aplicarRechazadasImagenes(ResultadoAltaMl.creado("MLA1", null), UNA);
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(r.advertencia()).isEqualTo("1 omitida(s): x.webp (formato)");
    }

    @Test
    void creado_conRechazadas_preservaAdvertenciaPrevia() {
        ResultadoAltaMl base = ResultadoAltaMl.creado("MLA1", null).conAdvertencia("ítem creado pero falló la descripción");
        ResultadoAltaMl r = MercadoLibreService.aplicarRechazadasImagenes(base, UNA);
        assertThat(r.advertencia()).isEqualTo("ítem creado pero falló la descripción; 1 omitida(s): x.webp (formato)");
    }

    @Test
    void error_noSeModifica() {
        ResultadoAltaMl base = ResultadoAltaMl.error("falta título ML");
        ResultadoAltaMl r = MercadoLibreService.aplicarRechazadasImagenes(base, UNA);
        assertThat(r).isSameAs(base);
    }

    @Test
    void sinRechazadas_noSeModifica() {
        ResultadoAltaMl base = ResultadoAltaMl.creado("MLA1", null);
        assertThat(MercadoLibreService.aplicarRechazadasImagenes(base, List.of())).isSameAs(base);
    }
}
