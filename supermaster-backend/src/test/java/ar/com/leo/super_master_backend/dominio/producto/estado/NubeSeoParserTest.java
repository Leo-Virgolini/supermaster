package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.SeoCanalDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class NubeSeoParserTest {

    private static final ObjectMapper M = new ObjectMapper();
    private JsonNode json(String s) { return M.readTree(s); }

    @Test
    void productNull() {
        assertThat(NubeSeoParser.parse(null)).isNull();
    }

    @Test
    void i18nObjetoYTagsArray() {
        SeoCanalDTO seo = NubeSeoParser.parse(json("""
            {"seo_title":{"es":"Titulo"},"seo_description":{"es":"Desc"},"tags":["a","b","c"]}"""));
        assertThat(seo.title()).isEqualTo("Titulo");
        assertThat(seo.description()).isEqualTo("Desc");
        assertThat(seo.tags()).isEqualTo("a, b, c");
    }

    @Test
    void textualYTagsString() {
        SeoCanalDTO seo = NubeSeoParser.parse(json("""
            {"seo_title":"T","seo_description":"D","tags":"x, y"}"""));
        assertThat(seo.title()).isEqualTo("T");
        assertThat(seo.description()).isEqualTo("D");
        assertThat(seo.tags()).isEqualTo("x, y");
    }

    @Test
    void ausentesQuedanNull() {
        SeoCanalDTO seo = NubeSeoParser.parse(json("{}"));
        assertThat(seo).isNotNull();
        assertThat(seo.title()).isNull();
        assertThat(seo.description()).isNull();
        assertThat(seo.tags()).isNull();
    }
}
