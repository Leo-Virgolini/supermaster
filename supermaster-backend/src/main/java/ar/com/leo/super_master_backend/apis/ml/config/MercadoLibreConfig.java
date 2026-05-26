package ar.com.leo.super_master_backend.apis.ml.config;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(MercadoLibreProperties.class)
public class MercadoLibreConfig {

    // Mantenemos referencia para poder cerrarlo en @PreDestroy y evitar acumulación
    // de instancias entre reloads del context (especialmente con devtools en dev).
    private HttpClient httpClient;

    @Bean
    public RestClient mercadoLibreRestClient(MercadoLibreProperties properties) {
        // HttpClient de Java 11+ con connection pool + keep-alive nativos.
        // Sustituye a SimpleClientHttpRequestFactory (HttpURLConnection sin pool),
        // que limitaba la concurrencia real con 25+ threads paralelos.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(this.httpClient);
        factory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader("accept", "application/json")
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
