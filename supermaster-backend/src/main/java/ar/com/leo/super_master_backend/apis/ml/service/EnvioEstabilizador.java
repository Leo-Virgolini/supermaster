package ar.com.leo.super_master_backend.apis.ml.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Estabilización iterativa PVP ↔ costo de envío. Pura: sin red ni BD. */
final class EnvioEstabilizador {
    private EnvioEstabilizador() {}

    record ResultadoEstabilizacion(BigDecimal pvp, BigDecimal costoEnvioSinIva,
            BigDecimal costoEnvioConIva, int iteraciones) {}

    static ResultadoEstabilizacion estabilizar(
            Function<BigDecimal, BigDecimal> pvpFn,
            Function<BigDecimal, BigDecimal> envioConIvaFn,
            BigDecimal divisorIva, int maxIteraciones) {
        BigDecimal costoEnvioSinIva = BigDecimal.ZERO;
        BigDecimal costoEnvioConIva = BigDecimal.ZERO;
        BigDecimal pvp = BigDecimal.ZERO;
        int it = 0;
        List<BigDecimal> vistos = new ArrayList<>();
        while (it < maxIteraciones) {
            it++;
            pvp = pvpFn.apply(costoEnvioSinIva);
            BigDecimal nuevoConIva = envioConIvaFn.apply(pvp);
            if (nuevoConIva.compareTo(costoEnvioConIva) == 0) {
                break;
            }
            boolean oscila = vistos.stream().anyMatch(v -> v.compareTo(nuevoConIva) == 0);
            if (oscila) {
                BigDecimal ciclo = nuevoConIva.max(costoEnvioConIva);
                costoEnvioConIva = ciclo;
                costoEnvioSinIva = ciclo.compareTo(BigDecimal.ZERO) > 0
                        ? ciclo.divide(divisorIva, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                // Recomputar pvp coherente con el envío final elegido (max del ciclo).
                pvp = pvpFn.apply(costoEnvioSinIva);
                break;
            }
            vistos.add(nuevoConIva);
            costoEnvioConIva = nuevoConIva;
            costoEnvioSinIva = nuevoConIva.compareTo(BigDecimal.ZERO) > 0
                    ? nuevoConIva.divide(divisorIva, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }
        return new ResultadoEstabilizacion(pvp, costoEnvioSinIva, costoEnvioConIva, it);
    }
}
