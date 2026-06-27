package ar.com.leo.super_master_backend.apis.openai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Utilidad compartida para calcular el costo en USD de una llamada a OpenAI. */
public final class OpenAiCostoUtil {

    private static final BigDecimal MILLON = new BigDecimal("1000000");

    private OpenAiCostoUtil() {}

    /** Costo USD = in/1e6·precioIn + out/1e6·precioOut, redondeado a 6 decimales. */
    public static BigDecimal calcular(long tokensEntrada, long tokensSalida,
                                      BigDecimal precioInput1m, BigDecimal precioOutput1m) {
        BigDecimal costoIn = BigDecimal.valueOf(tokensEntrada).multiply(precioInput1m).divide(MILLON, 6, RoundingMode.HALF_UP);
        BigDecimal costoOut = BigDecimal.valueOf(tokensSalida).multiply(precioOutput1m).divide(MILLON, 6, RoundingMode.HALF_UP);
        return costoIn.add(costoOut);
    }
}
