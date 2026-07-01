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
    void parsear_excluyeAutogestionadosYReadOnlyYFixed_yAgrupaPorRelevance() throws Exception {
        // ML ya no devuelve attribute_group_id="MAIN": todos vienen como "OTHERS".
        // El grupo se deriva de relevance (1 = principal), no del grupo.
        String json = """
          [
            {"id":"BRAND","name":"Marca","value_type":"string","relevance":1,
             "attribute_group_id":"OTHERS","attribute_group_name":"Otros","tags":{"required":true}},
            {"id":"SALE_FORMAT","name":"Formato de venta","value_type":"list","relevance":3,
             "attribute_group_id":"OTHERS","values":[{"id":"1359391","name":"Unidad"}]},
            {"id":"MODEL","name":"Modelo","value_type":"string","relevance":1,
             "attribute_group_id":"OTHERS","value_max_length":255,"example":"A01 Dual SIM",
             "tags":{"required":true}},
            {"id":"PACKAGE_WIDTH","name":"Ancho","value_type":"number_unit",
             "tags":{"read_only":true}},
            {"id":"HEADPHONE_FORMAT","name":"Formato","value_type":"list","tags":{"fixed":true}}
          ]""";
        JsonNode arr = new ObjectMapper().readTree(json);
        List<MlAtributoDefDTO> defs = MlCategoriaAtributoService.parsear(arr);
        // BRAND excluido (auto-gestionado), PACKAGE_WIDTH (read_only) y HEADPHONE_FORMAT (fixed) excluidos
        assertThat(defs).extracting(MlAtributoDefDTO::id)
                .containsExactlyInAnyOrder("MODEL", "SALE_FORMAT");

        MlAtributoDefDTO model = defs.stream().filter(d -> d.id().equals("MODEL")).findFirst().orElseThrow();
        assertThat(model.required()).isTrue();
        assertThat(model.relevance()).isEqualTo(1);
        assertThat(model.grupo()).isEqualTo("PRINCIPALES");
        assertThat(model.valueMaxLength()).isEqualTo(255);
        assertThat(model.example()).isEqualTo("A01 Dual SIM");

        MlAtributoDefDTO sf = defs.stream().filter(d -> d.id().equals("SALE_FORMAT")).findFirst().orElseThrow();
        assertThat(sf.grupo()).isEqualTo("SECUNDARIAS");
        assertThat(sf.relevance()).isEqualTo(3);
        assertThat(sf.valueMaxLength()).isNull();
        assertThat(sf.values()).extracting(MlAtributoValorDTO::name).containsExactly("Unidad");
    }

    @Test
    void parsear_marcaEjesDeVariacion() throws Exception {
        String json = """
          [
            {"id":"COLOR","name":"Color","value_type":"list",
             "tags":{"allow_variations":true,"hidden":true},
             "values":[{"id":"52049","name":"Negro"}]},
            {"id":"MODEL","name":"Modelo","value_type":"string","tags":{}}
          ]""";
        JsonNode arr = new ObjectMapper().readTree(json);
        List<MlAtributoDefDTO> defs = MlCategoriaAtributoService.parsear(arr);

        MlAtributoDefDTO color = defs.stream().filter(d -> d.id().equals("COLOR")).findFirst().orElseThrow();
        MlAtributoDefDTO model = defs.stream().filter(d -> d.id().equals("MODEL")).findFirst().orElseThrow();
        assertThat(color.allowVariations()).isTrue();
        assertThat(color.variationAttribute()).isFalse();
        assertThat(model.allowVariations()).isFalse();
    }

    @Test
    void parsear_ordenaPorRelevance_yUsaTooltipComoHintFallback() throws Exception {
        String json = """
          [
            {"id":"C","name":"C","value_type":"string","relevance":3},
            {"id":"A","name":"A","value_type":"string","relevance":1,"hint":"ayuda directa"},
            {"id":"B","name":"B","value_type":"string","relevance":2,"tooltip":"viene de tooltip"}
          ]""";
        JsonNode arr = new ObjectMapper().readTree(json);
        List<MlAtributoDefDTO> defs = MlCategoriaAtributoService.parsear(arr);
        // Ordenados por relevance ascendente (1 = más importante primero)
        assertThat(defs).extracting(MlAtributoDefDTO::id).containsExactly("A", "B", "C");
        assertThat(defs.get(0).hint()).isEqualTo("ayuda directa");
        // Sin hint pero con tooltip → se usa el tooltip como hint
        assertThat(defs.get(1).hint()).isEqualTo("viene de tooltip");
        assertThat(defs.get(2).hint()).isNull();
    }
}
