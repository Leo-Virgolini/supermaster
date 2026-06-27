package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoConfig;
import ar.com.leo.super_master_backend.apis.openai.repository.SeoConfigRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeoConfigServiceTest {

    @Mock SeoConfigRepository repository;
    @InjectMocks SeoConfigService service;

    private SeoConfig fila() {
        SeoConfig c = new SeoConfig();
        c.setId(1L);
        c.setPromptHogar("prompt hogar");
        c.setPromptGastro("prompt gastro");
        c.setModel("gpt-5-mini");
        c.setPrecioInput1m(new BigDecimal("0.25"));
        c.setPrecioOutput1m(new BigDecimal("2.00"));
        return c;
    }

    @Test
    void promptDe_devuelveColumnaSegunCanal() {
        when(repository.findById(1L)).thenReturn(Optional.of(fila()));
        assertThat(service.promptDe(SeoCanal.HOGAR)).isEqualTo("prompt hogar");
        assertThat(service.promptDe(SeoCanal.GASTRO)).isEqualTo("prompt gastro");
    }

    @Test
    void obtener_mapeaTodosLosCampos() {
        when(repository.findById(1L)).thenReturn(Optional.of(fila()));
        SeoConfigDTO dto = service.obtener();
        assertThat(dto.promptHogar()).isEqualTo("prompt hogar");
        assertThat(dto.model()).isEqualTo("gpt-5-mini");
        assertThat(dto.precioInput1m()).isEqualByComparingTo("0.25");
    }

    @Test
    void actualizar_persisteYDevuelve() {
        SeoConfig c = fila();
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        when(repository.save(c)).thenReturn(c);
        SeoConfigDTO dto = service.actualizar(new SeoConfigUpdateDTO(
                "h2", "g2", "gpt-5", new BigDecimal("1.0"), new BigDecimal("3.0")));
        assertThat(dto.promptHogar()).isEqualTo("h2");
        assertThat(dto.model()).isEqualTo("gpt-5");
        assertThat(c.getFechaModificacion()).isNotNull();
    }

    @Test
    void cargar_sinFila_lanzaNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.obtener()).isInstanceOf(NotFoundException.class);
    }
}
