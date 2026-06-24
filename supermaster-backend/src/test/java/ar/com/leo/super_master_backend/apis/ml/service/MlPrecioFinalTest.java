package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * Tests unitarios puros para la lógica del orquestador calcularPrecioFinalParaPublicar.
 *
 * <ol>
 *   <li>Que {@code estabilizarYRedondear} (método package-private que contiene el
 *       try/catch de traducción del orquestador) convierte {@link BadRequestException}
 *       y {@link NotFoundException} en {@link IllegalStateException}. Estos tests
 *       ejercen el bloque catch real del orquestador.</li>
 *   <li>Que el {@link EnvioEstabilizador} propaga las RuntimeException directamente
 *       (contrato del núcleo iterativo). Los tests 3-4 validan SOLO el comportamiento
 *       de {@code EnvioEstabilizador}, NO el catch del orquestador.</li>
 *   <li>Que el bucle estabilizador + redondeo produce el PVP esperado en el caso feliz.</li>
 *   <li>Que {@code redondearPrecioMl} redondea correctamente.</li>
 * </ol>
 */
class MlPrecioFinalTest {

    private static final BigDecimal DIV_IVA_21 = new BigDecimal("1.21");

    /**
     * Instancia mínima de MercadoLibreService para invocar métodos package-private puros.
     * CALLS_REAL_METHODS: los métodos que no acceden a campos mockeados se ejecutan realmente.
     * estabilizarYRedondear solo llama a EnvioEstabilizador.estabilizar (estático) y
     * redondearPrecioMl (estático), por lo que no necesita ningún campo de la instancia.
     */
    private final MercadoLibreService svc = mock(MercadoLibreService.class, CALLS_REAL_METHODS);

    // ----------------------------------------------------------------
    // 1. Traducción de excepción en el bloque catch del orquestador
    //    (ejercen el código real de estabilizarYRedondear)
    // ----------------------------------------------------------------

    /**
     * Un BadRequestException lanzado por pvpFn (p.ej. producto sin costo/márgenes)
     * DEBE convertirse en IllegalStateException en el bloque catch del orquestador.
     * Este test invoca estabilizarYRedondear directamente y verifica esa traducción.
     */
    @Test
    void badRequestException_del_motor_se_traduce_a_illegalStateException() {
        assertThatThrownBy(() ->
            svc.estabilizarYRedondear(
                costoEnvioSinIva -> { throw new BadRequestException("El producto no tiene costo"); },
                pvp -> BigDecimal.ZERO,
                DIV_IVA_21
            )
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("costo");
    }

    /**
     * Un NotFoundException lanzado por pvpFn (p.ej. canal/margen no encontrado)
     * DEBE convertirse en IllegalStateException en el bloque catch del orquestador.
     */
    @Test
    void notFoundException_del_motor_se_traduce_a_illegalStateException() {
        assertThatThrownBy(() ->
            svc.estabilizarYRedondear(
                costoEnvioSinIva -> { throw new NotFoundException("Canal no encontrado"); },
                pvp -> BigDecimal.ZERO,
                DIV_IVA_21
            )
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Canal");
    }

    /**
     * Un pvp <= 0 resultante del bucle provoca IllegalStateException (guard de validación
     * dentro de estabilizarYRedondear, tras la estabilización).
     */
    @Test
    void pvp_cero_resulta_en_illegalStateException() {
        assertThatThrownBy(() ->
            svc.estabilizarYRedondear(
                costoEnvioSinIva -> BigDecimal.ZERO,
                pvp -> BigDecimal.ZERO,
                DIV_IVA_21
            )
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("pvp");
    }

    // ----------------------------------------------------------------
    // 2. Contrato del núcleo iterativo (valida EnvioEstabilizador, NO el catch del orquestador)
    // ----------------------------------------------------------------

    /**
     * Verifica que EnvioEstabilizador propaga BadRequestException directamente (sin envolver).
     * Este test valida el comportamiento del NÚCLEO, no el catch del orquestador.
     */
    @Test
    void estabilizador_propaga_badRequestException_directamente() {
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
     * Verifica que EnvioEstabilizador propaga NotFoundException directamente (sin envolver).
     * Este test valida el comportamiento del NÚCLEO, no el catch del orquestador.
     */
    @Test
    void estabilizador_propaga_notFoundException_directamente() {
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
    // 3. Bucle estabilizador + redondeo (caso feliz)
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
    // 4. Redondeo del PVP final
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
