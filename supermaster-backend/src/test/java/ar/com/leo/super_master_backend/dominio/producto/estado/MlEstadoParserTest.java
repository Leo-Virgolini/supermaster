package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MlEstadoParserTest {

    private static JsonNode json(String s) {
        return new ObjectMapper().readTree(s);
    }

    @Test
    void parse_extraeEstadoPrecioStockYDimensiones() {
        JsonNode item = json("""
            {
              "status": "active",
              "price": 12345.0,
              "available_quantity": 7,
              "attributes": [
                {"id": "SELLER_PACKAGE_HEIGHT", "value_name": "10 cm"},
                {"id": "SELLER_PACKAGE_WIDTH",  "value_name": "20 cm"},
                {"id": "SELLER_PACKAGE_LENGTH", "value_name": "30 cm"},
                {"id": "SELLER_PACKAGE_WEIGHT", "value_name": "214 g"}
              ]
            }
            """);

        EstadoCanalDTO dto = MlEstadoParser.parse(item);

        assertThat(dto.publicado()).isTrue();
        assertThat(dto.estado()).isEqualTo("active");
        assertThat(dto.precio()).isEqualByComparingTo(new BigDecimal("12345.0"));
        assertThat(dto.stock()).isEqualTo(7);
        assertThat(dto.peso()).isEqualTo("214 g");
        assertThat(dto.dimensiones()).isEqualTo("10 cm × 20 cm × 30 cm");
        assertThat(dto.error()).isFalse();
    }

    @Test
    void parse_sinAtributosDeDimensiones_dejaPesoYDimsNull() {
        JsonNode item = json("""
            {"status": "paused", "price": 100, "available_quantity": 0, "attributes": []}
            """);

        EstadoCanalDTO dto = MlEstadoParser.parse(item);

        assertThat(dto.estado()).isEqualTo("paused");
        assertThat(dto.peso()).isNull();
        assertThat(dto.dimensiones()).isNull();
        assertThat(dto.publicado()).isTrue();
    }
}
