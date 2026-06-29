package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlDatosParserTest {

    private static final ObjectMapper M = new ObjectMapper();

    private JsonNode parse(String json) {
        return M.readTree(json);
    }

    @Test
    void categoryIdYAtributosFichaTecnica() {
        JsonNode item = parse("""
            {
              "category_id": "MLA1234",
              "attributes": [
                {"id":"ITEM_CONDITION","value_id":"2230284","value_name":"Nuevo"},
                {"id":"SELLER_SKU","value_name":"ABC"},
                {"id":"BRAND","value_id":"111","value_name":"Acme"},
                {"id":"COLOR","value_id":"-1","value_name":null}
              ]
            }
            """);
        assertThat(MlDatosParser.categoryId(item)).isEqualTo("MLA1234");
        List<MlAtributoDTO> attrs = MlDatosParser.atributos(item);
        assertThat(attrs).extracting(MlAtributoDTO::attributeId)
                .containsExactly("BRAND", "COLOR"); // ITEM_CONDITION y SELLER_SKU se omiten
        assertThat(attrs.get(0).valueId()).isEqualTo("111");
        assertThat(attrs.get(0).valueName()).isEqualTo("Acme");
        assertThat(attrs.get(0).noAplica()).isFalse();
        assertThat(attrs.get(1).noAplica()).isTrue();
        assertThat(attrs.get(1).valueName()).isNull();
    }

    @Test
    void itemSinAtributos() {
        JsonNode item = parse("{\"category_id\":\"MLA1\"}");
        assertThat(MlDatosParser.atributos(item)).isEmpty();
    }
}
