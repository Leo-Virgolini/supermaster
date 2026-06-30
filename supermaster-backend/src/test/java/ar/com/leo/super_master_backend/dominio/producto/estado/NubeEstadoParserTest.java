package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NubeEstadoParserTest {

    private static JsonNode json(String s) {
        return new ObjectMapper().readTree(s);
    }

    @Test
    void parse_visibleConVariant() {
        JsonNode product = json("""
            {
              "id": 555,
              "published": true,
              "variants": [
                {"id": 999, "price": "12345.00", "stock": 4, "weight": "1.50",
                 "height": "10.00", "width": "20.00", "depth": "30.00"}
              ]
            }
            """);

        EstadoCanalDTO dto = NubeEstadoParser.parse(product);

        assertThat(dto.publicado()).isTrue();
        assertThat(dto.estado()).isEqualTo("visible");
        assertThat(dto.precio()).isEqualByComparingTo(new BigDecimal("12345.00"));
        assertThat(dto.stock()).isEqualTo(4);
        assertThat(dto.peso()).isEqualTo("1.50 kg");
        assertThat(dto.dimensiones()).isEqualTo("10.00 × 20.00 × 30.00 cm");
    }

    @Test
    void parse_ocultaYStockNulo() {
        JsonNode product = json("""
            {"id": 1, "published": false, "variants": [{"id": 2, "price": "10", "stock": null,
              "weight": "0.050", "height": "5.00", "width": "5.00", "depth": "8.00"}]}
            """);

        EstadoCanalDTO dto = NubeEstadoParser.parse(product);

        assertThat(dto.estado()).isEqualTo("oculta");
        assertThat(dto.stock()).isNull();
        assertThat(dto.publicado()).isTrue();
    }

    @Test
    void parse_cuentaImagenesDeImages() {
        JsonNode product = json("""
            {"published":true,"variants":[{"price":"10","stock":2}],
             "images":[{"id":1},{"id":2}]}
            """);
        assertThat(NubeEstadoParser.parse(product).imagenes()).isEqualTo(2);
    }
}
