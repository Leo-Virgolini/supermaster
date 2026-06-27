package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenPromptDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenPrompt;
import ar.com.leo.super_master_backend.apis.openai.repository.ImagenPromptRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ImagenIaConfigService {

    private final ImagenPromptRepository repository;

    @Transactional(readOnly = true)
    public String prompt() {
        return repository.findById(1L).map(ImagenPrompt::getContenido)
                .orElseThrow(() -> new NotFoundException("No hay prompt de carátula configurado (revisar seed de imagen_prompt)"));
    }

    @Transactional(readOnly = true)
    public ImagenPromptDTO obtener() {
        return repository.findById(1L).map(p -> new ImagenPromptDTO(p.getContenido(), p.getFechaModificacion()))
                .orElseThrow(() -> new NotFoundException("No hay prompt de carátula configurado"));
    }

    @Transactional
    public ImagenPromptDTO actualizar(String contenido) {
        ImagenPrompt p = repository.findById(1L)
                .orElseThrow(() -> new NotFoundException("No hay prompt de carátula configurado"));
        p.setContenido(contenido);
        p.setFechaModificacion(LocalDateTime.now());
        repository.save(p);
        return new ImagenPromptDTO(p.getContenido(), p.getFechaModificacion());
    }
}
