package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoConfig;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoConfigRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Lee y actualiza la configuración de SEO (prompts por canal + parámetros). Singleton id=1. */
@Service
@RequiredArgsConstructor
public class SeoConfigService {

    private final SeoConfigRepository repository;

    @Transactional(readOnly = true)
    public SeoConfig cargar() {
        return repository.findById(1L).orElseThrow(() -> new NotFoundException(
                "No hay configuración de SEO (revisar el seed de seo_config)"));
    }

    /** Prompt del canal (HOGAR/GASTRO). */
    @Transactional(readOnly = true)
    public String promptDe(SeoCanal canal) {
        SeoConfig c = cargar();
        return canal == SeoCanal.GASTRO ? c.getPromptGastro() : c.getPromptHogar();
    }

    @Transactional(readOnly = true)
    public SeoConfigDTO obtener() {
        return toDto(cargar());
    }

    @Transactional
    public SeoConfigDTO actualizar(SeoConfigUpdateDTO dto) {
        SeoConfig c = cargar();
        c.setPromptHogar(dto.promptHogar());
        c.setPromptGastro(dto.promptGastro());
        c.setModel(dto.model());
        c.setPrecioInput1m(dto.precioInput1m());
        c.setPrecioOutput1m(dto.precioOutput1m());
        c.setFechaModificacion(LocalDateTime.now());
        repository.save(c);
        return toDto(c);
    }

    private SeoConfigDTO toDto(SeoConfig c) {
        return new SeoConfigDTO(c.getPromptHogar(), c.getPromptGastro(), c.getModel(),
                c.getPrecioInput1m(), c.getPrecioOutput1m(), c.getFechaModificacion());
    }
}
