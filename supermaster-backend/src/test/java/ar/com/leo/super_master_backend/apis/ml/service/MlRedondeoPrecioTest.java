package ar.com.leo.super_master_backend.apis.ml.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class MlRedondeoPrecioTest {
    @Test
    void redondea_a_entero_half_up() {
        assertThat(MercadoLibreService.redondearPrecioMl(new BigDecimal("18340.49"))).isEqualByComparingTo("18340");
        assertThat(MercadoLibreService.redondearPrecioMl(new BigDecimal("18340.50"))).isEqualByComparingTo("18341");
        assertThat(MercadoLibreService.redondearPrecioMl(new BigDecimal("18340.99"))).isEqualByComparingTo("18341");
    }

    @Test
    void null_devuelve_null() {
        assertThat(MercadoLibreService.redondearPrecioMl(null)).isNull();
    }
}
