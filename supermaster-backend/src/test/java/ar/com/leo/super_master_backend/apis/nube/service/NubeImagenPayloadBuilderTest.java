package ar.com.leo.super_master_backend.apis.nube.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NubeImagenPayloadBuilderTest {

    @Test
    void construir_armaFilenameAttachmentPosition() {
        Map<String, Object> payload = NubeImagenPayloadBuilder.construir("ABC123.jpg", "QUJD", 1);
        assertThat(payload.get("filename")).isEqualTo("ABC123.jpg");
        assertThat(payload.get("attachment")).isEqualTo("QUJD");
        assertThat(payload.get("position")).isEqualTo(1);
    }
}
