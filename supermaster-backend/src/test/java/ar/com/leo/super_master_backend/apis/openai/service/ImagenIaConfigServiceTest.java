package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigDTO;
import ar.com.leo.super_master_backend.apis.openai.dto.ImagenConfigUpdateDTO;
import ar.com.leo.super_master_backend.apis.openai.entity.ImagenConfig;
import ar.com.leo.super_master_backend.apis.openai.repository.ImagenConfigRepository;
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
class ImagenIaConfigServiceTest {

    @Mock ImagenConfigRepository repository;
    @InjectMocks ImagenIaConfigService service;

    private ImagenConfig fila() {
        ImagenConfig c = new ImagenConfig();
        c.setId(1L);
        c.setContenido("prompt caratula");
        c.setModel("gpt-image-2");
        c.setSize("1024x1024");
        c.setOutputFormat("jpeg");
        c.setQuality("high");
        c.setPrecioInput1m(new BigDecimal("8.00"));
        c.setPrecioOutput1m(new BigDecimal("30.00"));
        return c;
    }

    @Test
    void prompt_devuelveContenido() {
        when(repository.findById(1L)).thenReturn(Optional.of(fila()));
        assertThat(service.prompt()).isEqualTo("prompt caratula");
    }

    @Test
    void obtener_mapea() {
        when(repository.findById(1L)).thenReturn(Optional.of(fila()));
        ImagenConfigDTO dto = service.obtener();
        assertThat(dto.size()).isEqualTo("1024x1024");
        assertThat(dto.outputFormat()).isEqualTo("jpeg");
        assertThat(dto.precioInput1m()).isEqualByComparingTo("8.00");
    }

    @Test
    void actualizar_persiste() {
        ImagenConfig c = fila();
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        when(repository.save(c)).thenReturn(c);
        ImagenConfigDTO dto = service.actualizar(new ImagenConfigUpdateDTO(
                "p2", "gpt-image-2", "auto", "png", "medium",
                new BigDecimal("5.0"), new BigDecimal("20.0")));
        assertThat(dto.outputFormat()).isEqualTo("png");
        assertThat(c.getFechaModificacion()).isNotNull();
    }

    @Test
    void cargar_sinFila_lanzaNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.obtener()).isInstanceOf(NotFoundException.class);
    }
}
