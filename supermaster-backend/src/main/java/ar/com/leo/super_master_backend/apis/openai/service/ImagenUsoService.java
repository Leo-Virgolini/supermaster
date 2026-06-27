package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.config.OpenAiImageProperties;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenUso;
import ar.com.leo.super_master_backend.apis.openai.repository.ImagenUsoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImagenUsoService {

    private final ImagenUsoRepository repository;
    private final OpenAiImageProperties properties;

    public static BigDecimal calcularCosto(long tokensEntrada, long tokensSalida, BigDecimal precioIn1m, BigDecimal precioOut1m) {
        return OpenAiCostoUtil.calcular(tokensEntrada, tokensSalida, precioIn1m, precioOut1m);
    }

    @Transactional
    public void registrar(long tokensEntrada, long tokensSalida) {
        BigDecimal costo = calcularCosto(tokensEntrada, tokensSalida, properties.precioInput1m(), properties.precioOutput1m());
        if (repository.registrar(tokensEntrada, tokensSalida, costo) == 0) {
            log.warn("imagen_uso id=1 no existe; el uso no se registró");
        }
    }

    @Transactional(readOnly = true)
    public ImagenUsoDTO obtener() {
        ImagenUso u = repository.findById(1L).orElseThrow(() -> new NotFoundException("Fila de uso de imagen (id=1) no encontrada"));
        return new ImagenUsoDTO(u.getConsultas(), u.getTokensEntrada(), u.getTokensSalida(), u.getCostoUsd(),
                properties.model(), properties.precioInput1m(), properties.precioOutput1m());
    }
}
