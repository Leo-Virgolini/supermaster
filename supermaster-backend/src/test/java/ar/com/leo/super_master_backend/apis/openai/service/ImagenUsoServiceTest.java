package ar.com.leo.super_master_backend.apis.openai.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class ImagenUsoServiceTest {
    @Test
    void calcularCosto_sumaInputYOutputPorMillon() {
        BigDecimal costo = ImagenUsoService.calcularCosto(1_000_000L, 500_000L, new BigDecimal("5.00"), new BigDecimal("40.00"));
        assertThat(costo).isEqualByComparingTo(new BigDecimal("25.000000"));
    }
}
