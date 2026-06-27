package ar.com.leo.super_master_backend.apis.openai.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiPropertiesTest {
    @Test
    void defaults_timeouts() {
        OpenAiProperties p = new OpenAiProperties(null, null, null);
        assertThat(p.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(p.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(p.readTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void respeta_baseUrl_provisto() {
        OpenAiProperties p = new OpenAiProperties("https://custom.api.com", null, null);
        assertThat(p.baseUrl()).isEqualTo("https://custom.api.com");
    }
}
