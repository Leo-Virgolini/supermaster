package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.controller.MercadoLibreController;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDefDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoValorDTO;
import ar.com.leo.super_master_backend.apis.ml.service.MlCategoriaAtributoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test liviano del endpoint GET /api/ml/categorias/{categoryId}/atributos.
 * Llama directamente al método del controller con el service mockeado,
 * siguiendo el patrón del repo (sin @WebMvcTest).
 */
class MlCategoriaAtributosEndpointTest {

    private final MlCategoriaAtributoService service = mock(MlCategoriaAtributoService.class);
    private final MercadoLibreController controller = new MercadoLibreController(null, null, service);

    @Test
    void atributosCategoria_devuelve200ConListaDelServicio() {
        String categoryId = "MLA1055";
        MlAtributoDefDTO atributo = new MlAtributoDefDTO(
                "BICYCLE_TYPE", "Tipo de bicicleta", "string",
                List.of(new MlAtributoValorDTO("1", "MTB")),
                List.of(), null,
                true, false, false, "PRINCIPALES"
        );
        when(service.obtenerAtributos(categoryId)).thenReturn(List.of(atributo));

        ResponseEntity<List<MlAtributoDefDTO>> response = controller.atributosCategoria(categoryId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).id()).isEqualTo("BICYCLE_TYPE");
        verify(service).obtenerAtributos(categoryId);
    }

    @Test
    void atributosCategoria_listaVacia_devuelve200ArrayVacio() {
        String categoryId = "MLA9999";
        when(service.obtenerAtributos(categoryId)).thenReturn(List.of());

        ResponseEntity<List<MlAtributoDefDTO>> response = controller.atributosCategoria(categoryId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }
}
