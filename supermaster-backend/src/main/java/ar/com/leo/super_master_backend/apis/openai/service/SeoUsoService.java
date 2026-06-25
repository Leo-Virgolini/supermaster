package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.config.OpenAiProperties;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoUso;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoUsoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Registra y lee el consumo acumulado de OpenAI (singleton id=1). */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeoUsoService {

    private static final BigDecimal MILLON = new BigDecimal("1000000");

    private final SeoUsoRepository repository;
    private final OpenAiProperties properties;

    /** Costo USD = in/1e6·precioIn + out/1e6·precioOut, redondeado a 6 decimales. Puro/testeable. */
    public static BigDecimal calcularCosto(long tokensEntrada, long tokensSalida, BigDecimal precioIn1m, BigDecimal precioOut1m) {
        BigDecimal costoIn = BigDecimal.valueOf(tokensEntrada).multiply(precioIn1m).divide(MILLON, 6, RoundingMode.HALF_UP);
        BigDecimal costoOut = BigDecimal.valueOf(tokensSalida).multiply(precioOut1m).divide(MILLON, 6, RoundingMode.HALF_UP);
        return costoIn.add(costoOut);
    }

    @Transactional
    public void registrar(long tokensEntrada, long tokensSalida) {
        BigDecimal costo = calcularCosto(tokensEntrada, tokensSalida, properties.precioInput1m(), properties.precioOutput1m());
        int filas = repository.registrar(tokensEntrada, tokensSalida, costo);
        if (filas == 0) {
            log.warn("seo_uso id=1 no existe; el uso no se registró");
        }
    }

    @Transactional(readOnly = true)
    public SeoUsoDTO obtener() {
        SeoUso u = repository.findById(1L).orElseThrow(
                () -> new NotFoundException("Fila de uso de SEO (id=1) no encontrada"));
        return new SeoUsoDTO(
                u.getConsultas(), u.getTokensEntrada(), u.getTokensSalida(), u.getCostoUsd(),
                properties.model(), properties.precioInput1m(), properties.precioOutput1m());
    }
}
