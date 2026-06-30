package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenUsoDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenConfig;
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
    private final ImagenIaConfigService configService;

    public static BigDecimal calcularCosto(long tokensEntrada, long tokensSalida, BigDecimal precioIn1m, BigDecimal precioOut1m) {
        return OpenAiCostoUtil.calcular(tokensEntrada, tokensSalida, precioIn1m, precioOut1m);
    }

    @Transactional
    public void registrar(long tokensEntrada, long tokensSalida) {
        ImagenConfig c = configService.cargar();
        BigDecimal costo = calcularCosto(tokensEntrada, tokensSalida, c.getPrecioInput1m(), c.getPrecioOutput1m());
        if (repository.registrar(tokensEntrada, tokensSalida, costo) == 0) {
            log.warn("imagen_uso id=1 no existe; el uso no se registró");
        }
    }

    @Transactional(readOnly = true)
    public ImagenUsoDTO obtener() {
        ImagenConfig c = configService.cargar();
        ImagenUso u = repository.findById(1L).orElseThrow(() -> new NotFoundException("Fila de uso de imagen (id=1) no encontrada"));
        return new ImagenUsoDTO(u.getConsultas(), u.getTokensEntrada(), u.getTokensSalida(), u.getCostoUsd(),
                c.getModel(), c.getPrecioInput1m(), c.getPrecioOutput1m());
    }

    @Transactional
    public void reset() {
        if (repository.reset() == 0) {
            log.warn("imagen_uso id=1 no existe; el reset no afectó filas");
        }
    }
}
