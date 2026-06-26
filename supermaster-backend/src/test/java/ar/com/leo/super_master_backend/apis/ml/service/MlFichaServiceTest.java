package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDefDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlComponenteDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlFichaDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlSeccionDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MlFichaServiceTest {

    @Test
    void parsearFicha_agrupaEnSeccionesYFiltra() throws Exception {
        String json = """
          { "groups": [
            { "id":"MAIN", "label":"Características principales", "components": [
              { "component":"TEXT_INPUT", "label":"Marca",
                "ui_config":{"allow_custom_value":true,"allow_filtering":true,"hint":"Escribe la marca"},
                "attributes":[{"id":"BRAND","name":"Marca","value_type":"string","value_max_length":255,
                               "tags":["catalog_required","required"]}] },
              { "component":"COLOR_INPUT", "label":"Color", "ui_config":{},
                "attributes":[
                  {"id":"COLOR","name":"Color","value_type":"string","tags":["defines_picture","allow_variations"]},
                  {"id":"MAIN_COLOR","name":"Color","value_type":"list","tags":["vip_hidden","variation_attribute"],
                   "values":[{"id":"1","name":"Marrón","metadata":{"rgb":"A0522D"}}]}
                ] },
              { "component":"NUMBER_UNIT_INPUT", "label":"Diámetro", "ui_config":{},
                "attributes":[{"id":"DIAMETER","name":"Diámetro","value_type":"number_unit",
                               "allowed_units":[{"id":"cm","name":"cm"}],"default_unit":"cm","tags":[]}] }
            ] },
            { "id":"PRICING", "label":"Precios", "components":[
              { "component":"COMBO","label":"IVA","ui_config":{},
                "attributes":[{"id":"VALUE_ADDED_TAX","name":"IVA","value_type":"list","tags":["conditional_required"]}] } ] },
            { "id":"OTHER", "label":"Otros", "components":[
              { "component":"COMBO","label":"Forma","ui_config":{},
                "attributes":[{"id":"SHAPE","name":"Forma","value_type":"string","tags":[]}] },
              { "component":"TEXT_INPUT","label":"AGID","ui_config":{},
                "attributes":[{"id":"AGID","name":"AGID","value_type":"string","tags":["hidden"]}] } ] }
          ] }""";
        JsonNode input = new ObjectMapper().readTree(json);
        MlFichaDTO ficha = MlFichaService.parsearFicha(input);

        assertThat(ficha.secciones()).extracting(MlSeccionDTO::id)
                .containsExactly("VARIANTE", "PRINCIPALES", "SECUNDARIAS");

        MlSeccionDTO variante = ficha.secciones().get(0);
        assertThat(variante.componentes()).extracting(MlComponenteDTO::tipo).containsExactly("COLOR_INPUT");
        MlComponenteDTO color = variante.componentes().get(0);
        MlAtributoDefDTO mainColor = color.atributos().stream()
                .filter(a -> a.id().equals("MAIN_COLOR")).findFirst().orElseThrow();
        assertThat(mainColor.values().get(0).rgb()).isEqualTo("A0522D");

        MlSeccionDTO principales = ficha.secciones().get(1);
        assertThat(principales.componentes()).extracting(MlComponenteDTO::label)
                .containsExactly("Marca", "Diámetro");          // BRAND incluido; Color salió a VARIANTE
        MlComponenteDTO marca = principales.componentes().get(0);
        assertThat(marca.hint()).isEqualTo("Escribe la marca");
        assertThat(marca.allowCustomValue()).isTrue();
        assertThat(marca.atributos().get(0).required()).isTrue();
        assertThat(marca.atributos().get(0).valueMaxLength()).isEqualTo(255);

        MlSeccionDTO secundarias = ficha.secciones().get(2);
        // SHAPE visible; AGID (hidden) excluido → componente AGID descartado; IVA (PRICING) descartado
        assertThat(secundarias.componentes()).extracting(MlComponenteDTO::label).containsExactly("Forma");
    }
}
