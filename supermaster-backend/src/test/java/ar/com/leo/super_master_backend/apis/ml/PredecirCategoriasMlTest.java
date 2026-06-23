package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.dto.PrediccionCategoriaMlDTO;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PredecirCategoriasMlTest {

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode tree(String json) {
        return om.readTree(json);
    }

    @Test
    void parsePredicciones_mapeaIdYNombre() {
        JsonNode arr = tree("[{\"category_id\":\"MLA1055\",\"category_name\":\"Celulares y Smartphones\"},"
                + "{\"category_id\":\"MLA1000\",\"category_name\":\"Electrónica\"}]");
        List<PrediccionCategoriaMlDTO> preds = MercadoLibreService.parsePredicciones(arr);
        assertThat(preds).hasSize(2);
        assertThat(preds.get(0).categoryId()).isEqualTo("MLA1055");
        assertThat(preds.get(0).categoryName()).isEqualTo("Celulares y Smartphones");
        assertThat(preds.get(1).categoryId()).isEqualTo("MLA1000");
    }

    @Test
    void parsePredicciones_arrayVacio_listaVacia() {
        assertThat(MercadoLibreService.parsePredicciones(tree("[]"))).isEmpty();
    }

    @Test
    void parsePredicciones_salteaSinId() {
        JsonNode arr = tree("[{\"category_name\":\"Sin id\"},{\"category_id\":\"MLA1\",\"category_name\":\"Ok\"}]");
        List<PrediccionCategoriaMlDTO> preds = MercadoLibreService.parsePredicciones(arr);
        assertThat(preds).hasSize(1);
        assertThat(preds.get(0).categoryId()).isEqualTo("MLA1");
    }

    @Test
    void parsePathFromRoot_armaJerarquiaCompleta() {
        JsonNode cat = tree("{\"path_from_root\":[{\"id\":\"MLA5\",\"name\":\"Cocina\"},"
                + "{\"id\":\"MLA50\",\"name\":\"Baterías\"},{\"id\":\"MLA1234\",\"name\":\"Ollas\"}]}");
        assertThat(MercadoLibreService.parsePathFromRoot(cat)).isEqualTo("Cocina > Baterías > Ollas");
    }

    @Test
    void parsePathFromRoot_sinPath_null() {
        assertThat(MercadoLibreService.parsePathFromRoot(tree("{}"))).isNull();
    }

    @Test
    void resolverCategoriaMl_usaLaGuardadaSiExiste() {
        String cat = MercadoLibreService.resolverCategoriaMl("MLA999", "un título", t -> "AUTO");
        assertThat(cat).isEqualTo("MLA999");
    }

    @Test
    void resolverCategoriaMl_caeAlPredictorSiNoHayGuardada() {
        assertThat(MercadoLibreService.resolverCategoriaMl(null, "un título", t -> "AUTO")).isEqualTo("AUTO");
        assertThat(MercadoLibreService.resolverCategoriaMl("", "un título", t -> "AUTO")).isEqualTo("AUTO");
        assertThat(MercadoLibreService.resolverCategoriaMl("   ", "un título", t -> "AUTO")).isEqualTo("AUTO");
    }
}
