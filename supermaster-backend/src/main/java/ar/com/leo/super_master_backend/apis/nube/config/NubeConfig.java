package ar.com.leo.super_master_backend.apis.nube.config;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(NubeProperties.class)
public class NubeConfig {

    private HttpClient httpClient;

    @Bean
    public RestClient nubeRestClient(NubeProperties properties) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(this.httpClient);
        factory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("User-Agent", properties.userAgent())
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
    }
}
