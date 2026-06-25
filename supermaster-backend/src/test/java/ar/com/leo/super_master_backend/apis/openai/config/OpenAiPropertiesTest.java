package ar.com.leo.super_master_backend.apis.openai.config;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiPropertiesTest {
    @Test
    void defaults_precios() {
        OpenAiProperties p = new OpenAiProperties(null, null, null, null, null, null);
        assertThat(p.precioInput1m()).isEqualByComparingTo("0.25");
        assertThat(p.precioOutput1m()).isEqualByComparingTo("2.00");
    }

    @Test
    void respeta_precios_provistos() {
        OpenAiProperties p = new OpenAiProperties(null, null, null, null, new BigDecimal("0.15"), new BigDecimal("0.60"));
        assertThat(p.precioInput1m()).isEqualByComparingTo("0.15");
        assertThat(p.precioOutput1m()).isEqualByComparingTo("0.60");
    }
}
