package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.nube.config.NubeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * parseLinkNext extrae la URL con rel="next" del header Link y la pasa a relativa al baseUrl.
 * La doc de Tienda Nube recomienda usar las URLs del Link tal cual ("use the Link URLs instead
 * of building your own"), así que NO se modifica el query (no se decodifica ni reconstruye).
 */
class TiendaNubeParseLinkNextTest {

    private TiendaNubeService service() {
        NubeProperties props = new NubeProperties("https://api.tiendanube.com/v1", null, null, 0, null);
        return new TiendaNubeService(null, props, new ObjectMapper(), null);
    }

    @Test
    void extraeRelNext_yLaPasaARelativa() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Link", "<https://api.tiendanube.com/v1/999/categories?per_page=200&page=2>; rel=\"last\", "
                + "<https://api.tiendanube.com/v1/999/categories?per_page=200&page=2>; rel=\"next\"");

        String next = service().parseLinkNext(headers);

        // Tal cual viene en el Link (relativa al baseUrl), sin tocar el query.
        assertThat(next).isEqualTo("/999/categories?per_page=200&page=2");
    }

    @Test
    void sinHeaderNext_devuelveNull() {
        assertThat(service().parseLinkNext(new HttpHeaders())).isNull();
    }
}
