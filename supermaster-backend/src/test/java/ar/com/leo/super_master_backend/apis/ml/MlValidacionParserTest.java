package ar.com.leo.super_master_backend.apis.ml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MlValidacionParserTest {

    @Test
    void resumirError_extraeMensajeDeCausaError_ignorandoElJsonCrudo() {
        String raw = "400 Bad Request: \"{\"cause\":[{\"department\":\"supply\",\"cause_id\":7711,"
                + "\"type\":\"error\",\"code\":\"item.attribute.product_identifier.invalid_format\","
                + "\"message\":\"Product Identifier [GTIN] contains values with invalid format: [1178911569]\"}],"
                + "\"message\":\"Validation error\",\"status\":400}\"";
        assertThat(MlValidacionParser.resumirError(raw))
                .isEqualTo("Product Identifier [GTIN] contains values with invalid format: [1178911569]");
    }

    @Test
    void resumirError_ignoraWarnings_yJuntaSoloErrores() {
        String raw = "{\"cause\":["
                + "{\"type\":\"warning\",\"message\":\"ME2 adoption is mandatory\"},"
                + "{\"type\":\"error\",\"message\":\"Seller is not authorized for this brand\"}"
                + "],\"message\":\"Validation error\"}";
        assertThat(MlValidacionParser.resumirError(raw)).isEqualTo("Seller is not authorized for this brand");
    }

    @Test
    void resumirError_soloWarnings_devuelveNull() {
        String raw = "{\"cause\":[{\"type\":\"warning\",\"message\":\"price was ignored\"}],\"message\":\"OK\"}";
        assertThat(MlValidacionParser.resumirError(raw)).isNull();
    }

    @Test
    void resumirError_bodyInvalidFields_prefiereElTextoDescriptivoDeError() {
        // cause es un número (no array): el texto útil está en `error`, no en `message` (un código).
        String raw = "400 Bad Request: {\"cause\":374,\"message\":\"BODY_INVALID_FIELDS\",\"error\":\"The field family name is invalid\",\"status\":400}";
        assertThat(MlValidacionParser.resumirError(raw)).isEqualTo("The field family name is invalid");
    }

    @Test
    void resumirError_sinErrorDescriptivo_usaMessage() {
        String raw = "{\"message\":\"Item not found\",\"error\":\"not_found\"}";
        assertThat(MlValidacionParser.resumirError(raw)).isEqualTo("Item not found");
    }

    @Test
    void resumirError_variasCausasError_lasUne() {
        String raw = "{\"cause\":[{\"type\":\"error\",\"message\":\"A inválido\"},{\"type\":\"error\",\"message\":\"B inválido\"}]}";
        assertThat(MlValidacionParser.resumirError(raw)).isEqualTo("A inválido; B inválido");
    }

    @Test
    void resumirError_sinJson_devuelveElTextoTalCual() {
        assertThat(MlValidacionParser.resumirError("Timeout de red")).isEqualTo("Timeout de red");
        assertThat(MlValidacionParser.resumirError(null)).isNull();
    }
}
