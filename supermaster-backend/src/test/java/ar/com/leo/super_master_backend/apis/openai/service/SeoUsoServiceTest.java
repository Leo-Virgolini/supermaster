package ar.com.leo.super_master_backend.apis.openai.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class SeoUsoServiceTest {

    private static final BigDecimal P_IN = new BigDecimal("0.25");
    private static final BigDecimal P_OUT = new BigDecimal("2.00");

    @Test
    void costo_unMillonInput_igualPrecioInput() {
        assertThat(SeoUsoService.calcularCosto(1_000_000, 0, P_IN, P_OUT)).isEqualByComparingTo("0.25");
    }

    @Test
    void costo_unMillonOutput_igualPrecioOutput() {
        assertThat(SeoUsoService.calcularCosto(0, 1_000_000, P_IN, P_OUT)).isEqualByComparingTo("2.00");
    }

    @Test
    void costo_combinado_sumaAmbos() {
        // 500k in -> 0.125 ; 250k out -> 0.50 ; total 0.625
        assertThat(SeoUsoService.calcularCosto(500_000, 250_000, P_IN, P_OUT)).isEqualByComparingTo("0.625");
    }

    @Test
    void costo_cero_esCero() {
        assertThat(SeoUsoService.calcularCosto(0, 0, P_IN, P_OUT)).isEqualByComparingTo("0");
    }
}
