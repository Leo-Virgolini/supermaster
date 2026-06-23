package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MlFamilyNameTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void payload_tieneFamilyNameYNoTitle() {
        Producto p = new Producto();
        p.setSku("ABC123"); p.setTituloMl("Olla 5L");
        Map<String, Object> payload = MlItemPayloadBuilder.construir(
                p, "MLA1234", new BigDecimal("5000"), 0, List.of("PIC1"), "Olla 5L familia");
        assertThat(payload).containsEntry("family_name", "Olla 5L familia");
        assertThat(payload).doesNotContainKey("title");
    }

    @Test
    void construirFamilyName_recortaAlMax() {
        assertThat(MercadoLibreService.construirFamilyName("ABCDEFGHIJ", 5)).isEqualTo("ABCDE");
    }

    @Test
    void construirFamilyName_respetaCorto() {
        assertThat(MercadoLibreService.construirFamilyName("Olla 5L", 60)).isEqualTo("Olla 5L");
    }

    @Test
    void construirFamilyName_trimAntesYDespuesDelRecorte() {
        assertThat(MercadoLibreService.construirFamilyName("  ABC XYZ  ", 4)).isEqualTo("ABC");
    }

    @Test
    void parseMaxTitleLength_leeDeSettings() {
        var node = om.readTree("{\"settings\":{\"max_title_length\":70}}");
        assertThat(MercadoLibreService.parseMaxTitleLength(node)).isEqualTo(70);
    }

    @Test
    void parseMaxTitleLength_defaultSiFalta() {
        var node = om.readTree("{\"settings\":{}}");
        assertThat(MercadoLibreService.parseMaxTitleLength(node)).isEqualTo(60);
    }
}
