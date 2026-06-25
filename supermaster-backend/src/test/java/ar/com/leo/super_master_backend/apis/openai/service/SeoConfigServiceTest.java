package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoPrompt;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoPromptRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeoConfigServiceTest {

    private final SeoPromptRepository repo = mock(SeoPromptRepository.class);
    private final SeoConfigService service = new SeoConfigService(repo);

    @Test
    void promptDe_devuelveContenidoDeBD() {
        SeoPrompt p = new SeoPrompt();
        p.setCanal(SeoCanal.HOGAR);
        p.setContenido("PROMPT HOGAR");
        when(repo.findByCanal(SeoCanal.HOGAR)).thenReturn(Optional.of(p));

        assertThat(service.promptDe(SeoCanal.HOGAR)).isEqualTo("PROMPT HOGAR");
    }

    @Test
    void promptDe_sinFila_lanzaExcepcion() {
        when(repo.findByCanal(SeoCanal.GASTRO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.promptDe(SeoCanal.GASTRO))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("GASTRO");
    }

    @Test
    void actualizar_persisteContenidoYFecha() {
        SeoPrompt p = new SeoPrompt();
        p.setCanal(SeoCanal.HOGAR);
        p.setContenido("viejo");
        when(repo.findByCanal(SeoCanal.HOGAR)).thenReturn(Optional.of(p));
        when(repo.save(any(SeoPrompt.class))).thenAnswer(i -> i.getArgument(0));

        service.actualizar(SeoCanal.HOGAR, "nuevo contenido");

        assertThat(p.getContenido()).isEqualTo("nuevo contenido");
        assertThat(p.getFechaModificacion()).isNotNull();
        verify(repo).save(p);
    }
}
