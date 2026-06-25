package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoPromptDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoPrompt;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoPromptRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Lee y actualiza los prompts de SEO (uno por canal). Los prompts viven solo en BD. */
@Service
@RequiredArgsConstructor
public class SeoConfigService {

    private final SeoPromptRepository repository;

    /** Contenido del prompt del canal. Sin fallback al código: si falta la fila, error explícito. */
    @Transactional(readOnly = true)
    public String promptDe(SeoCanal canal) {
        return repository.findByCanal(canal)
                .map(SeoPrompt::getContenido)
                .orElseThrow(() -> new NotFoundException(
                        "No hay prompt de SEO configurado para el canal " + canal + " (revisar el seed de seo_prompt)"));
    }

    @Transactional(readOnly = true)
    public List<SeoPromptDTO> obtenerTodos() {
        return repository.findAll().stream()
                .map(p -> new SeoPromptDTO(p.getCanal(), p.getContenido(), p.getFechaModificacion()))
                .toList();
    }

    @Transactional
    public SeoPromptDTO actualizar(SeoCanal canal, String contenido) {
        SeoPrompt p = repository.findByCanal(canal)
                .orElseThrow(() -> new NotFoundException(
                        "No hay prompt de SEO para el canal " + canal + " (revisar el seed de seo_prompt)"));
        p.setContenido(contenido);
        p.setFechaModificacion(LocalDateTime.now());
        repository.save(p);
        return new SeoPromptDTO(p.getCanal(), p.getContenido(), p.getFechaModificacion());
    }
}
