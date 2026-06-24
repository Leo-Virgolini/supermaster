package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios puros para la lógica del orquestador calcularPrecioFinalParaPublicar.
 *
 * <p>El método orquestador en sí requiere demasiadas dependencias Spring para un test
 * de integración completo (MercadoLibreService necesita ObjectMapper, repos, RestClient,
 * secretos, etc.). Se testean en cambio las piezas clave:
 *
 * <ol>
 *   <li>Que BadRequestException y NotFoundException del motor de cálculo se propagan
 *       a través del EnvioEstabilizador (valida el contrato del try/catch del orquestador).</li>
 *   <li>Que el bucle estabilizador + redondeo produce el PVP esperado en el caso feliz.</li>
 *   <li>Que redondearPrecioMl redondea correctamente (complementa MlRedondeoPrecioTest).</li>
 * </ol>
 */
class MlPrecioFinalTest {

    private static final BigDecimal DIV_IVA_21 = new BigDecimal("1.21");

    // ----------------------------------------------------------------
    // 1. Propagación de BadRequestException a través del estabilizador
    // ----------------------------------------------------------------

    /**
     * Simula el pvpFn del orquestador lanzando BadRequestException (sin margen/costo).
     * El EnvioEstabilizador NO atrapa RuntimeExceptions no declaradas, así que la excepción
     * se propaga y el orquestador la convierte en IllegalStateException.
     *
     * Este test valida ese contrato: que la excepción SE PROPAGA desde la lambda y puede
     * ser atrapada por el catch del orquestador.
     */
    @Test
    void badRequestException_del_motor_se_propaga_desde_lambda() {
        // Simula exactamente lo que haría pvpFn del orquestador si el motor no puede calcular
        assertThatThrownBy(() ->
            EnvioEstabilizador.estabilizar(
                costoEnvioSinIva -> { throw new BadRequestException("El producto no tiene costo"); },
                pvp -> BigDecimal.ZERO,
                DIV_IVA_21,
                10
            )
        ).isInstanceOf(BadRequestException.class)
         .hasMessageContaining("costo");
    }

    /**
     * Simula NotFoundException (canal/margen no encontrado) propagándose desde pvpFn.
     */
    @Test
    void notFoundException_del_motor_se_propaga_desde_lambda() {
        assertThatThrownBy(() ->
            EnvioEstabilizador.estabilizar(
                costoEnvioSinIva -> { throw new NotFoundException("Canal no encontrado"); },
                pvp -> BigDecimal.ZERO,
                DIV_IVA_21,
                10
            )
        ).isInstanceOf(NotFoundException.class)
         .hasMessageContaining("Canal");
    }

    // ----------------------------------------------------------------
    // 2. Bucle estabilizador + redondeo (caso feliz)
    // ----------------------------------------------------------------

    /**
     * Simula el bucle completo del orquestador con lambdas que representan:
     * - pvpFn: PVP = 10000 + costoEnvioSinIva (simula comisión ya aplicada)
     * - envioConIvaFn: pvp < umbral → tier fijo 1210 (sinIva = 1000)
     *
     * Espera convergencia en pocas iteraciones y PVP final redondeado a entero.
     */
    @Test
    void bucle_con_envio_fijo_converge_y_pvp_es_entero() {
        BigDecimal umbral = new BigDecimal("50000");

        var r = EnvioEstabilizador.estabilizar(
            costoEnvioSinIva -> new BigDecimal("10000").add(costoEnvioSinIva),
            pvp -> pvp.compareTo(umbral) >= 0
                    ? new BigDecimal("2420")   // API ML (no llega acá en este test)
                    : new BigDecimal("1210"),  // tier < umbral
            DIV_IVA_21,
            10
        );

        assertThat(r.pvp()).isNotNull();
        assertThat(r.pvp()).isGreaterThan(BigDecimal.ZERO);
        assertThat(r.iteraciones()).isLessThanOrEqualTo(10);

        // Verificar que redondear produce entero sin decimales
        BigDecimal pvpFinal = MercadoLibreService.redondearPrecioMl(r.pvp());
        assertThat(pvpFinal).isNotNull();
        assertThat(pvpFinal.scale()).isEqualTo(0);
        assertThat(pvpFinal).isEqualByComparingTo("11000");
    }

    /**
     * Cuando pvp >= umbral, la envioConIvaFn usa el valor de API ML (más alto).
     * El bucle converge en un PVP mayor.
     */
    @Test
    void bucle_con_pvp_sobre_umbral_usa_envio_api() {
        BigDecimal umbral = new BigDecimal("5000");

        var r = EnvioEstabilizador.estabilizar(
            costoEnvioSinIva -> new BigDecimal("8000").add(costoEnvioSinIva),
            pvp -> pvp.compareTo(umbral) >= 0
                    ? new BigDecimal("2420")
                    : new BigDecimal("1210"),
            DIV_IVA_21,
            10
        );

        assertThat(r.pvp()).isGreaterThan(BigDecimal.ZERO);
        // Con envío API (2420) → sinIva = 2000 → pvp = 10000 → estabiliza
        BigDecimal pvpFinal = MercadoLibreService.redondearPrecioMl(r.pvp());
        assertThat(pvpFinal).isEqualByComparingTo("10000");
    }

    // ----------------------------------------------------------------
    // 3. Redondeo del PVP final
    // ----------------------------------------------------------------

    @Test
    void redondear_pvp_medio_sube() {
        assertThat(MercadoLibreService.redondearPrecioMl(new BigDecimal("15340.50")))
                .isEqualByComparingTo("15341");
    }

    @Test
    void redondear_pvp_bajo_medio_baja() {
        assertThat(MercadoLibreService.redondearPrecioMl(new BigDecimal("15340.49")))
                .isEqualByComparingTo("15340");
    }

    @Test
    void redondear_pvp_nulo_devuelve_nulo() {
        assertThat(MercadoLibreService.redondearPrecioMl(null)).isNull();
    }
}
