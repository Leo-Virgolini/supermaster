package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDefDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoValorDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlCategoriaAtributoServiceTest {

    @Test
    void parsear_excluyeAutogestionadosYReadOnlyYFixed_yAgrupa() throws Exception {
        String json = """
          [
            {"id":"BRAND","name":"Marca","value_type":"string","attribute_group_id":"MAIN"},
            {"id":"SALE_FORMAT","name":"Formato de venta","value_type":"list",
             "attribute_group_id":"OTHERS","values":[{"id":"1359391","name":"Unidad"}]},
            {"id":"BICYCLE_TYPE","name":"Tipo","value_type":"string","attribute_group_id":"MAIN",
             "tags":{"required":true}},
            {"id":"PACKAGE_WIDTH","name":"Ancho","value_type":"number_unit",
             "tags":{"read_only":true}},
            {"id":"HEADPHONE_FORMAT","name":"Formato","value_type":"list","tags":{"fixed":true}}
          ]""";
        JsonNode arr = new ObjectMapper().readTree(json);
        List<MlAtributoDefDTO> defs = MlCategoriaAtributoService.parsear(arr);
        // BRAND excluido (auto-gestionado), PACKAGE_WIDTH (read_only) y HEADPHONE_FORMAT (fixed) excluidos
        assertThat(defs).extracting(MlAtributoDefDTO::id)
                .containsExactlyInAnyOrder("SALE_FORMAT", "BICYCLE_TYPE");
        MlAtributoDefDTO bt = defs.stream().filter(d -> d.id().equals("BICYCLE_TYPE")).findFirst().orElseThrow();
        assertThat(bt.required()).isTrue();
        assertThat(bt.grupo()).isEqualTo("PRINCIPALES");
        MlAtributoDefDTO sf = defs.stream().filter(d -> d.id().equals("SALE_FORMAT")).findFirst().orElseThrow();
        assertThat(sf.grupo()).isEqualTo("SECUNDARIAS");
        assertThat(sf.values()).extracting(MlAtributoValorDTO::name).containsExactly("Unidad");
    }
}
