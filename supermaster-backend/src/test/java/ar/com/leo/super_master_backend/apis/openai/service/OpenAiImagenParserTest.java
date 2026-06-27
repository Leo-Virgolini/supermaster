package ar.com.leo.super_master_backend.apis.openai.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiImagenParserTest {

    private static JsonNode json(String s) {
        try {
            return new ObjectMapper().readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void extraeB64YTokens() {
        var root = json("""
            {"data":[{"b64_json":"QUJD"}],"usage":{"input_tokens":120,"output_tokens":4000}}""");
        assertThat(OpenAiImagenParser.b64(root)).isEqualTo("QUJD");
        assertThat(OpenAiImagenParser.tokensEntrada(root)).isEqualTo(120);
        assertThat(OpenAiImagenParser.tokensSalida(root)).isEqualTo(4000);
    }

    @Test
    void sinB64_devuelveNull() {
        assertThat(OpenAiImagenParser.b64(json("{\"data\":[]}"))).isNull();
    }
}
