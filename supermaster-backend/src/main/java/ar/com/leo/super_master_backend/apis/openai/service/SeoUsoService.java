package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.SeoUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoConfig;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoUso;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoUsoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Registra y lee el consumo acumulado de SEO (singleton id=1). Precios/modelo desde seo_config. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeoUsoService {

    private final SeoUsoRepository repository;
    private final SeoConfigService seoConfigService;

    public static BigDecimal calcularCosto(long tokensEntrada, long tokensSalida, BigDecimal precioIn1m, BigDecimal precioOut1m) {
        return OpenAiCostoUtil.calcular(tokensEntrada, tokensSalida, precioIn1m, precioOut1m);
    }

    @Transactional
    public void registrar(long tokensEntrada, long tokensSalida) {
        SeoConfig c = seoConfigService.cargar();
        BigDecimal costo = calcularCosto(tokensEntrada, tokensSalida, c.getPrecioInput1m(), c.getPrecioOutput1m());
        if (repository.registrar(tokensEntrada, tokensSalida, costo) == 0) {
            log.warn("seo_uso id=1 no existe; el uso no se registró");
        }
    }

    @Transactional(readOnly = true)
    public SeoUsoDTO obtener() {
        SeoConfig c = seoConfigService.cargar();
        SeoUso u = repository.findById(1L).orElseThrow(
                () -> new NotFoundException("Fila de uso de SEO (id=1) no encontrada"));
        return new SeoUsoDTO(u.getConsultas(), u.getTokensEntrada(), u.getTokensSalida(), u.getCostoUsd(),
                c.getModel(), c.getPrecioInput1m(), c.getPrecioOutput1m());
    }
}
