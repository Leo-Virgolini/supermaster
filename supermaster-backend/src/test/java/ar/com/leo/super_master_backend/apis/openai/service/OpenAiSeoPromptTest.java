package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoContexto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiSeoPromptTest {

    @Test
    void gastro_incluyeReglaRubroGastronomico() {
        String p = OpenAiSeoPrompts.systemPrompt(SeoCanal.GASTRO);
        assertThat(p).contains("gastronómico");
        assertThat(p).contains("seo_title: máximo 70 caracteres");
    }

    @Test
    void hogar_noIncluyeReglaRubroGastronomico() {
        String p = OpenAiSeoPrompts.systemPrompt(SeoCanal.HOGAR);
        assertThat(p).doesNotContain("gastronómico");
        assertThat(p).contains("seo_title: máximo 70 caracteres");
    }

    @Test
    void userMessage_incluyeDatosDelProducto() {
        SeoContexto c = new SeoContexto("Sartén 24cm", "SARTEN 24", "Tramontina", "Aluminio",
                List.of("Horno", "Freezer"), List.of("Diámetro: 24 cm"));
        String u = OpenAiSeoPrompts.userMessage(c);
        assertThat(u).contains("Sartén 24cm").contains("Tramontina").contains("Aluminio")
                .contains("Diámetro: 24 cm").contains("Horno").contains("Freezer");
        // El "nombre interno" (título Dux) ya no se manda.
        assertThat(u).doesNotContain("SARTEN 24").doesNotContain("Nombre interno");
    }

    @Test
    void userMessage_limpiaCodigosEntreParentesisDelTitulo() {
        SeoContexto c = new SeoContexto("Hermético Flor 2,7 L (712B)", null, null, null, null, null);
        String u = OpenAiSeoPrompts.userMessage(c);
        assertThat(u).contains("Hermético Flor 2,7 L").doesNotContain("712B");
    }
}
