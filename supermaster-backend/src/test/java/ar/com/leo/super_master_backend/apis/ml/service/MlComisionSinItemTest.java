package ar.com.leo.super_master_backend.apis.ml.service;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MlComisionSinItemTest {
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void extrae_meli_percentage_fee_del_listing_type() throws Exception {
        String json = """
            [{"listing_type_id":"gold_special","sale_fee_details":{"meli_percentage_fee":13,"percentage_fee":13}},
             {"listing_type_id":"gold_pro","sale_fee_details":{"meli_percentage_fee":15.5}}]""";
        assertThat(MercadoLibreService.parseMeliPercentageFee(om.readTree(json), "gold_special"))
                .isEqualByComparingTo("13");
    }

    @Test
    void listing_type_inexistente_devuelve_cero() throws Exception {
        String json = "[{\"listing_type_id\":\"free\",\"sale_fee_details\":{\"meli_percentage_fee\":0}}]";
        assertThat(MercadoLibreService.parseMeliPercentageFee(om.readTree(json), "gold_special"))
                .isEqualByComparingTo("0");
    }
}
