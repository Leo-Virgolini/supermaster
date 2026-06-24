package ar.com.leo.super_master_backend.apis.ml.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class EnvioEstabilizadorTest {

    private static final BigDecimal DIV_IVA = new BigDecimal("1.21");

    @Test
    void estabiliza_cuando_el_envio_se_repite() {
        // pvp = base + envioSinIva*2 ; envioConIva fijo 1210 (sinIva 1000).
        var r = EnvioEstabilizador.estabilizar(
                envioSinIva -> new BigDecimal("10000").add(envioSinIva.multiply(new BigDecimal("2"))),
                pvp -> new BigDecimal("1210"),
                DIV_IVA, 10);
        assertThat(r.costoEnvioConIva()).isEqualByComparingTo("1210");
        assertThat(r.costoEnvioSinIva()).isEqualByComparingTo("1000.00");
        assertThat(r.iteraciones()).isLessThanOrEqualTo(10);
    }

    @Test
    void corta_por_oscilacion_y_toma_el_mayor() {
        // sinIva<=900 -> pvp=5000 -> envio=2000 -> sinIva=1652 -> pvp=20000 -> envio=1000
        // -> sinIva=826 -> pvp=5000 -> envio=2000 (ya visto!) -> OSCILA -> max(2000,1000)=2000.
        // Nota: los valores 20000/5000 están en orden opuesto al del pvpFn > 900
        // para forzar que el ciclo pase por 2000 primero y luego 1000, creando oscilación.
        var r = EnvioEstabilizador.estabilizar(
                envioSinIva -> envioSinIva.compareTo(new BigDecimal("900")) > 0
                        ? new BigDecimal("20000") : new BigDecimal("5000"),
                pvp -> pvp.compareTo(new BigDecimal("12000")) > 0
                        ? new BigDecimal("1000") : new BigDecimal("2000"),
                DIV_IVA, 10);
        assertThat(r.costoEnvioConIva()).isEqualByComparingTo("2000");
    }
}
