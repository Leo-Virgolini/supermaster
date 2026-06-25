package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiSeoParseTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void parsea_camposBasicos() {
        String content = "{\"seo_title\":\"Sartén\",\"seo_description\":\"Una sartén linda\",\"tags\":\"sarten,cocina\"}";
        SeoGeneradoDTO dto = OpenAiSeoParser.parseContenido(content, om);
        assertThat(dto.seoTitle()).isEqualTo("Sartén");
        assertThat(dto.seoDescription()).isEqualTo("Una sartén linda");
        assertThat(dto.seoTags()).isEqualTo("sarten,cocina");
    }

    @Test
    void trunca_cortaEnPalabraCompleta() {
        String title = "Sartén de acero inoxidable profesional para cocina gastronómica industrial reforzada";
        String content = "{\"seo_title\":\"" + title + "\",\"seo_description\":\"x\",\"tags\":\"a\"}";
        SeoGeneradoDTO dto = OpenAiSeoParser.parseContenido(content, om);
        assertThat(dto.seoTitle().length()).isLessThanOrEqualTo(70);
        // No parte la última palabra: en el título original el carácter siguiente al corte es un espacio.
        assertThat(title.charAt(dto.seoTitle().length())).isEqualTo(' ');
    }

    @Test
    void trunca_titleA70_yDescA320() {
        String title80 = "x".repeat(80);
        String desc400 = "y".repeat(400);
        String content = "{\"seo_title\":\"" + title80 + "\",\"seo_description\":\"" + desc400 + "\",\"tags\":\"a,b\"}";
        SeoGeneradoDTO dto = OpenAiSeoParser.parseContenido(content, om);
        assertThat(dto.seoTitle()).hasSize(70);
        assertThat(dto.seoDescription()).hasSize(320);
    }
}
