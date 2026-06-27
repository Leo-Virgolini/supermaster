package ar.com.leo.super_master_backend.apis.openai.config;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, OpenAiImageProperties.class})
public class OpenAiConfig {

    private HttpClient httpClient;
    private HttpClient imageHttpClient;

    @Bean
    public RestClient openaiRestClient(OpenAiProperties properties) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(this.httpClient);
        factory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    public RestClient openaiImageRestClient(OpenAiImageProperties properties) {
        this.imageHttpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(this.imageHttpClient);
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @PreDestroy
    void shutdown() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception ignore) {
                // close() puede tirar si hay tareas en vuelo; no es crítico al shutdown.
            }
        }
        if (imageHttpClient != null) {
            try {
                imageHttpClient.close();
            } catch (Exception ignore) {
                // close() puede tirar si hay tareas en vuelo; no es crítico al shutdown.
            }
        }
    }
}
