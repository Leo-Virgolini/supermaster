package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenConfig;
import ar.com.leo.super_master_backend.apis.openai.repository.ImagenConfigRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Lee y actualiza la configuración de la carátula (prompt + parámetros). Singleton id=1. */
@Service
@RequiredArgsConstructor
public class ImagenIaConfigService {

    private final ImagenConfigRepository repository;

    @Transactional(readOnly = true)
    public ImagenConfig cargar() {
        return repository.findById(1L).orElseThrow(() -> new NotFoundException(
                "No hay configuración de carátula (revisar el seed de imagen_config)"));
    }

    @Transactional(readOnly = true)
    public String prompt() {
        return cargar().getContenido();
    }

    @Transactional(readOnly = true)
    public ImagenConfigDTO obtener() {
        return toDto(cargar());
    }

    @Transactional
    public ImagenConfigDTO actualizar(ImagenConfigUpdateDTO dto) {
        ImagenConfig c = cargar();
        c.setContenido(dto.contenido());
        c.setModel(dto.model());
        c.setSize(dto.size());
        c.setOutputFormat(dto.outputFormat());
        c.setQuality(dto.quality());
        c.setPrecioInput1m(dto.precioInput1m());
        c.setPrecioOutput1m(dto.precioOutput1m());
        c.setFechaModificacion(LocalDateTime.now());
        repository.save(c);
        return toDto(c);
    }

    private ImagenConfigDTO toDto(ImagenConfig c) {
        return new ImagenConfigDTO(c.getContenido(), c.getModel(), c.getSize(), c.getOutputFormat(),
                c.getQuality(), c.getPrecioInput1m(), c.getPrecioOutput1m(), c.getFechaModificacion());
    }
}
