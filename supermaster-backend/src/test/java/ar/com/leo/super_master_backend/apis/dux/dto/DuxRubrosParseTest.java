package ar.com.leo.super_master_backend.apis.dux.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test offline (sin red ni BD) que valida los {@code @JsonProperty} de los DTOs de Dux
 * usados para sincronizar id_dux de clasificaciones.
 */
class DuxRubrosParseTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void parseaRubros() {
        String json = "[{\"id_rubro\":10,\"rubro\":\"Hogar\"}]";

        DuxRubro[] rubros = om.readValue(json, DuxRubro[].class);

        assertThat(rubros).hasSize(1);
        assertThat(rubros[0].idRubro()).isEqualTo(10);
        assertThat(rubros[0].rubro()).isEqualTo("Hogar");
    }

    @Test
    void parseaSubrubros() {
        String json = "[{\"id_rubro\":20,\"id_sub_rubro\":201,\"rubro\":\"Bazar y Cocina\",\"sub_rubro\":\"Vajilla\"}]";

        DuxSubrubro[] subrubros = om.readValue(json, DuxSubrubro[].class);

        assertThat(subrubros).hasSize(1);
        assertThat(subrubros[0].idRubro()).isEqualTo(20);
        assertThat(subrubros[0].idSubRubro()).isEqualTo(201);
        assertThat(subrubros[0].rubro()).isEqualTo("Bazar y Cocina");
        assertThat(subrubros[0].subRubro()).isEqualTo("Vajilla");
    }
}
