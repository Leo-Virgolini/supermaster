package ar.com.leo.super_master_backend.dominio.imagen.service;

import ar.com.leo.super_master_backend.apis.openai.entity.ImagenConfig;
import ar.com.leo.super_master_backend.apis.openai.service.ImagenIaConfigService;
import ar.com.leo.super_master_backend.apis.openai.service.OpenAiImagenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaratulaServiceTest {

    @Mock ImagenService imagenService;
    @Mock OpenAiImagenService openAiImagenService;
    @Mock ImagenIaConfigService configService;
    @InjectMocks CaratulaService service;

    @Test
    void generar_conCrudaElegida_usaEsaCruda() {
        when(imagenService.resolverCrudasPorSku("ABC")).thenReturn(List.of("ABC.jpg", "ABC_1.jpg"));
        when(imagenService.leerCrudaBytes("ABC_1.jpg")).thenReturn(new byte[]{1});
        when(openAiImagenService.generarCaratula(new byte[]{1}, "ABC_1.jpg")).thenReturn(new byte[]{2});

        GeneracionCaratula g = service.generar("ABC", "ABC_1.jpg");

        assertThat(g.crudaNombre()).isEqualTo("ABC_1.jpg");
        assertThat(g.generada()).containsExactly(2);
    }

    @Test
    void generar_crudaNoPerteneceAlSku_lanza() {
        when(imagenService.resolverCrudasPorSku("ABC")).thenReturn(List.of("ABC.jpg"));
        assertThatThrownBy(() -> service.generar("ABC", "OTRO.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guardar_borraCrudasTrasGuardarOk() {
        ImagenConfig cfg = new ImagenConfig();
        cfg.setOutputFormat("jpeg");
        when(configService.cargar()).thenReturn(cfg);

        service.guardar("ABC", new byte[]{9});

        verify(imagenService).guardarCaratula("ABC", new byte[]{9}, "jpg");
        verify(imagenService).eliminarCrudasPorSku("ABC");
    }
}
