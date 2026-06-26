package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.controller.MercadoLibreController;
import ar.com.leo.super_master_backend.apis.ml.dto.MlFichaDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlSeccionDTO;
import ar.com.leo.super_master_backend.apis.ml.service.MlFichaService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test liviano del endpoint GET /api/ml/categorias/{categoryId}/ficha.
 * Llama directamente al método del controller con el service mockeado.
 */
class MlCategoriaFichaEndpointTest {

    private final MlFichaService service = mock(MlFichaService.class);
    private final MercadoLibreController controller = new MercadoLibreController(null, null, null, service);

    @Test
    void fichaCategoria_devuelve200ConFichaDelServicio() {
        String categoryId = "MLA413476";
        MlFichaDTO ficha = new MlFichaDTO(List.of(
                new MlSeccionDTO("PRINCIPALES", "Características principales", List.of())));
        when(service.obtenerFicha(categoryId)).thenReturn(ficha);

        ResponseEntity<MlFichaDTO> response = controller.fichaCategoria(categoryId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().secciones()).hasSize(1);
        assertThat(response.getBody().secciones().get(0).id()).isEqualTo("PRINCIPALES");
        verify(service).obtenerFicha(categoryId);
    }
}
